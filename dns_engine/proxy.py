"""Threaded UDP DNS proxy with upstream validation and loopback dual-stack listeners."""
import socket,socketserver,threading
from .rules import DomainRules
from .wire import DnsFormatError,make_nxdomain,parse_question,response_matches
class _Handler(socketserver.BaseRequestHandler):
    def handle(self):
        packet,sock=self.request; proxy=self.server.proxy
        try:
            name,_,_,_=parse_question(packet)
            if proxy.rules.is_blocked(name): sock.sendto(make_nxdomain(packet),self.client_address); proxy.blocked_count+=1; return
            sock.sendto(proxy.forward(packet),self.client_address); proxy.forwarded_count+=1
        except (DnsFormatError,OSError,ValueError): return
class _Server(socketserver.ThreadingUDPServer):
    allow_reuse_address=True; daemon_threads=True
    def __init__(self,address,proxy):
        self.proxy=proxy; self.address_family=socket.AF_INET6 if ":" in address[0] else socket.AF_INET
        super().__init__(address,_Handler)
        if self.address_family==socket.AF_INET6: self.socket.setsockopt(socket.IPPROTO_IPV6,socket.IPV6_V6ONLY,1)
class DnsProxy:
    def __init__(self,rules:DomainRules,upstreams=("1.1.1.1","8.8.8.8"),timeout=2.5,listen=(("127.0.0.1",53),("::1",53))):
        self.rules=rules; self.upstreams=tuple(upstreams); self.timeout=timeout
        self.listeners=(listen,) if len(listen)==2 and isinstance(listen[0],str) else tuple(listen)
        self.blocked_count=0; self.forwarded_count=0; self._servers=[]; self._threads=[]
    def forward(self,query):
        parse_question(query); errors=[]
        for upstream in self.upstreams:
            try:
                with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
                    client.settimeout(self.timeout); client.sendto(query,(upstream,53)); response,_=client.recvfrom(4096)
                if not response_matches(query,response): raise ValueError("mismatched DNS response")
                if response[3]&0x0f!=0: raise ValueError(f"upstream RCODE={response[3]&0x0f}")
                return response
            except (OSError,ValueError) as exc: errors.append(f"{upstream}: {exc}")
        raise OSError("all DNS upstreams failed: "+"; ".join(errors))
    def start(self):
        if self._servers: return
        try:
            for address in self.listeners: self._servers.append(_Server(address,self))
            self._threads=[threading.Thread(target=s.serve_forever,kwargs={"poll_interval":.25},daemon=True) for s in self._servers]
            for thread in self._threads: thread.start()
        except Exception:
            for server in self._servers: server.server_close()
            self._servers=[]; self._threads=[]; raise
    def serve_forever(self):
        self.start()
        try:
            for thread in self._threads: thread.join()
        finally: self.stop()
    def stop(self):
        servers=self._servers; threads=self._threads
        for server in servers: server.shutdown()
        for thread in threads: thread.join(timeout=3)
        for server in servers: server.server_close()
        self._servers=[]; self._threads=[]
    def shutdown(self): self.stop()
