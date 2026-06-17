@echo off
cd /d "%~dp0"
taskkill /F /IM javaw.exe >nul 2>&1
start "" "runtime\bin\javaw.exe" --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED -Dspring.profiles.active=standalone -jar "campus-assistant.jar"
powershell -NoProfile -WindowStyle Hidden -Command "$i=0; while($i -lt 40){Start-Sleep 3; try{$r=Invoke-WebRequest 'http://localhost:8080/' -TimeoutSec 2 -UseBasicParsing; if($r){cmd /c start http://localhost:8080; exit 0}}catch{}$i++} cmd /c start http://localhost:8080"
exit
