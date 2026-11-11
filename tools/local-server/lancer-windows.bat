@echo off
rem Local test server of Rago Practice (Windows). How to use it: LISEZ-MOI.txt.
rem Runs lancer.ps1 with PowerShell, then keeps the window open to read the messages.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lancer.ps1"
echo.
pause
