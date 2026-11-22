"""Tkinter desktop control panel for Guardian Windows DNS protection."""
import json,os,sys,socket,struct,subprocess,time
from pathlib import Path
import tkinter as tk
from tkinter import messagebox
from dns_engine import DomainRules,DnsProxy
from windows_engine.network_config import DnsAdapterManager,NetworkConfigError,is_admin
from windows_engine.filter_updates import FilterUpdater
from windows_engine.license_client import LicenseClientError,WindowsLicenseClient
from windows_engine.daemon_health import daemon_health_is_fresh, daemon_process_is_running
APP_DIR=Path(os.environ.get("PROGRAMDATA",r"C:\ProgramData"))/"Guardian"
CONFIG=APP_DIR/"guardian.json"; BACKUP=APP_DIR/"dns-backup.json"; STATE=APP_DIR/"protection.json"; STATS=APP_DIR/"dns-stats.json"; HEALTH=APP_DIR/"daemon-health.json"
TASK_NAME="GuardianDnsProxy"
def daemon_executable_path():
    if getattr(sys,"frozen",False):
        return Path(sys.executable).resolve().parent / "GuardianDnsProxy" / "GuardianDnsProxy.exe"
    return Path(__file__).resolve().parents[1] / "dist" / "GuardianDnsProxy" / "GuardianDnsProxy.exe"
def daemon_startup_diagnostics():
    details=[]
    daemon_exe=daemon_executable_path()
    details.append(f"DNS 백그라운드 실행 파일: {daemon_exe} (파일 존재: {daemon_exe.is_file()})")
    try:
        task=run_task("/Query","/TN",TASK_NAME,"/V","/FO","LIST")
        output="\n".join(part.strip() for part in (task.stdout,task.stderr) if part and part.strip())
        if output: details.append("작업 스케줄러 상태:\n"+output[-5000:])
        else: details.append("작업 스케줄러 상태를 읽지 못했습니다.")
    except Exception as exc:
        details.append("작업 스케줄러 조회 실패: "+str(exc))
    log_path=APP_DIR/"daemon-startup.log"
    try:
        if log_path.exists():
            lines=log_path.read_text(encoding="utf-8",errors="replace").splitlines()
            details.append("DNS 백그라운드 시작 로그:\n"+"\n".join(lines[-50:]))
        else:
            details.append("DNS 백그라운드 시작 로그가 없습니다: "+str(log_path))
    except OSError as exc:
        details.append("DNS 백그라운드 로그 읽기 실패: "+str(exc))
    try:
        health=json.loads(HEALTH.read_text(encoding="utf-8"))
        details.append("최근 프로세스 상태: "+json.dumps(health,ensure_ascii=False))
    except Exception:
        details.append("유효한 DNS 백그라운드 생존 상태 파일이 없습니다.")
    details.extend(dns_port_53_diagnostics())
    return "\n\n".join(details)

def dns_port_53_diagnostics():
    """Identify processes that may prevent Guardian from binding its DNS listeners."""
    details = ["DNS 포트 53 점유 상태:"]
    for protocol in ("udp", "tcp"):
        try:
            result = subprocess.run(
                ["netstat.exe", "-ano", "-p", protocol],
                capture_output=True, text=True, timeout=8,
            )
            matches = []
            for line in result.stdout.splitlines():
                parts = line.split()
                if len(parts) < 4 or not parts[1].endswith(":53"):
                    continue
                pid = parts[-1]
                if not pid.isdigit():
                    continue
                matches.append((parts[1], pid))
            if not matches:
                details.append(f"{protocol.upper()} 53: 점유 프로세스가 확인되지 않았습니다.")
                continue
            for endpoint, pid in matches:
                process = subprocess.run(
                    ["tasklist.exe", "/SVC", "/FI", f"PID eq {pid}", "/FO", "CSV", "/NH"],
                    capture_output=True, text=True, timeout=8,
                )
                owner = process.stdout.strip() or "프로세스/서비스 이름 확인 불가"
                details.append(f"{protocol.upper()} {endpoint} PID {pid}: {owner}")
                if endpoint == "0.0.0.0:53" and protocol == "udp":
                    details.append("주의: UDP 와일드카드 0.0.0.0:53 점유가 loopback DNS 확인과 충돌할 수 있습니다.")
        except (OSError, subprocess.SubprocessError) as exc:
            details.append(f"{protocol.upper()} 포트 조회 실패: {exc}")
    return details

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
def configured_probe_domain():
    """Choose a domain that the currently installed rules definitely block."""
    data=json.loads(CONFIG.read_text(encoding="utf-8"))
    rules_path=APP_DIR/data.get("rules_file","rules.txt")
    rules=DomainRules.from_text(rules_path.read_text(encoding="utf-8"))
    return next((domain for domain in sorted(rules.blocked) if rules.is_blocked(domain)),None)

def blocked_probe_query(domain="doubleclick.net"):
    labels=b"".join(bytes([len(p)])+p.encode("ascii") for p in domain.split("."))+b"\\0"
    return struct.pack("!HHHHHH",0x6a31,0x0100,1,0,0,0)+labels+struct.pack("!HH",1,1)
def is_dns_probe_response(query,response):
    # Readiness means the DNS listener answered, not that a particular
    # domain is blocked. User filter lists and allowlists are configurable.
    return (len(response)>=12 and len(query)>=12
            and response[:2]==query[:2]
            and bool(response[2]&0x80)
            and struct.unpack("!H",response[4:6])[0]==1)
def recv_exact(sock,length):
    """Read a complete TCP DNS frame field; recv(n) may return fewer than n bytes."""
    chunks=[]; remaining=length
    while remaining:
        chunk=sock.recv(remaining)
        if not chunk: raise OSError("DNS TCP response ended early")
        chunks.append(chunk); remaining-=len(chunk)
    return b"".join(chunks)
def proxy_ready():
    # The daemon publishes its heartbeat only after all UDP/TCP listeners bind.
    # Probe TCP because Windows SharedAccess may own wildcard UDP port 53.
    if not daemon_health_is_fresh(HEALTH): return False
    try:
        domain=configured_probe_domain()
        if not domain: return False
        query=blocked_probe_query(domain)
        with socket.socket(socket.AF_INET,socket.SOCK_STREAM) as client:
            client.settimeout(1.0)
            client.connect(("127.0.0.1",53))
            client.sendall(struct.pack("!H",len(query))+query)
            header=recv_exact(client,2)
            length=struct.unpack("!H",header)[0]
            if length<12 or length>4096: return False
            response=recv_exact(client,length)
        return is_dns_probe_response(query,response)
    except (OSError,ValueError,TypeError): return False
def run_task(*args):
    return subprocess.run(["schtasks.exe",*args],capture_output=True,text=True,timeout=15)
def ensure_daemon_running():
    if proxy_ready(): return
    daemon_exe=daemon_executable_path()
    if not daemon_exe.is_file():
        raise NetworkConfigError(f"Guardian DNS 백그라운드 실행 파일을 찾을 수 없습니다: {daemon_exe}. 설치 파일이 누락되었거나 보안 프로그램에 의해 격리되었을 수 있습니다.")

    # Do not issue a second Task Scheduler /Run while a previous daemon process
    # is still alive. IgnoreNew task instances can otherwise report success
    # while the original process remains unhealthy, obscuring the real failure.
    if daemon_process_is_running(HEALTH):
        deadline=time.monotonic()+12
        while time.monotonic()<deadline:
            if proxy_ready(): return
            if not daemon_process_is_running(HEALTH): break
            time.sleep(.25)
        if daemon_process_is_running(HEALTH):
            diagnostics=daemon_startup_diagnostics()
            raise NetworkConfigError("Guardian DNS background process is already running but did not answer on IPv4 loopback port 53; a duplicate start was not requested.\n\n"+diagnostics)

    result=run_task("/Run","/TN",TASK_NAME)
    if result.returncode:
        task_state=run_task("/Query","/TN",TASK_NAME,"/V","/FO","LIST")
        diagnostics=daemon_startup_diagnostics()
        detail=task_state.stdout.strip() or task_state.stderr.strip()
        raise NetworkConfigError((result.stderr.strip() or result.stdout.strip() or "Guardian DNS background task is not installed")+"\n"+detail+"\n"+diagnostics)
    deadline=time.monotonic()+12
    while time.monotonic()<deadline:
        if proxy_ready(): return
        time.sleep(.25)
    task_state=run_task("/Query","/TN",TASK_NAME,"/V","/FO","LIST")
    diagnostics=daemon_startup_diagnostics()
    detail=task_state.stdout.strip() or task_state.stderr.strip()
    raise NetworkConfigError("Guardian DNS background task did not become ready on IPv4 loopback port 53.\n"+detail+"\n"+diagnostics)
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
    BG="#0B1220"; SIDE="#101A2B"; PANEL="#151F32"; ALT="#1B2940"; BORDER="#263650"
    TEXT="#F4F7FB"; MUTED="#93A4BA"; TEAL="#2DD4BF"; GREEN="#34D399"; AMBER="#FBBF24"; BLUE="#60A5FA"
    def __init__(self,root):
        self.root=root; self.manager=DnsAdapterManager(BACKUP); self.active=False; self.status=tk.StringVar(value="보호 꺼짐")
        root.title("Guardian | Windows Security"); root.geometry("1120x760"); root.minsize(900,650); root.configure(bg=self.BG)
        root.option_add("*Font",("Segoe UI",10)); self._build_shell(); self.refresh_license_status()
        root.protocol("WM_DELETE_WINDOW",self.close); self.recover_previous_state(); self.refresh()
    def _label(self,p,t,s=10,c=None,w="normal",bg=None,**kw):
        return tk.Label(p,text=t,font=("Segoe UI",s,w),fg=c or self.TEXT,bg=bg or self.PANEL,**kw)
    def show_error(self, title, message):
        """Show a selectable error report with one-click copy for support/debugging."""
        dialog = tk.Toplevel(self.root)
        dialog.title(title)
        dialog.configure(bg=self.BG)
        dialog.geometry("760x520")
        dialog.minsize(560, 360)
        dialog.transient(self.root)

        header = tk.Frame(dialog, bg=self.PANEL, padx=18, pady=14)
        header.pack(fill="x")
        self._label(header, title, 13, self.TEXT, "bold").pack(anchor="w")
        self._label(header, "오류 내용을 복사해 지원 요청이나 진단에 첨부할 수 있습니다.", 9, self.MUTED).pack(anchor="w", pady=(5, 0))

        body = tk.Frame(dialog, bg=self.BG, padx=14, pady=12)
        body.pack(fill="both", expand=True)
        report = tk.Text(body, wrap="word", undo=False, bg="#0A1020", fg=self.TEXT,
                         insertbackground=self.TEXT, selectbackground="#245B70",
                         relief="flat", padx=12, pady=10, font=("Consolas", 9))
        scroll = tk.Scrollbar(body, command=report.yview)
        report.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        report.pack(side="left", fill="both", expand=True)
        report.insert("1.0", str(message))
        report.configure(state="disabled")

        footer = tk.Frame(dialog, bg=self.BG, padx=14, pady=(0, 14))
        footer.pack(fill="x")

        def copy_report():
            try:
                self.root.clipboard_clear()
                self.root.clipboard_append(str(message))
                self.root.update_idletasks()
                copy_button.configure(text="복사 완료")
                dialog.after(1800, lambda: copy_button.configure(text="오류 내용 복사"))
            except tk.TclError as exc:
                copy_button.configure(text="복사 실패")
                messagebox.showwarning("클립보드 오류", str(exc), parent=dialog)

        copy_button = self._button(footer, "오류 내용 복사", copy_report, "primary")
        copy_button.pack(side="left")
        self._button(footer, "전체 선택", lambda: (report.configure(state="normal"), report.tag_add("sel", "1.0", "end"), report.configure(state="disabled"), report.focus_set())).pack(side="left", padx=(8, 0))
        self._button(footer, "닫기", dialog.destroy).pack(side="right")
        dialog.bind("<Escape>", lambda _event: dialog.destroy())
        dialog.bind("<Control-c>", lambda _event: copy_report())
        dialog.bind("<Control-C>", lambda _event: copy_report())
        dialog.grab_set()
        dialog.focus_set()

    def _button(self,p,t,cmd,tone="secondary"):
        bg,fg={"primary":(self.TEAL,"#062A2A"),"danger":("#3A1D2A","#FDA4AF"),"secondary":(self.ALT,self.TEXT)}[tone]
        return tk.Button(p,text=t,command=cmd,bg=bg,fg=fg,activebackground=bg,activeforeground=fg,relief="flat",bd=0,cursor="hand2",padx=16,pady=10,font=("Segoe UI",10,"bold"))
    def _build_shell(self):
        shell=tk.Frame(self.root,bg=self.BG); shell.pack(fill="both",expand=True)
        side=tk.Frame(shell,bg=self.SIDE,width=224); side.pack(side="left",fill="y"); side.pack_propagate(False)
        self._label(side,"◈  GUARDIAN",19,self.TEXT,"bold",self.SIDE).pack(anchor="w",padx=22,pady=(27,3))
        self._label(side,"WINDOWS SECURITY",8,self.MUTED,"bold",self.SIDE).pack(anchor="w",padx=24,pady=(0,30))
        self._label(side,"WORKSPACE",8,"#657891","bold",self.SIDE).pack(anchor="w",padx=22,pady=(0,10))
        self.nav={}
        items=[("home","▦   대시보드",self.show_dashboard),("filters","◉   필터 업데이트",self.update_filters),("network","⌁   필터 서버 설정",self.open_filter_settings),("license","◇   라이선스",self.open_license_settings),("diagnostics","≋   진단 정보",self.show_diagnostics)]
        for key,label,fn in items:
            b=tk.Button(side,text=label,command=lambda k=key,f=fn:(self._select_nav(k),f()),anchor="w",bg=self.SIDE,fg=self.MUTED,activebackground=self.ALT,activeforeground=self.TEXT,relief="flat",bd=0,padx=22,pady=13,cursor="hand2",font=("Segoe UI",10,"bold")); b.pack(fill="x",padx=10,pady=2); self.nav[key]=b
        tk.Frame(side,bg=self.SIDE).pack(fill="both",expand=True)
        self._label(side,"GUARDIAN WINDOWS",8,"#657891","bold",self.SIDE).pack(anchor="w",padx=22)
        self._label(side,"Development build  •  0.1.0-dev",8,self.MUTED,bg=self.SIDE).pack(anchor="w",padx=22,pady=(5,20))
        body=tk.Frame(shell,bg=self.BG); body.pack(side="left",fill="both",expand=True)
        head=tk.Frame(body,bg=self.BG); head.pack(fill="x",padx=34,pady=(25,18))
        self.page_title=self._label(head,"대시보드",21,self.TEXT,"bold",self.BG); self.page_title.pack(side="left")
        self.top_status=self._label(head,"●  보호 꺼짐",9,self.AMBER,"bold",self.BG); self.top_status.pack(side="right",pady=8)
        self.page=tk.Frame(body,bg=self.BG); self.page.pack(fill="both",expand=True,padx=34,pady=(0,28)); self.show_dashboard()
    def _select_nav(self,key):
        for k,b in self.nav.items(): b.configure(bg=self.ALT if k==key else self.SIDE,fg=self.TEAL if k==key else self.MUTED)
    def _clear_page(self):
        for w in self.page.winfo_children(): w.destroy()
    def _metric(self,p,title,sub,color):
        f=tk.Frame(p,bg=self.PANEL,highlightthickness=1,highlightbackground=self.BORDER,padx=18,pady=15)
        self._label(f,title,9,self.MUTED,"bold").pack(anchor="w"); v=self._label(f,"0",23,color,"bold"); v.pack(anchor="w",pady=(8,2)); self._label(f,sub,9,self.MUTED).pack(anchor="w"); return f,v
    def show_dashboard(self):
        self._select_nav("home"); self.page_title.configure(text="대시보드"); self._clear_page()
        hero=tk.Frame(self.page,bg=self.PANEL,highlightthickness=1,highlightbackground=self.BORDER,padx=26,pady=23); hero.pack(fill="x",pady=(0,18))
        left=tk.Frame(hero,bg=self.PANEL); left.pack(side="left",fill="x",expand=True)
        self._label(left,"REAL-TIME NETWORK PROTECTION",9,self.TEAL,"bold").pack(anchor="w")
        self.hero_status=self._label(left,"보호가 꺼져 있습니다",22,self.TEXT,"bold"); self.hero_status.pack(anchor="w",pady=(8,4))
        self.hero_description=self._label(left,"DNS 요청을 검사하고 광고·추적 도메인을 차단합니다.",10,self.MUTED); self.hero_description.pack(anchor="w")
        ctl=tk.Frame(hero,bg=self.PANEL); ctl.pack(side="right",padx=(20,0))
        self.start_button=self._button(ctl,"보호 시작",self.start_protection,"primary"); self.start_button.pack(fill="x",pady=(0,8))
        self.stop_button=self._button(ctl,"보호 중지",self.stop_protection,"danger"); self.stop_button.pack(fill="x")
        metrics=tk.Frame(self.page,bg=self.BG); metrics.pack(fill="x",pady=(0,18))
        for i in range(3): metrics.columnconfigure(i,weight=1,uniform="metric")
        self.blocked_value=self._metric(metrics,"차단된 요청","광고 및 추적 도메인",self.TEAL); self.forwarded_value=self._metric(metrics,"정상 전달","업스트림 DNS 응답",self.BLUE); self.adapters_value=self._metric(metrics,"보호 상태","네트워크 어댑터",self.TEXT)
        for i,item in enumerate((self.blocked_value,self.forwarded_value,self.adapters_value)): item[0].grid(row=0,column=i,sticky="nsew",padx=(0 if i==0 else 8,0 if i==2 else 8))
        lower=tk.Frame(self.page,bg=self.BG); lower.pack(fill="both",expand=True); lower.columnconfigure((0,1),weight=1,uniform="cards")
        for col,title,eyebrow,accent,button,fn in [(0,"필터 상태","FILTER INTELLIGENCE",self.TEAL,"필터 업데이트",self.update_filters),(1,"라이선스","GUARDIAN ACCOUNT",self.BLUE,"라이선스 관리",self.open_license_settings)]:
            card=tk.Frame(lower,bg=self.PANEL,highlightthickness=1,highlightbackground=self.BORDER,padx=18,pady=16); card.grid(row=0,column=col,sticky="nsew",padx=(0,9) if col==0 else (9,0))
            self._label(card,eyebrow,8,accent,"bold").pack(anchor="w"); self._label(card,title,14,self.TEXT,"bold").pack(anchor="w",pady=(9,8))
            if col==0: self.filter_status=self._label(card,"서버 미설정",10,self.AMBER)
            else: self.license_status=self._label(card,"무료 버전",10,self.MUTED)
            (self.filter_status if col==0 else self.license_status).pack(anchor="w"); self._button(card,button,fn).pack(anchor="w",pady=(16,0))
        self._label(self.page,"앱 창을 닫아도 백그라운드 DNS 보호는 유지됩니다.",9,self.MUTED,bg=self.BG).pack(anchor="w",pady=(14,0))
        self.set_active_ui(self.active,self.status.get())
    def show_diagnostics(self):
        self._select_nav("diagnostics"); self.page_title.configure(text="진단 정보"); self._clear_page()
        panel=tk.Frame(self.page,bg=self.PANEL,highlightthickness=1,highlightbackground=self.BORDER,padx=20,pady=18); panel.pack(fill="both",expand=True)
        self._label(panel,"시스템 진단",16,self.TEXT,"bold").pack(anchor="w")
        rows=[("애플리케이션 데이터",str(APP_DIR)),("DNS 백업","보존됨" if BACKUP.exists() else "없음"),("보호 상태 파일","존재" if STATE.exists() else "없음"),("백그라운드 상태","정상 응답" if daemon_health_is_fresh(HEALTH) else "응답 없음"),("DNS 작업 이름",TASK_NAME),("시작 로그",str(APP_DIR/"daemon-startup.log"))]
        for label,value in rows:
            row=tk.Frame(panel,bg=self.PANEL); row.pack(fill="x",pady=8); self._label(row,label,9,self.MUTED).pack(anchor="w"); self._label(row,value,9,self.TEXT).pack(anchor="w",pady=(3,0))
        self._button(panel,"시작 로그 폴더 열기",lambda:os.startfile(str(APP_DIR))).pack(anchor="w",pady=(16,0))
    def set_active_ui(self,active,label=None):
        self.active=active; current=label or ("보호 실행 중" if active else "보호 꺼짐"); self.status.set(current)
        if hasattr(self,"top_status"):
            self.top_status.configure(text="●  "+current,fg=self.GREEN if active else self.AMBER)
            self.hero_status.configure(text=current,fg=self.GREEN if active else self.TEXT)
            self.hero_description.configure(text="DNS 요청을 검사하고 구성된 필터 규칙을 적용하고 있습니다." if active else "DNS 요청을 검사하고 광고·추적 도메인을 차단합니다.")
            self.start_button.configure(state="disabled" if active else "normal"); self.stop_button.configure(state="normal" if active else "disabled")
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
                self.start_button.configure(state="disabled")
                self.show_error("Guardian DNS 복구",f"이전 보호 상태를 자동 복구하지 못했습니다. DNS 백업을 보존했습니다.\n\n{exc}")
        elif STATE.exists():
            write_state(False)
            try: stop_daemon()
            except Exception as exc:
                self.set_active_ui(False,"보호 꺼짐 · 백그라운드 종료 확인 필요")
                self.show_error("Guardian DNS 백그라운드 종료",f"DNS 보호는 꺼졌지만 백그라운드 프로세스 종료를 확인하지 못했습니다.\n\n{exc}")
    def start_protection(self):
        if self.active: return
        try:
            ensure_config(); load_runtime_config(CONFIG); write_state(True); ensure_daemon_running()
            count=self.manager.enable(); self.set_active_ui(True,f"보호 실행 중 · 어댑터 {count}개")
        except Exception as exc:
            if BACKUP.exists():
                # A partial rollback leaves DNS on loopback; keep the daemon alive.
                if proxy_ready(): self.set_active_ui(True,"보호 경로 유지 중 · DNS 복구 필요")
                else: self.start_button.configure(state="disabled")
            else:
                write_state(False)
                try: stop_daemon()
                except Exception as stop_error:
                    self.set_active_ui(False,"보호 시작 실패 · 백그라운드 종료 확인 필요")
                    self.show_error("Guardian 보호 시작 실패",f"{exc}\n\n백그라운드 종료 실패: {stop_error}")
                    return
                self.set_active_ui(False,"보호 시작 실패")
            self.show_error("Guardian 보호 시작 실패",str(exc))
    def stop_protection(self):
        if not self.active and not BACKUP.exists(): return
        try: self.manager.restore()
        except Exception as exc:
            self.set_active_ui(True,"보호 경로 유지 중 · DNS 복구 필요")
            self.show_error("DNS 복구 실패",f"백그라운드 DNS 프록시를 계속 유지하고 백업을 보존했습니다.\n\n{exc}"); return
        write_state(False)
        try: stop_daemon()
        except Exception as exc:
            self.set_active_ui(False,"DNS 복구 완료 · 백그라운드 종료 확인 필요")
            self.show_error("Guardian DNS 종료 실패",f"기존 DNS 설정은 복구했지만 백그라운드 프로세스 종료를 확인하지 못했습니다.\n\n{exc}")
            return
        self.set_active_ui(False)
    def open_filter_settings(self):
        try: data=json.loads(CONFIG.read_text(encoding="utf-8"))
        except Exception as exc: self.show_error("필터 서버 설정",str(exc)); return
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
        except Exception as exc: self.show_error("Guardian 라이선스",str(exc)); return
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
        except Exception as exc: self.show_error("필터 업데이트 실패",str(exc))
    def refresh(self):
        try:
            stats=json.loads(STATS.read_text(encoding="utf-8")) if STATS.exists() else {}
            if hasattr(self,"blocked_value"): self.blocked_value[1].configure(text=f"{int(stats.get('blocked',0)):,}")
            if hasattr(self,"forwarded_value"): self.forwarded_value[1].configure(text=f"{int(stats.get('forwarded',0)):,}")
            if hasattr(self,"adapters_value"): self.adapters_value[1].configure(text="보호 중" if self.active else "대기 중")
        except (OSError,ValueError,TypeError,KeyError): pass
        try:
            status=json.loads((APP_DIR/"filter-update-status.json").read_text(encoding="utf-8"))
            label={"updated":"서명 필터 업데이트 완료","current":"최신 필터 사용 중","error":"필터 업데이트 오류"}.get(status.get("state"),"필터 상태 확인 필요")
            if hasattr(self,"filter_status"):
                self.filter_status.configure(text=f"{label} {status.get('sourceVersion','')}".strip(),fg=self.GREEN if status.get("state") in ("updated","current") else self.AMBER)
        except (OSError,ValueError,TypeError): pass
        if hasattr(self,"top_status"): self.set_active_ui(self.active,self.status.get())
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
