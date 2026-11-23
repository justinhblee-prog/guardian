from pathlib import Path


def test_windows_error_dialog_exposes_copy_select_all_keyboard_and_scroll():
    source = (Path(__file__).with_name("desktop.py")).read_text(encoding="utf-8")
    assert "def show_error_dialog(self, title, message):" in source
    assert 'text="오류 내용 복사"' in source
    assert 'text="전체 선택"' in source
    assert 'text.bind("<Control-c>"' in source
    assert 'text.bind("<Control-C>"' in source
    assert 'dialog.bind("<Control-c>"' in source
    assert 'yscrollcommand=scrollbar.set' in source
    assert "scrollbar.configure(command=text.yview)" in source
    assert source.count("self.show_error_dialog(") == 9
    assert "messagebox.showerror(" not in source
