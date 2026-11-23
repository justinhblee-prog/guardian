"""Tkinter desktop control panel for Guardian Windows DNS protection."""
import json,os,sys,socket,struct,subprocess,time,urllib.request,webbrowser
from pathlib import Path
import tkinter as tk
from tkinter import messagebox
from dns_engine import DomainRules,DnsProxy
from windows_engine.network_config import DnsAdapterManager,NetworkConfigError,is_admin
from windows_engine.filter_updates import FilterUpdater
from windows_engine.license_client import LicenseClientError,WindowsLicenseClient
from windows_engine.daemon_health import daemon_health_is_fresh, daemon_process_is_running
APP_DIR=Path(os.environ.get("PROGRAMDATA",r"C:\ProgramData"))/"Guardian"
CONFIG=APP_DIR/"guardian.json"; BACKUP=APP_DIR/"dns-backup.json"; STATE=APP_DIR/"protection.json"; STATS=APP_DIR/"dns-stats.json"; HEALTH=APP_DIR/"daemon-health.json"; LOG_FILE=APP_DIR/"logs"/"proxy-daemon.log"
TASK_NAME="GuardianDnsProxy"
DEFAULT_RULES="! Guardian bootstrap rules\n||doubleclick.net^\n||googlesyndication.com^\n||googleadservices.com^\n||adservice.google.com^\n||adnxs.com^\n||criteo.com^\n||taboola.com^\n||outbrain.com^\n||scorecardresearch.com^\n||quantserve.com^\n||mixpanel.com^\n||hotjar.com\n"
def ensure_config():
    APP_DIR.mkdir(parents=True,exist_ok=True); rules=APP_DIR/"rules.txt"
    if not rules.exists(): rules.write_text(DEFAULT_RULES,encoding="utf-8")
    if CONFIG.exists(): data=json.loads(CONFIG.read_text(encoding="utf-8"))
    else: data={"rules_file":"rules.txt","listen_port":53,"upstreams":["1.1.1.1","8.8.8.8","2606:4700:4700::1111","2001:4860:4860::8888"]}
    data.setdefault("manifest_base_url","")
    data.setdefault("manifest_public_key","")
    data.setdefault("filter_source_id","adguard")
    data.setdefault("license_api_base_url","")
    data.setdefault("license_public_key","")
    CONFIG.write_text(json.dumps(data,indent=2),encoding="utf-8")
def elevate():
    try:
        import ctypes
        exe=sys.executable; params="--elevated" if getattr(sys,"frozen",False) else "-m windows_engine.desktop --elevated"
        return int(ctypes.windll.shell32.ShellExecuteW(None,"runas",exe,params,str(Path.cwd()),1))>32
    except Exception: return False
def load_runtime_config(path):
    data=json.loads(path.read_text(encoding="utf-8"))
    rules=DomainRules.from_text(path.with_name(data.get("rules_file","rules.txt")).read_text(encoding="utf-8"))
    if not rules.blocked: raise NetworkConfigError("No valid DNS filter rules are loaded; protection will not start")
    return data,rules
def write_state(enabled):
    APP_DIR.mkdir(parents=True,exist_ok=True); tmp=STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps({"enabled":bool(enabled),"updatedAt":time.time()}),encoding="utf-8")
    os.replace(tmp,STATE)
def blocked_probe_query():
    labels=b"".join(bytes([len(p)])+p.encode("ascii") for p in "doubleclick.net".split("."))+b"\0"
    return struct.pack("!HHHHHH",0x6a31,0x0100,1,0,0,0)+labels+struct.pack("!HH",1,1)
def is_dns_probe_response(query,response):
    # Readiness means the DNS listener answered, not that a particular
    # domain is blocked. User filter lists and allowlists are configurable.
    return (len(response)>=12 and len(query)>=12
            and response[:2]==query[:2]
            and bool(response[2]&0x80)
            and struct.unpack("!H",response[4:6])[0]==1)
def probe_tcp_dns(query):
    with socket.create_connection(("127.0.0.1",53),timeout=.8) as client:
        client.settimeout(.8); client.sendall(struct.pack("!H",len(query))+query)
        header=b""
        while len(header)<2:
            chunk=client.recv(2-len(header))
            if not chunk: return False
            header+=chunk
        length=struct.unpack("!H",header)[0]
        if length<12 or length>4096: return False
        response=b""
        while len(response)<length:
            chunk=client.recv(length-len(response))
            if not chunk: return False
            response+=chunk
        return is_dns_probe_response(query,response)

def proxy_ready():
    # A generic DNS response on loopback is not proof that Guardian owns the port.
    if not daemon_health_is_fresh(HEALTH): return False
    try:
        query=blocked_probe_query()
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.settimeout(.8); client.sendto(query,("127.0.0.1",53)); response,_=client.recvfrom(512)
        if not is_dns_probe_response(query,response): return False
        return probe_tcp_dns(query)
    except OSError: return False
def daemon_readiness_diagnosis():
    details=[]
    try:
        task=run_task("/Query","/TN",TASK_NAME,"/V","/FO","LIST")
        task_text=(task.stdout or task.stderr or "").strip()
        details.append("Task Scheduler query: "+(task_text[:1800] if task_text else f"no output (exit {task.returncode})"))
    except Exception as exc: details.append("Task Scheduler query failed: "+str(exc))
    try:
        health=json.loads(HEALTH.read_text(encoding="utf-8"))
        pid=health.get("pid"); heartbeat=float(health.get("heartbeatAt",0)); age=time.time()-heartbeat
        alive=daemon_process_is_running(HEALTH)
        details.append(f"Daemon heartbeat: pid={pid}, age={age:.1f}s, process_alive={alive}")
    except Exception as exc: details.append("Daemon heartbeat: unavailable ("+str(exc)+")")
    try:
        query=blocked_probe_query()
        details.append("IPv4 TCP 127.0.0.1:53: "+("valid DNS response" if probe_tcp_dns(query) else "invalid or incomplete DNS response"))
    except OSError as exc: details.append("IPv4 TCP 127.0.0.1:53: "+str(exc))
    try:
        query=blocked_probe_query()
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.settimeout(.7); client.sendto(query,("127.0.0.1",53)); response,_=client.recvfrom(512)
        details.append("IPv4 UDP 127.0.0.1:53: "+("DNS response received" if is_dns_probe_response(query,response) else "invalid DNS response"))
    except OSError as exc: details.append("IPv4 UDP 127.0.0.1:53: "+str(exc))
    try:
        for protocol in ("udp","tcp"):
            result=subprocess.run(["netstat.exe","-ano","-p",protocol],capture_output=True,text=True,timeout=5)
            matches=[line.strip() for line in (result.stdout or "").splitlines() if ":53" in line]
            details.append(f"Port 53 {protocol.upper()} endpoints: "+(" | ".join(matches[:8]) if matches else "no matching endpoint reported"))
    except Exception as exc: details.append("Port 53 ownership query failed: "+str(exc))
    try:
        if LOG_FILE.exists():
            tail="\n".join(LOG_FILE.read_text(encoding="utf-8",errors="replace").splitlines()[-18:])
            details.append("Daemon log (last lines):\n"+tail[:4500])
        else: details.append("Daemon log: not created at "+str(LOG_FILE))
    except Exception as exc: details.append("Daemon log could not be read: "+str(exc))
    return "\n".join(details)

def run_task(*args):
    return subprocess.run(["schtasks.exe",*args],capture_output=True,text=True,timeout=15)
def ensure_daemon_running():
    if proxy_ready(): return
    result=run_task("/Run","/TN",TASK_NAME)
    if result.returncode: raise NetworkConfigError(result.stderr.strip() or result.stdout.strip() or "Guardian DNS background task is not installed")
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        if proxy_ready(): return
        time.sleep(.25)
    raise NetworkConfigError("Guardian DNS background task did not become ready on IPv4 loopback port 53.\n\nStartup diagnosis:\n"+daemon_readiness_diagnosis())
def stop_daemon(delete=False):
    run_task("/End","/TN",TASK_NAME)
    if daemon_process_is_running(HEALTH):
        deadline=time.monotonic()+5
        while daemon_process_is_running(HEALTH) and time.monotonic()<deadline:
            time.sleep(.2)
        if daemon_process_is_running(HEALTH):
            raise NetworkConfigError("Guardian DNS background process did not stop; network protection state was not marked stopped")
    if delete:
        run_task("/Delete","/TN",TASK_NAME,"/F")
        for _ in range(5):
            query=run_task("/Query","/TN",TASK_NAME)
            if query.returncode!=0: return
            time.sleep(.3)
        powershell=subprocess.run(["powershell.exe","-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-Command","Unregister-ScheduledTask -TaskName 'GuardianDnsProxy' -Confirm:$false -ErrorAction SilentlyContinue"],capture_output=True,text=True,timeout=20)
        for _ in range(10):
            query=run_task("/Query","/TN",TASK_NAME)
            if query.returncode!=0: return
            time.sleep(.3)
        raise NetworkConfigError("Could not remove the boot-start Guardian DNS task; uninstall must be retried")
def restore_and_remove_task():
    manager=DnsAdapterManager(BACKUP); manager.restore(); write_state(False); stop_daemon(delete=True)
def self_test():
    import traceback,tempfile
    rules=DomainRules.from_text(DEFAULT_RULES); proxy=DnsProxy(rules,listen=(("127.0.0.1",0),("::1",0))); proxy.start()
    try:
        port=proxy._udp_servers[0].server_address[1]; query=blocked_probe_query()
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.settimeout(2); client.sendto(query,("127.0.0.1",port)); response,_=client.recvfrom(512)
        if response[3]&15!=3: raise RuntimeError("IPv4 blocked domain did not return NXDOMAIN")
        v6port=proxy._udp_servers[1].server_address[1]
        with socket.socket(socket.AF_INET6,socket.SOCK_DGRAM) as client:
            client.settimeout(2); client.sendto(query,("::1",v6port,0,0)); response6,_=client.recvfrom(512)
        if response6[3]&15!=3: raise RuntimeError("IPv6 blocked domain did not return NXDOMAIN")
        proxy.stop()
        if sys.stdout: print("Guardian Windows IPv4/IPv6 executable self-test passed")
    except Exception:
        Path(tempfile.gettempdir(),"GuardianDesktopSelfTest.log").write_text(traceback.format_exc(),encoding="utf-8")
        raise
class GuardianDesktop:
    def __init__(self,root):
        self.root=root; self.manager=DnsAdapterManager(BACKUP); self.active=False; self.current_page='home'; self.pages={}; self.nav_buttons={}
        self.status=tk.StringVar(value='보호 꺼짐')
        root.title('Guardian AdBlock'); root.geometry('1180x760'); root.minsize(1020,680); root.configure(bg='#101820')
        shell=tk.Frame(root,bg='#101820'); shell.pack(fill='both',expand=True)
        sidebar=tk.Frame(shell,bg='#111e2a',width=228); sidebar.pack(side='left',fill='y'); sidebar.pack_propagate(False)
        tk.Label(sidebar,text='GUARDIAN',font=('Segoe UI',22,'bold'),fg='#f2f6fa',bg='#111e2a').pack(anchor='w',padx=22,pady=(28,2))
        tk.Label(sidebar,text='WINDOWS SECURITY',font=('Segoe UI',9,'bold'),fg='#65d6ae',bg='#111e2a').pack(anchor='w',padx=23,pady=(0,24))
        self.nav=tk.Frame(sidebar,bg='#111e2a'); self.nav.pack(fill='x',padx=12)
        self.content=tk.Frame(shell,bg='#101820'); self.content.pack(side='left',fill='both',expand=True)
        top=tk.Frame(self.content,bg='#101820'); top.pack(fill='x',padx=30,pady=(24,10))
        self.page_title=tk.Label(top,text='대시보드',font=('Segoe UI',22,'bold'),fg='#f2f6fa',bg='#101820'); self.page_title.pack(side='left')
        self.header_status=tk.Label(top,textvariable=self.status,font=('Segoe UI',10,'bold'),fg='#f3b64a',bg='#263440',padx=14,pady=8); self.header_status.pack(side='right')
        self.workspace=tk.Frame(self.content,bg='#101820'); self.workspace.pack(fill='both',expand=True,padx=30,pady=(10,28))
        self.pages={name:tk.Frame(self.workspace,bg='#101820') for name in ('home','protection','filters','exclusions','activity','premium','updates','settings')}
        nav_items=[('home','홈'),('protection','보호'),('exclusions','앱 제외'),('activity','활동'),('settings','설정')]
        for key,label in nav_items:
            button=tk.Button(self.nav,text=label,anchor='w',command=lambda k=key:self.show_page(k),bg='#111e2a',fg='#bdcbd7',activebackground='#234a43',activeforeground='white',relief='flat',bd=0,font=('Segoe UI',10,'bold'),padx=14,pady=12,cursor='hand2')
            button.pack(fill='x',pady=2); self.nav_buttons[key]=button
        home=self.pages['home']
        tk.Label(home,text='기기 전체 DNS 보호 상태와 차단 활동을 관리합니다.',font=('Segoe UI',11),fg='#9fb2c3',bg='#101820').pack(anchor='w',pady=(0,22))
        self.status_label=tk.Label(home,textvariable=self.status,font=('Segoe UI',19,'bold'),fg='#f3b64a',bg='#101820'); self.status_label.pack(anchor='w',pady=10)
        actions=tk.Frame(home,bg='#101820'); actions.pack(anchor='w',pady=10)
        self.start_button=tk.Button(actions,text='보호 시작',command=self.start_protection,width=18,height=2,bg='#197a57',fg='white',font=('Segoe UI',11,'bold'),relief='flat',cursor='hand2'); self.start_button.pack(side='left',padx=(0,10))
        self.stop_button=tk.Button(actions,text='보호 중지',command=self.stop_protection,width=18,height=2,bg='#8b3940',fg='white',font=('Segoe UI',11,'bold'),relief='flat',state='disabled',cursor='hand2'); self.stop_button.pack(side='left')
        self.start_buttons=[self.start_button]; self.stop_buttons=[self.stop_button]
        self.stats=tk.Label(home,text='차단 0건  |  전달 0건',font=('Segoe UI',15,'bold'),fg='#d7e0e8',bg='#101820'); self.stats.pack(anchor='w',pady=24)
        self.license_status=tk.Label(home,text='라이선스: 무료',font=('Segoe UI',10),fg='#9fb2c3',bg='#101820'); self.license_status.pack(anchor='w',pady=5)
        self.filter_status=tk.Label(home,text='필터 업데이트: 서버 미설정',font=('Segoe UI',10),fg='#9fb2c3',bg='#101820'); self.filter_status.pack(anchor='w',pady=5)
        tk.Label(home,text='백그라운드 DNS 보호 · IPv4/IPv6 · 기존 DNS 자동 복구',font=('Segoe UI',10),fg='#9fb2c3',bg='#101820').pack(anchor='w',pady=18)
        tk.Label(home,text='앱 창을 닫아도 보호는 유지됩니다. 보호 중지 시 기존 DNS 설정을 복구합니다.',font=('Segoe UI',9),fg='#aab8c4',bg='#101820').pack(anchor='w')
        self._section('protection','보호 설정','보호 시작·중지와 DNS 엔진 상태를 관리합니다.')
        protection_start=tk.Button(self.pages['protection'],text='보호 시작',command=self.start_protection,width=18,height=2,bg='#197a57',fg='white',relief='flat')
        protection_start.pack(anchor='w',pady=8); self.start_buttons.append(protection_start)
        protection_stop=tk.Button(self.pages['protection'],text='보호 중지',command=self.stop_protection,width=18,height=2,bg='#8b3940',fg='white',relief='flat',state='disabled')
        protection_stop.pack(anchor='w',pady=8); self.stop_buttons.append(protection_stop)
        self._section('filters','필터 관리','서명된 광고·추적·위협 필터 목록을 업데이트합니다. 프로그램 업데이트와 별개입니다.')
        tk.Button(self.pages['filters'],text='필터 업데이트 실행',command=self.update_filters,width=22,height=2,bg='#197a57',fg='white',relief='flat').pack(anchor='w',pady=8)
        tk.Button(self.pages['filters'],text='필터 서버 설정',command=self.open_filter_settings,width=22,height=2,bg='#33404c',fg='white',relief='flat').pack(anchor='w',pady=8)
        self._section('exclusions','앱 제외 관리','DNS 필터링에서 제외할 앱 정책을 설정합니다.')
        tk.Label(self.pages['exclusions'],text='현재 Windows DNS 프록시는 DNS 요청의 도메인만 처리하며 요청을 보낸 Windows 프로세스 정보를 제공하지 않습니다. 따라서 앱별 제외를 임의로 적용하지 않습니다. 앱별 정책을 안전하게 지원하려면 Windows Filtering Platform 기반 프로세스 식별·정책 계층과 관리자 권한 설치/제거, IPv4·IPv6 회귀 테스트가 먼저 필요합니다. 현재는 미지원 상태입니다.',wraplength=700,fg='#f0c477',bg='#101820',justify='left').pack(anchor='w',pady=10)
        self._section('activity','활동 및 기록','DNS 요청·차단 통계와 진단 기록을 확인합니다.')
        tk.Label(self.pages['activity'],text='백그라운드 DNS 엔진이 기록한 누적 요청 통계입니다.',fg='#9fb2c3',bg='#101820').pack(anchor='w',pady=(0,14))
        self.activity_stats=tk.Label(self.pages['activity'],text='차단 0건  |  정상 전달 0건  |  오류 0건',font=('Segoe UI',16,'bold'),fg='#d7e0e8',bg='#101820')
        self.activity_stats.pack(anchor='w',pady=10)
        self.activity_updated=tk.Label(self.pages['activity'],text='통계 수집 대기 중',fg='#9fb2c3',bg='#101820')
        self.activity_updated.pack(anchor='w',pady=4)
        tk.Button(self.pages['activity'],text='지금 새로고침',command=self.refresh,width=18,height=2,bg='#294e70',fg='white',relief='flat').pack(anchor='w',pady=12)
        tk.Label(self.pages['activity'],text='통계는 Guardian DNS 엔진이 실행 중일 때 갱신됩니다. 현재는 DNS 요청의 도메인별 상세 이력은 저장하지 않습니다.',wraplength=720,justify='left',fg='#f0c477',bg='#101820').pack(anchor='w',pady=12)
        self._section('premium','라이선스 및 Premium','공통 Guardian 라이선스와 기기 활성화를 관리합니다.')
        tk.Button(self.pages['premium'],text='라이선스 관리',command=self.open_license_settings,width=22,height=2,bg='#5946a8',fg='white',relief='flat').pack(anchor='w',pady=8)
        self._section('updates','프로그램 업데이트','Guardian Windows 앱의 새 버전을 확인합니다. 필터 목록 갱신과 다른 기능입니다.')
        self.update_version=tk.Label(self.pages['updates'],text='설치 버전: 0.1.0-dev',fg='#d7e0e8',bg='#101820',font=('Segoe UI',12,'bold')); self.update_version.pack(anchor='w',pady=10)
        self.program_update_status=tk.StringVar(value='업데이트 확인 전')
        tk.Label(self.pages['updates'],textvariable=self.program_update_status,wraplength=760,justify='left',fg='#bdcbd7',bg='#101820').pack(anchor='w',pady=12)
        row=tk.Frame(self.pages['updates'],bg='#101820'); row.pack(anchor='w',pady=10)
        tk.Button(row,text='업데이트 확인',command=self.check_program_updates,width=18,height=2,bg='#197a57',fg='white',relief='flat').pack(side='left',padx=(0,10))
        self.open_release_button=tk.Button(row,text='공식 릴리스 페이지',command=self.open_release_page,state='disabled',width=20,height=2,bg='#294e70',fg='white',relief='flat'); self.open_release_button.pack(side='left')
        tk.Label(self.pages['updates'],text='안전상 자동 설치는 수행하지 않습니다. 정식 서명 검증 및 업데이트 설치 절차가 검증되기 전까지 공식 릴리스에서 수동 설치합니다.',wraplength=760,justify='left',fg='#f0c477',bg='#101820').pack(anchor='w',pady=10)
        self.show_settings_page()
        self.show_page('home')
        self.refresh_license_status()
        root.protocol('WM_DELETE_WINDOW',self.close); self.recover_previous_state(); self.refresh()
    def show_error_dialog(self,title,message):
        """Show actionable diagnostics with reliable copy/select-all support."""
        detail=str(message)
        dialog=tk.Toplevel(self.root)
        dialog.title(title)
        dialog.configure(bg="#101820")
        dialog.geometry("780x540")
        dialog.minsize(560,360)
        dialog.transient(self.root)
        dialog.grab_set()
        header=tk.Frame(dialog,bg="#101820")
        header.pack(fill="x",padx=18,pady=(16,8))
        tk.Label(header,text="오류가 발생했습니다",font=("Segoe UI",14,"bold"),
                 fg="#ff9b85",bg="#101820").pack(anchor="w")
        tk.Label(header,text="아래 진단 내용을 그대로 복사해 전달할 수 있습니다.",
                 font=("Segoe UI",9),fg="#bdcbd7",bg="#101820").pack(anchor="w",pady=(4,0))
        area=tk.Frame(dialog,bg="#101820")
        area.pack(fill="both",expand=True,padx=18,pady=8)
        scrollbar=tk.Scrollbar(area,orient="vertical")
        scrollbar.pack(side="right",fill="y")
        text=tk.Text(area,wrap="word",undo=False,relief="flat",bd=0,
                     bg="#172635",fg="#f2f6fa",insertbackground="white",
                     selectbackground="#287c70",selectforeground="white",
                     font=("Consolas",9),padx=12,pady=10,
                     yscrollcommand=scrollbar.set)
        text.pack(side="left",fill="both",expand=True)
        scrollbar.configure(command=text.yview)
        text.insert("1.0",detail)
        text.configure(state="disabled")
        feedback=tk.StringVar(value="오류 내용을 선택하거나 전체 복사할 수 있습니다.")
        tk.Label(dialog,textvariable=feedback,font=("Segoe UI",9),
                 fg="#9fb2c3",bg="#101820").pack(anchor="w",padx=18,pady=(2,6))
        buttons=tk.Frame(dialog,bg="#101820")
        buttons.pack(fill="x",padx=18,pady=(0,16))
        def copy_all():
            try:
                dialog.clipboard_clear()
                dialog.clipboard_append(detail)
                dialog.update_idletasks()
                feedback.set("오류 내용 전체를 클립보드에 복사했습니다.")
            except tk.TclError as error:
                feedback.set("클립보드 복사 실패: "+str(error))
        def select_all(event=None):
            text.configure(state="normal")
            text.tag_add("sel","1.0","end-1c")
            text.mark_set("insert","1.0")
            text.configure(state="disabled")
            text.focus_set()
            return "break"
        def copy_selection(event=None):
            try:
                selected=text.get("sel.first","sel.last")
            except tk.TclError:
                selected=detail
            try:
                dialog.clipboard_clear()
                dialog.clipboard_append(selected)
                dialog.update_idletasks()
                feedback.set("선택한 오류 내용을 클립보드에 복사했습니다.")
            except tk.TclError as error:
                feedback.set("클립보드 복사 실패: "+str(error))
            return "break"
        text.bind("<Control-a>",select_all)
        text.bind("<Control-A>",select_all)
        text.bind("<Control-c>",copy_selection)
        text.bind("<Control-C>",copy_selection)
        tk.Button(buttons,text="오류 내용 복사",command=copy_all,
                  bg="#197a57",fg="white",activebackground="#238b67",
                  relief="flat",padx=16,pady=8,cursor="hand2").pack(side="left")
        tk.Button(buttons,text="전체 선택",command=select_all,
                  bg="#294e70",fg="white",relief="flat",
                  padx=16,pady=8,cursor="hand2").pack(side="left",padx=8)
        tk.Button(buttons,text="닫기",command=dialog.destroy,
                  bg="#33404c",fg="white",relief="flat",
                  padx=20,pady=8,cursor="hand2").pack(side="right")
        text.focus_set()
        dialog.bind("<Escape>",lambda _event:dialog.destroy())
        dialog.bind("<Control-c>",copy_selection)
        dialog.bind("<Control-C>",copy_selection)
        dialog.wait_window()

    def _section(self,page,title,description):
        frame=self.pages[page]
        tk.Label(frame,text=title,font=('Segoe UI',20,'bold'),fg='#f2f6fa',bg='#101820').pack(anchor='w',pady=(8,6))
        tk.Label(frame,text=description,font=('Segoe UI',10),fg='#9fb2c3',bg='#101820',wraplength=760,justify='left').pack(anchor='w',pady=(0,18))
    def show_page(self,page):
        for frame in self.pages.values(): frame.pack_forget()
        if page == 'settings':
            self.show_settings_page()
        self.pages[page].pack(fill='both',expand=True)
        labels={'home':'대시보드','protection':'보호 설정','filters':'필터 관리','exclusions':'앱 제외 관리','activity':'활동 및 기록','premium':'라이선스 · Premium','updates':'프로그램 업데이트','settings':'일반 설정 · 지원'}
        if page != 'settings':
            self.page_title.configure(text=labels[page])
        self.current_page=page
        selected_page = page if page in self.nav_buttons else ('settings' if page in {'filters','premium','updates'} else page)
        for key,button in self.nav_buttons.items(): button.configure(bg='#234a43' if key==selected_page else '#111e2a',fg='white' if key==selected_page else '#bdcbd7')
    def _clear_settings_page(self):
        for widget in self.pages['settings'].winfo_children():
            widget.destroy()
        self.settings_detail = None
        self.settings_category_buttons = {}

    def _settings_subpage_header(self,title,description):
        for widget in self.settings_detail.winfo_children():
            widget.destroy()
        self.page_title.configure(text=title)
        tk.Label(self.settings_detail,text=title,font=('Segoe UI',20,'bold'),
                 fg='#f2f6fa',bg='#101820').pack(anchor='w',pady=(4,6))
        tk.Label(self.settings_detail,text=description,font=('Segoe UI',10),
                 fg='#9fb2c3',bg='#101820',wraplength=720,justify='left').pack(anchor='w',pady=(0,18))

    def _select_settings_category(self,key,action):
        for name,button in self.settings_category_buttons.items():
            selected=name==key
            button.configure(bg='#234a43' if selected else '#151F32',
                             fg='#ffffff' if selected else '#bdcbd7')
        action()

    def show_settings_page(self):
        self._clear_settings_page()
        self.page_title.configure(text='설정')
        split=tk.Frame(self.pages['settings'],bg='#101820')
        split.pack(fill='both',expand=True)
        sidebar=tk.Frame(split,bg='#111e2a',width=232,highlightthickness=1,
                         highlightbackground='#263650')
        sidebar.pack(side='left',fill='y',padx=(0,18))
        sidebar.pack_propagate(False)
        self.settings_detail=tk.Frame(split,bg='#101820')
        self.settings_detail.pack(side='left',fill='both',expand=True)
        tk.Label(sidebar,text='설정 메뉴',font=('Segoe UI',13,'bold'),
                 fg='#f2f6fa',bg='#111e2a').pack(anchor='w',padx=14,pady=(16,12))
        sections=[
            ('general','일반','앱 동작과 기본 환경',self.show_general_settings),
            ('filtering','필터링','DNS 보호 및 필터 관리',self.show_filtering_settings),
            ('premium','라이선스 및 프리미엄','이용 기간과 기기 관리',self.show_license_settings),
            ('support','고객 지원','진단과 오류 복사',self.show_support_page),
            ('about','정보','버전과 업데이트',self.show_about_page),
            ('exit','종료','앱 종료 및 보호 유지 안내',self.show_exit_settings),
        ]
        self.settings_category_buttons={}
        for key,title,detail,action in sections:
            button=tk.Button(sidebar,text=title+'\n'+detail,anchor='w',justify='left',
                             command=lambda k=key,a=action:self._select_settings_category(k,a),
                             bg='#151F32',fg='#bdcbd7',activebackground='#234a43',
                             activeforeground='white',relief='flat',bd=0,
                             font=('Segoe UI',10,'bold'),padx=13,pady=12,
                             wraplength=196,cursor='hand2')
            button.pack(fill='x',padx=9,pady=3)
            self.settings_category_buttons[key]=button
        self._select_settings_category('general',self.show_general_settings)

    def show_exit_settings(self):
        self._settings_subpage_header('종료','Guardian 앱 창을 닫아도 백그라운드 DNS 보호는 명시적으로 중지하지 않는 한 유지됩니다.')
        tk.Button(self.settings_detail,text='Guardian 종료',command=self.confirm_exit,
                  bg='#8b3940',fg='white',relief='flat',padx=18,pady=10).pack(anchor='w')

    def show_general_settings(self):
        self._settings_subpage_header('일반','Windows 앱의 기본 동작과 백그라운드 보호 동작을 확인합니다.')
        tk.Label(self.settings_detail,text='앱 창을 닫아도 DNS 보호 데몬은 백그라운드에서 계속 실행됩니다. 보호를 해제하려면 보호 화면에서 명시적으로 중지하세요.',
                 font=('Segoe UI',10),fg='#bdcbd7',bg='#101820',wraplength=760,justify='left').pack(anchor='w',pady=(0,14))
        tk.Button(self.settings_detail,text='시스템 진단 보기',command=self.show_diagnostics,
                  bg='#197a57',fg='white',relief='flat',padx=16,pady=9).pack(anchor='w')

    def show_filtering_settings(self):
        self._settings_subpage_header('필터링','서명된 광고·추적·위협 필터와 DNS 규칙 공급원을 관리합니다. 프로그램 업데이트와 별개입니다.')
        tk.Label(self.settings_detail,text='필터 목록을 갱신하면 서명과 SHA-256을 검증한 뒤 로컬 규칙을 교체합니다.',
                 font=('Segoe UI',10),fg='#bdcbd7',bg='#101820',wraplength=760,justify='left').pack(anchor='w',pady=(0,14))
        tk.Button(self.settings_detail,text='필터 서버 설정',command=self.open_filter_settings,
                  bg='#26364B',fg='white',relief='flat',padx=16,pady=9).pack(anchor='w',pady=(0,8))
        tk.Button(self.settings_detail,text='필터 목록 지금 업데이트',command=self.update_filters,
                  bg='#197a57',fg='white',relief='flat',padx=16,pady=9).pack(anchor='w')

    def show_license_settings(self):
        self._settings_subpage_header('라이선스 및 프리미엄','Windows와 Android는 동일한 Guardian 라이선스와 기기 활성화 한도를 공유합니다.')
        tk.Label(self.settings_detail,text='발급받은 키를 활성화하고 이 기기의 등록 상태를 갱신하거나 해제할 수 있습니다.',
                 font=('Segoe UI',10),fg='#bdcbd7',bg='#101820',wraplength=760,justify='left').pack(anchor='w',pady=(0,14))
        tk.Button(self.settings_detail,text='라이선스 활성화 및 기기 관리',command=self.open_license_settings,
                  bg='#5946a8',fg='white',relief='flat',padx=16,pady=9).pack(anchor='w')

    def show_support_page(self):
        self._settings_subpage_header('고객 지원','문제 해결을 위해 DNS 상태, 포트 점유, 서비스 작업 상태와 오류 로그를 확인하고 복사할 수 있습니다.')
        tk.Button(self.settings_detail,text='시스템 진단 및 오류 내용 보기',command=self.show_diagnostics,
                  bg='#197a57',fg='white',relief='flat',padx=16,pady=9).pack(anchor='w')

    def show_diagnostics(self):
        self._settings_subpage_header('시스템 진단','아래 전체 진단 내용을 복사해 지원 요청에 첨부할 수 있습니다.')
        area=tk.Frame(self.settings_detail,bg='#101820')
        area.pack(fill='both',expand=True)
        scroll=tk.Scrollbar(area,orient='vertical')
        scroll.pack(side='right',fill='y')
        report=tk.Text(area,wrap='word',bg='#172635',fg='#f2f6fa',insertbackground='white',
                       selectbackground='#287c70',relief='flat',padx=12,pady=10,
                       yscrollcommand=scroll.set,height=16)
        report.pack(side='left',fill='both',expand=True)
        scroll.configure(command=report.yview)
        try:
            details=daemon_readiness_diagnosis()
        except Exception as exc:
            details='진단 정보 생성 실패: '+str(exc)
        report.insert('1.0',details)
        report.configure(state='disabled')
        actions=tk.Frame(self.settings_detail,bg='#101820')
        actions.pack(anchor='w',pady=10)
        def copy_report():
            try:
                self.root.clipboard_clear()
                self.root.clipboard_append(details)
                self.root.update_idletasks()
                feedback.configure(text='전체 진단 정보를 클립보드에 복사했습니다.',fg='#54d69b')
            except tk.TclError as exc:
                feedback.configure(text='복사 실패: '+str(exc),fg='#ff9b85')
        feedback=tk.Label(self.settings_detail,text='Ctrl+C 또는 전체 복사 버튼으로 진단 내용을 복사할 수 있습니다.',
                          fg='#9fb2c3',bg='#101820')
        feedback.pack(anchor='w')
        tk.Button(actions,text='오류 내용 복사',command=copy_report,bg='#197a57',fg='white',
                  relief='flat',padx=14,pady=8).pack(side='left')
        def select_all():
            report.configure(state='normal')
            report.tag_add('sel','1.0','end')
            report.configure(state='disabled')
            report.focus_set()
        tk.Button(actions,text='전체 선택',command=select_all,bg='#294e70',fg='white',
                  relief='flat',padx=14,pady=8).pack(side='left',padx=8)
        report.bind('<Control-a>',lambda _event:(select_all(),'break'))
        report.bind('<Control-c>',lambda _event:(copy_report(),'break'))
        report.bind('<Control-C>',lambda _event:(copy_report(),'break'))

    def show_about_page(self):
        self._settings_subpage_header('정보','Guardian AdBlock for Windows 제품 및 업데이트 정보를 확인합니다.')
        tk.Label(self.settings_detail,text='Guardian AdBlock',font=('Segoe UI',16,'bold'),
                 fg='#f2f6fa',bg='#101820').pack(anchor='w')
        tk.Label(self.settings_detail,text='설치 버전: '+self.update_version.cget('text').replace('설치 버전: ',''),
                 font=('Segoe UI',10),fg='#9fb2c3',bg='#101820').pack(anchor='w',pady=(8,4))
        tk.Label(self.settings_detail,text='주식회사 유진컨설팅 · Guardian AdBlock',
                 font=('Segoe UI',10),fg='#9fb2c3',bg='#101820').pack(anchor='w')
        tk.Label(self.settings_detail,text='프로그램 업데이트는 왼쪽의 별도 메뉴에서 확인합니다. 필터 업데이트와 완전히 분리되어 있습니다.',
                 font=('Segoe UI',9),fg='#9fb2c3',bg='#101820',wraplength=740,justify='left').pack(anchor='w',pady=(12,0))
        tk.Button(self.settings_detail,text='프로그램 업데이트 열기',
                  command=lambda:self.show_page('updates'),bg='#294e70',fg='white',
                  relief='flat',padx=14,pady=8).pack(anchor='w',pady=(12,0))

    def confirm_exit(self):
        if messagebox.askyesno('Guardian 종료','Guardian 앱 창을 종료하시겠습니까? 백그라운드 DNS 보호는 명시적으로 중지하지 않는 한 유지됩니다.',parent=self.root):
            self.close()

    def check_program_updates(self):
        self.program_update_status.set('공식 GitHub 릴리스 확인 중...'); self.root.update_idletasks()
        try:
            request=urllib.request.Request('https://api.github.com/repos/justinhblee-prog/guardian/releases?per_page=20',headers={'Accept':'application/vnd.github+json','User-Agent':'GuardianAdBlock-Windows'})
            with urllib.request.urlopen(request,timeout=12) as response: releases=json.loads(response.read(1024*1024).decode('utf-8'))
            release=next((item for item in releases if not item.get('draft') and not item.get('prerelease')),None)
            if not release: self.program_update_status.set('공식 릴리스가 아직 게시되지 않았습니다.'); return
            self.latest_release_url=release.get('html_url',''); tag=release.get('tag_name','버전 정보 없음')
            assets=release.get('assets',[]); installer=next((item for item in assets if item.get('name','').lower().endswith('.exe') and 'windows' in item.get('name','').lower()),None)
            self.program_update_status.set('최신 릴리스: '+tag+'\n'+('Windows 설치 파일: '+installer.get('name') if installer else 'Windows 설치 파일이 릴리스 자산에 없습니다.'))
            self.open_release_button.configure(state='normal' if self.latest_release_url else 'disabled')
        except Exception as exc: self.program_update_status.set('업데이트 확인 실패: '+str(exc)); self.open_release_button.configure(state='disabled')
    def open_release_page(self):
        url=getattr(self,'latest_release_url','')
        if url: webbrowser.open(url)
    def set_active_ui(self,active,label=None):
        self.active=active; self.status.set(label or ("보호 실행 중" if active else "보호 꺼짐"))
        self.status_label.configure(fg="#54d69b" if active else "#f3b64a")
        self.header_status.configure(fg="#54d69b" if active else "#f3b64a",bg="#183b32" if active else "#263440")
        for button in self.start_buttons: button.configure(state="disabled" if active else "normal")
        for button in self.stop_buttons: button.configure(state="normal" if active else "disabled")
    def recover_previous_state(self):
        if BACKUP.exists():
            try:
                state=json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {}
                if state.get("enabled") is True:
                    ensure_daemon_running(); self.set_active_ui(True,"보호 실행 중 · 재연결됨"); return
                self.manager.restore(); write_state(False)
            except Exception as exc:
                # If DNS still points at loopback, never stop a possibly live proxy.
                if BACKUP.exists() and proxy_ready(): self.set_active_ui(True,"보호 경로 유지 중 · 복구 확인 필요")
                else:
                    try: self.manager.restore(); write_state(False)
                    except Exception: pass
                [button.configure(state="disabled") for button in self.start_buttons]
                self.show_error_dialog("Guardian DNS 복구",f"이전 보호 상태를 자동 복구하지 못했습니다. DNS 백업을 보존했습니다.\n\n{exc}")
        elif STATE.exists():
            write_state(False)
            try: stop_daemon()
            except Exception as exc:
                self.set_active_ui(False,"보호 꺼짐 · 백그라운드 종료 확인 필요")
                self.show_error_dialog("Guardian DNS 백그라운드 종료",f"DNS 보호는 꺼졌지만 백그라운드 프로세스 종료를 확인하지 못했습니다.\n\n{exc}")
    def start_protection(self):
        if self.active: return
        try:
            ensure_config(); load_runtime_config(CONFIG); write_state(True); ensure_daemon_running()
            count=self.manager.enable(); self.set_active_ui(True,f"보호 실행 중 · 어댑터 {count}개")
        except Exception as exc:
            if BACKUP.exists():
                # A partial rollback leaves DNS on loopback; keep the daemon alive.
                if proxy_ready(): self.set_active_ui(True,"보호 경로 유지 중 · DNS 복구 필요")
                else: [button.configure(state="disabled") for button in self.start_buttons]
            else:
                write_state(False)
                try: stop_daemon()
                except Exception as stop_error:
                    self.set_active_ui(False,"보호 시작 실패 · 백그라운드 종료 확인 필요")
                    self.show_error_dialog("Guardian 보호 시작 실패",f"{exc}\n\n백그라운드 종료 실패: {stop_error}")
                    return
                self.set_active_ui(False,"보호 시작 실패")
            self.show_error_dialog("Guardian 보호 시작 실패",str(exc))
    def stop_protection(self):
        if not self.active and not BACKUP.exists(): return
        try: self.manager.restore()
        except Exception as exc:
            self.set_active_ui(True,"보호 경로 유지 중 · DNS 복구 필요")
            self.show_error_dialog("DNS 복구 실패",f"백그라운드 DNS 프록시를 계속 유지하고 백업을 보존했습니다.\n\n{exc}"); return
        write_state(False)
        try: stop_daemon()
        except Exception as exc:
            self.set_active_ui(False,"DNS 복구 완료 · 백그라운드 종료 확인 필요")
            self.show_error_dialog("Guardian DNS 종료 실패",f"기존 DNS 설정은 복구했지만 백그라운드 프로세스 종료를 확인하지 못했습니다.\n\n{exc}")
            return
        self.set_active_ui(False)
    def open_filter_settings(self):
        try: data=json.loads(CONFIG.read_text(encoding="utf-8"))
        except Exception as exc: self.show_error_dialog("필터 서버 설정",str(exc)); return
        dialog=tk.Toplevel(self.root); dialog.title("서명 필터 서버 설정"); dialog.geometry("560x360"); dialog.configure(bg="#101820")
        tk.Label(dialog,text="HTTPS manifest 기본 주소",fg="white",bg="#101820").pack(anchor="w",padx=16,pady=(14,2))
        base=tk.Entry(dialog,width=72); base.insert(0,data.get("manifest_base_url","")); base.pack(fill="x",padx=16)
        tk.Label(dialog,text="필터 source ID",fg="white",bg="#101820").pack(anchor="w",padx=16,pady=(10,2))
        source=tk.Entry(dialog); source.insert(0,data.get("filter_source_id","adguard")); source.pack(fill="x",padx=16)
        tk.Label(dialog,text="RSA 공개키 (X.509 SPKI Base64)",fg="white",bg="#101820").pack(anchor="w",padx=16,pady=(10,2))
        key=tk.Text(dialog,height=5,wrap="word"); key.insert("1.0",data.get("manifest_public_key","")); key.pack(fill="both",expand=True,padx=16)
        def save():
            data["manifest_base_url"]=base.get().strip(); data["filter_source_id"]=source.get().strip(); data["manifest_public_key"]=key.get("1.0","end").strip()
            tmp=CONFIG.with_suffix(".tmp"); tmp.write_text(json.dumps(data,indent=2),encoding="utf-8"); os.replace(tmp,CONFIG)
            dialog.destroy(); messagebox.showinfo("필터 서버 설정","설정이 저장되었습니다. 공개키는 배포 서버의 오프라인 서명 공개키와 정확히 일치해야 합니다.")
        tk.Button(dialog,text="저장",command=save,bg="#197a57",fg="white",relief="flat").pack(pady=10)
    def _license_client(self,data):
        base=data.get("license_api_base_url","").strip()
        public_key=data.get("license_public_key","").strip()
        if not base or not public_key:
            raise LicenseClientError("라이선스 API HTTPS 주소와 RSA 공개키를 먼저 설정하세요.")
        return WindowsLicenseClient(base,public_key,APP_DIR,app_version="0.1.0-dev")
    def refresh_license_status(self):
        try:
            data=json.loads(CONFIG.read_text(encoding="utf-8")) if CONFIG.exists() else {}
            client=self._license_client(data)
            entitlement=client.current_entitlement()
            if entitlement:
                self.license_status.configure(text=f"라이선스: Guardian Pro · 만료 {time.strftime('%Y-%m-%d',time.localtime(entitlement['expiresAt']))}",fg="#54d69b")
            else:
                self.license_status.configure(text="라이선스: 무료",fg="#9fb2c3")
        except Exception:
            self.license_status.configure(text="라이선스: 설정 필요",fg="#f3b64a")
    def open_license_settings(self):
        try: data=json.loads(CONFIG.read_text(encoding="utf-8"))
        except Exception as exc: self.show_error_dialog("Guardian 라이선스",str(exc)); return
        dialog=tk.Toplevel(self.root); dialog.title("Guardian 공통 라이선스"); dialog.geometry("620x520"); dialog.configure(bg="#101820")
        tk.Label(dialog,text="공통 라이선스 API HTTPS 주소",fg="white",bg="#101820").pack(anchor="w",padx=16,pady=(14,2))
        base=tk.Entry(dialog); base.insert(0,data.get("license_api_base_url","")); base.pack(fill="x",padx=16)
        tk.Label(dialog,text="RSA 공개키 (X.509 SPKI Base64)",fg="white",bg="#101820").pack(anchor="w",padx=16,pady=(10,2))
        public=tk.Text(dialog,height=4,wrap="word"); public.insert("1.0",data.get("license_public_key","")); public.pack(fill="x",padx=16)
        tk.Label(dialog,text="Guardian Pro 라이선스 키 (발급받은 키를 입력)",fg="white",bg="#101820").pack(anchor="w",padx=16,pady=(10,2))
        license_key=tk.Entry(dialog); license_key.pack(fill="x",padx=16)
        tk.Label(dialog,text="Windows와 Android는 같은 라이선스를 사용하며 기기 활성화 한도를 공유합니다.",fg="#9fb2c3",bg="#101820",wraplength=560,justify="left").pack(anchor="w",padx=16,pady=10)
        status=tk.StringVar(value="서버 주소와 공개키를 저장한 뒤 라이선스를 활성화하세요.")
        tk.Label(dialog,textvariable=status,fg="#9fb2c3",bg="#101820",wraplength=560,justify="left").pack(anchor="w",padx=16,pady=6)
        def save_config():
            data["license_api_base_url"]=base.get().strip()
            data["license_public_key"]=public.get("1.0","end").strip()
            tmp=CONFIG.with_suffix(".tmp"); tmp.write_text(json.dumps(data,indent=2),encoding="utf-8"); os.replace(tmp,CONFIG)
            return data
        def activate():
            try:
                saved=save_config()
                entitlement=self._license_client(saved).activate(license_key.get())
                status.set("Guardian Pro 활성화 완료 · 만료 "+time.strftime("%Y-%m-%d",time.localtime(entitlement["expiresAt"])))
                self.refresh_license_status()
            except Exception as exc: status.set("활성화 실패: "+str(exc))
        def refresh():
            try:
                saved=save_config()
                entitlement=self._license_client(saved).refresh()
                status.set("라이선스 갱신 완료 · 만료 "+time.strftime("%Y-%m-%d",time.localtime(entitlement["expiresAt"])))
                self.refresh_license_status()
            except Exception as exc: status.set("갱신 실패: "+str(exc))
        def deactivate():
            try:
                saved=save_config()
                self._license_client(saved).deactivate()
                status.set("이 Windows 기기의 라이선스 등록을 해제했습니다.")
                self.refresh_license_status()
            except Exception as exc: status.set("기기 해제 실패: "+str(exc))
        buttons=tk.Frame(dialog,bg="#101820"); buttons.pack(pady=12)
        tk.Button(buttons,text="설정 저장",command=lambda: (save_config(),status.set("설정 저장 완료")),bg="#33404c",fg="white",relief="flat").pack(side="left",padx=5)
        tk.Button(buttons,text="라이선스 활성화",command=activate,bg="#197a57",fg="white",relief="flat").pack(side="left",padx=5)
        tk.Button(buttons,text="갱신",command=refresh,bg="#294e70",fg="white",relief="flat").pack(side="left",padx=5)
        tk.Button(buttons,text="이 기기 해제",command=deactivate,bg="#8b3940",fg="white",relief="flat").pack(side="left",padx=5)
    def update_filters(self):
        try:
            data=json.loads(CONFIG.read_text(encoding="utf-8"))
            base=data.get("manifest_base_url","").strip(); key=data.get("manifest_public_key","").strip(); source=data.get("filter_source_id","adguard").strip()
            if not base or not key: raise NetworkConfigError("서명 필터 업데이트를 사용하려면 HTTPS manifest 주소와 운영 공개키를 먼저 설정해야 합니다.")
            rules_path=CONFIG.with_name(data.get("rules_file","rules.txt"))
            updater=FilterUpdater(base,source,key,APP_DIR/"manifest-state.json",rules_path)
            result=updater.update()
            status={"state":"updated","sourceVersion":result["sourceVersion"],"checkedAt":time.time()}
            tmp=APP_DIR/"filter-update-status.tmp"; tmp.write_text(json.dumps(status),encoding="utf-8"); os.replace(tmp,APP_DIR/"filter-update-status.json")
            messagebox.showinfo("필터 업데이트 완료",f"서명 검증 및 SHA-256 확인 완료\n버전: {result['sourceVersion']}\n차단 규칙: {len(DomainRules.from_text(rules_path.read_text(encoding='utf-8')).blocked):,}개")
        except Exception as exc: self.show_error_dialog("필터 업데이트 실패",str(exc))
    def refresh(self):
        try:
            stats=json.loads(STATS.read_text(encoding="utf-8")) if STATS.exists() else {}
            blocked=int(stats.get('blocked',0)); forwarded=int(stats.get('forwarded',0)); failed=int(stats.get('failed',0))
            summary=f"광고/추적 차단 {blocked:,}건  |  정상 전달 {forwarded:,}건  |  오류 {failed:,}건"
            self.stats.configure(text=summary)
            self.activity_stats.configure(text=summary)
            updated_at=float(stats.get('updatedAt',0) or 0)
            self.activity_updated.configure(text=('마지막 수집: '+time.strftime('%Y-%m-%d %H:%M:%S',time.localtime(updated_at))) if updated_at>0 else 'DNS 엔진 통계 수집 대기 중')
        except (OSError,ValueError,TypeError): pass
        try:
            status=json.loads((APP_DIR/"filter-update-status.json").read_text(encoding="utf-8"))
            label={"updated":"서명 필터 업데이트 완료","current":"최신 필터 사용 중","error":"필터 업데이트 오류"}.get(status.get("state"),"필터 업데이트 상태 확인 필요")
            version=status.get("sourceVersion","")
            self.filter_status.configure(text=f"{label} {version}".strip(),fg="#54d69b" if status.get("state") in ("updated","current") else "#ff9b85")
        except (OSError,ValueError,TypeError): pass
        self.root.after(1000,self.refresh)
    def close(self):
        self.root.destroy()  # Background DNS task intentionally continues until explicit Stop.
def main():
    if "--self-test" in sys.argv: self_test(); return 0
    if "--restore-dns" in sys.argv:
        if not is_admin(): return 1
        restore_and_remove_task(); return 0
    if os.name!="nt": raise SystemExit("Windows 10/11 only")
    if not is_admin(): return 0 if elevate() else 1
    ensure_config(); root=tk.Tk(); GuardianDesktop(root); root.mainloop(); return 0
if __name__=="__main__": raise SystemExit(main())
