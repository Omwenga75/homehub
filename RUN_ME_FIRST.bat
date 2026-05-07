@echo off
setlocal enabledelayedexpansion

REM Navigate to the HomeHub directory
cd /d "C:\Users\USER\Desktop\HomeHub"

echo.
echo ========================================
echo  HomeHub Firebase Email Update Setup
echo ========================================
echo.

REM Check if serviceAccountKey.json exists
if exist "serviceAccountKey.json" (
    echo ✓ Service account key found!
    echo Proceeding to email update...
    echo.
    goto RUN_UPDATE
)

REM If key doesn't exist, get it first
echo ✗ Service account key NOT found!
echo.
echo Step 1: Getting your Firebase service key...
echo Opening Firebase Console in your browser...
echo.

start "" "https://console.firebase.google.com/project/homehub-588b9/settings/serviceaccounts/adminsdk"

echo.
echo FOLLOW THESE STEPS:
echo 1. Browser opens to Firebase Console
echo 2. Click "Generate new private key" button
echo 3. Click "Generate key" to confirm
echo 4. JSON file downloads to your Downloads folder
echo 5. Move the downloaded file to THIS folder
echo 6. Rename it to: serviceAccountKey.json
echo.
echo After you've saved the file, press any key...
pause >nul

REM Check again if file exists
if not exist "serviceAccountKey.json" (
    echo.
    echo ✗ ERROR: serviceAccountKey.json still not found!
    echo Please save the file and try again.
    echo.
    pause
    exit /b 1
)

echo.
echo ✓ serviceAccountKey.json found!
echo.

:RUN_UPDATE
echo Step 2: Running email update...
echo.
echo Updating emails from @host.com to @caretaker.com
echo Creating backup automatically...
echo.

python update_emails.py --backup --old-domain @host.com --new-domain @caretaker.com

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✓ SUCCESS! All emails updated!
    echo ========================================
    echo.
    echo Your emails have been updated.
    echo A backup was created automatically.
    echo.
) else (
    echo.
    echo ========================================
    echo ✗ UPDATE FAILED
    echo ========================================
    echo.
    echo Check the errors above and try again.
    echo.
)

echo.
pause
exit /b %errorlevel%