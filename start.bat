@echo off
setlocal
rem One click launches Python Agent, Java backend and Vue frontend together.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
exit /b %errorlevel%
