#!/usr/bin/env python3
"""Resolve one Android AlertDialog ListView row to a safe tap coordinate."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET


def resolve_dialog_row(xml_text: str, expected_text: str) -> tuple[int, int]:
    """Return the center of one exact, visible AlertDialog ListView TextView row."""
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as exc:
        raise ValueError(f"UI hierarchy is not valid XML: {exc}") from exc

    parents = {child: parent for parent in root.iter() for child in parent}
    matches: list[tuple[int, int]] = []

    for node in root.iter("node"):
        parent = parents.get(node)
        if parent is None:
            continue
        if (
            parent.attrib.get("class") != "android.widget.ListView"
            or parent.attrib.get("resource-id") != "android:id/select_dialog_listview"
        ):
            continue
        if (
            node.attrib.get("class") != "android.widget.TextView"
            or node.attrib.get("resource-id") != "android:id/text1"
            or node.attrib.get("text", "").strip() != expected_text
            or node.attrib.get("visible-to-user", "true") != "true"
            or node.attrib.get("enabled") != "true"
        ):
            continue

        bounds = node.attrib.get("bounds", "")
        try:
            coordinates = bounds.strip("[]").split("][")
            if len(coordinates) != 2:
                continue
            x1, y1 = map(int, coordinates[0].split(","))
            x2, y2 = map(int, coordinates[1].split(","))
        except ValueError:
            continue
        if x2 <= x1 or y2 <= y1:
            continue
        matches.append(((x1 + x2) // 2, (y1 + y2) // 2))

    if len(matches) != 1:
        raise ValueError(
            "Expected one exact, visible AlertDialog ListView row "
            f"for {expected_text!r}; found {len(matches)}."
        )
    return matches[0]


def main() -> int:
    if len(sys.argv) != 2 or not sys.argv[1].strip():
        print("Usage: resolve_android_dialog_row.py EXPECTED_TEXT", file=sys.stderr)
        return 2
    xml_text = sys.stdin.read()
    try:
        x, y = resolve_dialog_row(xml_text, sys.argv[1])
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(x, y)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
