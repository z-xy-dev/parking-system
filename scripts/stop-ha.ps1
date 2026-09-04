# 停止本脚本启动的全部 parking-system 实例（不含 Nacos / MySQL / Redis）
# 用法 (PowerShell)：  .\scripts\stop-ha.ps1
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*parking-system*' -or $_.CommandLine -like '*-jar *service/target*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Write-Host "stopped parking-system java instances"

# 如需同时停掉 Nginx VIP：
#   nginx -p "<项目根目录>/nginx" -c "<项目根目录>/nginx/nginx.conf" -s stop
