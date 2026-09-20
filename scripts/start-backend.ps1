# 一键拉起 5 个微服务（独立进程，不随启动器退出被杀）
# 依赖：MySQL 3306、Nacos 8848、Redis 6379（可用 scripts/start-infra.sh up 拉起）
#
# 用法（PowerShell）：.\scripts\start-backend.ps1
#
# 凭据来源（优先级从高到低）：已有环境变量 > 项目根目录 .env > 占位默认值
# 复制 .env.example 为 .env 并填写自己的密码即可，.env 已被 .gitignore 忽略。
#
# 注意1：本机 Start-Process 会因 Path/PATH 重复键报错，必须用 .NET Process 类
# 注意2：不要用 RedirectStandardOutput（不读取会撑满缓冲区卡死 java），走 cmd 落盘
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java.exe" }
$logDir = Join-Path $root "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

if (Test-Path (Join-Path $root ".env")) {
  Get-Content (Join-Path $root ".env") | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
      $name = $matches[1]
      if (-not (Test-Path "Env:$name")) { Set-Item "Env:$name" $matches[2].Trim() }
    }
  }
}
if (-not $env:DB_HOST) { $env:DB_HOST = "localhost" }
if (-not $env:DB_PORT) { $env:DB_PORT = "3306" }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = "root" }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "changeme" }
if (-not $env:NACOS_SERVER_ADDR) { $env:NACOS_SERVER_ADDR = "localhost:8848" }
if (-not $env:NACOS_USERNAME) { $env:NACOS_USERNAME = "nacos" }
if (-not $env:NACOS_PASSWORD) { $env:NACOS_PASSWORD = "changeme" }
if (-not $env:REDIS_HOST) { $env:REDIS_HOST = "localhost" }
if (-not $env:REDIS_PORT) { $env:REDIS_PORT = "6379" }

# 某些会话会注入 SERVER__PORT=0，会被 Spring 映射成 server.port=0（随机端口）
Remove-Item Env:\SERVER__PORT -ErrorAction SilentlyContinue
Remove-Item Env:\SERVER__HOST -ErrorAction SilentlyContinue

# 多网卡下 Nacos 客户端会挑错 IP（实测挑到虚拟网卡地址，网关路由 10061），固定注册 IP
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
