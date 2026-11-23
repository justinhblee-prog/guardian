"""Transactional Windows adapter DNS redirection with rollback support."""
import json,os,subprocess,tempfile
from pathlib import Path

class NetworkConfigError(RuntimeError): pass

def is_admin():
    try:
        import ctypes
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except (AttributeError,OSError): return False

def run_powershell(script):
    """Execute the DNS adapter helper without showing a PowerShell console window."""
    options={"capture_output":True,"text":True,"timeout":30}
    if os.name=="nt":
        startup=subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startup.wShowWindow = 0
        options["startupinfo"]=startup
        options["creationflags"]=getattr(subprocess,"CREATE_NO_WINDOW",0x08000000)
    result=subprocess.run(
        ["powershell.exe","-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-Command",script],
        **options
    )
    if result.returncode: raise NetworkConfigError(result.stderr.strip() or "PowerShell command failed")
    return result.stdout.strip()

class DnsAdapterManager:
    def __init__(self,backup_path:Path,runner=run_powershell): self.backup_path=Path(backup_path); self.runner=runner
    def _snapshot(self):
        script=r"""
$up = @(Get-NetAdapter -Physical | Where-Object { $_.Status -eq 'Up' })
$out = foreach ($a in $up) {
  $dns = @(Get-DnsClientServerAddress -InterfaceIndex $a.ifIndex)
  $ipif = @(Get-NetIPInterface -InterfaceIndex $a.ifIndex)
  foreach ($family in @('IPv4','IPv6')) {
    $d = $dns | Where-Object { $_.AddressFamily -eq $family } | Select-Object -First 1
    $i = $ipif | Where-Object { $_.AddressFamily -eq $family } | Select-Object -First 1
    [pscustomobject]@{ InterfaceIndex=[int]$a.ifIndex; InterfaceName=$a.Name; AddressFamily=$family; ServerAddresses=@($d.ServerAddresses); Dhcp=[string]$i.Dhcp }
  }
}
ConvertTo-Json -InputObject @($out) -Depth 5 -Compress
"""
        raw=self.runner(script)
        try: data=json.loads(raw) if raw else []
        except json.JSONDecodeError as exc: raise NetworkConfigError("Could not parse Windows adapter DNS state") from exc
        if isinstance(data,dict): data=[data]
        if not data: raise NetworkConfigError("No active physical network adapters found")
        return data
    def _save(self,data):
        self.backup_path.parent.mkdir(parents=True,exist_ok=True)
        fd,tmp=tempfile.mkstemp(prefix="dns-backup-",suffix=".tmp",dir=self.backup_path.parent)
        try:
            with os.fdopen(fd,"w",encoding="utf-8") as f: json.dump(data,f,ensure_ascii=False,indent=2); f.flush(); os.fsync(f.fileno())
            os.replace(tmp,self.backup_path)
        finally:
            if os.path.exists(tmp): os.unlink(tmp)
    def _set_loopback(self,item):
        address="127.0.0.1" if item["AddressFamily"]=="IPv4" else "::1"
        script=f"Set-DnsClientServerAddress -InterfaceIndex {int(item['InterfaceIndex'])} -ServerAddresses '{address}' -ErrorAction Stop"
        self.runner(script)
    def _restore_item(self,item):
        index=int(item["InterfaceIndex"]); servers=item.get("ServerAddresses") or []; dhcp=str(item.get("Dhcp","")).lower()
        if dhcp=="enabled" or not servers:
            self.runner(f"Set-DnsClientServerAddress -InterfaceIndex {index} -ResetServerAddresses -ErrorAction Stop")
        else:
            safe=",".join("'"+str(x).replace("'","''")+"'" for x in servers)
            self.runner(f"Set-DnsClientServerAddress -InterfaceIndex {index} -ServerAddresses @({safe}) -ErrorAction Stop")
    def enable(self):
        if self.backup_path.exists(): raise NetworkConfigError("A DNS backup already exists; restore it before enabling protection again")
        snapshot=self._snapshot(); self._save(snapshot)
        try:
            for item in snapshot: self._set_loopback(item)
        except Exception as exc:
            failures=[]
            for item in reversed(snapshot):
                try: self._restore_item(item)
                except Exception as restore_exc: failures.append(str(restore_exc))
            if not failures: self.backup_path.unlink(missing_ok=True)
            detail=("; rollback errors: "+" | ".join(failures)) if failures else ""
            raise NetworkConfigError(f"Could not enable local DNS on all adapters: {exc}{detail}") from exc
        return len(snapshot)
    def restore(self):
        if not self.backup_path.exists(): return 0
        try: snapshot=json.loads(self.backup_path.read_text(encoding="utf-8"))
        except (OSError,json.JSONDecodeError) as exc: raise NetworkConfigError("DNS backup is unreadable; refusing to overwrite it") from exc
        failures=[]; restored=0
        for item in reversed(snapshot):
            try: self._restore_item(item); restored+=1
            except Exception as exc: failures.append(f"{item.get('InterfaceName','adapter')} {item.get('AddressFamily')}: {exc}")
        if failures: raise NetworkConfigError("Some DNS settings could not be restored: "+" | ".join(failures))
        self.backup_path.unlink(missing_ok=True)
        return restored
