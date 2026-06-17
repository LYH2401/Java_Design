[Setup]
AppName=Campus Assistant
AppVersion=0.0.2
AppPublisher=Campus
DefaultDirName={autopf}\Campus Assistant
DefaultGroupName=Campus Assistant
OutputDir=E:\Desktop\installer
OutputBaseFilename=CampusAssistantV0.0.2_Setup
SetupIconFile=E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\ico\favicon (1).ico
Compression=lzma2/max
SolidCompression=yes
UninstallDisplayName=Campus Assistant
UninstallDisplayIcon={app}\icon.ico
DisableDirPage=no
DisableProgramGroupPage=no
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\campus-assistant.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\launcher.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app\icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\Campus Assistant\Campus Assistant"; Filename: "{app}\launcher.bat"; WorkingDir: "{app}"; IconFilename: "{app}\icon.ico"; Comment: "Start Campus Assistant"
Name: "{autoprograms}\Campus Assistant\Uninstall Campus Assistant"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Campus Assistant"; Filename: "{app}\launcher.bat"; WorkingDir: "{app}"; IconFilename: "{app}\icon.ico"; Comment: "Start Campus Assistant"

[UninstallRun]
Filename: "taskkill"; Parameters: "/F /IM javaw.exe"; Flags: runhidden; RunOnceId: killJavaw
Filename: "taskkill"; Parameters: "/F /IM java.exe"; Flags: runhidden; RunOnceId: killJava
Filename: "{cmd}"; Parameters: "/c if exist ""%USERPROFILE%\.campus-assistant"" rmdir /s /q ""%USERPROFILE%\.campus-assistant"""; Flags: runhidden; RunOnceId: cleanData

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
begin
  if CurStep = ssInstall then
  begin
    Exec('taskkill', '/F /IM javaw.exe', '', SW_HIDE, ewNoWait, ResultCode);
    Exec('taskkill', '/F /IM java.exe', '', SW_HIDE, ewNoWait, ResultCode);
  end;
end;
