from windows_engine import desktop


class FragmentedSocket:
    def __init__(self, chunks):
        self.chunks = list(chunks)

    def recv(self, _size):
        return self.chunks.pop(0) if self.chunks else b""


def test_recv_exact_reassembles_fragmented_tcp_header():
    assert desktop.recv_exact(FragmentedSocket([bytes([0]), bytes([42])]), 2) == bytes([0, 42])


def test_recv_exact_fails_on_truncated_tcp_frame():
    try:
        desktop.recv_exact(FragmentedSocket([bytes([0])]), 2)
    except OSError as exc:
        assert "ended early" in str(exc)
    else:
        raise AssertionError("truncated DNS TCP frame must fail")


def test_configured_probe_domain_uses_a_domain_blocked_by_current_rules(tmp_path, monkeypatch):
    (tmp_path / "custom-rules.txt").write_text(
        "||ads.example^\n@@||allowed.ads.example^\n||tracking.example^\n",
        encoding="utf-8",
    )
    config = tmp_path / "guardian.json"
    config.write_text('{"rules_file":"custom-rules.txt"}', encoding="utf-8")
    monkeypatch.setattr(desktop, "APP_DIR", tmp_path)
    monkeypatch.setattr(desktop, "CONFIG", config)

    assert desktop.configured_probe_domain() == "ads.example"
    query = desktop.blocked_probe_query(desktop.configured_probe_domain())
    assert b"ads" in query


def test_proxy_readiness_status_reports_stale_daemon_heartbeat(monkeypatch):
    monkeypatch.setattr(desktop, "daemon_health_is_fresh", lambda _path: False)

    ready, reason = desktop.proxy_readiness_status()

    assert not ready
    assert "heartbeat" in reason


def test_proxy_readiness_status_reports_tcp_connect_error_without_domain_details(monkeypatch):
    monkeypatch.setattr(desktop, "daemon_health_is_fresh", lambda _path: True)
    monkeypatch.setattr(desktop, "configured_probe_domain", lambda: "private.example")

    def fail_socket(*_args, **_kwargs):
        raise OSError(10061, "connection refused")

    monkeypatch.setattr(desktop.socket, "socket", fail_socket)

    ready, reason = desktop.proxy_readiness_status()

    assert not ready
    assert "errno=10061" in reason
    assert "private.example" not in reason


def test_proxy_readiness_status_accepts_valid_tcp_dns_response(monkeypatch):
    import struct

    monkeypatch.setattr(desktop, "daemon_health_is_fresh", lambda _path: True)
    monkeypatch.setattr(desktop, "configured_probe_domain", lambda: "ads.example")
    query = desktop.blocked_probe_query("ads.example")
    response = bytearray(query)
    response[2] |= 0x80
    framed = struct.pack("!H", len(response)) + bytes(response)

    class FakeTcpSocket:
        def __init__(self, *_args, **_kwargs):
            self.received = bytearray(framed)

        def __enter__(self): return self
        def __exit__(self, *_args): return False
        def settimeout(self, _value): pass
        def connect(self, _address): pass
        def sendall(self, _payload): pass

        def recv(self, size):
            chunk = bytes(self.received[:size])
            del self.received[:size]
            return chunk

    monkeypatch.setattr(desktop.socket, "socket", FakeTcpSocket)

    ready, reason = desktop.proxy_readiness_status()

    assert ready
    assert reason == "local TCP DNS response validated"
