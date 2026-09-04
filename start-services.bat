@echo off
cd /d C:\Users\ZXY\Desktop\parking-system
set JVM_OPTS=-Xms128m -Xmx320m -XX:+UseG1GC

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

echo All services started. Waiting for registration...
