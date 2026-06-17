Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")

AppDir = FSO.GetParentFolderName(WScript.ScriptFullName)
JavaExe = AppDir & "\runtime\bin\javaw.exe"
JarFile = AppDir & "\campus-assistant.jar"

Function IsServerRunning()
    On Error Resume Next
    Set Http = CreateObject("MSXML2.ServerXMLHTTP")
    Http.SetTimeouts 2000, 2000, 2000, 2000
    Http.Open "GET", "http://localhost:8080/", False
    Http.Send
    If Http.Status >= 200 And Http.Status < 500 Then
        IsServerRunning = True
    Else
        IsServerRunning = False
    End If
    Http.Close
    On Error GoTo 0
End Function

If Not IsServerRunning() Then
    JavaArgs = "--add-opens java.base/java.lang=ALL-UNNAMED " & _
               "--add-opens java.base/java.util=ALL-UNNAMED " & _
               "--add-opens java.base/java.lang.reflect=ALL-UNNAMED " & _
               "--add-opens java.base/java.text=ALL-UNNAMED " & _
               "--add-opens java.base/java.time=ALL-UNNAMED " & _
               "-Dspring.profiles.active=standalone " & _
               "-jar """ & JarFile & """"
    WshShell.Run """" & JavaExe & """ " & JavaArgs, 0, False
    WScript.Sleep 3000
    For i = 1 To 30
        If IsServerRunning() Then Exit For
        WScript.Sleep 2000
    Next
End If

WshShell.Run "http://localhost:8080"
