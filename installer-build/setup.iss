[Setup]
AppName=Campus Assistant
AppVersion=1.0.0
AppPublisher=Campus
DefaultDirName={autopf}\Campus Assistant
DefaultGroupName=Campus Assistant
OutputDir=E:\Desktop\installer
OutputBaseFilename=CampusAssistant-Setup
SetupIconFile=E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\icon.ico
Compression=lzma2/max
SolidCompression=yes
UninstallDisplayName=Campus Assistant
UninstallDisplayIcon={app}\icon.ico
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\campus-assistant.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\launcher.vbs"; DestDir: "{app}"; Flags: ignoreversion
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\Campus Assistant\Campus Assistant"; Filename: "wscript.exe"; Parameters: """{app}\launcher.vbs"""; WorkingDir: "{app}"; IconFilename: "{app}\icon.ico"; Comment: "Start Campus Assistant"
Name: "{autoprograms}\Campus Assistant\Uninstall Campus Assistant"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Campus Assistant"; Filename: "wscript.exe"; Parameters: """{app}\launcher.vbs"""; WorkingDir: "{app}"; IconFilename: "{app}\icon.ico"; Comment: "Start Campus Assistant"

[Run]
Filename: "wscript.exe"; Parameters: """{app}\launcher.vbs"""; Description: "Start Campus Assistant"; Flags: postinstall nowait

[UninstallRun]
Filename: "taskkill"; Parameters: "/F /IM javaw.exe"; Flags: runhidden; RunOnceId: killJava

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
begin
  if CurStep = ssInstall then
  begin
    Exec('taskkill', '/F /IM javaw.exe', '', SW_HIDE, ewNoWait, ResultCode);
  end;
end;
