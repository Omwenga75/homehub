@echo off
mkdir "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout" 2>nul
mkdir "C:\Users\USER\Desktop\HomeHub\app\src\main\res\drawable" 2>nul

echo Copying layouts...
copy /Y "C:\Users\USER\HomeView\app\src\main\res\layout\activity_admin_dashboard.xml" "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout\"
copy /Y "C:\Users\USER\HomeView\app\src\main\res\layout\activity_host_dashboard.xml" "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout\"
copy /Y "C:\Users\USER\HomeView\app\src\main\res\layout\activity_admin_login.xml" "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout\"
copy /Y "C:\Users\USER\HomeView\app\src\main\res\layout\activity_login.xml" "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout\"
copy /Y "C:\Users\USER\HomeView\app\src\main\res\layout\item_admin_property.xml" "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout\"
copy /Y "C:\Users\USER\HomeView\app\src\main\res\layout\item_host.xml" "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout\"

echo Copying drawables...
xcopy /Y /Q "C:\Users\USER\HomeView\app\src\main\res\drawable\*" "C:\Users\USER\Desktop\HomeHub\app\src\main\res\drawable\"

echo Done.
