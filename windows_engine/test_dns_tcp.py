"""Regression coverage for the local DNS-over-TCP listener."""
import socket
import struct

from dns_engine import DnsProxy, DomainRules


def _query(domain="doubleclick.net"):
    labels=b"".join(bytes([len(part)])+part.encode("ascii") for part in domain.split("."))+b"\0"
    return struct.pack("!HHHHHH",0x4A31,0x0100,1,0,0,0)+labels+struct.pack("!HH",1,1)


def _read_exact(sock,length):
    chunks=[]; remaining=length
    while remaining:
        chunk=sock.recv(remaining)
        if not chunk:
            raise AssertionError("DNS TCP response ended early")
        chunks.append(chunk); remaining-=len(chunk)
    return b"".join(chunks)


def test_tcp_listener_returns_complete_block_response():
    rules=DomainRules()
    rules.add_block("doubleclick.net")
    proxy=DnsProxy(rules,listen=(("127.0.0.1",0),))
    proxy.start()
    try:
        port=proxy._tcp_servers[0].server_address[1]
        query=_query()
        with socket.create_connection(("127.0.0.1",port),timeout=3) as client:
            client.settimeout(3)
            client.sendall(struct.pack("!H",len(query))+query)
            length=struct.unpack("!H",_read_exact(client,2))[0]
            response=_read_exact(client,length)
        assert length>=12
        assert response[:2]==query[:2]
        assert response[2]&0x80
        assert response[3]&0x0F==3
        assert struct.unpack("!H",response[4:6])[0]==1
    finally:
        proxy.stop()
def test_tcp_listener_returns_servfail_for_unexpected_handler_error():
    rules=DomainRules()
    rules.is_blocked=lambda _domain: (_ for _ in ()).throw(RuntimeError("synthetic rule failure"))
    proxy=DnsProxy(rules,listen=(("127.0.0.1",0),))
    proxy.start()
    try:
        port=proxy._tcp_servers[0].server_address[1]
        query=_query("example.org")
        with socket.create_connection(("127.0.0.1",port),timeout=3) as client:
            client.settimeout(3)
            client.sendall(struct.pack("!H",len(query))+query)
            length=struct.unpack("!H",_read_exact(client,2))[0]
            response=_read_exact(client,length)
        assert response[:2]==query[:2]
        assert response[2]&0x80
        assert response[3]&0x0F==2
        assert proxy.tcp_error_count==1
        assert proxy.last_tcp_error_type=="RuntimeError"
    finally:
        proxy.stop()

def test_tcp_listener_returns_servfail_when_query_processing_raises_oserror():
    rules=DomainRules()
    proxy=DnsProxy(rules,listen=(("127.0.0.1",0),))
    proxy.process_query=lambda _packet: (_ for _ in ()).throw(OSError("synthetic upstream socket failure"))
    proxy.start()
    try:
        port=proxy._tcp_servers[0].server_address[1]
        query=_query("example.org")
        with socket.create_connection(("127.0.0.1",port),timeout=3) as client:
            client.settimeout(3)
            client.sendall(struct.pack("!H",len(query))+query)
            length=struct.unpack("!H",_read_exact(client,2))[0]
            response=_read_exact(client,length)
        assert response[:2]==query[:2]
        assert response[2]&0x80
        assert response[3]&0x0F==2
        assert proxy.tcp_error_count==1
        assert proxy.last_tcp_error_type=="OSError"
        assert proxy.last_tcp_error_stage=="process DNS request"
    finally:
        proxy.stop()


def test_tcp_listener_records_incomplete_request_without_leaking_query_data():
    proxy=DnsProxy(DomainRules(),listen=(("127.0.0.1",0),))
    proxy.start()
    try:
        port=proxy._tcp_servers[0].server_address[1]
        with socket.create_connection(("127.0.0.1",port),timeout=3) as client:
            client.settimeout(3)
            client.sendall(b"\\x00")
            client.shutdown(socket.SHUT_WR)
            assert client.recv(1)==b""
        assert proxy.tcp_error_count==1
        assert proxy.last_tcp_error_type=="OSError"
        assert proxy.last_tcp_error_stage=="read request length"
    finally:
        proxy.stop()
