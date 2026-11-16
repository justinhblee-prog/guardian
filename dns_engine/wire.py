"""Defensive DNS wire-format helpers."""
import struct
class DnsFormatError(ValueError): pass

def decode_name(packet: bytes, offset: int):
    labels=[]; cursor=offset; end=None; seen=set(); hops=0
    while True:
        if cursor>=len(packet): raise DnsFormatError("name exceeds packet")
        length=packet[cursor]
        if length & 0xC0 == 0xC0:
            if cursor+1>=len(packet): raise DnsFormatError("truncated pointer")
            ptr=((length&0x3f)<<8)|packet[cursor+1]
            if ptr>=len(packet) or ptr in seen: raise DnsFormatError("invalid compression pointer")
            seen.add(ptr); hops+=1
            if hops>32: raise DnsFormatError("pointer loop")
            if end is None: end=cursor+2
            cursor=ptr; continue
        if length & 0xC0: raise DnsFormatError("reserved label encoding")
        cursor+=1
        if length==0:
            if end is None: end=cursor
            break
        if length>63 or cursor+length>len(packet): raise DnsFormatError("invalid label length")
        try: labels.append(packet[cursor:cursor+length].decode("ascii"))
        except UnicodeDecodeError as exc: raise DnsFormatError("non-ASCII label") from exc
        cursor+=length
        if sum(len(x)+1 for x in labels)>254: raise DnsFormatError("name too long")
    return ".".join(labels).lower(),end

def parse_question(packet: bytes):
    if len(packet)<12: raise DnsFormatError("short DNS header")
    ident,flags,qdcount,_,_,_=struct.unpack("!HHHHHH",packet[:12])
    if flags&0x8000: raise DnsFormatError("not a query")
    if qdcount!=1: raise DnsFormatError("exactly one question required")
    name,end=decode_name(packet,12)
    if end+4>len(packet): raise DnsFormatError("truncated question")
    qtype,qclass=struct.unpack("!HH",packet[end:end+4])
    if qclass!=1: raise DnsFormatError("only IN class supported")
    return name,qtype,qclass,end+4

def make_nxdomain(query: bytes):
    parse_question(query)
    ident,flags,qdcount,_,_,_=struct.unpack("!HHHHHH",query[:12])
    response_flags=0x8000|0x0080|(flags&0x0100)|(flags&0x0010)|3
    return struct.pack("!HHHHHH",ident,response_flags,qdcount,0,0,0)+query[12:]

def response_matches(query: bytes,response: bytes):
    try:
        qname,qtype,_,_=parse_question(query)
        if len(response)<12: return False
        rid,rflags,qdcount,_,_,_=struct.unpack("!HHHHHH",response[:12])
        if rid!=struct.unpack("!H",query[:2])[0] or not(rflags&0x8000) or qdcount!=1: return False
        # Parse response question while treating QR as clear for the shared parser.
        candidate=response[:2]+struct.pack("!H",rflags&0x7fff)+response[4:]
        rname,rtype,_,_=parse_question(candidate)
        return qname==rname and qtype==rtype
    except (DnsFormatError,struct.error): return False
