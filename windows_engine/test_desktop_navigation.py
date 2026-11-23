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



def test_windows_settings_uses_persistent_category_and_detail_panes():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "self.settings_detail=tk.Frame(split,bg='#0b1220')" in source
    assert "sidebar=tk.Frame(split,bg='#121d2c',width=270" in source
    assert "self.settings_detail.winfo_children()" in source
    assert "self._select_settings_category('general',self.show_general_settings,collapse=False)" in source
    assert "command=lambda k=key,a=action:self._select_settings_category(k,a)" in source


def test_windows_settings_details_render_in_detail_pane_not_over_sidebar():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    settings_methods = source[source.index("    def show_general_settings"):source.index("    def confirm_exit")]
    assert "self.settings_detail" in settings_methods
    assert "self.pages['settings'],text=" not in settings_methods
    assert "self.pages['settings'],bg=" not in settings_methods


def test_windows_dns_task_path_is_checked_and_repaired_before_start():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "def expected_daemon_executable():" in source
    assert '"GuardianDnsProxy"/"GuardianDnsProxy.exe"' in source
    assert "def ensure_daemon_task():" in source
    assert 'run_task("/Create","/TN",TASK_NAME' in source
    assert "ensure_daemon_task()" in source
    assert "Registered task executable:" in source


def test_windows_dns_diagnostics_identify_port_53_process_owners():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert '["tasklist.exe","/FI",f"PID eq {pid}","/FO","LIST"]' in source
    assert "Expected DNS executable:" in source


def test_windows_task_xml_parser_handles_namespaces_and_bom():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "def task_executable_from_xml(xml_text):" in source
    assert 'xml_text=xml_text.lstrip("\\ufeff")' in source
    assert 'root=ET.fromstring(xml_text)' in source
    assert 'root.find(".//{*}Actions/{*}Exec/{*}Command")' in source


def test_windows_dashboard_stats_are_initialized_before_first_refresh():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert 'self.stats=metric_card(metrics,"차단된 요청","0"' in source
    assert 'self.forwarded_stats=metric_card(metrics,"정상 전달","0"' in source
    assert 'self.failed_stats=metric_card(metrics,"DNS 오류","0"' in source
    assert 'self.stats.configure(text=f"{blocked:,}")' in source
    assert 'self.forwarded_stats.configure(text=f"{forwarded:,}")' in source
    assert 'self.failed_stats.configure(text=f"{failed:,}")' in source

def test_windows_settings_sidebar_collapses_to_icon_rail_and_can_expand():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "def _set_settings_sidebar_collapsed(self,collapsed):" in source
    assert "self.settings_sidebar.configure(width=68 if collapsed else 270)" in source
    assert "button.configure(text=symbol if collapsed else" in source
    assert "self._set_settings_sidebar_collapsed(True)" in source
    assert "command=lambda:self._set_settings_sidebar_collapsed(not self.settings_sidebar_collapsed)" in source



def test_windows_settings_sidebar_stays_expanded_after_category_selection():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "def _select_settings_category(self,key,action,collapse=False):" in source
    assert "command=lambda k=key,a=action:self._select_settings_category(k,a)" in source


def test_windows_window_uses_compact_adaptive_dimensions():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "window_width=max(900,min(1200,screen_width-80))" in source
    assert "window_height=max(600,min(760,screen_height-100))" in source
    assert "root.minsize(900,600)" in source
