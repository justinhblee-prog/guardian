#define MyAppName "Guardian License Manager"
#define MyAppVersion "0.1.0-dev"
#define MyAppPublisher "Guardian"
#define MyAppExeName "GuardianLicenseManager.exe"
[Setup]
AppId={{B1C5F1B0-8B1C-4E5D-9B7A-1C2D0E7A6F31}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Guardian License Manager
DefaultGroupName=Guardian License Manager
OutputDir=..\..\..\artifacts\installer
OutputBaseFilename=Guardian-License-Manager-Setup-{#MyAppVersion}-win-x64
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
UninstallDisplayIcon={app}\{#MyAppExeName}
SetupLogging=yes
CloseApplications=yes
RestartApplications=no
[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"
[Tasks]
Name: "desktopicon"; Description: "바탕 화면 바로 가기 만들기"; GroupDescription: "추가 작업:"; Flags: unchecked
[Files]
Source: "..\..\..\artifacts\GuardianLicenseManager\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\Guardian License Manager"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\Guardian License Manager"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Guardian License Manager 실행"; Flags: postinstall nowait skipifsilent
