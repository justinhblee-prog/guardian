from pathlib import Path


def test_windows_error_dialog_exposes_copy_select_all_keyboard_and_scroll():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "def show_error_dialog(self,title,message):" in source
    assert 'text="오류 내용 복사"' in source
    assert 'text="전체 선택"' in source
    assert 'text.bind("<Control-c>"' in source
    assert 'text.bind("<Control-C>"' in source
    assert 'dialog.bind("<Control-c>"' in source
    assert 'yscrollcommand=scrollbar.set' in source
    assert "scrollbar.configure(command=text.yview)" in source
    assert source.count("self.show_error_dialog(") == 7
    assert "messagebox.showerror(" not in source


from windows_engine.desktop import safe_error_details, safe_error_message


def test_error_dialog_and_support_report_use_sanitized_diagnostics():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "safe_error_details(message, active_trace, active_type)" in source
    assert "details=safe_error_message(details,max_chars=12000)" in source


def test_safe_error_message_redacts_credentials_endpoints_addresses_and_paths():
    raw = (
        "Authorization: Bearer bearer-secret token=token-secret "
        "license-key=license-secret https://api.private.test/activate?key=secret "
        "host='license.internal' 192.0.2.10 "
        r"C:\\Users\\Alice\\AppData /home/alice/guardian"
    )
    safe = safe_error_message(raw)
    for forbidden in (
        "bearer-secret", "token-secret", "license-secret", "api.private.test",
        "license.internal", "192.0.2.10", "Alice", "/home/alice",
    ):
        assert forbidden not in safe
    assert "[URL redacted]" in safe
    assert "[path redacted]" in safe
    assert "[IP redacted]" in safe


def test_safe_error_details_keep_only_bounded_trace_frame_identity():
    def fail():
        raise RuntimeError("token=private-value https://private.example/path")

    try:
        fail()
    except RuntimeError as error:
        details = safe_error_details(str(error), error.__traceback__, type(error))
    else:
        raise AssertionError("test exception was not raised")

    assert "RuntimeError:" in details
    assert "private-value" not in details
    assert "private.example" not in details
    assert "test_safe_error_details_keep_only_bounded_trace_frame_identity" in details
    assert "/home/" not in details
    assert "C:\\Users\\" not in details


def test_safe_error_message_is_bounded():
    assert len(safe_error_message("x" * 20000)) == 4000
