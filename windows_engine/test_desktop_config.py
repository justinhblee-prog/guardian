import json,pytest
from windows_engine.desktop import load_runtime_config
from windows_engine.network_config import NetworkConfigError
def test_runtime_config_requires_nonempty_filter(tmp_path):
    (tmp_path/"rules.txt").write_text("! empty\n",encoding="utf-8"); (tmp_path/"guardian.json").write_text(json.dumps({"rules_file":"rules.txt"}),encoding="utf-8")
    with pytest.raises(NetworkConfigError): load_runtime_config(tmp_path/"guardian.json")
def test_runtime_config_loads_filter_and_upstreams(tmp_path):
    (tmp_path/"rules.txt").write_text("||ads.example^\n",encoding="utf-8"); (tmp_path/"guardian.json").write_text(json.dumps({"rules_file":"rules.txt","upstreams":["9.9.9.9"]}),encoding="utf-8")
    data,rules=load_runtime_config(tmp_path/"guardian.json")
    assert data["upstreams"]==["9.9.9.9"] and rules.is_blocked("cdn.ads.example")


def test_dns_readiness_accepts_forwarded_noerror_response():
    from windows_engine.desktop import blocked_probe_query,is_dns_probe_response
    query=blocked_probe_query()
    response=bytearray(query)
    response[2]=0x81
    response[3]=0x80
    assert is_dns_probe_response(query,bytes(response))

def test_dns_readiness_accepts_blocked_nxdomain_response():
    from windows_engine.desktop import blocked_probe_query,is_dns_probe_response
    query=blocked_probe_query()
    response=bytearray(query)
    response[2]=0x81
    response[3]=0x83
    assert is_dns_probe_response(query,bytes(response))

def test_dns_readiness_rejects_unrelated_or_nonresponse_packet():
    from windows_engine.desktop import blocked_probe_query,is_dns_probe_response
    query=blocked_probe_query()
    wrong_id=bytearray(query); wrong_id[0] ^= 0xff; wrong_id[2]=0x81
    query_only=bytearray(query)
    assert not is_dns_probe_response(query,bytes(wrong_id))
    assert not is_dns_probe_response(query,bytes(query_only))


def test_proxy_readiness_rejects_unowned_loopback_dns(tmp_path, monkeypatch):
    import windows_engine.desktop as desktop
    monkeypatch.setattr(desktop, "HEALTH", tmp_path / "missing-daemon-health.json")
    def unexpected_socket(*args, **kwargs):
        raise AssertionError("must not trust an arbitrary loopback DNS responder")
    monkeypatch.setattr(desktop.socket, "socket", unexpected_socket)
    assert not desktop.proxy_ready()

def test_proxy_readiness_uses_tcp_listener_and_valid_dns_response(monkeypatch):
    import struct
    import windows_engine.desktop as desktop

    monkeypatch.setattr(desktop, "daemon_health_is_fresh", lambda path: True)
    monkeypatch.setattr(desktop, "configured_probe_domain", lambda: "doubleclick.net")
    query = desktop.blocked_probe_query()
    response = bytearray(query)
    response[2] = 0x81
    response[3] = 0x83
    framed = struct.pack("!H", len(response)) + bytes(response)

    class FakeTcpSocket:
        def __init__(self, family, kind):
            assert family == desktop.socket.AF_INET
            assert kind == desktop.socket.SOCK_STREAM
            self.received = bytearray(framed)
            self.sent = b""
        def __enter__(self): return self
        def __exit__(self, *args): return False
        def settimeout(self, value): assert value == 3.0
        def connect(self, address): assert address == ("127.0.0.1", 53)
        def sendall(self, payload):
            self.sent = payload
            assert payload == struct.pack("!H", len(query)) + query
        def recv(self, size):
            chunk = bytes(self.received[:size])
            del self.received[:size]
            return chunk

    monkeypatch.setattr(desktop.socket, "socket", FakeTcpSocket)
    assert desktop.proxy_ready()


def test_proxy_readiness_rejects_tcp_listener_with_invalid_response(monkeypatch):
    import struct
    import windows_engine.desktop as desktop

    monkeypatch.setattr(desktop, "daemon_health_is_fresh", lambda path: True)
    query = desktop.blocked_probe_query()
    wrong = bytearray(query)
    wrong[0] ^= 0xFF
    wrong[2] = 0x81
    wrong[3] = 0x83
    framed = struct.pack("!H", len(wrong)) + bytes(wrong)

    class FakeTcpSocket:
        def __init__(self, family, kind): self.received = bytearray(framed)
        def __enter__(self): return self
        def __exit__(self, *args): return False
        def settimeout(self, value): pass
        def connect(self, address): pass
        def sendall(self, payload): pass
        def recv(self, size):
            chunk = bytes(self.received[:size])
            del self.received[:size]
            return chunk

    monkeypatch.setattr(desktop.socket, "socket", FakeTcpSocket)
    assert not desktop.proxy_ready()


def test_windows_primary_navigation_matches_shared_five_item_menu():
    import inspect
    from windows_engine.desktop import GuardianDesktop

    shell = inspect.getsource(GuardianDesktop._build_shell)
    expected = [
        ("home", "홈", "show_dashboard"),
        ("protection", "보호", "show_protection_page"),
        ("app-exclusions", "앱 제외", "show_app_exclusions_page"),
        ("activity", "활동", "show_activity_page"),
        ("settings", "설정", "show_settings_page"),
    ]
    for key, label, handler in expected:
        assert f'"{key}"' in shell
        assert label in shell
        assert callable(getattr(GuardianDesktop, handler))
    assert '"premium"' not in shell
    assert '"rules"' not in shell

def test_windows_protection_ui_refresh_is_safe_off_dashboard():
    import inspect
    from windows_engine.desktop import GuardianDesktop

    source = inspect.getsource(GuardianDesktop.set_active_ui)
    assert 'self.hero_status.winfo_exists()' in source
    assert 'self.start_button.winfo_exists()' in source
    assert 'self.stop_button.winfo_exists()' in source
