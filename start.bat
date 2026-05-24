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

:: Auto-detect Java from JAVA_HOME, fallback to PATH
set JAVA_CMD=java
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set JAVA_CMD=%JAVA_HOME%\bin\java.exe
    )
)

:: Build -D arguments for API keys (as fallback if dotenv fails)
set EXTRA_OPTS=-Dspring.datasource.password=%MYSQL_PASS%
if not "%DASHSCOPE_KEY%"=="" set EXTRA_OPTS=%EXTRA_OPTS% -DAI_DASHSCOPE_API_KEY=%DASHSCOPE_KEY%
if not "%DEEPSEEK_KEY%"=="" set EXTRA_OPTS=%EXTRA_OPTS% -DDEEPSEEK_API=%DEEPSEEK_KEY%

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
