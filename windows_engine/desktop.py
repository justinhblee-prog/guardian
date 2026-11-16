"""Tkinter desktop control panel for Guardian Windows DNS protection."""
import json,os,sys,socket,struct
from pathlib import Path
import tkinter as tk
from tkinter import messagebox
from dns_engine import DomainRules,DnsProxy
from windows_engine.network_config import DnsAdapterManager,NetworkConfigError,is_admin
APP_DIR=Path(os.environ.get("PROGRAMDATA",r"C:\ProgramData"))/"Guardian"
CONFIG=APP_DIR/"guardian.json"; BACKUP=APP_DIR/"dns-backup.json"
DEFAULT_RULES="! Guardian bootstrap rules\n||doubleclick.net^\n||googlesyndication.com^\n||googleadservices.com^\n||adservice.google.com^\n||adnxs.com^\n||criteo.com^\n||taboola.com^\n||outbrain.com^\n||scorecardresearch.com^\n||quantserve.com^\n||mixpanel.com^\n||hotjar.com\n"
def ensure_config():
    APP_DIR.mkdir(parents=True,exist_ok=True); rules=APP_DIR/"rules.txt"
    if not rules.exists(): rules.write_text(DEFAULT_RULES,encoding="utf-8")
    if not CONFIG.exists(): CONFIG.write_text(json.dumps({"rules_file":"rules.txt","listen_port":53,"upstreams":["1.1.1.1","8.8.8.8"]},indent=2),encoding="utf-8")
def elevate():
    try:
        import ctypes
        exe=sys.executable
        params="--elevated" if getattr(sys,"frozen",False) else "-m windows_engine.desktop --elevated"
        return int(ctypes.windll.shell32.ShellExecuteW(None,"runas",exe,params,str(Path.cwd()),1))>32
    except Exception: return False

def load_runtime_config(path):
    data=json.loads(path.read_text(encoding="utf-8"))
    rules=DomainRules.from_text(path.with_name(data.get("rules_file","rules.txt")).read_text(encoding="utf-8"))
    if not rules.blocked: raise NetworkConfigError("No valid DNS filter rules are loaded; protection will not start")
    return data,rules

class GuardianDesktop:
    def __init__(self,root):
        self.root=root; self.proxy=None; self.manager=DnsAdapterManager(BACKUP); self.active=False
        self.status=tk.StringVar(value="보호 꺼짐"); root.title("Guardian AdBlock"); root.geometry("620x440"); root.minsize(560,400); root.configure(bg="#101820")
        tk.Label(root,text="GUARDIAN",font=("Segoe UI",24,"bold"),fg="#f2f6fa",bg="#101820").pack(pady=(24,2))
        tk.Label(root,text="Windows DNS Protection",font=("Segoe UI",10),fg="#9fb2c3",bg="#101820").pack()
        self.status_label=tk.Label(root,textvariable=self.status,font=("Segoe UI",15,"bold"),fg="#f3b64a",bg="#101820"); self.status_label.pack(pady=26)
        row=tk.Frame(root,bg="#101820"); row.pack()
        self.start_button=tk.Button(row,text="보호 시작",command=self.start_protection,width=16,height=2,bg="#197a57",fg="white",font=("Segoe UI",11,"bold"),relief="flat"); self.start_button.pack(side="left",padx=8)
        self.stop_button=tk.Button(row,text="보호 중지",command=self.stop_protection,width=16,height=2,bg="#8b3940",fg="white",font=("Segoe UI",11,"bold"),relief="flat",state="disabled"); self.stop_button.pack(side="left",padx=8)
        self.stats=tk.Label(root,text="차단 0건  |  전달 0건",font=("Segoe UI",12),fg="#d7e0e8",bg="#101820"); self.stats.pack(pady=24)
        tk.Label(root,text="IPv4/IPv6 로컬 DNS · 원래 DNS 설정 백업 및 중지 시 복구",font=("Segoe UI",9),fg="#9fb2c3",bg="#101820").pack(pady=6)
        tk.Label(root,text="보호 시작 시 네트워크 어댑터 DNS가 로컬 필터로 전환됩니다. 중지 시 원래 설정을 복구합니다.",wraplength=540,font=("Segoe UI",9),fg="#aab8c4",bg="#101820").pack(pady=12)
        root.protocol("WM_DELETE_WINDOW",self.close); self.recover_previous_state(); self.refresh()
    def recover_previous_state(self):
        if BACKUP.exists():
            try: self.manager.restore()
            except Exception as exc:
                self.status.set("DNS 복구 필요 — 보호 시작 불가"); self.status_label.configure(fg="#ff7777"); self.start_button.configure(state="disabled")
                messagebox.showerror("Guardian DNS 복구",f"이전 실행의 DNS 백업을 복구하지 못했습니다. 네트워크 설정을 보존하기 위해 보호 시작을 잠갔습니다.\n\n{exc}")
    def start_protection(self):
        if self.active: return
        try:
            ensure_config(); data,rules=load_runtime_config(CONFIG); port=int(data.get("listen_port",53))
            self.proxy=DnsProxy(rules,tuple(data.get("upstreams",["1.1.1.1","8.8.8.8"])),listen=(("127.0.0.1",port),("::1",port)))
            self.proxy.start()  # Bind both loopback sockets before changing adapter DNS.
            count=self.manager.enable(); self.active=True; self.status.set(f"보호 실행 중 · 어댑터 {count}개"); self.status_label.configure(fg="#54d69b")
            self.start_button.configure(state="disabled"); self.stop_button.configure(state="normal")
        except Exception as exc:
            if self.proxy:
                try: self.proxy.stop()
                except Exception: pass
                self.proxy=None
            self.status.set("보호 시작 실패"); self.status_label.configure(fg="#ff7777"); messagebox.showerror("Guardian 보호 시작 실패",str(exc))
    def stop_protection(self):
        if not self.active and not BACKUP.exists(): return
        try: self.manager.restore()
        except Exception as exc:
            messagebox.showerror("DNS 복구 실패",f"로컬 DNS 프록시를 계속 유지합니다. 네트워크가 끊기지 않도록 백업을 보존했습니다.\n\n{exc}"); return
        if self.proxy: self.proxy.stop(); self.proxy=None
        self.active=False; self.status.set("보호 꺼짐"); self.status_label.configure(fg="#f3b64a"); self.start_button.configure(state="normal"); self.stop_button.configure(state="disabled")
    def refresh(self):
        if self.proxy: self.stats.configure(text=f"광고/추적 차단 {self.proxy.blocked_count:,}건  |  정상 전달 {self.proxy.forwarded_count:,}건")
        self.root.after(1000,self.refresh)
    def close(self):
        if self.active or BACKUP.exists():
            if not messagebox.askyesno("Guardian 종료","보호를 중지하고 기존 DNS 설정을 복구한 후 종료하시겠습니까?"): return
            self.stop_protection()
            if self.active or BACKUP.exists(): return
        self.root.destroy()
def self_test():
    rules=DomainRules.from_text(DEFAULT_RULES)
    proxy=DnsProxy(rules,listen=(("127.0.0.1",0),("::1",0)))
    proxy.start()
    try:
        port=proxy._servers[0].server_address[1]
        labels=b"".join(bytes([len(p)])+p.encode("ascii") for p in "doubleclick.net".split("."))+b"\\0"
        query=struct.pack("!HHHHHH",0x1234,0x0100,1,0,0,0)+labels+struct.pack("!HH",1,1)
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.settimeout(2)
            client.sendto(query,("127.0.0.1",port))
            response,_=client.recvfrom(512)
        if response[3]&0x0f!=3: raise RuntimeError("IPv4 blocked domain did not return NXDOMAIN")
        v6port=proxy._servers[1].server_address[1]
        with socket.socket(socket.AF_INET6,socket.SOCK_DGRAM) as client:
            client.settimeout(2)
            client.sendto(query,("::1",v6port,0,0))
            response6,_=client.recvfrom(512)
        if response6[3]&0x0f!=3: raise RuntimeError("IPv6 blocked domain did not return NXDOMAIN")
        print("Guardian Windows IPv4/IPv6 executable self-test passed")
    finally:
        proxy.stop()

def main():
    if '--self-test' in sys.argv:
        self_test(); return 0
    if os.name!="nt": raise SystemExit("Windows 10/11 only")
    if not is_admin(): return 0 if elevate() else 1
    ensure_config(); root=tk.Tk(); GuardianDesktop(root); root.mainloop(); return 0
if __name__=="__main__": raise SystemExit(main())
