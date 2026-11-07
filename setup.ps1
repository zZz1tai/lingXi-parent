# First-time local dependency setup. ASCII-only for Windows PowerShell 5.1.
param([switch]$Dev)
$ErrorActionPreference = 'Stop'

try {
    foreach ($name in @('python', 'node', 'npm.cmd', 'java', 'javac')) {
        if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
            throw ('Install the required tool first: ' + $name + '. See README.md.')
        }
    }
    & python -c 'import sys; assert sys.version_info >= (3,12), "Python 3.12+ required"'
    if ($LASTEXITCODE -ne 0) { throw 'Python version check failed.' }
    & node -e 'const [a,b]=process.versions.node.split(".").map(Number); if (!((a===20&&b>=19)||(a===22&&b>=12)||a>22)) process.exit(1)'
    if ($LASTEXITCODE -ne 0) { throw 'Node 20.19+ or 22.12+ required (excluding Node 21).' }

    $agentDirectory = Join-Path $PSScriptRoot 'lingXi-agent'
    $pythonPath = Join-Path $agentDirectory '.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $pythonPath)) {
        & python -m venv (Join-Path $agentDirectory '.venv')
        if ($LASTEXITCODE -ne 0) { throw 'Virtual environment creation failed.' }
    }
    $requirements = if ($Dev) { 'requirements-dev.txt' } else { 'requirements.txt' }
    & $pythonPath -m pip install -r (Join-Path $agentDirectory $requirements)
    if ($LASTEXITCODE -ne 0) { throw 'Agent dependency installation failed.' }
    & $pythonPath -m pip check
    if ($LASTEXITCODE -ne 0) { throw 'Agent dependency integrity check failed.' }

    Push-Location (Join-Path $PSScriptRoot 'lingXi-vue')
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    }
    finally { Pop-Location }
    Write-Host 'Dependencies installed. Initialize MySQL, start Redis, configure local credentials, then run .\start.ps1 -CheckOnly and .\start.ps1.' -ForegroundColor Green
    Write-Host 'Maven must also be available (PATH or MAVEN_HOME). See README.md.'
}
catch {
    Write-Error $_
    exit 1
}
