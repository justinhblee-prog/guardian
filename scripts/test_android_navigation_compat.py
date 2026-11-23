import unittest

from scripts.android_navigation_compat import (
    resolve_premium_category,
    resolve_premium_entry,
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


if __name__ == "__main__":
    unittest.main()
