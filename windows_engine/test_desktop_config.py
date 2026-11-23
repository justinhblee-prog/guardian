import json,pytest
from pathlib import Path
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

def test_desktop_navigation_uses_approved_primary_and_settings_hierarchy():
    source=Path(__file__).with_name("desktop.py").read_text(encoding="utf-8")
    assert "nav_items=[('home','홈'),('protection','보호'),('exclusions','앱 제외'),('activity','활동'),('settings','설정')]" in source
    for label in ("일반","필터링","라이선스 및 Premium","고객 지원","정보","종료"):
        assert "('"+({"일반":"general","필터링":"filters","라이선스 및 Premium":"premium","고객 지원":"support","정보":"info","종료":"exit"}[label])+"','"+label+"'" in source
    assert "def show_error_dialog(self,title,message):" in source
    assert "오류 전문 복사" in source
    assert "messagebox.showerror(" not in source


def test_android_navigation_contract_uses_approved_primary_hierarchy():
    root=Path(__file__).resolve().parents[1]
    source=(root/"android/app/src/main/java/com/guardian/adblock/GuardianNavigation.kt").read_text(encoding="utf-8")
    assert 'GuardianNavigationItem("app-exclusions", "▣", "앱 제외")' in source
    assert 'GuardianNavigationItem("settings", "⚙", "설정")' in source
    assert 'GuardianNavigationItem("premium"' not in source
    activity=(root/"android/app/src/main/java/com/guardian/adblock/MainActivity.kt").read_text(encoding="utf-8")
    assert 'text = "최근 오류 내용 복사"' in activity
