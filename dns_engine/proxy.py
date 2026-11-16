"""Threaded UDP DNS proxy with upstream validation and fallback."""
import socket,socketserver
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
    def __init__(self,address,proxy): self.proxy=proxy; super().__init__(address,_Handler)
class DnsProxy:
    def __init__(self,rules:DomainRules,upstreams=("1.1.1.1","8.8.8.8"),timeout=2.5,listen=("127.0.0.1",53)):
        self.rules=rules; self.upstreams=tuple(upstreams); self.timeout=timeout; self.listen=listen; self.blocked_count=0; self.forwarded_count=0; self._server=None
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
    def serve_forever(self):
        self._server=_Server(self.listen,self)
        try: self._server.serve_forever(poll_interval=.25)
        finally: self._server.server_close(); self._server=None
    def shutdown(self):
        if self._server is not None: self._server.shutdown()
