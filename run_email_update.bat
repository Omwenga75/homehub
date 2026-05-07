@echo off
echo ========================================
echo  HomeHub Firebase Email Update Setup
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Checking for Python...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo Python not found. Installing Python...
    winget install Python.Python.3.11 --accept-source-agreements --accept-package-agreements
    if %errorlevel% neq 0 (
        echo Failed to install Python. Please install Python manually from python.org
        pause
        exit /b 1
    )
) else (
    echo Python is already installed.
)

echo.
echo Step 2: Installing Firebase Admin SDK...
pip install firebase-admin
if %errorlevel% neq 0 (
    echo Failed to install Firebase Admin SDK
    pause
    exit /b 1
)

echo.
echo Step 3: Checking for service account key...
if not exist "serviceAccountKey.json" (
    echo.
    echo ========================================
    echo  IMPORTANT: Service Account Key Missing
    echo ========================================
    echo.
    echo You need to get your Firebase service account key:
    echo.
    echo 1. Go to: https://console.firebase.google.com/
    echo 2. Select project: homehub-588b9
    echo 3. Project Settings -^> Service Accounts
    echo 4. Generate new private key
    echo 5. Download the JSON file
    echo 6. Save it as 'serviceAccountKey.json' in this folder
    echo.
    echo Press any key after you've downloaded the key...
    pause >nul
)

if not exist "serviceAccountKey.json" (
    echo Still no serviceAccountKey.json found. Please get the key first.
    pause
    exit /b 1
)

echo.
echo Step 4: Testing Firebase connection...
python update_emails.py --test-connection
if %errorlevel% neq 0 (
    echo.
    echo Firebase connection test failed!
    echo Please check your service account key and try again.
    pause
    exit /b 1
)

echo.
echo Step 5: Running email update (dry run first)...
python update_emails.py --dry-run
if %errorlevel% neq 0 (
    echo Dry run failed. Please check the errors above.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Ready to Apply Changes
echo ========================================
echo.
set /p confirm="Do you want to apply the email changes now? (y/N): "
if /i not "%confirm%"=="y" (
    echo Operation cancelled.
    pause
    exit /b 0
)

echo.
echo Step 6: Applying email changes with backup...
python update_emails.py --backup
if %errorlevel% neq 0 (
    echo Email update failed. Please check the errors above.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  SUCCESS! Email update completed.
echo ========================================
echo.
echo Your emails have been updated from @host.com to @caretaker.com
echo A backup was created in case you need to revert changes.
echo.
pause