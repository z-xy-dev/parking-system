# 一键拉起 5 个微服务（独立进程，不随启动器退出被杀）
# 依赖：MySQL 3306 本机、Nacos 8848 + Redis 6379 容器
# DB / Nacos 凭据写在此文件，避免出现在命令行里
# 注意1：本机 Start-Process 会因 Path/PATH 重复键报错，必须用 .NET Process 类
# 注意2：不要用 RedirectStandardOutput（不读取会撑满缓冲区卡死 java），走 cmd 落盘
$ErrorActionPreference = "Continue"
$root = "."
$java = "java.exe"
$logDir = Join-Path $root "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# WorkBuddy 会话注入 SERVER__PORT=0，会被 Spring 映射成 server.port=0（随机端口）
Remove-Item Env:\SERVER__PORT -ErrorAction SilentlyContinue
Remove-Item Env:\SERVER__HOST -ErrorAction SilentlyContinue

$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "changeme"
$env:NACOS_USERNAME = "nacos"
$env:NACOS_PASSWORD = "nacos"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"

# 多网卡下 Nacos 客户端会挑错 IP（实测挑到 192.168.21.1，网关路由 10061），固定注册 IP
$ipArg = "--spring.cloud.nacos.discovery.ip=127.0.0.1"

$services = @(
  @{ Name = "user";    Jar = "user-service\target\user-service-1.0.0.jar";       Port = 8081; Wait = 12 },
  @{ Name = "parking"; Jar = "parking-service\target\parking-service-1.0.0.jar"; Port = 8082; Wait = 12 },
  @{ Name = "payment"; Jar = "payment-service\target\payment-service-1.0.0.jar"; Port = 8084; Wait = 12 },
  @{ Name = "order";   Jar = "order-service\target\order-service-1.0.0.jar";     Port = 8085; Wait = 15 },
  @{ Name = "gateway"; Jar = "gateway-service\target\gateway-service-1.0.0.jar"; Port = 8080; Wait = 18 }
)

$result = @()
foreach ($s in $services) {
  $jarPath = Join-Path $root $s.Jar
  if (-not (Test-Path $jarPath)) { $result += "MISSING JAR: $jarPath"; continue }

  $out = Join-Path $logDir ($s.Name + ".log")
  $err = Join-Path $logDir ($s.Name + ".err.log")
  # 通过 cmd /c 启动，输出直接落盘；进程由 Windows 会话持有，不随本脚本结束被杀
  $cmdArgs = '/c ""' + $java + '" -jar "' + $jarPath + '" --server.port=' + $s.Port + ' ' + $ipArg + ' > "' + $out + '" 2> "' + $err + '""'

  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = "cmd.exe"
  $psi.Arguments = $cmdArgs
  $psi.WorkingDirectory = $root
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $p = [System.Diagnostics.Process]::Start($psi)
  $result += ("started " + $s.Name + " port=" + $s.Port + " pid=" + $p.Id)
  Start-Sleep -Seconds $s.Wait
}

Start-Sleep -Seconds 12
foreach ($pt in 8080, 8081, 8082, 8084, 8085) {
  $c = Get-NetTCPConnection -LocalPort $pt -State Listen -ErrorAction SilentlyContinue
  if ($c) { $result += "port $pt LISTENING" } else { $result += "port $pt NOT listening" }
}
$result | Set-Content -Path "$logDir\port-report.txt"
