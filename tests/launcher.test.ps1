# Offline launcher regression checks; never starts application services.
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $repository 'start.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
[void][System.Management.Automation.Language.Parser]::ParseFile((Join-Path $repository 'setup.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
foreach ($name in @('ConvertFrom-DotEnvValue', 'Get-DotEnvValue', 'Import-JavaDatabaseCredentials', 'Start-LingXiTerminalTab')) {
    $definition = $ast.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name }, $true)
    if (-not $definition) { throw ('Missing function: ' + $name) }
    Invoke-Expression $definition.Extent.Text
}

$scratch = Join-Path $repository ('tmp\launcher test-' + [Guid]::NewGuid().ToString('N'))
[void][System.IO.Directory]::CreateDirectory($scratch)
$originalUsername = $env:DB_USERNAME
$originalUrl = $env:SPRING_DATASOURCE_DRUID_MASTER_URL
try {
    $envFile = Join-Path $scratch 'test.env'
    [System.IO.File]::WriteAllText($envFile, "DB_USERNAME=file-user`nSPRING_DATASOURCE_DRUID_MASTER_URL='jdbc:mysql://127.0.0.1:3307/dkd'`n")
    $env:DB_USERNAME = 'process-user'
    $env:SPRING_DATASOURCE_DRUID_MASTER_URL = $null
    Import-JavaDatabaseCredentials -EnvFile $envFile
    if ($env:DB_USERNAME -ne 'process-user') { throw 'Process environment must win.' }
    if ($env:SPRING_DATASOURCE_DRUID_MASTER_URL -ne 'jdbc:mysql://127.0.0.1:3307/dkd') { throw 'Custom database URL was not imported.' }

    $RootDir = $scratch
    $WindowsTerminalCommand = $null
    function Start-Process {
        param($FilePath, $ArgumentList, $WorkingDirectory, $WindowStyle, [switch]$PassThru)
        if ($WindowStyle -ne 'Hidden') { throw 'Background process must be hidden.' }
        $script:capturedCommand = $ArgumentList
        [pscustomobject]@{ Id = 123 }
    }
    $echoCommand = '"{0}" /d /c echo launcher-ok' -f $env:ComSpec
    Start-LingXiTerminalTab -Title 'Test Service' -WorkingDirectory $scratch -CommandLine $echoCommand
    # Execute only the harmless echo command to verify cmd redirection/quoting.
    $echoProcess = Microsoft.PowerShell.Management\Start-Process -FilePath 'cmd.exe' -ArgumentList $script:capturedCommand -WorkingDirectory $scratch -WindowStyle Hidden -Wait -PassThru
    if ($echoProcess.ExitCode -ne 0) { throw 'Fallback command failed.' }
    $log = Join-Path $scratch 'output\Test-Service.log'
    if (-not (Test-Path -LiteralPath $log)) { throw 'Fallback log was not created.' }
    if ((Get-Content -LiteralPath $log -Raw).Trim() -ne 'launcher-ok') { throw 'Fallback command/log quoting failed.' }
    Write-Host 'Launcher regression checks passed.'
}
finally {
    $env:DB_USERNAME = $originalUsername
    $env:SPRING_DATASOURCE_DRUID_MASTER_URL = $originalUrl
    # Keep temporary evidence under ignored tmp/; no recursive filesystem deletion.
}
