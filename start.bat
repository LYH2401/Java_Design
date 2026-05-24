@echo off
cd /d "%~dp0"
chcp 65001 >nul
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
echo   2. JDK 17+ is installed
echo   3. Database 'campus_assistant' is created
echo   4. Maven is installed (if JAR needs rebuild)
echo.

:: Check .env file
if not exist .env (
    echo [INFO] .env not found, copying from .env.example...
    copy .env.example .env >nul
    echo [INFO] Please edit .env with your config, then re-run this script
    pause
    exit /b 1
)

:: Read MySQL password from .env
for /f "tokens=2 delims==" %%a in ('findstr "MYSQL_ROOT_PASSWORD" .env') do set MYSQL_PASS=%%a
if "%MYSQL_PASS%"=="" (
    echo [WARN] MYSQL_ROOT_PASSWORD not found in .env, using default: campus123
    set MYSQL_PASS=campus123
)

:: Read API keys from .env (for -D fallback)
for /f "tokens=2 delims==" %%a in ('findstr "AI_DASHSCOPE_API_KEY" .env') do set DASHSCOPE_KEY=%%a
for /f "tokens=2 delims==" %%a in ('findstr "DEEPSEEK_API" .env') do set DEEPSEEK_KEY=%%a

:: Check JAR exists
if not exist "target\campus-assistant-1.0.0.jar" (
    echo [ERROR] JAR not found, building with Maven...
    call mvn package -DskipTests -q
    if %errorlevel% neq 0 (
        echo [ERROR] Maven build failed!
        pause
        exit /b 1
    )
    echo [OK] Maven build succeeded
)

echo.
echo [START] Launching Spring Boot application...
echo         URL:      http://localhost:8080
echo         Swagger:  http://localhost:8080/swagger-ui.html
echo.
echo Press Ctrl+C to stop
echo ========================================
echo.

:: === Auto-detect JDK 17+ ===
:: Priority: 1) JAVA_HOME  2) known JDK 25 path  3) PATH java
set JAVA_CMD=
set JAVA_VER=0

:: Try JAVA_HOME first
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

:: Try known JDK 25 path
if "%JAVA_CMD%"=="" (
    if exist "E:\JDK\jdk-25_windows-x64_bin\jdk-25.0.2\bin\java.exe" (
        set "JAVA_CMD=E:\JDK\jdk-25_windows-x64_bin\jdk-25.0.2\bin\java.exe"
    )
)

:: Fallback to PATH
if "%JAVA_CMD%"=="" set "JAVA_CMD=java"

:: Verify Java version >= 17
for /f "tokens=3" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do (
    set VER_STRING=%%v
)
:: Strip quotes from version string
set VER_STRING=%VER_STRING:"=%
:: Parse major version (handle 1.8.0, 17.0.x, 25.0.x formats)
for /f "tokens=1 delims=." %%a in ("%VER_STRING%") do set JAVA_MAJOR=%%a
if %JAVA_MAJOR% EQU 1 (
    for /f "tokens=2 delims=." %%b in ("%VER_STRING%") do set JAVA_MAJOR=%%b
)

echo [CHECK] Java command : %JAVA_CMD%
echo [CHECK] Java version : %VER_STRING% (major=%JAVA_MAJOR%)

if %JAVA_MAJOR% LSS 17 (
    echo [ERROR] JDK 17+ is required, but found version %JAVA_MAJOR%
    echo         Install JDK 17+ from: https://adoptium.net/
    echo         Or set JAVA_HOME to point to a JDK 17+ installation
    pause
    exit /b 1
)

:: Build -D arguments for API keys (as fallback if dotenv fails)
set EXTRA_OPTS=-Dspring.datasource.password=%MYSQL_PASS%
if not "%DASHSCOPE_KEY%"=="" set EXTRA_OPTS=%EXTRA_OPTS% -DAI_DASHSCOPE_API_KEY=%DASHSCOPE_KEY%
if not "%DEEPSEEK_KEY%"=="" set EXTRA_OPTS=%EXTRA_OPTS% -DDEEPSEEK_API=%DEEPSEEK_KEY%

echo [START] Using Java: %JAVA_CMD%
echo.
"%JAVA_CMD%" %EXTRA_OPTS% -jar target\campus-assistant-1.0.0.jar
goto END

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
    copy .env.example .env >nul
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
