@echo off
REM ============================================================
REM  parking-system - Windows one-shot starter (5 backend jars + frontend)
REM  Portable template: contains PLACEHOLDER values only.
REM
REM  How to use:
REM    1. copy start-services.example.bat start-services.bat
REM    2. edit your own copy (or export env vars before running)
REM    3. double-click start-services.bat
REM
REM  NOTE: start-services.bat is git-ignored on purpose, so your real
REM  credentials never end up in the repository.
REM ============================================================

cd /d "%~dp0"

REM ===== Environment variables: prefer what is already defined =====
if not defined DB_HOST set DB_HOST=localhost
if not defined DB_PORT set DB_PORT=3306
if not defined DB_USERNAME set DB_USERNAME=root
if not defined DB_PASSWORD set DB_PASSWORD=changeme
if not defined REDIS_HOST set REDIS_HOST=localhost
if not defined REDIS_PORT set REDIS_PORT=6379
if not defined NACOS_SERVER_ADDR set NACOS_SERVER_ADDR=localhost:8848
if not defined NACOS_USERNAME set NACOS_USERNAME=nacos
if not defined NACOS_PASSWORD set NACOS_PASSWORD=nacos

set JVM_OPTS=-Xms128m -Xmx320m -XX:+UseG1GC

REM ===== Ensure logs directory exists =====
if not exist logs mkdir logs

echo Starting user-service...
start "user-service" /min cmd /c java %JVM_OPTS% -jar user-service\target\user-service-1.0.0.jar > logs\user-service.log 2>&1
timeout /t 5 /nobreak >nul

echo Starting parking-service...
start "parking-service" /min cmd /c java %JVM_OPTS% -jar parking-service\target\parking-service-1.0.0.jar > logs\parking-service.log 2>&1
timeout /t 5 /nobreak >nul

echo Starting payment-service...
start "payment-service" /min cmd /c java %JVM_OPTS% -jar payment-service\target\payment-service-1.0.0.jar > logs\payment-service.log 2>&1
timeout /t 5 /nobreak >nul

echo Starting order-service...
start "order-service" /min cmd /c java %JVM_OPTS% -jar order-service\target\order-service-1.0.0.jar > logs\order-service.log 2>&1
timeout /t 5 /nobreak >nul

echo Starting gateway-service...
start "gateway-service" /min cmd /c java %JVM_OPTS% -jar gateway-service\target\gateway-service-1.0.0.jar > logs\gateway-service.log 2>&1
timeout /t 3 /nobreak >nul

echo Starting frontend (parking-web)...
cd /d "%~dp0parking-web"
start "parking-web" /min cmd /c npm run dev
cd /d "%~dp0"

echo.
echo ========================================
echo  All services started!
echo  Backend:  user(8081) parking(8082) payment(8084) order(8085) gateway(8080)
echo  Frontend: http://localhost:5173
echo  API docs: http://localhost:8080/api/doc.html
echo  Nacos:    http://localhost:8848/nacos (nacos/nacos)
echo ========================================
