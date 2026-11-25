from pathlib import Path

from windows_engine.desktop import safe_error_details, safe_error_message


def test_error_dialog_uses_sanitized_details_not_raw_traceback():
    source = Path(__file__).with_name("desktop.py").read_text(encoding="utf-8")
    assert "safe_error_details(message, active_trace, active_type)" in source
    assert "traceback.format_exc()" not in source
    assert "report=safe_error_message(report,max_chars=12000)" in source


def test_safe_error_message_redacts_credentials_endpoints_addresses_and_paths():
    raw = (
        "Authorization: Bearer bearer-secret token=token-secret "
        "license-key=license-secret https://api.private.test/activate?key=secret "
        "host='license.internal' 192.0.2.10 "
        r"C:\Users\Alice\AppData /home/alice/guardian"
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


def test_safe_error_details_omit_exception_message_and_absolute_paths():
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
    assert "test_safe_error_details_omit_exception_message_and_absolute_paths" in details
    assert "/home/" not in details
    assert "C:\\Users\\" not in details


def test_safe_error_message_is_bounded():
    assert len(safe_error_message("x" * 20000)) == 4000
