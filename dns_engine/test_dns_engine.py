import struct,pytest
from dns_engine.rules import DomainRules
from dns_engine.wire import DnsFormatError,make_nxdomain,parse_question,response_matches
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
