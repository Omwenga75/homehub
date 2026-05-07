@echo off
echo ========================================
echo  HomeHub Email Update - Quick Run
echo ========================================
echo.

cd /d "%~dp0"

echo Checking for service account key...
if not exist "serviceAccountKey.json" (
    echo ERROR: serviceAccountKey.json not found!
    echo.
    echo Please get your Firebase service account key first:
    echo 1. Firebase Console -^> homehub-588b9 -^> Project Settings -^> Service Accounts
    echo 2. Generate new private key -^> Download JSON -^> Save as serviceAccountKey.json
    echo.
    pause
    exit /b 1
)

echo Testing Firebase connection...
python update_emails.py --test-connection
if %errorlevel% neq 0 (
    echo Connection test failed. Please check your setup.
    pause
    exit /b 1
)

echo.
echo Running email update with backup...
python update_emails.py --backup
if %errorlevel% neq 0 (
    echo Email update failed.
    pause
    exit /b 1
)

echo.
echo SUCCESS! Email update completed.
pause