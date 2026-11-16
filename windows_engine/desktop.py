"""Tkinter desktop control panel for Guardian Windows DNS protection."""
import json,os,sys,socket,struct,subprocess,time
from pathlib import Path
import tkinter as tk
from tkinter import messagebox
from dns_engine import DomainRules,DnsProxy
from windows_engine.network_config import DnsAdapterManager,NetworkConfigError,is_admin
from windows_engine.filter_updates import FilterUpdater
APP_DIR=Path(os.environ.get("PROGRAMDATA",r"C:\ProgramData"))/"Guardian"
CONFIG=APP_DIR/"guardian.json"; BACKUP=APP_DIR/"dns-backup.json"; STATE=APP_DIR/"protection.json"; STATS=APP_DIR/"dns-stats.json"
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
def proxy_ready():
    try:
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.settimeout(.4); client.sendto(blocked_probe_query(),("127.0.0.1",53)); response,_=client.recvfrom(512)
        return len(response)>=12 and response[:2]==b"j1" and response[3]&15==3
    except OSError: return False
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
    raise NetworkConfigError("Guardian DNS background task did not become ready on IPv4 loopback port 53")
def stop_daemon(delete=False):
    run_task("/End","/TN",TASK_NAME)
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
        self.root=root; self.manager=DnsAdapterManager(BACKUP); self.active=False
        self.status=tk.StringVar(value="보호 꺼짐"); root.title("Guardian AdBlock"); root.geometry("620x440"); root.minsize(560,400); root.configure(bg="#101820")
        tk.Label(root,text="GUARDIAN",font=("Segoe UI",24,"bold"),fg="#f2f6fa",bg="#101820").pack(pady=(24,2))
        tk.Label(root,text="Windows DNS Protection",font=("Segoe UI",10),fg="#9fb2c3",bg="#101820").pack()
        self.status_label=tk.Label(root,textvariable=self.status,font=("Segoe UI",15,"bold"),fg="#f3b64a",bg="#101820"); self.status_label.pack(pady=26)
        row=tk.Frame(root,bg="#101820"); row.pack()
        self.start_button=tk.Button(row,text="보호 시작",command=self.start_protection,width=16,height=2,bg="#197a57",fg="white",font=("Segoe UI",11,"bold"),relief="flat"); self.start_button.pack(side="left",padx=8)
        self.stop_button=tk.Button(row,text="보호 중지",command=self.stop_protection,width=16,height=2,bg="#8b3940",fg="white",font=("Segoe UI",11,"bold"),relief="flat",state="disabled"); self.stop_button.pack(side="left",padx=8)
        self.stats=tk.Label(root,text="차단 0건  |  전달 0건",font=("Segoe UI",12),fg="#d7e0e8",bg="#101820"); self.stats.pack(pady=20)
        actions=tk.Frame(root,bg="#101820"); actions.pack(pady=4)
        tk.Button(actions,text="필터 업데이트",command=self.update_filters,width=16,bg="#294e70",fg="white",relief="flat").pack(side="left",padx=6)
        tk.Button(actions,text="필터 서버 설정",command=self.open_filter_settings,width=16,bg="#33404c",fg="white",relief="flat").pack(side="left",padx=6)
        self.filter_status=tk.Label(root,text="필터 업데이트: 서버 미설정",font=("Segoe UI",9),fg="#9fb2c3",bg="#101820"); self.filter_status.pack(pady=3)
        tk.Label(root,text="백그라운드 DNS 보호 · IPv4/IPv6 · 기존 DNS 자동 복구",font=("Segoe UI",9),fg="#9fb2c3",bg="#101820").pack(pady=6)
        tk.Label(root,text="보호 시작 시 어댑터 DNS를 로컬 필터로 전환합니다. 앱 창을 닫아도 백그라운드 보호는 유지되며, 보호 중지는 원래 DNS 설정을 복구합니다.",wraplength=540,font=("Segoe UI",9),fg="#aab8c4",bg="#101820").pack(pady=12)
        root.protocol("WM_DELETE_WINDOW",self.close); self.recover_previous_state(); self.refresh()
    def set_active_ui(self,active,label=None):
        self.active=active; self.status.set(label or ("보호 실행 중" if active else "보호 꺼짐"))
        self.status_label.configure(fg="#54d69b" if active else "#f3b64a")
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
                messagebox.showerror("Guardian DNS 복구",f"이전 보호 상태를 자동 복구하지 못했습니다. DNS 백업을 보존했습니다.\n\n{exc}")
        elif STATE.exists():
            write_state(False); stop_daemon()
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
                write_state(False); stop_daemon(); self.set_active_ui(False,"보호 시작 실패")
            messagebox.showerror("Guardian 보호 시작 실패",str(exc))
    def stop_protection(self):
        if not self.active and not BACKUP.exists(): return
        try: self.manager.restore()
        except Exception as exc:
            self.set_active_ui(True,"보호 경로 유지 중 · DNS 복구 필요")
            messagebox.showerror("DNS 복구 실패",f"백그라운드 DNS 프록시를 계속 유지하고 백업을 보존했습니다.\n\n{exc}"); return
        write_state(False); stop_daemon(); self.set_active_ui(False)
    def open_filter_settings(self):
        try: data=json.loads(CONFIG.read_text(encoding="utf-8"))
        except Exception as exc: messagebox.showerror("필터 서버 설정",str(exc)); return
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
        except Exception as exc: messagebox.showerror("필터 업데이트 실패",str(exc))
    def refresh(self):
        try:
            stats=json.loads(STATS.read_text(encoding="utf-8")) if STATS.exists() else {}
            self.stats.configure(text=f"광고/추적 차단 {int(stats.get('blocked',0)):,}건  |  정상 전달 {int(stats.get('forwarded',0)):,}건")
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
