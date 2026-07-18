@echo off
setlocal
rem One click starts Python Agent, Java backend and Vue frontend.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
exit /b %errorlevel%
