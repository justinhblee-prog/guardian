"""Threaded dual-stack UDP/TCP DNS proxy with validated upstream fallback."""
import ipaddress,socket,socketserver,struct,threading
from .rules import DomainRules
from .wire import DnsFormatError,make_nxdomain,make_servfail,parse_question,response_matches

class _UdpHandler(socketserver.BaseRequestHandler):
    def handle(self):
        packet,sock=self.request; proxy=self.server.proxy
        try: sock.sendto(proxy.process_query(packet),self.client_address)
        except (DnsFormatError,ValueError): return

class _TcpHandler(socketserver.StreamRequestHandler):
    @staticmethod
    def _read_exact(stream,length):
        chunks=[]; remaining=length
        while remaining:
            chunk=stream.read(remaining)
            if not chunk: raise OSError("TCP DNS request ended before its frame was complete")
            chunks.append(chunk); remaining-=len(chunk)
        return b"".join(chunks)
    def handle(self):
        self.request.settimeout(self.server.proxy.timeout)
        packet=None
        try:
            header=self._read_exact(self.rfile,2)
            length=struct.unpack("!H",header)[0]
            if length<12 or length>4096: return
            packet=self._read_exact(self.rfile,length)
            response=self.server.proxy.process_query(packet)
            self.wfile.write(struct.pack("!H",len(response))+response)
        except (OSError,DnsFormatError,ValueError):
            return
        except Exception as exc:
            # An unexpected rule/handler exception must not silently close a
            # valid DNS-over-TCP request. Record only the exception type (never
            # the queried hostname) and return a protocol-level SERVFAIL.
            self.server.proxy.record_tcp_error(exc)
            if packet is not None:
                try:
                    response=make_servfail(packet)
                    self.wfile.write(struct.pack("!H",len(response))+response)
                except (OSError,ValueError):
                    return

class _UdpServer(socketserver.ThreadingUDPServer):
    allow_reuse_address=True; daemon_threads=True
    def __init__(self,address,proxy):
        self.proxy=proxy; self.address_family=socket.AF_INET6 if ":" in address[0] else socket.AF_INET
        super().__init__(address,_UdpHandler)

class _TcpServer(socketserver.ThreadingTCPServer):
    allow_reuse_address=True; daemon_threads=True
    def __init__(self,address,proxy):
        self.proxy=proxy; self.address_family=socket.AF_INET6 if ":" in address[0] else socket.AF_INET
        super().__init__(address,_TcpHandler)

class DnsProxy:
    def __init__(self,rules:DomainRules,upstreams=("1.1.1.1","8.8.8.8"),timeout=2.5,listen=(("127.0.0.1",53),("::1",53))):
        self.rules=rules; self.upstreams=tuple(upstreams); self.timeout=timeout
        self.listeners=(listen,) if len(listen)==2 and isinstance(listen[0],str) else tuple(listen)
        self.blocked_count=0; self.forwarded_count=0; self.failed_count=0
        self.tcp_error_count=0; self.last_tcp_error_type=""
        self._udp_servers=[]; self._tcp_servers=[]; self._servers=[]; self._threads=[]
    def record_tcp_error(self,error):
        self.tcp_error_count+=1
        self.last_tcp_error_type=type(error).__name__

    @staticmethod
    def _recv_exact(sock,length):
        chunks=[]; remaining=length
        while remaining:
            chunk=sock.recv(remaining)
            if not chunk: raise OSError("upstream TCP DNS response ended early")
            chunks.append(chunk); remaining-=len(chunk)
        return b"".join(chunks)
    def _forward_tcp(self,query,upstream,port=53):
        with socket.create_connection((upstream,port),timeout=self.timeout) as client:
            client.settimeout(self.timeout); client.sendall(struct.pack("!H",len(query))+query)
            length=struct.unpack("!H",self._recv_exact(client,2))[0]
            if length<12 or length>65535: raise ValueError("invalid upstream TCP DNS length")
            response=self._recv_exact(client,length)
        if not response_matches(query,response): raise ValueError("mismatched TCP DNS response")
        if response[3]&15!=0: raise ValueError(f"upstream TCP DNS RCODE={response[3]&15}")
        return response
    def forward(self,query):
        parse_question(query); errors=[]
        for upstream in self.upstreams:
            try:
                host,port=upstream if isinstance(upstream,tuple) else (upstream,53)
                parsed_address=ipaddress.ip_address(host)
                family=socket.AF_INET if parsed_address.version==4 else socket.AF_INET6
                with socket.socket(family,socket.SOCK_DGRAM) as client:
                    client.settimeout(self.timeout); client.connect((host,port)); client.send(query); response=client.recv(4096)
                if not response_matches(query,response): raise ValueError("mismatched UDP DNS response")
                if response[3]&15!=0: raise ValueError(f"upstream UDP DNS RCODE={response[3]&15}")
                if response[2]&0x02: response=self._forward_tcp(query,host,port)
                return response
            except (OSError,ValueError) as exc: errors.append(f"{upstream}: {exc}")
        raise OSError("all DNS upstreams failed: "+"; ".join(errors))
    def process_query(self,packet):
        name,_,_,_=parse_question(packet)
        if self.rules.is_blocked(name): self.blocked_count+=1; return make_nxdomain(packet)
        try: response=self.forward(packet)
        except (OSError,ValueError): self.failed_count+=1; return make_servfail(packet)
        self.forwarded_count+=1; return response
    def start(self):
        if self._servers: return
        try:
            for address in self.listeners: self._udp_servers.append(_UdpServer(address,self))
            for address in self.listeners: self._tcp_servers.append(_TcpServer(address,self))
            self._servers=self._udp_servers+self._tcp_servers
            self._threads=[threading.Thread(target=s.serve_forever,kwargs={"poll_interval":.25},daemon=True) for s in self._servers]
            for thread in self._threads: thread.start()
        except Exception:
            for server in self._servers+self._udp_servers+self._tcp_servers:
                try: server.server_close()
                except OSError: pass
            self._servers=[]; self._udp_servers=[]; self._tcp_servers=[]; self._threads=[]; raise
    def serve_forever(self):
        self.start()
        try:
            for thread in self._threads: thread.join()
        finally: self.stop()
    def stop(self):
        servers=self._servers; threads=self._threads
        # socketserver.shutdown() waits for serve_forever() to set its internal
        # completion event. If a listener thread exited during startup, calling
        # shutdown() on that server can wait forever. Only signal live workers.
        for index,server in enumerate(servers):
            thread=threads[index] if index < len(threads) else None
            if thread is not None and thread.is_alive():
                server.shutdown()
        for thread in threads:
            if thread.is_alive(): thread.join(timeout=3)
        for server in servers:
            server.server_close()
        self._servers=[]; self._udp_servers=[]; self._tcp_servers=[]; self._threads=[]
    def shutdown(self): self.stop()
