@echo off
setlocal enabledelayedexpansion

REM Navigate to the HomeHub directory
cd /d "C:\Users\USER\Desktop\HomeHub"

echo ========================================
echo  Firebase Service Account Key Setup
echo ========================================
echo.
echo You have Firebase set up for your Android app,
echo but you need a SERVICE ACCOUNT KEY for Python scripts.
echo.
echo This key is different from google-services.json
echo.

echo Opening Firebase Console...
start "" "https://console.firebase.google.com/project/homehub-588b9/settings/serviceaccounts/adminsdk"

echo.
echo INSTRUCTIONS:
echo 1. Firebase Console should open in your browser
echo 2. Click "Generate new private key"
echo 3. Download the JSON file
echo 4. Save it as "serviceAccountKey.json" in this folder:
echo    %cd%
echo 5. Return here and run: final_update.bat
echo.
pause