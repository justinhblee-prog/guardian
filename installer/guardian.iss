#define MyAppName "Guardian AdBlock"
#ifndef MyAppVersion
  #define MyAppVersion "0.7.42-dev"
#endif
#ifndef MyAppBuildNumber
  #define MyAppBuildNumber "55"
#endif
#define MyAppPublisher "Eugene Consulting"
#define MyAppExeName "GuardianAdBlock.exe"

[Setup]
AppId={{A2C3E5A4-5C62-4C3F-9B7E-2D8E5D8A0F11}
AppName={#MyAppName}
AppVersion={#MyAppVersion} (Build {#MyAppBuildNumber})
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Guardian AdBlock
DefaultGroupName=Guardian AdBlock
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
OutputDir=..\dist\installer
OutputBaseFilename=Guardian-AdBlock-Windows-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
UninstallDisplayIcon={app}\{#MyAppExeName}

[Files]
Source: "..\dist\GuardianAdBlock\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\dist\GuardianDnsProxy\*"; DestDir: "{app}\GuardianDnsProxy"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\dist\GuardianSelfTest\*"; DestDir: "{app}\GuardianSelfTest"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\dist\GuardianMaintenance\*"; DestDir: "{app}\GuardianMaintenance"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "Register-GuardianDnsTask.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\\VERSION"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Guardian AdBlock"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\Guardian AdBlock"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch Guardian AdBlock"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{app}\GuardianMaintenance\GuardianMaintenance.exe"; Parameters: "--restore-dns"; RunOnceId: "GuardianRestoreDns"; Flags: runhidden waituntilterminated
Filename: "{sys}\schtasks.exe"; Parameters: "/End /TN GuardianDnsProxy"; RunOnceId: "GuardianEndDnsTask"; Flags: runhidden waituntilterminated
Filename: "{sys}\schtasks.exe"; Parameters: "/Delete /TN GuardianDnsProxy /F"; RunOnceId: "GuardianDeleteDnsTask"; Flags: runhidden waituntilterminated
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command ""Unregister-ScheduledTask -TaskName 'GuardianDnsProxy' -Confirm:$false -ErrorAction SilentlyContinue"""; RunOnceId: "GuardianUnregisterDnsTask"; Flags: runhidden waituntilterminated

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
  Parameters: String;
begin
  if CurStep = ssPostInstall then begin
    Parameters := '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\Register-GuardianDnsTask.ps1') + '" -ExecutablePath "' + ExpandConstant('{app}\GuardianDnsProxy\GuardianDnsProxy.exe') + '"';
    if (not Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'), Parameters, ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, ResultCode)) or (ResultCode <> 0) then begin
      MsgBox('Guardian could not register its boot-start DNS protection task with an explicit working directory. Setup will stop; no DNS settings have been changed.', mbError, MB_OK);
      Abort;
    end;
  end;
end;

function InitializeUninstall(): Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec(ExpandConstant('{app}\GuardianMaintenance\GuardianMaintenance.exe'), '--restore-dns', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  if (not Result) or (ResultCode <> 0) then begin
    MsgBox('Guardian could not restore the original DNS settings. Uninstall is cancelled to avoid leaving network settings changed.', mbError, MB_OK);
    Result := False;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ResultCode: Integer;
begin
  if CurUninstallStep = usUninstall then begin
    Exec(ExpandConstant('{sys}\schtasks.exe'), '/End /TN GuardianDnsProxy', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Exec(ExpandConstant('{sys}\schtasks.exe'), '/Delete /TN GuardianDnsProxy /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'), '-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "Unregister-ScheduledTask -TaskName ''GuardianDnsProxy'' -Confirm:$false -ErrorAction SilentlyContinue"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  end;
end;
