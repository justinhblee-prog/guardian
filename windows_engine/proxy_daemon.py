"""Boot-startable DNS proxy daemon managed by the Guardian desktop UI."""
import json,os,socket,struct,sys,threading,time
from pathlib import Path
from dns_engine import DnsProxy,DomainRules
APP_DIR=Path(os.environ.get("PROGRAMDATA",r"C:\ProgramData"))/"Guardian"
CONFIG=APP_DIR/"guardian.json"; STATE=APP_DIR/"protection.json"; STATS=APP_DIR/"dns-stats.json"
def load_runtime():
    data=json.loads(CONFIG.read_text(encoding="utf-8"))
    rules=DomainRules.from_text((APP_DIR/data.get("rules_file","rules.txt")).read_text(encoding="utf-8"))
    if not rules.blocked: raise RuntimeError("No valid DNS filter rules; daemon refuses to start")
    return data,rules
def self_test():
    rules=DomainRules(); rules.add_block("doubleclick.net")
    proxy=DnsProxy(rules,listen=(("127.0.0.1",0),("::1",0))); proxy.start()
    try:
        labels=b"".join(bytes([len(p)])+p.encode("ascii") for p in "doubleclick.net".split("."))+b"\0"
        query=struct.pack("!HHHHHH",0x1234,0x0100,1,0,0,0)+labels+struct.pack("!HH",1,1)
        for index,(family,address) in enumerate(((socket.AF_INET,("127.0.0.1",proxy._servers[0].server_address[1])),(socket.AF_INET6,("::1",proxy._servers[1].server_address[1],0,0)))):
            with socket.socket(family,socket.SOCK_DGRAM) as client:
                client.settimeout(2); client.sendto(query,address); response,_=client.recvfrom(512)
            if response[3]&15!=3: raise RuntimeError("loopback DNS block test failed")
    finally: proxy.stop()
    if sys.stdout: print("Guardian DNS daemon IPv4/IPv6 self-test passed")
def main():
    if "--self-test" in sys.argv: self_test(); return 0
    if os.name!="nt": raise SystemExit("Windows 10/11 only")
    try: state=json.loads(STATE.read_text(encoding="utf-8"))
    except (OSError,json.JSONDecodeError): return 0
    if state.get("enabled") is not True: return 0
    APP_DIR.mkdir(parents=True,exist_ok=True); data,rules=load_runtime()
    port=int(data.get("listen_port",53)); proxy=DnsProxy(rules,tuple(data.get("upstreams",["1.1.1.1","8.8.8.8"])),listen=(("127.0.0.1",port),("::1",port)))
    stop=threading.Event()
    def write_stats():
        while not stop.wait(1):
            tmp=STATS.with_suffix(".tmp")
            tmp.write_text(json.dumps({"blocked":proxy.blocked_count,"forwarded":proxy.forwarded_count}),encoding="utf-8")
            os.replace(tmp,STATS)
    writer=threading.Thread(target=write_stats,daemon=True); writer.start()
    try: proxy.serve_forever()
    finally: stop.set(); writer.join(timeout=2)
    return 0
if __name__=="__main__": raise SystemExit(main())
