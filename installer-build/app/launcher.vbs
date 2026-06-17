Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")

AppDir = FSO.GetParentFolderName(WScript.ScriptFullName)
JavaExe = AppDir & "\runtime\bin\javaw.exe"
JarFile = AppDir & "\campus-assistant.jar"

JavaOpts = "--add-opens java.base/java.lang=ALL-UNNAMED " & _
           "--add-opens java.base/java.util=ALL-UNNAMED " & _
           "--add-opens java.base/java.lang.reflect=ALL-UNNAMED " & _
           "--add-opens java.base/java.text=ALL-UNNAMED " & _
           "--add-opens java.base/java.time=ALL-UNNAMED " & _
           "-Dspring.profiles.active=standalone"

Cmd = """" & JavaExe & """ " & JavaOpts & " -jar """ & JarFile & """"
WshShell.Run Cmd, 0, False

Const MaxWait = 180
For i = 1 To MaxWait
    WScript.Sleep 1000
    On Error Resume Next
    Set Http = CreateObject("MSXML2.ServerXMLHTTP")
    Http.SetTimeouts 2000, 2000, 2000, 2000
    Http.Open "GET", "http://localhost:8080/", False
    Http.SetRequestHeader "Accept", "text/html"
    Http.Send
    StatusCode = Http.Status
    Http.Close
    If StatusCode > 0 Then
        On Error GoTo 0
        Exit For
    End If
    On Error GoTo 0
Next

WshShell.Run "http://localhost:8080"
