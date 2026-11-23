from pathlib import Path


def test_windows_primary_navigation_matches_approved_five_destinations():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    expected = "nav_items=[('home','홈'),('protection','보호'),('exclusions','앱 제외'),('activity','활동'),('settings','설정')]"
    assert expected in source
    assert "('filters','필터 관리')" not in source
    assert "('premium','라이선스 · Premium')" not in source
    assert "('updates','프로그램 업데이트')" not in source


def test_windows_settings_contains_the_six_agreed_submenus():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    for label in ("일반", "필터링", "라이선스 및 프리미엄", "고객 지원", "정보", "종료"):
        assert f"('{label}'" in source
    assert "프로그램 업데이트 열기" in source
    assert "show_filtering_settings" in source
    assert "show_license_settings" in source
