[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=0
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles
[Strings]
InstallPrompt=This will install Campus Assistant on your computer. Do you wish to continue?
DisplayLicense=
FinishMessage=Campus Assistant has been installed successfully! A shortcut has been created on your desktop.
TargetName=E:\Desktop\installer\CampusAssistant-Setup.exe
FriendlyName=Campus Assistant
AppLaunched=setup-post.bat
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0="campus-assistant.jar"
FILE1="setup-post.bat"
[SourceFiles]
SourceFiles0=E:\Desktop\新建文件夹\Java 实验一\Java课设\校园智能服务小助手\installer-build\app
[SourceFiles0]
%FILE0%=
%FILE1%=
