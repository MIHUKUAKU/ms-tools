@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install.ps1" %*
set "result=%ERRORLEVEL%"
if not "%result%"=="0" echo Installation failed. Please read the message above.
pause
exit /b %result%
