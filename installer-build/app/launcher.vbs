Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")
AppDir = FSO.GetParentFolderName(WScript.ScriptFullName)
JavaExe = AppDir & "\runtime\bin\javaw.exe"
JarFile = AppDir & "\campus-assistant.jar"
JavaArgs = "--add-opens java.base/java.lang=ALL-UNNAMED " & _
           "--add-opens java.base/java.util=ALL-UNNAMED " & _
           "--add-opens java.base/java.lang.reflect=ALL-UNNAMED " & _
           "--add-opens java.base/java.text=ALL-UNNAMED " & _
           "--add-opens java.base/java.time=ALL-UNNAMED " & _
           "-Dspring.profiles.active=standalone " & _
           "-jar """ & JarFile & """"
WshShell.Run """" & JavaExe & """ " & JavaArgs, 0, False
For i = 1 To 40
    WScript.Sleep 3000
    On Error Resume Next
    Set Http = CreateObject("MSXML2.ServerXMLHTTP")
    Http.SetTimeouts 2000, 2000, 2000, 2000
    Http.Open "GET", "http://localhost:8080/", False
    Http.Send
    If Http.Status >= 200 And Http.Status < 500 Then
        On Error GoTo 0
        Exit For
    End If
    Http.Close
    On Error GoTo 0
Next
WshShell.Run "http://localhost:8080"
