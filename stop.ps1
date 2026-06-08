$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location -Path $PSScriptRoot

Write-Host "========================================================" -ForegroundColor Green
Write-Host "     RedStVE - 一键停止所有服务脚本" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host ""

# 1. 关闭 Spring Boot 和 Node.js 进程 (强制关闭进程树)
Write-Host "[*] 正在关闭应用服务 (Backend, Frontend, Remotion)..." -ForegroundColor Cyan

# 通过读取底层进程的 CommandLine 参数来精准抓取启动脚本唤起的黑框
$cmds = Get-CimInstance Win32_Process -Filter "Name = 'cmd.exe' OR Name = 'pwsh.exe' OR Name = 'powershell.exe'"
$found = $false
foreach ($cmd in $cmds) {
    # 兼容 cmd.exe (title RedStVE) 和 pwsh.exe (WindowTitle = 'RedStVE')
    if ($cmd.CommandLine -match "RedStVE" -and $cmd.CommandLine -notmatch "stop.ps1") {
        $found = $true
        Write-Host "  [+] 成功捕捉并清理项目运行窗口及底层进程树 (PID: $($cmd.ProcessId))" -ForegroundColor Yellow
        taskkill /F /T /PID $cmd.ProcessId 2>&1 | Out-Null
    }
}

if (-Not $found) {
    Write-Host "  [-] 未检测到正在运行的应用服务窗口。" -ForegroundColor Gray
}

Write-Host "[OK] 应用服务已全部安全关闭！" -ForegroundColor Green
Write-Host ""

# 2. 停止 Docker 基础设施
Write-Host "[*] 正在关闭基础设施 (MySQL, Redis, ChromaDB)..." -ForegroundColor Cyan
docker-compose down
if ($LASTEXITCODE -ne 0) {
    Write-Host "[警告] Docker 基础设施关闭时遇到异常，这通常是因为尚未启动或 Docker 未运行。" -ForegroundColor Yellow
} else {
    Write-Host "[OK] Docker 基础设施已全部关闭并销毁容器！" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "     所有服务已成功结束运行！期待您的再次使用。" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host ""

Write-Host "请按 Enter 键退出本控制台..." -ForegroundColor Yellow
Read-Host
