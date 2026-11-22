from windows_engine import desktop


class FragmentedSocket:
    def __init__(self, chunks):
        self.chunks = list(chunks)

    def recv(self, _size):
        return self.chunks.pop(0) if self.chunks else b""


def test_recv_exact_reassembles_fragmented_tcp_header():
    assert desktop.recv_exact(FragmentedSocket([b"\\x00", b"\\x2a"]), 2) == b"\\x00\\x2a"


def test_recv_exact_fails_on_truncated_tcp_frame():
    try:
        desktop.recv_exact(FragmentedSocket([b"\\x00"]), 2)
    except OSError as exc:
        assert "ended early" in str(exc)
    else:
        raise AssertionError("truncated DNS TCP frame must fail")


def test_configured_probe_domain_uses_a_domain_blocked_by_current_rules(tmp_path, monkeypatch):
    (tmp_path / "custom-rules.txt").write_text(
        "||ads.example^\\n@@||allowed.ads.example^\\n||tracking.example^\\n",
        encoding="utf-8",
    )
    config = tmp_path / "guardian.json"
    config.write_text('{"rules_file":"custom-rules.txt"}', encoding="utf-8")
    monkeypatch.setattr(desktop, "APP_DIR", tmp_path)
    monkeypatch.setattr(desktop, "CONFIG", config)

    assert desktop.configured_probe_domain() == "ads.example"
    query = desktop.blocked_probe_query(desktop.configured_probe_domain())
    assert b"ads" in query
