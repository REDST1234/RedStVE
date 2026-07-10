$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 强制将工作目录切换到脚本所在的项目根目录，防止找不到 docker-compose.yml
Set-Location -Path $PSScriptRoot

function Pause-And-Exit {
    Write-Host "`n请按 Enter 键退出..." -ForegroundColor Yellow
    Read-Host
    exit 1
}

$pwshCommand = Get-Command "pwsh.exe" -ErrorAction SilentlyContinue
if (-Not $pwshCommand) {
    Write-Host "[异常] 未找到 pwsh.exe！请先安装 PowerShell 7+，再重新执行启动脚本。" -ForegroundColor Red
    Pause-And-Exit
}

$pwshExe = $pwshCommand.Source

if ($PSVersionTable.PSEdition -ne "Core") {
    Write-Host "[*] 检测到当前不是 pwsh，正在自动切换到 PowerShell 7..." -ForegroundColor Cyan
    & $pwshExe -NoLogo -NoProfile -File $PSCommandPath @args
    exit $LASTEXITCODE
}

Write-Host "========================================================" -ForegroundColor Green
Write-Host "     RedStVE - 一键环境检查与项目启动脚本" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host ""

# 1. 环境检查
Write-Host "[*] 正在检查系统依赖环境..." -ForegroundColor Cyan

# 检查 Java
try {
    $javaVer = (java -version 2>&1)
    if ($LASTEXITCODE -ne 0 -and $javaVer -match "is not recognized") { throw }
}
catch {
    Write-Host "[异常] 未找到 Java 环境！请安装 JDK (推荐 17+) 并配置到系统环境变量。" -ForegroundColor Red
    Pause-And-Exit
}

# 检查 Maven
try {
    $mvnVer = (mvn -v 2>&1)
    if ($LASTEXITCODE -ne 0 -and $mvnVer -match "is not recognized") { throw }
}
catch {
    Write-Host "[异常] 未找到 Maven 环境！请安装 Maven 并配置到系统环境变量。" -ForegroundColor Red
    Pause-And-Exit
}

# 检查 Node.js
try {
    $nodeVer = (node -v 2>&1)
    if ($LASTEXITCODE -ne 0 -and $nodeVer -match "is not recognized") { throw }
}
catch {
    Write-Host "[异常] 未找到 Node.js 环境！请安装 Node.js (推荐 18+) 并配置到系统环境变量。" -ForegroundColor Red
    Pause-And-Exit
}

# 检查 FFmpeg
try {
    $ffmpegVer = (ffmpeg -version 2>&1)
    if ($LASTEXITCODE -ne 0 -and $ffmpegVer -match "is not recognized") { throw }
}
catch {
    Write-Host "[异常] 未找到 FFmpeg 环境！视频处理核心依赖 FFmpeg，请安装并配置到系统 PATH 中。" -ForegroundColor Red
    Pause-And-Exit
}

# 检查 Docker
try {
    $dockerVer = (docker -v 2>&1)
    if ($LASTEXITCODE -ne 0 -and $dockerVer -match "is not recognized") { throw }
}
catch {
    Write-Host "[异常] 未找到 Docker 环境！MySQL、Redis 和 ChromaDB 向量库强依赖 Docker，请确保已安装并启动 Docker Desktop。" -ForegroundColor Red
    Pause-And-Exit
}

Write-Host "[OK] 所有基础环境命令检查通过！" -ForegroundColor Green
Write-Host ""

# 1.1 核心外围 AI 服务状态探测 (非阻塞检测)
Write-Host "[*] 正在探测本地重型 AI 服务状态 (ComfyUI 等)..." -ForegroundColor Cyan
$comfyPort8188 = Test-NetConnection -ComputerName "localhost" -Port 8188 -WarningAction SilentlyContinue
$comfyPort8001 = Test-NetConnection -ComputerName "localhost" -Port 8001 -WarningAction SilentlyContinue

if ($comfyPort8188.TcpTestSucceeded -or $comfyPort8001.TcpTestSucceeded) {
    Write-Host "  [✓] 成功探测到 ComfyUI 正在后台运行！" -ForegroundColor Green
    
    # 进一步探测 ComfyUI-RMBG 插件依赖
    $comfyPort = if ($comfyPort8188.TcpTestSucceeded) { 8188 } else { 8001 }
    Write-Host "  [*] 正在检查 ComfyUI-RMBG (背景移除插件) 是否已加载..." -ForegroundColor Cyan
    try {
        $objectInfo = Invoke-WebRequest -Uri "http://localhost:$comfyPort/object_info" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        # 使用正则模糊匹配 RMBG 相关节点类名或标识 (忽略大小写)
        if ($objectInfo.Content -match "(?i)(rmbg|remove.*background)") {
            Write-Host "  [✓] 已检测到 RMBG 抠图节点！(智能抠图工作流已完全就绪)" -ForegroundColor Green
        } else {
            Write-Host "  [警告] ComfyUI 已启动，但似乎未检测到 'ComfyUI-RMBG' 插件节点！" -ForegroundColor Yellow
            Write-Host "         缺少该插件会导致【AI 视频抠图与背景移除】工作流执行失败。" -ForegroundColor Gray
            Write-Host "         请评委/开发者在 ComfyUI Manager 中搜索 'ComfyUI-RMBG' 并进行安装。" -ForegroundColor Gray
        }
    } catch {
        Write-Host "  [警告] 无法读取 ComfyUI 节点信息，可能是服务仍在初始化中。请确保您已安装 RMBG 插件。" -ForegroundColor Yellow
    }

} else {
    Write-Host "  [警告] 当前未探测到运行中的 ComfyUI 服务 (端口 8188 / 8001)！" -ForegroundColor Yellow
    Write-Host "         这不会阻止系统启动，但在您使用【AI 视频抠图与背景移除】功能时将会报错。" -ForegroundColor Gray
    Write-Host "         请在需要时，确保您已单独启动了 ComfyUI 的环境及工作流。" -ForegroundColor Gray
}
Write-Host ""

# 1.1.5 探测 Ollama 服务及模型
Write-Host "[*] 正在探测 Ollama 向量模型服务状态..." -ForegroundColor Cyan
$ollamaPort11434 = Test-NetConnection -ComputerName "localhost" -Port 11434 -WarningAction SilentlyContinue
if ($ollamaPort11434.TcpTestSucceeded) {
    Write-Host "  [✓] 成功探测到 Ollama 服务正在后台运行！" -ForegroundColor Green
    try {
        $ollamaList = (ollama list 2>&1)
        if ($ollamaList -match "nomic-embed-text") {
            Write-Host "  [✓] 核心嵌入模型 [nomic-embed-text] 已就绪！" -ForegroundColor Green
        } else {
            Write-Host "  [警告] Ollama 中未检测到 [nomic-embed-text] 模型！" -ForegroundColor Yellow
            Write-Host "         正在尝试自动为您拉取 (根据网速可能需要几分钟，请耐心等待)..." -ForegroundColor Cyan
            # 如果不想阻塞启动流程，也可以只给提示。这里选择自动阻塞拉取，避免后续执行报错。
            ollama pull nomic-embed-text
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  [✓] 模型 [nomic-embed-text] 自动拉取成功！" -ForegroundColor Green
            } else {
                Write-Host "  [异常] 模型 [nomic-embed-text] 拉取失败，这会导致后续拆解向量库同步报错！请手动执行 ollama pull nomic-embed-text" -ForegroundColor Red
            }
        }
    } catch {
        Write-Host "  [警告] 无法通过命令行读取 Ollama 信息，请确认已将 ollama 路径加入环境变量。" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [警告] 当前未探测到运行中的 Ollama 服务 (端口 11434)！" -ForegroundColor Yellow
    Write-Host "         缺少此服务会导致【视频结构化数据同步向量库】(Sync to VectorStore) 报错。" -ForegroundColor Gray
    Write-Host "         如果是首次安装，请双击打开 Ollama。如果是使用远端 Ollama，请在 .env 中修改 OLLAMA_BASE_URL。" -ForegroundColor Gray
}
Write-Host ""

# 1.2 自动初始化环境变量文件（解决新机器痛点）
Write-Host "[*] 正在检查并初始化环境变量文件..." -ForegroundColor Cyan

$EnvCreated = $false

# 后端
if (-Not (Test-Path "backend\.env")) {
    if (Test-Path "backend\.env.template") {
        Copy-Item -Path "backend\.env.template" -Destination "backend\.env"
        Write-Host "  [+] 检测为新机器，已自动为您生成 backend\.env" -ForegroundColor Green
        $EnvCreated = $true
    }
} else {
    Write-Host "  [✓] backend\.env 已存在" -ForegroundColor Gray
}

# 前端
if (-Not (Test-Path "frontend\.env.local")) {
    if (Test-Path "frontend\.env.local.example") {
        Copy-Item -Path "frontend\.env.local.example" -Destination "frontend\.env.local"
        Write-Host "  [+] 检测为新机器，已自动为您生成 frontend\.env.local" -ForegroundColor Green
        $EnvCreated = $true
    }
} else {
    Write-Host "  [✓] frontend\.env.local 已存在" -ForegroundColor Gray
}

if ($EnvCreated) {
    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Magenta
    Write-Host " [中断提醒] 系统检测到这是首次拉取代码，已为您生成配置文件！" -ForegroundColor Magenta
    Write-Host " 请您现在立刻使用编辑器打开以下文件，并填入您的 API-KEY 和端口：" -ForegroundColor Magenta
    Write-Host "  1. backend/.env" -ForegroundColor Yellow
    Write-Host "  2. frontend/.env.local" -ForegroundColor Yellow
    Write-Host " 填写完毕并保存后，请重新执行：pwsh -File .\start.ps1" -ForegroundColor Magenta
    Write-Host "========================================================" -ForegroundColor Magenta
    Pause-And-Exit
}
Write-Host ""

# 2. 启动基础设施 (MySQL, Redis, Chroma)
Write-Host "[*] 正在启动基础设施 (MySQL, Redis, ChromaDB)..." -ForegroundColor Cyan
docker-compose up -d mysql redis chroma
if ($LASTEXITCODE -ne 0) {
    Write-Host "[异常] 基础设施启动失败！请检查 Docker 引擎是否正常运行。" -ForegroundColor Red
    Pause-And-Exit
}
Write-Host "[OK] 基础设施启动成功！" -ForegroundColor Green
Write-Host "[*] 正在等待数据库与向量库完成初次环境预热（约需 15 秒，请耐心等待）..." -ForegroundColor Yellow
Start-Sleep -Seconds 15
Write-Host "[OK] 预热完成，底层基础服务就绪！" -ForegroundColor Green
Write-Host ""

# 3. 安装依赖与启动各个服务
Write-Host "[*] 准备启动各个应用服务（将打开新的命令行窗口）..." -ForegroundColor Cyan

# 统一屏蔽可能污染本地启动的全局系统环境变量
$env:REDIS_HOST = $null
$env:REDIS_PASSWORD = $null
$env:DB_PASSWORD = $null
$env:DB_USERNAME = $null
$env:ARK_API_KEY = $null
$env:ARK_MODEL_ENDPOINT = $null
$env:SEEDREAM_API_KEY = $null
$env:OLLAMA_BASE_URL = $null
$env:COMFYUI_BASE_URL = $null

# 启动 Remotion Service
Write-Host "[*] 正在处理 Remotion Service..." -ForegroundColor Cyan
Set-Location -Path "remotion-service"
if (-Not (Test-Path "node_modules")) {
    Write-Host "[*] 初次运行，正在安装 Remotion 依赖，请稍候..." -ForegroundColor Yellow
    npm install
}
Write-Host "[*] 触发启动 Remotion Service (已最小化至任务栏)..." -ForegroundColor Green
Start-Process $pwshExe -WindowStyle Minimized -ArgumentList "-NoExit", "-Command", "`$host.ui.RawUI.WindowTitle = 'RedStVE - Remotion Service'; Write-Host '正在启动 Remotion Service (npm run server)...'; npm run server"
Set-Location -Path ".."

# 启动 Frontend
Write-Host "[*] 正在处理 Frontend..." -ForegroundColor Cyan
Set-Location -Path "frontend"
if (-Not (Test-Path "node_modules")) {
    Write-Host "[*] 初次运行，正在安装 Frontend 依赖，请稍候..." -ForegroundColor Yellow
    npm install
}
Write-Host "[*] 触发启动 Frontend (已最小化至任务栏)..." -ForegroundColor Green
Start-Process $pwshExe -WindowStyle Minimized -ArgumentList "-NoExit", "-Command", "`$host.ui.RawUI.WindowTitle = 'RedStVE - Frontend'; Write-Host '正在启动 Frontend...'; npm run dev"
Set-Location -Path ".."

# 启动 Backend
Write-Host "[*] 正在处理 Backend 服务..." -ForegroundColor Cyan
Set-Location -Path "backend"

Write-Host "[*] 触发启动 Backend (已最小化至任务栏)..." -ForegroundColor Green
Start-Process $pwshExe -WindowStyle Minimized -ArgumentList "-NoExit", "-Command", "`$host.ui.RawUI.WindowTitle = 'RedStVE - Backend (Spring Boot)'; Write-Host '正在启动 Spring Boot 后端...'; mvn spring-boot:run"
Set-Location -Path ".."

Write-Host ""
Write-Host "========================================================" -ForegroundColor Green
Write-Host "  启动指令已全部下发！所有的运行窗口已自动为您【最小化】隐藏在任务栏中。" -ForegroundColor Green
Write-Host "  如果您需要查看报错日志，可以点击任务栏的黑框图标查看。" -ForegroundColor Yellow
Write-Host "  [后端接口]: http://localhost:8080" -ForegroundColor Green
Write-Host "  [前端页面]: http://localhost:5173 (视 Vite 配置而定)" -ForegroundColor Green
Write-Host "  [Remotion]: http://localhost:3001" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host ""

Write-Host "全部执行完毕，请按 Enter 键退出当前启动器..." -ForegroundColor Yellow
Read-Host
