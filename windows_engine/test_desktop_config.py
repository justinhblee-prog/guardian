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


def test_license_server_revalidation_interval_is_five_minutes():
    from windows_engine.desktop import license_status_poll_due
    assert license_status_poll_due(0, 300)
    assert license_status_poll_due(100, 400)
    assert not license_status_poll_due(100, 399.9)


def test_license_server_revalidation_does_not_poll_too_frequently():
    from windows_engine.desktop import license_status_poll_due
    assert not license_status_poll_due(1000, 1001)
    assert not license_status_poll_due(1000, 1299)
    assert license_status_poll_due(1000, 1300)
