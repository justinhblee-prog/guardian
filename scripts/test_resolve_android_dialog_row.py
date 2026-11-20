import unittest

from scripts.resolve_android_dialog_row import resolve_dialog_row


def node(text, bounds, *, clickable="false", visible="true", enabled="true"):
    return (
        '<node index="0" text="{text}" resource-id="android:id/text1" '
        'class="android.widget.TextView" clickable="{clickable}" '
        'visible-to-user="{visible}" enabled="{enabled}" bounds="{bounds}" />'
    ).format(
        text=text,
        bounds=bounds,
        clickable=clickable,
        visible=visible,
        enabled=enabled,
    )


def hierarchy(*children, outside=""):
    return (
        '<hierarchy><node class="android.widget.FrameLayout">'
        '<node class="android.widget.ListView" '
        'resource-id="android:id/select_dialog_listview">'
        + "".join(children)
        + '</node>'
        + outside
        + '</node></hierarchy>'
    )


class AndroidDialogRowTests(unittest.TestCase):
    def test_resolves_non_clickable_list_row(self):
        xml = hierarchy(
            node("광고 차단이 되지 않아요", "[70,510][1010,636]")
        )
        self.assertEqual(
            resolve_dialog_row(xml, "광고 차단이 되지 않아요"),
            (540, 573),
        )

    def test_ignores_same_text_outside_dialog_list(self):
        outside = (
            '<node text="광고 차단이 되지 않아요" '
            'class="android.widget.TextView" clickable="true" '
            'enabled="true" bounds="[10,10][100,50]" />'
        )
        xml = hierarchy(
            node("광고 차단이 되지 않아요", "[70,510][1010,636]"),
            outside=outside,
        )
        self.assertEqual(
            resolve_dialog_row(xml, "광고 차단이 되지 않아요"),
            (540, 573),
        )

    def test_rejects_ambiguous_duplicate_rows(self):
        xml = hierarchy(
            node("광고 차단이 되지 않아요", "[70,510][1010,636]"),
            node("광고 차단이 되지 않아요", "[70,636][1010,762]"),
        )
        with self.assertRaisesRegex(ValueError, "found 2"):
            resolve_dialog_row(xml, "광고 차단이 되지 않아요")

    def test_ignores_invisible_or_disabled_rows(self):
        xml = hierarchy(
            node(
                "광고 차단이 되지 않아요",
                "[70,510][1010,636]",
                visible="false",
            ),
            node(
                "광고 차단이 되지 않아요",
                "[70,636][1010,762]",
                enabled="false",
            ),
        )
        with self.assertRaisesRegex(ValueError, "found 0"):
            resolve_dialog_row(xml, "광고 차단이 되지 않아요")

    def test_rejects_invalid_hierarchy_xml(self):
        with self.assertRaisesRegex(ValueError, "not valid XML"):
            resolve_dialog_row("<hierarchy>", "FAQ")


if __name__ == "__main__":
    unittest.main()
