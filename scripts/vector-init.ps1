# ============================================================================
# 向量数据初始化脚本 (PowerShell)
# 将 MySQL / 文件系统中的模板、BGM、品类知识同步至 Chroma 向量库
#
# 用法：
#   .\scripts\vector-init.ps1                 # 默认 http://localhost:8080
#   .\scripts\vector-init.ps1 http://host:port # 自定义服务地址
# ============================================================================
param(
    [string]$BaseUrl = "http://100.79.235.109:8080"
)

$Endpoint = "${BaseUrl}/api/v1/admin/vector/init"

Write-Host ">>> 向量数据初始化" -ForegroundColor Cyan
Write-Host ">>> 目标: ${Endpoint}"
Write-Host ""

try {
    $Response = Invoke-RestMethod -Uri $Endpoint -Method Post -ContentType "application/json"
    Write-Host ">>> 初始化完成" -ForegroundColor Green
    $Response | ConvertTo-Json -Depth 5
} catch {
    Write-Host ">>> 初始化失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
