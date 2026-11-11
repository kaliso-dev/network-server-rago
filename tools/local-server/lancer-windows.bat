@echo off
rem Serveur de test local Rago Practice (Windows). Mode d'emploi : LISEZ-MOI.txt.
rem Lance lancer.ps1 avec PowerShell, puis garde la fenetre ouverte pour lire les messages.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lancer.ps1"
echo.
pause
