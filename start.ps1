# 灵犀系统启动脚本 (PowerShell)
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  灵犀系统启动脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 启动 lingXi-agent (Python) - 开启热更新
Write-Host "[1/2] 启动 AI Agent 服务 (热更新已开启)..." -ForegroundColor Yellow
Set-Location "D:\code\lingXi-parent\lingXi-agent"
Start-Process "cmd" "/k uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload"
Write-Host "      Agent 服务启动中... http://localhost:5000" -ForegroundColor Green

Start-Sleep -Seconds 3

# 启动前端 (Vue)
Write-Host "[2/2] 启动前端服务..." -ForegroundColor Yellow
Set-Location "D:\code\lingXi-parent\lingXi-vue"
Start-Process "cmd" "/k npm run dev"
Write-Host "      前端服务启动中..." -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  启动完成！" -ForegroundColor Green
Write-Host "  Agent:  http://localhost:5000" -ForegroundColor White
Write-Host "  前端:   http://localhost:5173 (默认)" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Read-Host "按 Enter 关闭此窗口"
