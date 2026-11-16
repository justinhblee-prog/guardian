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
