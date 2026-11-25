import unittest

from scripts.android_navigation_compat import (
    resolve_premium_category,
    resolve_premium_entry,
    resolve_primary_navigation,
    resolve_settings_item,
)


class PremiumNavigationCompatibilityTests(unittest.TestCase):
    def test_legacy_english_premium_tab_is_selected_directly(self):
        xml = '<hierarchy><node content-desc="Premium" clickable="true" enabled="true" /></hierarchy>'
        self.assertEqual(resolve_premium_entry(xml), "direct:Premium")

    def test_legacy_korean_premium_tab_is_selected_directly(self):
        xml = '<hierarchy><node content-desc="프리미엄" clickable="true" enabled="true" /></hierarchy>'
        self.assertEqual(resolve_premium_entry(xml), "direct:프리미엄")

    def test_current_settings_navigation_is_selected(self):
        xml = '<hierarchy><node content-desc="설정" clickable="true" enabled="true" /></hierarchy>'
        self.assertEqual(resolve_premium_entry(xml), "settings")

    def test_intermediate_all_menu_navigation_is_supported(self):
        xml = '<hierarchy><node content-desc="전체 메뉴" clickable="true" enabled="true" /></hierarchy>'
        self.assertEqual(resolve_premium_entry(xml), "all-menu")

    def test_non_clickable_nested_settings_label_is_not_a_navigation_route(self):
        xml = '<hierarchy><node content-desc="설정" clickable="false" enabled="true" /></hierarchy>'
        with self.assertRaises(ValueError):
            resolve_premium_entry(xml)

    def test_disabled_settings_navigation_item_is_not_a_route(self):
        xml = '<hierarchy><node content-desc="설정" clickable="true" enabled="false" /></hierarchy>'
        with self.assertRaises(ValueError):
            resolve_premium_entry(xml)

    def test_hidden_settings_navigation_item_is_not_a_route(self):
        xml = '<hierarchy><node content-desc="설정" clickable="true" enabled="true" visible-to-user="false" /></hierarchy>'
        with self.assertRaises(ValueError):
            resolve_premium_entry(xml)

    def test_non_clickable_legacy_premium_label_is_not_a_navigation_route(self):
        xml = '<hierarchy><node content-desc="Premium" clickable="false" enabled="true" /></hierarchy>'
        with self.assertRaises(ValueError):
            resolve_premium_entry(xml)

    def test_hidden_legacy_premium_tab_is_not_a_navigation_route(self):
        xml = '<hierarchy><node content-desc="Premium" clickable="true" enabled="true" visible-to-user="false" /></hierarchy>'
        with self.assertRaises(ValueError):
            resolve_premium_entry(xml)

    def test_english_premium_category_label_is_supported(self):
        xml = '<hierarchy><node text="라이선스 및 Premium" /></hierarchy>'
        self.assertEqual(resolve_premium_category(xml), "라이선스 및 Premium")

    def test_korean_premium_category_label_is_supported(self):
        xml = '<hierarchy><node text="라이선스 및 프리미엄" /></hierarchy>'
        self.assertEqual(resolve_premium_category(xml), "라이선스 및 프리미엄")

    def test_current_app_exclusion_settings_label_is_supported(self):
        xml = '<hierarchy><node text="앱 제외 관리" visible-to-user="true" /></hierarchy>'
        self.assertEqual(resolve_settings_item(xml, "앱 제외 관리"), "앱 제외 관리")

    def test_legacy_app_exclusion_settings_label_is_supported(self):
        xml = '<hierarchy><node text="앱 제외" visible-to-user="true" /></hierarchy>'
        self.assertEqual(resolve_settings_item(xml, "앱 제외 관리"), "앱 제외")

    def test_legacy_filter_settings_label_is_supported(self):
        xml = '<hierarchy><node content-desc="필터 규칙" visible-to-user="true" /></hierarchy>'
        self.assertEqual(resolve_settings_item(xml, "필터 관리"), "필터 규칙")

    def test_missing_settings_item_fails_closed(self):
        with self.assertRaises(ValueError):
            resolve_settings_item('<hierarchy><node text="일반" /></hierarchy>', "앱 제외 관리")

    def test_missing_navigation_fails_closed(self):
        with self.assertRaises(ValueError):
            resolve_premium_entry('<hierarchy><node content-desc="홈" /></hierarchy>')

    def test_missing_settings_category_fails_closed(self):
        with self.assertRaises(ValueError):
            resolve_premium_category('<hierarchy><node text="일반" /></hierarchy>')



class PrimaryNavigationCompatibilityTests(unittest.TestCase):
    def test_redesigned_five_destination_navigation_is_detected(self):
        xml = '<hierarchy>' + ''.join(
            f'<node class="android.widget.LinearLayout" content-desc="{label}" clickable="true" enabled="true" />'
            for label in ("홈", "보호", "앱 제외", "활동", "설정")
        ) + '</hierarchy>'
        self.assertEqual(resolve_primary_navigation(xml), "redesigned")

    def test_legacy_english_premium_navigation_is_detected(self):
        xml = '<hierarchy>' + ''.join(
            f'<node class="android.widget.LinearLayout" content-desc="{label}" clickable="true" enabled="true" />'
            for label in ("홈", "보호", "활동", "Premium", "전체 메뉴")
        ) + '</hierarchy>'
        self.assertEqual(resolve_primary_navigation(xml), "legacy:Premium")

    def test_legacy_korean_premium_navigation_is_detected(self):
        xml = '<hierarchy>' + ''.join(
            f'<node class="android.widget.LinearLayout" content-desc="{label}" clickable="true" enabled="true" />'
            for label in ("홈", "보호", "활동", "프리미엄", "전체 메뉴")
        ) + '</hierarchy>'
        self.assertEqual(resolve_primary_navigation(xml), "legacy:프리미엄")

    def test_incomplete_navigation_is_rejected(self):
        xml = '<hierarchy><node class="android.widget.LinearLayout" content-desc="홈" clickable="true" enabled="true" /></hierarchy>'
        with self.assertRaises(ValueError):
            resolve_primary_navigation(xml)



if __name__ == "__main__":
    unittest.main()
