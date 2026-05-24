@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title Campus Assistant Launcher

echo ========================================
echo   Campus Assistant - Launcher
echo ========================================
echo.

echo Select startup mode:
echo   [1] Direct start (recommended, no Docker)
echo   [2] Docker start (requires Docker Desktop)
echo   [3] Exit
echo.

set /p MODE="Enter option [1/2/3]: "

if "%MODE%"=="1" goto DIRECT_START
if "%MODE%"=="2" goto DOCKER_START
if "%MODE%"=="3" exit /b 0
echo Invalid option, enter 1, 2 or 3
goto END

:DIRECT_START
echo.
echo ========================================
echo   Direct Start Mode
echo ========================================
echo.
echo [Prerequisites] Please ensure:
echo   1. MySQL 8.0 is running locally (port 3306)
echo   2. JDK 17+ is installed (auto-detected below)
echo   3. Database 'campus_assistant' is created
echo   4. Maven is installed (if JAR needs rebuild)
echo.

:: ============================================================
:: STEP 1 - Load environment from .env file
:: ============================================================
echo [STEP 1] Loading .env...

if not exist .env (
    if exist .env.example (
        echo [INFO] .env not found, copying from .env.example...
        copy /y .env.example .env >nul
        echo [WARN] Please edit .env with your real API keys, then re-run.
        echo        Required: AI_DASHSCOPE_API_KEY, DEEPSEEK_API, MYSQL_ROOT_PASSWORD
        pause
        exit /b 1
    ) else (
        echo [ERROR] .env and .env.example not found!
        pause
        exit /b 1
    )
)

:: Parse .env file (key=value, skip comments and blank lines)
set "MYSQL_PASS="
set "DASHSCOPE_KEY="
set "DEEPSEEK_KEY="

for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    set "key=%%a"
    set "val=%%b"
    if not "!key!"=="" (
        if not "!key:~0,1!"=="#" (
            for /f "tokens=1" %%v in ("!val!") do set "val_clean=%%v"
            if "!key!"=="MYSQL_ROOT_PASSWORD" set "MYSQL_PASS=!val_clean!"
            if "!key!"=="AI_DASHSCOPE_API_KEY" set "DASHSCOPE_KEY=!val_clean!"
            if "!key!"=="DEEPSEEK_API" set "DEEPSEEK_KEY=!val_clean!"
        )
    )
)

if "%MYSQL_PASS%"=="" (
    echo [WARN] MYSQL_ROOT_PASSWORD not found in .env, using default: campus123
    set "MYSQL_PASS=campus123"
) else (
    echo [OK]   MYSQL_ROOT_PASSWORD = ****
)

if "%DASHSCOPE_KEY%"=="" (
    echo [WARN] AI_DASHSCOPE_API_KEY not set in .env - DashScope model will fail!
) else (
    echo [OK]   AI_DASHSCOPE_API_KEY = ****
)

if "%DEEPSEEK_KEY%"=="" (
    echo [WARN] DEEPSEEK_API not set in .env - DeepSeek model will fail!
) else (
    echo [OK]   DEEPSEEK_API = ****
)
echo.

:: ============================================================
:: STEP 2 - Auto-detect JDK 17+
:: ============================================================
echo [STEP 2] Scanning for JDK 17+...

set "FOUND_JAVA="

:: --- Strategy 1: JAVA_HOME environment variable ---
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        call :try_java "%JAVA_HOME%\bin\java.exe" && (
            set "FOUND_JAVA=%JAVA_HOME%\bin\java.exe"
            goto :java_ok
        )
    )
)

:: --- Strategy 2: Scan common JDK directories recursively ---
echo          Scanning common JDK install locations...

:: Collect candidate directories: list all folders under E:\JDK, C:\Program Files\Java etc.
set "SEARCH_PATHS=E:\JDK C:\Program Files\Java C:\Program Files\Eclipse Adoptium D:\JDK C:\Java"

for %%p in (%SEARCH_PATHS%) do (
    if exist "%%p\" (
        for /f "delims=" %%d in ('dir /b /ad "%%p" 2^>nul') do (
            if exist "%%p\%%d\bin\java.exe" (
                call :try_java "%%p\%%d\bin\java.exe" && (
                    set "FOUND_JAVA=%%p\%%d\bin\java.exe"
                    goto :java_ok
                )
            )
        )
    )
)

:: --- Strategy 3: Fallback - check all java.exe on PATH ---
echo          Checking PATH...

for /f "delims=" %%j in ('where java 2^>nul') do (
    call :try_java "%%j" && (
        set "FOUND_JAVA=%%j"
        goto :java_ok
    )
)

:: --- Strategy 4: Deep scan E:\JDK tree (slower but thorough) ---
echo          Deep scanning...
for /f "delims=" %%j in ('dir /s /b "E:\JDK\java.exe" 2^>nul') do (
    call :try_java "%%j" && (
        set "FOUND_JAVA=%%j"
        goto :java_ok
    )
)

:: If we get here, no JDK 17+ was found
echo.
echo [ERROR] No JDK 17+ found on this system!
echo.
echo   Troubleshooting:
echo     1. Install JDK 17+ from https://adoptium.net/
echo     2. Or set JAVA_HOME system variable to your JDK 17+ path
echo     3. Known JDK paths found on your system:
where java 2>nul
echo.
pause
exit /b 1

:java_ok
echo [OK]   Found JDK: !FOUND_JAVA!
echo [OK]   Version : !JAVA_VER!
echo.

:: ============================================================
:: STEP 3 - Check / Build JAR
:: ============================================================
echo [STEP 3] Checking JAR...

if not exist "target\campus-assistant-1.0.0.jar" (
    echo [INFO] JAR not found, building with Maven...
    :: Use the found JDK for Maven too
    for %%F in ("!FOUND_JAVA!") do set "JDK_BIN=%%~dpF"
    set "PATH=!JDK_BIN!;%PATH%"
    call mvn package -DskipTests -q
    if !errorlevel! neq 0 (
        echo [ERROR] Maven build failed! Is Maven installed and on PATH?
        pause
        exit /b 1
    )
    echo [OK]   Build succeeded
) else (
    echo [OK]   JAR found: target\campus-assistant-1.0.0.jar
)
echo.

:: ============================================================
:: STEP 4 - Launch Application
:: ============================================================
echo [STEP 4] Starting application...
echo.
echo   URL:      http://localhost:8080
echo   Swagger:  http://localhost:8080/swagger-ui.html
echo   Log:      campus.log
echo.
echo   Press Ctrl+C in the terminal window to stop
echo ========================================
echo.

:: Build Java options
set "JAVA_OPTS=-Dspring.datasource.password=%MYSQL_PASS%"
if not "%DASHSCOPE_KEY%"=="" set "JAVA_OPTS=%JAVA_OPTS% -DAI_DASHSCOPE_API_KEY=%DASHSCOPE_KEY%"
if not "%DEEPSEEK_KEY%"=="" set "JAVA_OPTS=%JAVA_OPTS% -DDEEPSEEK_API=%DEEPSEEK_KEY%"

:: Launch application in the same window (so user can Ctrl+C)
"%FOUND_JAVA%" %JAVA_OPTS% -jar target\campus-assistant-1.0.0.jar 2>&1

goto END

:: ============================================================
:: DOCKER START MODE
:: ============================================================
:DOCKER_START
echo.
echo ========================================
echo   Docker Start Mode
echo ========================================
echo.

echo [CHECK] Docker environment...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker Desktop is not installed
    echo        Download: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)
echo [OK] Docker is installed

echo.

if not exist .env (
    echo [INFO] .env not found, copying from .env.example...
    copy /y .env.example .env >nul
    echo [IMPORTANT] Please edit .env and fill in your API keys:
    echo        AI_DASHSCOPE_API_KEY=sk-your-api-key-here
    echo        DEEPSEEK_API=sk-your-deepseek-key-here
    echo.
    echo Then re-run this script.
    pause
    exit /b 1
)

echo [OK] .env file exists

echo.
echo [BUILD] Building Docker image...
docker compose build
if %errorlevel% neq 0 (
    echo [ERROR] Docker image build failed!
    pause
    exit /b 1
)

echo.
echo [START] Starting services...
docker compose up -d
if %errorlevel% neq 0 (
    echo [ERROR] Service startup failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   Startup Complete!
echo.
echo   Frontend:  http://localhost:8080
echo   Swagger:   http://localhost:8080/swagger-ui.html
echo.
echo   Commands:
echo     View logs:    docker compose logs -f
echo     Stop:         docker compose down
echo     Restart:      docker compose restart
echo ========================================

:END
pause
exit /b 0

:: ============================================================
:: Helper: Test if a java.exe is JDK 17+
:: Usage: call :try_java "path\to\java.exe"
:: Sets JAVA_VER and returns errorlevel 0 if JDK 17+, 1 otherwise
:: ============================================================
:try_java
set "TEST_JAVA=%~1"
if not exist "!TEST_JAVA!" exit /b 1

:: Get version string from java -version (goes to stderr)
for /f "tokens=3" %%v in ('"!TEST_JAVA!" -version 2^>^&1 ^| findstr /i "version"') do (
    set "VER_STR=%%~v"
)

:: If we couldn't get version, skip this candidate
if "!VER_STR!"=="" exit /b 1

:: Parse major version
:: Formats: "1.8.0_491" -> major=8, "17.0.9" -> major=17, "25.0.2" -> major=25
set "MAJOR=!VER_STR!"
for /f "tokens=1 delims=." %%a in ("!VER_STR!") do (
    set "MAJOR=%%a"
)
if "!MAJOR!"=="1" (
    for /f "tokens=2 delims=." %%b in ("!VER_STR!") do set "MAJOR=%%b"
)

:: Check if major >= 17
if !MAJOR! GEQ 17 (
    set "JAVA_VER=!VER_STR!"
    exit /b 0
)

:: Not suitable, clean up and return failure
set "VER_STR="
set "MAJOR="
exit /b 1
