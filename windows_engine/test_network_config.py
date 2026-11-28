import json
import pytest
from windows_engine.network_config import DnsAdapterManager,NetworkConfigError

def snapshot_runner(script):
    if "Get-NetAdapter" in script:
        return json.dumps([
          {"InterfaceIndex":7,"InterfaceName":"Ethernet","AddressFamily":"IPv4","ServerAddresses":["9.9.9.9"],"Dhcp":"Disabled"},
          {"InterfaceIndex":7,"InterfaceName":"Ethernet","AddressFamily":"IPv6","ServerAddresses":[],"Dhcp":"Enabled"}
        ])
    return ""

def test_enable_saves_backup_before_redirect_and_restore(tmp_path):
    calls=[]
    def runner(script): calls.append(script); return snapshot_runner(script)
    backup=tmp_path/"dns-backup.json"; manager=DnsAdapterManager(backup,runner)
    assert manager.enable()==2
    assert backup.exists() and "127.0.0.1" in calls[1] and "::1" in calls[2]
    assert manager.restore()==2 and not backup.exists()
    assert any("-ServerAddresses @('9.9.9.9')" in c for c in calls)
    assert any("-ResetServerAddresses" in c for c in calls)

def test_refuses_to_overwrite_existing_backup(tmp_path):
    backup=tmp_path/"dns-backup.json"; backup.write_text("[]")
    manager=DnsAdapterManager(backup,snapshot_runner)
    with pytest.raises(NetworkConfigError): manager.enable()

def test_failed_change_rolls_back(tmp_path):
    calls=[]
    def runner(script):
        calls.append(script)
        if "Get-NetAdapter" in script: return snapshot_runner(script)
        if "ServerAddresses '::1'" in script: raise RuntimeError("IPv6 change failed")
        return ""
    backup=tmp_path/"dns-backup.json"; manager=DnsAdapterManager(backup,runner)
    with pytest.raises(NetworkConfigError): manager.enable()
    assert any("-ServerAddresses @('9.9.9.9')" in c for c in calls)
    assert not backup.exists()


def test_failed_rollback_keeps_backup_for_later_recovery(tmp_path):
    calls = []
    fail_ipv6_redirect = True
    fail_ipv6_rollback = True

    def runner(script):
        nonlocal fail_ipv6_redirect, fail_ipv6_rollback
        calls.append(script)
        if "Get-NetAdapter" in script:
            return snapshot_runner(script)
        if fail_ipv6_redirect and "ServerAddresses '::1'" in script:
            fail_ipv6_redirect = False
            raise RuntimeError("simulated IPv6 redirect failure")
        if fail_ipv6_rollback and "-ResetServerAddresses" in script:
            fail_ipv6_rollback = False
            raise RuntimeError("simulated IPv6 rollback failure")
        return ""

    backup = tmp_path / "dns-backup.json"
    manager = DnsAdapterManager(backup, runner)
    with pytest.raises(NetworkConfigError, match="Could not enable local DNS"):
        manager.enable()

    # The IPv6 redirect fails, then its rollback also fails; retain the
    # original snapshot so a later explicit restore can recover both.
    assert backup.exists()
    assert json.loads(backup.read_text(encoding="utf-8"))[0]["InterfaceIndex"] == 7

    assert manager.restore() == 2
    assert not backup.exists()
    assert any("-ResetServerAddresses" in call for call in calls)


def test_restore_failure_preserves_backup_and_retry_restores_remaining_adapters(tmp_path):
    fail_ipv4_restore = True
    calls = []

    def runner(script):
        nonlocal fail_ipv4_restore
        calls.append(script)
        if "Get-NetAdapter" in script:
            return snapshot_runner(script)
        if fail_ipv4_restore and "-ServerAddresses @('9.9.9.9')" in script:
            fail_ipv4_restore = False
            raise RuntimeError("simulated adapter restore failure")
        return ""

    backup = tmp_path / "dns-backup.json"
    manager = DnsAdapterManager(backup, runner)
    manager._save([
        {"InterfaceIndex": 7, "InterfaceName": "Ethernet", "AddressFamily": "IPv4",
         "ServerAddresses": ["9.9.9.9"], "Dhcp": "Disabled"},
        {"InterfaceIndex": 7, "InterfaceName": "Ethernet", "AddressFamily": "IPv6",
         "ServerAddresses": [], "Dhcp": "Enabled"},
    ])

    with pytest.raises(NetworkConfigError, match="Some DNS settings could not be restored"):
        manager.restore()
    assert backup.exists()
    assert any("-ResetServerAddresses" in call for call in calls)

    assert manager.restore() == 2
    assert not backup.exists()
