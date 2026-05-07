@echo off
setlocal enabledelayedexpansion

REM Navigate to the HomeHub directory
cd /d "C:\Users\USER\Desktop\HomeHub"

echo ========================================
echo  HomeHub Email Update - FINAL STEP
echo ========================================
echo.

if not exist "serviceAccountKey.json" (
    echo ERROR: serviceAccountKey.json not found!
    echo.
    echo Run RUN_ME_FIRST.bat to set everything up.
    echo.
    pause
    exit /b 1
)

echo Starting email update...
echo.
python update_emails.py --backup --old-domain @host.com --new-domain @caretaker.com

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo  SUCCESS! All emails updated!
    echo ========================================
) else (
    echo.
    echo ========================================
    echo  UPDATE FAILED - Check errors above
    echo ========================================
)

echo.
pause