#define MyAppName "Guardian AdBlock"
#define MyAppVersion "0.7.48"
#define MyAppPublisher "Eugene Consulting"
#define MyAppExeName "GuardianAdBlock.exe"

[Setup]
AppId={{A2C3E5A4-5C62-4C3F-9B7E-2D8E5D8A0F11}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
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
Source: "..\\dist\\GuardianDnsProxy\\*"; DestDir: "{app}\\GuardianDnsProxy"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Guardian AdBlock"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\Guardian AdBlock"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch Guardian AdBlock"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{app}\{#MyAppExeName}"; Parameters: "--restore-dns"; RunOnceId: "GuardianRestoreDns"; Flags: runhidden waituntilterminated
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
    Parameters := '/Create /TN GuardianDnsProxy /SC ONSTART /RU SYSTEM /RL HIGHEST /TR "' + ExpandConstant('{app}\GuardianDnsProxy\GuardianDnsProxy.exe') + '" /F';
    if (not Exec(ExpandConstant('{sys}\schtasks.exe'), Parameters, '', SW_HIDE, ewWaitUntilTerminated, ResultCode)) or (ResultCode <> 0) then begin
      MsgBox('Guardian could not register its boot-start DNS protection task. Setup will stop; no DNS settings have been changed.', mbError, MB_OK);
      Abort;
    end;
  end;
end;

function InitializeUninstall(): Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec(ExpandConstant('{app}\GuardianAdBlock.exe'), '--restore-dns', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
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
