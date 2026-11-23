#!/usr/bin/env python3
"""Resolve Android Premium navigation across published and redesigned builds."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET


def _root(hierarchy: str) -> ET.Element:
    try:
        return ET.fromstring(hierarchy)
    except ET.ParseError as exc:
        raise ValueError("Android UI hierarchy is not valid XML") from exc


def resolve_premium_entry(hierarchy: str) -> str:
    """Return a stable route for the Premium screen from a UIAutomator dump."""
    root = _root(hierarchy)
    descriptions = {
        node.attrib.get("content-desc", "").strip()
        for node in root.iter("node")
        if node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("enabled", "true") == "true"
    }
    # Legacy releases expose Premium as a direct bottom-navigation tab.
    for label in ("Premium", "프리미엄"):
        if label in descriptions:
            return "direct:" + label
    # Current releases expose Premium inside the Settings hierarchy.
    if "설정" in descriptions:
        return "settings"
    # Intermediate builds used a generic All Menu destination.
    if "전체 메뉴" in descriptions:
        return "all-menu"
    raise ValueError("No supported Premium or Settings navigation item is visible")


def resolve_premium_category(hierarchy: str) -> str:
    """Return the exact Settings category label used by the current build."""
    root = _root(hierarchy)
    labels = [node.attrib.get("text", "").strip() for node in root.iter("node")]
    for label in ("라이선스 및 프리미엄", "라이선스 및 Premium"):
        if any(label == text or label in text for text in labels):
            return label
    raise ValueError("No supported License/Premium category is present in Settings")


def main() -> int:
    if len(sys.argv) != 2 or sys.argv[1] not in {"entry", "category"}:
        print("usage: android_navigation_compat.py entry|category", file=sys.stderr)
        return 2
    hierarchy = sys.stdin.read()
    try:
        value = resolve_premium_entry(hierarchy) if sys.argv[1] == "entry" else resolve_premium_category(hierarchy)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(value)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
