"""Windows DNS proxy process; network changes are coordinated by the desktop controller."""
import argparse,json,logging,os
from pathlib import Path
from dns_engine import DomainRules,DnsProxy
DEFAULT_CONFIG=Path(os.environ.get("PROGRAMDATA",r"C:\ProgramData"))/"Guardian"/"guardian.json"
def load_config(path:Path):
    data=json.loads(path.read_text(encoding="utf-8"))
    rules_path=path.with_name(data.get("rules_file","rules.txt"))
    rules=DomainRules.from_text(rules_path.read_text(encoding="utf-8"))
    return data,rules
def main(argv=None):
    parser=argparse.ArgumentParser(description="Guardian Windows DNS protection service")
    parser.add_argument("--config",type=Path,default=DEFAULT_CONFIG); args=parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO,format="%(asctime)s %(levelname)s %(message)s")
    data,rules=load_config(args.config)
    port=int(data.get("listen_port",53)); addresses=data.get("listen_addresses",["127.0.0.1","::1"])
    listeners=tuple((address,port) for address in addresses)
    proxy=DnsProxy(rules,tuple(data.get("upstreams",["1.1.1.1","8.8.8.8"])),listen=listeners)
    logging.info("Starting loopback DNS proxy on %d; block=%d allow=%d",port,len(rules.blocked),len(rules.allowed))
    try: proxy.serve_forever()
    except KeyboardInterrupt: logging.info("Stopping DNS proxy")
    return 0
if __name__=="__main__": raise SystemExit(main())
