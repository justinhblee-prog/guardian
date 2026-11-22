"""Windows DNS proxy process; network changes are coordinated by the desktop controller."""
import argparse
import json
import logging
import logging.handlers
import os
import tempfile
from pathlib import Path

from dns_engine import DomainRules, DnsProxy

DEFAULT_CONFIG = Path(os.environ.get("PROGRAMDATA", r"C:\ProgramData")) / "Guardian" / "guardian.json"


def configure_logging(config_path: Path) -> Path:
    """Persist startup failures even when Task Scheduler launches without a console."""
    candidates = [
        config_path.parent / "daemon-startup.log",
        Path(os.environ.get("PROGRAMDATA", r"C:\ProgramData")) / "Guardian" / "daemon-startup.log",
        Path(tempfile.gettempdir()) / "Guardian" / "daemon-startup.log",
    ]
    log_path = None
    for candidate in candidates:
        try:
            candidate.parent.mkdir(parents=True, exist_ok=True)
            handler = logging.handlers.RotatingFileHandler(
                candidate, maxBytes=1_000_000, backupCount=3, encoding="utf-8"
            )
            log_path = candidate
            break
        except OSError:
            continue

    handlers = [handler] if log_path is not None else []
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s pid=%(process)d %(message)s",
        handlers=handlers,
        force=True,
    )
    if log_path is None:
        logging.basicConfig(level=logging.INFO, force=True)
        logging.error("No writable Guardian daemon log location; file diagnostics unavailable")
    else:
        logging.info("=== Guardian DNS daemon startup ===")
        logging.info("Startup diagnostics log initialized")
    return log_path


def load_config(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    rules_path = path.with_name(data.get("rules_file", "rules.txt"))
    rules = DomainRules.from_text(rules_path.read_text(encoding="utf-8"))
    return data, rules


def main(argv=None):
    parser = argparse.ArgumentParser(description="Guardian Windows DNS protection service")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    args = parser.parse_args(argv)
    configure_logging(args.config)
    stage = "load-config"
    try:
        logging.info("stage=%s config_exists=%s", stage, args.config.is_file())
        data, rules = load_config(args.config)
        port = int(data.get("listen_port", 53))
        addresses = data.get("listen_addresses", ["127.0.0.1", "::1"])
        listeners = tuple((address, port) for address in addresses)
        logging.info(
            "stage=config-loaded rules_blocked=%d rules_allowed=%d listener_count=%d port=%d",
            len(rules.blocked), len(rules.allowed), len(listeners), port,
        )

        stage = "create-dns-proxy"
        proxy = DnsProxy(
            rules,
            tuple(data.get("upstreams", [
                "1.1.1.1", "8.8.8.8",
                "2606:4700:4700::1111", "2001:4860:4860::8888",
            ])),
            listen=listeners,
        )
        stage = "bind-and-serve"
        logging.info("stage=%s Starting loopback DNS proxy", stage)
        proxy.serve_forever()
    except KeyboardInterrupt:
        logging.info("DNS daemon interrupted; stopping")
        return 0
    except Exception:
        logging.exception("DNS daemon startup/runtime failure stage=%s", stage)
        raise
    logging.info("DNS daemon stopped")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
