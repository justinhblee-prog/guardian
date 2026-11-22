from windows_engine import version


def test_shared_version_file_contains_semantic_version_and_build(monkeypatch, tmp_path):
    metadata = tmp_path / "VERSION"
    metadata.write_text("0.7.42-dev\n55\n", encoding="utf-8")
    monkeypatch.setattr(version, "_version_file", lambda: metadata)
    monkeypatch.delenv("GUARDIAN_VERSION_NAME", raising=False)
    assert version.get_product_version() == "0.7.42-dev"
    assert version.get_product_build_number() == 55


def test_version_override_is_validated(monkeypatch):
    monkeypatch.setenv("GUARDIAN_VERSION_NAME", "0.7.43-dev")
    assert version.get_product_version() == "0.7.43-dev"
    monkeypatch.setenv("GUARDIAN_VERSION_NAME", "not-a-version")
    import pytest
    with pytest.raises(ValueError):
        version.get_product_version()



def test_missing_shared_version_metadata_fails_closed(monkeypatch, tmp_path):
    missing = tmp_path / "VERSION"
    monkeypatch.setattr(version, "_version_file", lambda: missing)
    monkeypatch.delenv("GUARDIAN_VERSION_NAME", raising=False)
    import pytest
    with pytest.raises(RuntimeError, match="VERSION metadata is missing"):
        version.get_product_version()
    with pytest.raises(RuntimeError, match="VERSION metadata is missing or invalid"):
        version.get_product_build_number()
