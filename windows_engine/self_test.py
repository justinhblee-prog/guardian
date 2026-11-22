#!/usr/bin/env python3
"""Console-only installed-package smoke test for Guardian's IPv4/IPv6 DNS engine."""
import socket
import struct
import tempfile
import traceback
from pathlib import Path

from dns_engine import DomainRules, DnsProxy


def dns_query(name="guardian-self-test.invalid"):
    labels = b"".join(bytes([len(part)]) + part.encode("ascii") for part in name.split("."))
    return struct.pack("!HHHHHH", 0x6A31, 0x0100, 1, 0, 0, 0) + labels + b"\x00" + struct.pack("!HH", 1, 1)


def recv_exact(sock, length):
    chunks = []
    while length:
        chunk = sock.recv(length)
        if not chunk:
            raise OSError("DNS TCP response ended before the complete frame arrived")
        chunks.append(chunk)
        length -= len(chunk)
    return b"".join(chunks)


def verify_response(query, response, label):
    if len(response) < 12 or response[:2] != query[:2] or not (response[2] & 0x80):
        raise RuntimeError(f"{label} returned an invalid DNS response")
    if response[3] & 0x0F != 3:
        raise RuntimeError(f"{label} did not return NXDOMAIN for the configured block rule")


def main():
    log_path = Path(tempfile.gettempdir(), "GuardianDnsSelfTest.log")
    log_path.write_text("", encoding="utf-8")

    def log(message):
        with log_path.open("a", encoding="utf-8") as handle:
            handle.write(message + "\n")
        print(message, flush=True)

    proxy = None
    try:
        log("Guardian package self-test: loading isolated DNS block rule")
        rules = DomainRules.from_text("||guardian-self-test.invalid^\n")
        if not rules.is_blocked("guardian-self-test.invalid"):
            raise RuntimeError("The isolated self-test domain was not recognized as blocked")
        proxy = DnsProxy(rules, listen=(("127.0.0.1", 0), ("::1", 0)))
        proxy.start()
        query = dns_query()
        log("Guardian package self-test: temporary IPv4/IPv6 UDP and TCP listeners started")

        for index, (family, host, label) in enumerate((
            (socket.AF_INET, "127.0.0.1", "IPv4"),
            (socket.AF_INET6, "::1", "IPv6"),
        )):
            udp_port = proxy._udp_servers[index].server_address[1]
            address = (host, udp_port) if family == socket.AF_INET else (host, udp_port, 0, 0)
            with socket.socket(family, socket.SOCK_DGRAM) as client:
                client.settimeout(4)
                client.sendto(query, address)
                response, _ = client.recvfrom(512)
            verify_response(query, response, f"{label} UDP")
            log(f"Guardian package self-test: {label} UDP blocked-domain query passed")

            tcp_port = proxy._tcp_servers[index].server_address[1]
            with socket.create_connection((host, tcp_port), timeout=4) as client:
                client.settimeout(4)
                client.sendall(struct.pack("!H", len(query)) + query)
                frame_length = struct.unpack("!H", recv_exact(client, 2))[0]
                response = recv_exact(client, frame_length)
            verify_response(query, response, f"{label} TCP")
            log(f"Guardian package self-test: {label} TCP blocked-domain query passed")
    except Exception:
        with log_path.open("a", encoding="utf-8") as handle:
            handle.write(traceback.format_exc())
        raise
    finally:
        if proxy is not None:
            log("Guardian package self-test: stopping temporary DNS listeners")
            proxy.stop()
            log("Guardian package self-test: temporary DNS listeners stopped")

    log("Guardian installed DNS package self-test passed (IPv4/IPv6 UDP/TCP)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
