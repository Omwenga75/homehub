@echo off
set "root=app\src\main\res\layout"
set "base=app\src\main\res\layouts"

echo Reverting layouts to flat structure...

:: Move all XMLs back to original folder (using recursive logic in CMD is tricky, so we'll do it by domain)
set "domains=auth student supplier property chat billing admin caretaker other"

for %%d in (%domains%) do (
    if exist "%base%\%%d\layout\*.xml" (
        echo Reverting domain: %%d
        move "%base%\%%d\layout\*.xml" "%root%\"
    )
)

:: Delete the base directory
rd /s /q "%base%"

echo Reversion complete!
pause
