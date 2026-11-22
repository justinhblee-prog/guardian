import logging
from pathlib import Path

from windows_engine.service import configure_logging


def test_daemon_startup_logging_persists_without_console(tmp_path: Path):
    config_path = tmp_path / "guardian.json"
    log_path = configure_logging(config_path)

    assert log_path == tmp_path / "daemon-startup.log"
    logging.error("test startup failure marker")
    for handler in logging.getLogger().handlers:
        handler.flush()

    contents = log_path.read_text(encoding="utf-8")
    assert "Guardian DNS daemon startup" in contents
    assert "test startup failure marker" in contents
