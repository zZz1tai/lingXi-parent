param([switch]$SkipBackendBuild, [string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$repoRoot = Split-Path $PSScriptRoot -Parent
$cache = Join-Path $PSScriptRoot '.cache'
$runtime = Join-Path $repoRoot 'lingXi-vue/src-tauri/runtime'
$lock = Get-Content (Join-Path $PSScriptRoot 'runtime-lock.json') -Raw | ConvertFrom-Json

function Assert-Exit([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step 失败（退出码 $LASTEXITCODE）" }
}

function Expand-Runtime([string]$Name, $Spec) {
    $archive = Join-Path $cache "$Name.zip"
    if (!(Test-Path -LiteralPath $archive)) {
        Write-Host "下载 $Name 运行环境…"
        Invoke-WebRequest -Uri $Spec.url -OutFile "$archive.partial" -UseBasicParsing
        Move-Item -LiteralPath "$archive.partial" -Destination $archive
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $Spec.sha256) {
        throw "$Name 下载校验失败，请移除 $archive 后重试。"
    }
    $destination = Join-Path $runtime $Name
    if (Test-Path -LiteralPath $destination) { throw "运行环境目录已存在：$destination" }
    New-Item -ItemType Directory -Path $destination | Out-Null
    $staging = Join-Path $cache "$Name-extract"
    if (Test-Path -LiteralPath $staging) { throw "解压目录已存在：$staging，请先检查上次打包现场。" }
    Expand-Archive -LiteralPath $archive -DestinationPath $staging
    $source = $staging
    if ($Spec.folder) { $source = Join-Path $staging $Spec.folder }
    Copy-Item -Path (Join-Path $source '*') -Destination $destination -Recurse
    # 仅移除本脚本明确创建、位于缓存中的临时解压目录。
    $resolvedStaging = (Resolve-Path -LiteralPath $staging).Path
    $resolvedCache = (Resolve-Path -LiteralPath $cache).Path
    if (!$resolvedStaging.StartsWith($resolvedCache + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw '解压目录越界，停止清理。'
    }
    Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
}

Push-Location $repoRoot
try {
    & $Python -c "import sys; assert sys.version_info[:2] == (3, 12), '打包需要 Python 3.12 x64'; assert sys.maxsize > 2**32, '打包需要 x64 Python'"
    Assert-Exit '打包机 Python 版本检查'
    New-Item -ItemType Directory -Force -Path $cache | Out-Null
    # 只能重建专用的、未跟踪的安装包资源目录，绝不访问用户数据目录。
    if (Test-Path -LiteralPath $runtime) {
        $resolvedRuntime = (Resolve-Path -LiteralPath $runtime).Path
        $expectedRuntime = [IO.Path]::GetFullPath((Join-Path $repoRoot 'lingXi-vue/src-tauri/runtime'))
        if ($resolvedRuntime -ne $expectedRuntime) { throw '运行环境目录越界，停止打包。' }
        Remove-Item -LiteralPath $resolvedRuntime -Recurse -Force
    }
    New-Item -ItemType Directory -Path $runtime | Out-Null
    foreach ($name in @('python', 'java', 'mysql', 'redis')) {
        Expand-Runtime $name $lock.$name
    }
    # 不分发数据库的调试符号和开发链接库，减少安装包体积。
    Get-ChildItem -LiteralPath (Join-Path $runtime 'mysql') -Recurse -File | Where-Object { $_.Extension -in @('.pdb', '.lib') } | ForEach-Object {
        if (!$_.FullName.StartsWith([IO.Path]::GetFullPath($runtime) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw '清理文件越界' }
        Remove-Item -LiteralPath $_.FullName -Force
    }
    # MySQL 依赖 VC Runtime；从构建机的微软可再分发运行库复制为应用私有 DLL。
    # GitHub windows-2022 构建机预装该运行库，用户机器不需要安装构建工具。
    foreach ($dll in @('vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140.dll', 'msvcp140_1.dll', 'msvcp140_2.dll', 'concrt140.dll')) {
        $source = Join-Path $env:WINDIR "System32/$dll"
        if (!(Test-Path -LiteralPath $source)) { throw "打包机缺少微软可再分发运行库 $dll" }
        Copy-Item -LiteralPath $source -Destination (Join-Path $runtime 'mysql/bin')
    }
    if (!$SkipBackendBuild) {
        Push-Location (Join-Path $repoRoot 'dkd-parent')
        try { & mvn -B -pl lingXi-admin -am package -DskipTests; Assert-Exit 'Java 打包' }
        finally { Pop-Location }
    }
    New-Item -ItemType Directory -Path (Join-Path $runtime 'backend') | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot 'dkd-parent/lingXi-admin/target/lingXi-admin.jar') -Destination (Join-Path $runtime 'backend')
    New-Item -ItemType Directory -Path (Join-Path $runtime 'agent') | Out-Null
    # 只复制代码；不复制开发机虚拟环境、.env、知识库内容和缓存。
    Copy-Item -LiteralPath (Join-Path $repoRoot 'lingXi-agent/app') -Destination (Join-Path $runtime 'agent') -Recurse
    Get-ChildItem -LiteralPath (Join-Path $runtime 'agent') -Filter '*.pyc' -Recurse -File | Remove-Item -Force
    $sitePackages = Join-Path $runtime 'python/Lib/site-packages'
    & $Python -m pip install --only-binary=:all: --target $sitePackages -r (Join-Path $repoRoot 'lingXi-agent/requirements.txt')
    Assert-Exit 'Python 运行依赖打包（打包机必须为 Python 3.12 x64）'
    @('python312.zip', '.', 'Lib/site-packages', '../agent', 'import site') | Set-Content -LiteralPath (Join-Path $runtime 'python/python312._pth') -Encoding ASCII
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'launcher.py') -Destination $runtime
    Copy-Item -LiteralPath (Join-Path $repoRoot 'dkd-parent/sql/lingxi_all.sql') -Destination (Join-Path $runtime 'schema.sql')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'runtime-lock.json') -Destination $runtime
    & (Join-Path $runtime 'python/python.exe') -c "import fastapi, uvicorn, langchain, langgraph, psycopg; from app.main import app; print('桌面 AI 运行环境检查通过')"
    Assert-Exit '嵌入式 Python 导入检查'
    Write-Host '完整桌面资源已准备完成。'
} finally {
    Pop-Location
}
