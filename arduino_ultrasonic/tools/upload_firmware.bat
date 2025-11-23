@echo off
REM Arduino Uno R4 펌웨어 업로드 스크립트 (Windows)
REM 사용법: upload_firmware.bat [포트] [펌웨어파일]

echo ========================================
echo Arduino Uno R4 Firmware Upload Tool
echo ========================================
echo.

REM Arduino CLI 설치 확인
where arduino-cli >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: arduino-cli가 설치되어 있지 않습니다.
    echo.
    echo 설치 방법:
    echo   1. https://arduino.github.io/arduino-cli/latest/installation/ 방문
    echo   2. Windows installer 다운로드 및 설치
    echo   3. 또는 winget install Arduino.ArduinoCLI
    echo.
    pause
    exit /b 1
)

REM 포트 자동 감지 또는 사용자 입력
if "%~1"=="" (
    echo 사용 가능한 포트 검색 중...
    arduino-cli board list
    echo.
    set /p PORT="Arduino가 연결된 포트를 입력하세요 (예: COM3): "
) else (
    set PORT=%~1
)

REM 펌웨어 파일 확인
if "%~2"=="" (
    if exist "arduino_ultrasonic_uno_r4.hex" (
        set FIRMWARE=arduino_ultrasonic_uno_r4.hex
    ) else if exist "..\build\arduino_ultrasonic_uno_r4.hex" (
        set FIRMWARE=..\build\arduino_ultrasonic_uno_r4.hex
    ) else (
        set /p FIRMWARE="펌웨어 파일 경로를 입력하세요: "
    )
) else (
    set FIRMWARE=%~2
)

REM 파일 존재 확인
if not exist "%FIRMWARE%" (
    echo ERROR: 펌웨어 파일을 찾을 수 없습니다: %FIRMWARE%
    pause
    exit /b 1
)

echo.
echo 펌웨어 업로드 시작...
echo   포트: %PORT%
echo   파일: %FIRMWARE%
echo.

REM Arduino Uno R4 코어 설치 확인
arduino-cli core list | findstr "arduino:renesas_uno" >nul
if %ERRORLEVEL% NEQ 0 (
    echo Arduino Uno R4 코어 설치 중...
    arduino-cli core update-index
    arduino-cli core install arduino:renesas_uno
)

REM 펌웨어 업로드
echo 업로드 중...
arduino-cli upload -p %PORT% --fqbn arduino:renesas_uno:minima --input-file %FIRMWARE%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✓ 업로드 완료!
    echo.
    echo 시리얼 모니터를 열려면 다음 명령을 실행하세요:
    echo   arduino-cli monitor -p %PORT% -c baudrate=115200
    echo.
) else (
    echo.
    echo ✗ 업로드 실패
    echo.
    echo 문제 해결:
    echo   1. Arduino가 올바른 포트에 연결되어 있는지 확인
    echo   2. 다른 프로그램(Arduino IDE, 시리얼 모니터 등)이 포트를 사용 중인지 확인
    echo   3. Arduino를 재부팅하고 다시 시도
    echo.
)

pause
