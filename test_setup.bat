@echo off
echo ========================================
echo  HomeHub Firebase Setup Test
echo ========================================
echo.

cd /d "%~dp0"

echo Checking Python...
python --version
if %errorlevel% neq 0 (
    echo ERROR: Python not found!
    echo Please install Python first.
    pause
    exit /b 1
)

echo.
echo Checking Firebase Admin SDK...
python -c "import firebase_admin; print('Firebase Admin SDK: OK')"
if %errorlevel% neq 0 (
    echo Installing Firebase Admin SDK...
    pip install firebase-admin
)

echo.
echo Checking service account key...
if exist "serviceAccountKey.json" (
    echo Service account key found.
    echo.
    echo Testing Firebase connection...
    python update_emails.py --test-connection
) else (
    echo serviceAccountKey.json not found.
    echo.
    echo Please get your Firebase service account key:
    echo Firebase Console -^> homehub-588b9 -^> Project Settings -^> Service Accounts
    echo Generate new private key -^> Download JSON -^> Save as serviceAccountKey.json
)

echo.
echo Setup check complete.
pause