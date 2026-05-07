@echo off
echo ===================================
echo   HomeHub Build Cache Cleanup
echo ===================================
echo.

echo Deleting app\build...
if exist "app\build" (
    rmdir /s /q "app\build"
    echo   DONE - app\build deleted
) else (
    echo   SKIPPED - app\build not found
)

echo Deleting build...
if exist "build" (
    rmdir /s /q "build"
    echo   DONE - build deleted
) else (
    echo   SKIPPED - build not found
)

echo Deleting Gradle transforms cache...
if exist "%USERPROFILE%\.gradle\caches\transforms-3" (
    rmdir /s /q "%USERPROFILE%\.gradle\caches\transforms-3"
    echo   DONE - transforms-3 deleted
) else (
    echo   SKIPPED - transforms-3 not found
)

echo.
echo ===================================
echo   Cleanup Complete!
echo   Now go to Android Studio:
echo   1. File - Sync Project with Gradle Files
echo   2. Build - Clean Project
echo   3. Build - Rebuild Project
echo ===================================
echo.
pause
