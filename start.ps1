# One-click launcher: Python Agent + Java backend + Vue frontend.
# Keep this file ASCII-only so Windows PowerShell 5.1 can parse it without a UTF-8 BOM.
$ErrorActionPreference = 'Stop'

$RootDir = $PSScriptRoot
$AgentDir = Join-Path $RootDir 'lingXi-agent'
$JavaDir = Join-Path $RootDir 'dkd-parent'
$VueDir = Join-Path $RootDir 'lingXi-vue'

function Resolve-MavenCommand {
    $command = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $command) {
        $command = Get-Command 'mvn' -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if ($command) {
        return $command.Source
    }

    if ($env:MAVEN_HOME) {
        $mavenHomeCommand = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
        if (Test-Path -LiteralPath $mavenHomeCommand) {
            return $mavenHomeCommand
        }
    }

    $wrapperCache = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    if (Test-Path -LiteralPath $wrapperCache) {
        $cachedCommand = Get-ChildItem -LiteralPath $wrapperCache -Filter 'mvn.cmd' -Recurse -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($cachedCommand) {
            return $cachedCommand.FullName
        }
    }

    throw 'Maven was not found. Install Maven or configure MAVEN_HOME/PATH.'
}

function Resolve-PythonCommand {
    $venvPython = Join-Path $AgentDir '.venv\Scripts\python.exe'
    if (Test-Path -LiteralPath $venvPython) {
        return $venvPython
    }

    $command = Get-Command 'python.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) {
        return $command.Source
    }

    throw 'Python was not found. Create lingXi-agent\.venv or configure Python PATH.'
}

function Resolve-WindowsTerminalCommand {
    $command = Get-Command 'wt.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) {
        return $command.Source
    }

    throw 'Windows Terminal (wt.exe) was not found.'
}

function Start-LingXiTerminalTab {
    param(
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$CommandLine
    )

    $terminalArguments = '-w LingXiServices new-tab --title "{0}" --suppressApplicationTitle --startingDirectory "{1}" cmd.exe /k {2}' -f `
        $Title, $WorkingDirectory, $CommandLine
    Start-Process -FilePath $WindowsTerminalCommand -ArgumentList $terminalArguments
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host '  LingXi one-click launcher' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ''

try {
    $PythonCommand = Resolve-PythonCommand
    $MavenCommand = Resolve-MavenCommand
    $WindowsTerminalCommand = Resolve-WindowsTerminalCommand

    Write-Host '[1/3] Starting AI Agent with reload...' -ForegroundColor Yellow
    $agentStartup = '"{0}" -m uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload' -f $PythonCommand
    Start-LingXiTerminalTab -Title 'LingXi AI Agent' -WorkingDirectory $AgentDir -CommandLine $agentStartup
    Write-Host '      Agent: http://localhost:5000' -ForegroundColor Green

    Start-Sleep -Seconds 1

    Write-Host '[2/3] Installing Java modules and starting LingXiApplication...' -ForegroundColor Yellow
    $javaStartup = 'call "{0}" -pl lingXi-admin -am -DskipTests install && call "{0}" -f lingXi-admin\pom.xml -DskipTests -Dspring-boot.run.main-class=com.lingXi.LingXiApplication spring-boot:run' -f $MavenCommand
    Start-LingXiTerminalTab -Title 'LingXi Java Backend' -WorkingDirectory $JavaDir -CommandLine $javaStartup
    Write-Host '      Java: http://localhost:8080 (starts after Maven install)' -ForegroundColor Green

    Start-Sleep -Seconds 1

    Write-Host '[3/3] Starting Vue frontend...' -ForegroundColor Yellow
    $vueStartup = 'npm run dev'
    Start-LingXiTerminalTab -Title 'LingXi Vue Frontend' -WorkingDirectory $VueDir -CommandLine $vueStartup
    Write-Host '      Vue: use the URL printed in the npm window' -ForegroundColor Green

    Write-Host ''
    Write-Host '========================================' -ForegroundColor Cyan
    Write-Host '  All three start commands were launched.' -ForegroundColor Green
    Write-Host '========================================' -ForegroundColor Cyan
}
catch {
    Write-Host ''
    Write-Host ('Launcher failed: ' + $_.Exception.Message) -ForegroundColor Red
    Read-Host 'Press Enter to close this launcher window'
    exit 1
}
