$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$installer = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'lingXi-vue/src-tauri/target/release/bundle/nsis') -Filter '*-setup.exe' | Select-Object -First 1
if (!$installer) { throw '没有找到完整安装包。' }
# 仅供 GitHub 临时 Windows runner 验证，拒绝在日常电脑上静默安装。
if ($env:GITHUB_ACTIONS -ne 'true') { throw '安装验收只能在 GitHub Actions 临时机器中执行。' }
$destination = Join-Path $env:RUNNER_TEMP 'LingXiInstalled'
$installed = Start-Process -FilePath $installer.FullName -ArgumentList @('/S', "/D=$destination") -WindowStyle Hidden -Wait -PassThru
if ($installed.ExitCode -ne 0) { throw "安装包安装失败：$($installed.ExitCode)" }
$executable = Join-Path $destination 'lingxi-ai-platform.exe'
if (!(Test-Path -LiteralPath $executable)) { throw '安装完成后没有找到桌面主程序。' }
$data = Join-Path $env:LOCALAPPDATA 'com.lingxi.terminal/server'
$process = $null
# 隐藏窗口没有 MainWindowHandle；直接向本次进程的顶层窗口发送正常关闭消息。
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class LingXiDesktopClose {
    private delegate bool WindowCallback(IntPtr window, IntPtr parameter);
    [DllImport("user32.dll")] private static extern bool EnumWindows(WindowCallback callback, IntPtr parameter);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowTextLength(IntPtr window);
    [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr window, uint message, IntPtr wparam, IntPtr lparam);
    public static bool Close(int processId) {
        bool sent = false;
        EnumWindows((window, parameter) => {
            uint owner;
            GetWindowThreadProcessId(window, out owner);
            if (owner == processId && GetWindowTextLength(window) > 0) {
                sent = PostMessage(window, 0x0010, IntPtr.Zero, IntPtr.Zero) || sent;
            }
            return true;
        }, IntPtr.Zero);
        return sent;
    }
}
'@
try {
    foreach ($attempt in @(1, 2)) {
        $started = Get-Date
        $process = Start-Process -FilePath $executable -PassThru -WindowStyle Hidden
        $deadline = (Get-Date).AddMinutes(8)
        $ready = $false
        while ((Get-Date) -lt $deadline) {
            $process.Refresh()
            if ($process.HasExited) { throw "桌面程序意外退出：$($process.ExitCode)" }
            $statusFile = Join-Path $data 'status.json'
            if ((Test-Path -LiteralPath $statusFile) -and (Get-Item -LiteralPath $statusFile).LastWriteTime -ge $started) {
                $status = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
                if ($status.phase -eq 'error') { throw $status.message }
                if ($status.phase -eq 'ready') { $ready = $true; break }
            }
            Start-Sleep -Seconds 1
        }
        if (!$ready) { throw '安装后的桌面程序启动超时。' }
        $health = Invoke-RestMethod 'http://127.0.0.1:18080/captchaImage'
        if ($health.code -ne 200) { throw '安装后的业务服务不可用。' }
        # 使用窗口关闭路径，验证 Rust 退出回调会正常关闭私有 MySQL。
        if (![LingXiDesktopClose]::Close($process.Id)) { throw '无法关闭桌面窗口。' }
        if (!$process.WaitForExit(90000)) { throw '桌面程序关闭超时。' }
        $remaining = Get-NetTCPConnection -LocalPort 13306,16379,15000,18080 -State Listen -ErrorAction SilentlyContinue
        if ($remaining) { throw '关闭桌面窗口后仍存在私有服务监听。' }
    }
    Write-Host '安装包安装、原生桌面首次启动、再次启动和窗口关闭清理验收通过。'
} finally {
    if ($process -and !$process.HasExited) { $null = [LingXiDesktopClose]::Close($process.Id); $null = $process.WaitForExit(90000) }
}
