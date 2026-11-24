@echo off
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo [DockPad] Sanal ortam olusturuluyor...
    python -m venv .venv
    call .venv\Scripts\activate.bat
    echo [DockPad] Paketler yukleniyor...
    pip install -r requirements.txt
) else (
    call .venv\Scripts\activate.bat
)

echo [DockPad] PC Agent baslatiliyor... (Port 8765 / UDP 5005)
python main.py
pause
