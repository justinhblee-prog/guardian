#define MyAppName "Guardian AdBlock"
#define MyAppVersion "0.1.0-dev"
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
Source: "..\dist\GuardianDnsProxy\GuardianDnsProxy.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Guardian AdBlock"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\Guardian AdBlock"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{sys}\schtasks.exe"; Parameters: '/Create /TN GuardianDnsProxy /SC ONSTART /RU SYSTEM /RL HIGHEST /TR "{app}\GuardianDnsProxy.exe" /F'; Flags: runhidden waituntilterminated
Filename: "{app}\{#MyAppExeName}"; Description: "Launch Guardian AdBlock"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{app}\{#MyAppExeName}"; Parameters: "--restore-dns"; Flags: runhidden waituntilterminated
