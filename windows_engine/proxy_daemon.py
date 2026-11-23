"""Boot-startable DNS proxy daemon managed by the Guardian desktop UI."""
import json,logging,logging.handlers,os,socket,struct,sys,threading,time
from pathlib import Path
from dns_engine import DnsProxy,DomainRules
from windows_engine.filter_updates import FilterUpdater,FilterUpdateError
from windows_engine.daemon_health import write_daemon_health,remove_daemon_health
APP_DIR=Path(os.environ.get("PROGRAMDATA",r"C:\ProgramData"))/"Guardian"
CONFIG=APP_DIR/"guardian.json"; STATE=APP_DIR/"protection.json"; STATS=APP_DIR/"dns-stats.json"; UPDATE_STATUS=APP_DIR/"filter-update-status.json"; HEALTH=APP_DIR/"daemon-health.json"
LOG_DIR=APP_DIR/"logs"; LOG_FILE=LOG_DIR/"proxy-daemon.log"

def configure_logging():
    LOG_DIR.mkdir(parents=True,exist_ok=True)
    handler=logging.handlers.RotatingFileHandler(LOG_FILE,maxBytes=2*1024*1024,backupCount=3,encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s [pid=%(process)d] %(message)s"))
    root=logging.getLogger(); root.setLevel(logging.INFO)
    root.handlers.clear(); root.addHandler(handler)
def load_runtime():
    data=json.loads(CONFIG.read_text(encoding="utf-8"))
    rules=DomainRules.from_text((APP_DIR/data.get("rules_file","rules.txt")).read_text(encoding="utf-8"))
    if not rules.blocked: raise RuntimeError("No valid DNS filter rules; daemon refuses to start")
    return data,rules
def self_test():
    rules=DomainRules(); rules.add_block("doubleclick.net")
    proxy=DnsProxy(rules,listen=(("127.0.0.1",0),("::1",0))); proxy.start()
    labels=b"".join(bytes([len(p)])+p.encode("ascii") for p in "doubleclick.net".split("."))+b"\0"
    query=struct.pack("!HHHHHH",0x1234,0x0100,1,0,0,0)+labels+struct.pack("!HH",1,1)
    for family,address in ((socket.AF_INET,("127.0.0.1",proxy._udp_servers[0].server_address[1])),(socket.AF_INET6,("::1",proxy._udp_servers[1].server_address[1],0,0))):
        with socket.socket(family,socket.SOCK_DGRAM) as client:
            client.settimeout(2); client.sendto(query,address); response,_=client.recvfrom(512)
        if response[3]&15!=3: raise RuntimeError("loopback DNS block test failed")
    proxy.stop()
    if sys.stdout: print("Guardian DNS daemon IPv4/IPv6 self-test passed")
def run_daemon():
    if os.name!="nt": raise RuntimeError("Windows 10/11 only")
    try: state=json.loads(STATE.read_text(encoding="utf-8"))
    except (OSError,json.JSONDecodeError): return 0
    if state.get("enabled") is not True: return 0
    APP_DIR.mkdir(parents=True,exist_ok=True); data,rules=load_runtime()
    port=int(data.get("listen_port",53)); proxy=DnsProxy(rules,tuple(data.get("upstreams",["1.1.1.1","8.8.8.8","2606:4700:4700::1111","2001:4860:4860::8888"])),listen=(("127.0.0.1",port),("::1",port)))
    rules_path=APP_DIR/data.get("rules_file","rules.txt")
    stop=threading.Event()
    logging.info("Starting Guardian DNS daemon; executable=%s cwd=%s config=%s port=%d blocked_rules=%d",sys.executable,os.getcwd(),CONFIG,port,len(rules.blocked))
    logging.info("Binding loopback UDP/TCP listeners on 127.0.0.1:%d and ::1:%d",port,port)
    proxy.start()  # Bind all IPv4/IPv6 UDP/TCP sockets before publishing readiness.
    logging.info("All four loopback DNS sockets bound successfully")
    write_daemon_health(HEALTH)
    logging.info("Published daemon health heartbeat at %s",HEALTH)
    def heartbeat_tasks():
        while not stop.wait(1):
            try: write_daemon_health(HEALTH)
            except Exception: logging.exception("Could not refresh daemon health heartbeat")
    def background_tasks():
        last_mtime=rules_path.stat().st_mtime_ns; last_update_check=0
        while not stop.wait(1):
            tmp=STATS.with_suffix(".tmp")
            try:
                tmp.write_text(json.dumps({"blocked":proxy.blocked_count,"forwarded":proxy.forwarded_count,"failed":proxy.failed_count,"updatedAt":time.time()}),encoding="utf-8")
                os.replace(tmp,STATS)
            except OSError: pass
            try:
                current=rules_path.stat().st_mtime_ns
                if current!=last_mtime:
                    replacement=DomainRules.from_text(rules_path.read_text(encoding="utf-8"))
                    if replacement.blocked:
                        proxy.rules=replacement
                        last_mtime=current
            except (OSError,UnicodeError): pass
            base=data.get("manifest_base_url","").strip(); key=data.get("manifest_public_key","").strip()
            if base and key and time.monotonic()-last_update_check>=21600:
                last_update_check=time.monotonic()
                try:
                    updater=FilterUpdater(base,data.get("filter_source_id","adguard"),key,APP_DIR/"manifest-state.json",rules_path)
                    result=updater.update()
                    status={"state":"updated","sourceVersion":result["sourceVersion"],"checkedAt":time.time()}
                except FilterUpdateError as exc:
                    state="current" if "stale or has already been applied" in str(exc) else "error"
                    status={"state":state,"message":str(exc),"checkedAt":time.time()}
                except Exception as exc:
                    status={"state":"error","message":str(exc),"checkedAt":time.time()}
                try:
                    status_tmp=UPDATE_STATUS.with_suffix(".tmp")
                    status_tmp.write_text(json.dumps(status,ensure_ascii=False),encoding="utf-8")
                    os.replace(status_tmp,UPDATE_STATUS)
                except OSError: pass
    heartbeat=threading.Thread(target=heartbeat_tasks,daemon=True); heartbeat.start()
    writer=threading.Thread(target=background_tasks,daemon=True); writer.start()
    try: proxy.serve_forever()
    finally:
        logging.info("DNS proxy stopped; shutting down background workers")
        stop.set(); writer.join(timeout=2); heartbeat.join(timeout=2); remove_daemon_health(HEALTH,os.getpid())
    return 0

def main():
    if "--self-test" in sys.argv:
        self_test()
        return 0
    try:
        configure_logging()
        logging.info("Guardian DNS background task process started; argv=%r",sys.argv)
        return run_daemon()
    except Exception:
        try: logging.exception("Guardian DNS daemon failed during startup or runtime")
        except Exception: pass
        return 1

if __name__=="__main__": raise SystemExit(main())
