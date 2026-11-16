import struct,pytest
from dns_engine.rules import DomainRules
from dns_engine.wire import DnsFormatError,make_nxdomain,make_servfail,parse_question,response_matches
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
            size=struct.unpack("!H",client.recv(2))[0]; response=client.recv(size)
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
    tcp=socket.socket(socket.AF_INET,socket.SOCK_STREAM); tcp.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); tcp.bind(("127.0.0.1",0)); tcp.listen(1)
    port=tcp.getsockname()[1]
    udp=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); udp.bind(("127.0.0.1",port)); udp.settimeout(2)
    def udp_reply():
        packet,address=udp.recvfrom(4096); truncated=bytearray(packet); truncated[2]=0x83; truncated[3]=0x80; udp.sendto(truncated,address)
    def tcp_reply():
        conn,_=tcp.accept()
        with conn:
            size=struct.unpack("!H",conn.recv(2))[0]; packet=conn.recv(size)
            answer=bytearray(packet); answer[2]=0x81; answer[3]=0x80
            conn.sendall(struct.pack("!H",len(answer))+answer)
    threads=[threading.Thread(target=udp_reply),threading.Thread(target=tcp_reply)]
    for thread in threads: thread.start()
    try:
        proxy=DnsProxy(DomainRules(),upstreams=(("127.0.0.1",port),),listen=("127.0.0.1",0))
        response=proxy.forward(query("normal.example"))
        assert response[2]&0x02==0 and response[3]&15==0
    finally:
        udp.close(); tcp.close()
        for thread in threads: thread.join(timeout=2)
