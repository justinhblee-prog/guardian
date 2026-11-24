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
    labels = [
        node.attrib.get("text", "").strip()
        for node in root.iter("node")
        if node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("enabled", "true") == "true"
    ]
    for label in ("라이선스 및 프리미엄", "라이선스 및 Premium"):
        if any(label == text or label in text for text in labels):
            return label
    raise ValueError("No supported License/Premium category is present in Settings")


def resolve_settings_item(hierarchy: str, requested: str) -> str:
    """Resolve current or legacy labels for a Settings destination."""
    root = _root(hierarchy)
    aliases = {
        "앱 제외 관리": ("앱 제외 관리", "앱 제외"),
        "필터 관리": ("필터 관리", "필터 규칙"),
    }
    candidates = aliases.get(requested, (requested,))
    visible = {
        value.strip()
        for node in root.iter("node")
        if node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("enabled", "true") == "true"
        for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        if value.strip()
    }
    for candidate in candidates:
        if candidate in visible:
            return candidate
    raise ValueError("No visible compatible Settings item for: " + requested)


def main() -> int:
    if len(sys.argv) not in (2, 3) or sys.argv[1] not in {"entry", "category", "settings-item"}:
        print("usage: android_navigation_compat.py entry|category|settings-item [title]", file=sys.stderr)
        return 2
    if sys.argv[1] == "settings-item" and len(sys.argv) != 3:
        print("settings-item requires a requested title", file=sys.stderr)
        return 2
    hierarchy = sys.stdin.read()
    try:
        if sys.argv[1] == "entry":
            value = resolve_premium_entry(hierarchy)
        elif sys.argv[1] == "category":
            value = resolve_premium_category(hierarchy)
        else:
            value = resolve_settings_item(hierarchy, sys.argv[2])
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(value)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
