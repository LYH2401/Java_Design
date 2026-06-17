@echo off
cd /d "%~dp0"
start "" "runtime\bin\javaw.exe" --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED -Dspring.profiles.active=standalone -jar "campus-assistant.jar"
powershell -NoProfile -WindowStyle Hidden -Command "do { Start-Sleep 3; try { $r = Invoke-WebRequest 'http://localhost:8080/' -TimeoutSec 2 -UseBasicParsing; if ($r) { Start-Process 'http://localhost:8080'; exit 0 } } catch {} } while ($true)"
exit
