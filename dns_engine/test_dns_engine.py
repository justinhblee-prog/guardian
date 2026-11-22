import struct,pytest
from dns_engine.rules import DomainRules
from dns_engine.wire import DnsFormatError,make_nxdomain,make_servfail,parse_question,response_matches
def recv_exact(sock,length):
    chunks=[]
    while length:
        part=sock.recv(length)
        if not part: raise OSError("socket closed early")
        chunks.append(part); length-=len(part)
    return b"".join(chunks)
def query(name="ads.example",ident=0x1234):
    labels=b"".join(bytes([len(p)])+p.encode("ascii") for p in name.split("."))+b"\0"
    return struct.pack("!HHHHHH",ident,0x0100,1,0,0,0)+labels+struct.pack("!HH",1,1)
def test_block_allow_suffix_precedence():
    r=DomainRules(); r.add_block("ads.example"); r.add_allow("safe.ads.example")
    assert r.is_blocked("x.ads.example") and not r.is_blocked("safe.ads.example") and not r.is_blocked("x.safe.ads.example")
def test_query_parse_and_nxdomain():
    q=query(); assert parse_question(q)[:3]==("ads.example",1,1)
    a=make_nxdomain(q); assert a[:2]==q[:2] and a[2]&0x80 and a[3]&15==3 and a[12:]==q[12:]
def test_upstream_response_identity():
    q=query(); good=bytearray(q); good[2]=0x81
    assert response_matches(q,bytes(good))
    bad=bytearray(good); bad[0]^=1; assert not response_matches(q,bytes(bad))
def test_malformed_packets_rejected():
    with pytest.raises(DnsFormatError): parse_question(b"short")
    loop=struct.pack("!HHHHHH",1,0,1,0,0,0)+b"\xc0\x0c"+b"\0\1\0\1"
    with pytest.raises(DnsFormatError): parse_question(loop)
def test_adblock_and_hosts_rules():
    r=DomainRules.from_text("! comment\n||ads.example^\n@@||allowed.ads.example^\n0.0.0.0 tracker.example\n")
    assert r.is_blocked("sub.ads.example") and not r.is_blocked("allowed.ads.example") and r.is_blocked("tracker.example")


def test_loopback_proxy_blocks_udp_query_without_upstream():
    import socket
    from dns_engine.proxy import DnsProxy
    rules=DomainRules(); rules.add_block("ads.example")
    proxy=DnsProxy(rules,listen=("127.0.0.1",0)); proxy.start()
    try:
        port=proxy._servers[0].server_address[1]
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.settimeout(2); client.sendto(query("cdn.ads.example"),("127.0.0.1",port)); response,_=client.recvfrom(512)
        assert response[3]&0x0f==3 and proxy.blocked_count==1
    finally: proxy.stop()


def test_servfail_response_uses_rcode_two():
    response=make_servfail(query("normal.example"))
    assert response[2]&0x80 and response[3]&15==2

def test_loopback_proxy_blocks_tcp_query_without_upstream():
    import socket
    from dns_engine.proxy import DnsProxy
    rules=DomainRules(); rules.add_block("ads.example")
    proxy=DnsProxy(rules,listen=("127.0.0.1",0)); proxy.start()
    try:
        port=proxy._tcp_servers[0].server_address[1]
        with socket.create_connection(("127.0.0.1",port),timeout=2) as client:
            packet=query("cdn.ads.example"); client.sendall(struct.pack("!H",len(packet))+packet)
            size=struct.unpack("!H",recv_exact(client,2))[0]; response=recv_exact(client,size)
        assert response[3]&15==3 and proxy.blocked_count==1
    finally: proxy.stop()

def test_loopback_proxy_returns_servfail_on_upstream_failure():
    import socket
    from dns_engine.proxy import DnsProxy
    proxy=DnsProxy(DomainRules(),listen=("127.0.0.1",0))
    proxy.forward=lambda packet: (_ for _ in ()).throw(OSError("upstream unavailable"))
    proxy.start()
    try:
        port=proxy._udp_servers[0].server_address[1]
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.settimeout(2); client.sendto(query("normal.example"),("127.0.0.1",port)); response,_=client.recvfrom(512)
        assert response[3]&15==2 and proxy.failed_count==1
    finally: proxy.stop()

def test_truncated_udp_upstream_retries_over_tcp():
    import socket,threading
    from dns_engine.proxy import DnsProxy

    # Windows can reserve a subset of dynamic ports for system services. A TCP
    # ephemeral port is not guaranteed to be bindable by UDP on the same number.
    # Retry the pair allocation rather than turning a reserved-port collision
    # into a false DNS regression.
    tcp=udp=None
    last_bind_error=None
    for _ in range(32):
        candidate_tcp=socket.socket(socket.AF_INET,socket.SOCK_STREAM)
        candidate_udp=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
        try:
            candidate_tcp.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            candidate_tcp.bind(("127.0.0.1",0))
            port=candidate_tcp.getsockname()[1]
            candidate_udp.bind(("127.0.0.1",port))
            candidate_tcp.listen(1)
            candidate_udp.settimeout(2)
            tcp,udp=candidate_tcp,candidate_udp
            break
        except OSError as exc:
            last_bind_error=exc
            candidate_tcp.close()
            candidate_udp.close()
    if tcp is None or udp is None:
        raise OSError("Unable to reserve a shared loopback TCP/UDP port after 32 attempts.") from last_bind_error

    def udp_reply():
        packet,address=udp.recvfrom(4096); truncated=bytearray(packet); truncated[2]=0x83; truncated[3]=0x80; udp.sendto(truncated,address)
    def tcp_reply():
        conn,_=tcp.accept()
        with conn:
            size=struct.unpack("!H",recv_exact(conn,2))[0]; packet=recv_exact(conn,size)
            answer=bytearray(packet); answer[2]=0x81; answer[3]=0x80
            conn.sendall(struct.pack("!H",len(answer))+answer)
    threads=[threading.Thread(target=udp_reply,daemon=True),threading.Thread(target=tcp_reply,daemon=True)]
    for thread in threads: thread.start()
    try:
        proxy=DnsProxy(DomainRules(),upstreams=(("127.0.0.1",port),),listen=("127.0.0.1",0))
        response=proxy.forward(query("normal.example"))
        assert response[2]&0x02==0 and response[3]&15==0
    finally:
        udp.close(); tcp.close()
        for thread in threads: thread.join(timeout=2)


def test_ipv6_upstream_dns_query():
    import socket,threading
    from dns_engine.proxy import DnsProxy
    upstream=socket.socket(socket.AF_INET6,socket.SOCK_DGRAM)
    try: upstream.bind(("::1",0))
    except OSError:
        upstream.close(); pytest.skip("IPv6 loopback is unavailable")
    port=upstream.getsockname()[1]
    def respond():
        packet,address=upstream.recvfrom(4096)
        answer=bytearray(packet); answer[2]=0x81; answer[3]=0x80
        upstream.sendto(answer,address)
    thread=threading.Thread(target=respond); thread.start()
    try:
        proxy=DnsProxy(DomainRules(),upstreams=(("::1",port),),listen=("127.0.0.1",0))
        response=proxy.forward(query("normal.example"))
        assert response[2]&0x80 and response[3]&15==0
    finally:
        upstream.close(); thread.join(timeout=2)
