@echo off
setlocal
powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0installer\Install.ps1" -ChooseWorld -Mode Both
pause
