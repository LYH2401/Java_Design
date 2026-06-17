Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")

AppDir = FSO.GetParentFolderName(WScript.ScriptFullName)
JavaExe = AppDir & "\runtime\bin\javaw.exe"
JarFile = AppDir & "\campus-assistant.jar"

WScript.Echo "AppDir: " & AppDir
WScript.Echo "JavaExe: " & JavaExe
WScript.Echo "JarFile: " & JarFile
WScript.Echo "JavaExe exists: " & FSO.FileExists(JavaExe)
WScript.Echo "JarFile exists: " & FSO.FileExists(JarFile)

JavaArgs = "--add-opens java.base/java.lang=ALL-UNNAMED " & _
           "--add-opens java.base/java.util=ALL-UNNAMED " & _
           "--add-opens java.base/java.lang.reflect=ALL-UNNAMED " & _
           "--add-opens java.base/java.text=ALL-UNNAMED " & _
           "--add-opens java.base/java.time=ALL-UNNAMED " & _
           "-Dspring.profiles.active=standalone " & _
           "-jar """ & JarFile & """"

Cmd = """" & JavaExe & """ " & JavaArgs
WScript.Echo "Command: " & Cmd

result = WshShell.Run(Cmd, 0, False)
WScript.Echo "Run result: " & result
