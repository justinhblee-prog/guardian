#!/usr/bin/env python3
"""Console entry point for safe Windows DNS restoration during uninstall."""
import sys
from windows_engine.desktop import is_admin, restore_and_remove_task


def main():
    if "--restore-dns" not in sys.argv:
        print("GuardianMaintenance requires --restore-dns", file=sys.stderr, flush=True)
        return 2
    if not is_admin():
        print("Guardian DNS restoration requires an elevated administrator process.", file=sys.stderr, flush=True)
        return 1
    restore_and_remove_task()
    print("Guardian DNS settings restored and background task removed.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
