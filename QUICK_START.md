# 빠른 시작 가이드

GitHub에서 컴파일된 펌웨어를 다운로드하여 Arduino에 바로 업로드하는 방법입니다.

## 🚀 방법 1: GitHub에서 컴파일된 펌웨어 다운로드 (권장)

### 단계 1: GitHub Actions에서 펌웨어 다운로드

1. [GitHub Repository Actions 페이지](../../actions) 방문
2. 최신 성공한 빌드 클릭
3. 하단의 "Artifacts" 섹션에서 `arduino-firmware` 다운로드
4. ZIP 파일 압축 해제

**또는 Release에서 다운로드:**

1. [GitHub Releases 페이지](../../releases) 방문
2. 최신 릴리스 클릭
3. `arduino_ultrasonic_uno_r4.hex` 파일 다운로드

### 단계 2: Arduino CLI 설치

#### Windows

```powershell
winget install Arduino.ArduinoCLI
```

또는 [여기서 수동 설치](https://arduino.github.io/arduino-cli/latest/installation/)

#### macOS

```bash
brew install arduino-cli
```

#### Linux

```bash
curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh
```

### 단계 3: 펌웨어 업로드

#### Windows

1. `arduino_ultrasonic/tools/upload_firmware.bat` 실행
2. Arduino가 연결된 COM 포트 입력 (예: COM3)
3. 펌웨어 파일 경로 입력 (또는 같은 폴더에 .hex 파일 배치)

또는 명령 프롬프트에서:

```cmd
cd arduino_ultrasonic\tools
upload_firmware.bat COM3 "경로\arduino_ultrasonic_uno_r4.hex"
```

#### Linux/macOS

```bash
cd arduino_ultrasonic/tools
./upload_firmware.sh /dev/ttyACM0 "경로/arduino_ultrasonic_uno_r4.hex"
```

또는 자동 포트 감지:

```bash
./upload_firmware.sh
```

### 단계 4: 시리얼 모니터 열기

```bash
arduino-cli monitor -p [포트] -c baudrate=115200
```

예:
- Windows: `arduino-cli monitor -p COM3 -c baudrate=115200`
- Linux/macOS: `arduino-cli monitor -p /dev/ttyACM0 -c baudrate=115200`

---

## 🛠️ 방법 2: Arduino IDE 사용 (전통적인 방법)

### 단계 1: Arduino IDE 설치

1. [Arduino IDE 2.0](https://www.arduino.cc/en/software) 다운로드 및 설치

### 단계 2: Arduino Uno R4 보드 지원 추가

1. Arduino IDE 열기
2. `파일` → `환경설정` → `추가적인 보드 매니저 URLs`
3. 보드 매니저에서 "Arduino UNO R4" 검색 및 설치

### 단계 3: 코드 열기

1. 이 저장소 복제:
   ```bash
   git clone https://github.com/pjy821821/nano9.git
   cd nano9/arduino_ultrasonic
   ```

2. Arduino IDE에서 `arduino_ultrasonic.ino` 파일 열기

### 단계 4: 보드 및 포트 선택

1. `도구` → `보드` → `Arduino UNO R4 WiFi` (또는 Minima)
2. `도구` → `포트` → Arduino가 연결된 포트 선택

### 단계 5: 업로드

1. 업로드 버튼 (→) 클릭
2. 완료되면 시리얼 모니터 열기 (Ctrl+Shift+M)
3. 보드레이트를 115200으로 설정

---

## 📦 방법 3: 수동 업로드 (avrdude 사용)

고급 사용자를 위한 방법입니다.

### Arduino Uno R4용 avrdude 사용

```bash
# Arduino Uno R4 Minima
avrdude -C avrdude.conf -v -p atmega4809 -c arduino -P [포트] -b 115200 \
  -D -U flash:w:arduino_ultrasonic_uno_r4.hex:i
```

**참고**: Arduino Uno R4는 RA4M1 프로세서를 사용하므로 일반 avrdude로는 업로드가 어려울 수 있습니다. Arduino CLI 사용을 권장합니다.

---

## 🔍 포트 찾기

### Windows

장치 관리자에서 "포트 (COM 및 LPT)" 확인

또는 PowerShell:
```powershell
Get-WMIObject Win32_SerialPort | Select-Object Name,DeviceID
```

### Linux

```bash
ls /dev/ttyACM* /dev/ttyUSB*
```

또는:
```bash
arduino-cli board list
```

### macOS

```bash
ls /dev/cu.usbmodem*
```

---

## ⚡ 자동 빌드 트리거

코드를 수정한 후 GitHub에 푸시하면 자동으로 빌드됩니다:

```bash
git add .
git commit -m "Update ultrasonic sensor code"
git push
```

1. GitHub Actions가 자동으로 코드 컴파일
2. [Actions 페이지](../../actions)에서 빌드 진행상황 확인
3. 완료되면 Artifacts에서 펌웨어 다운로드

---

## 🎯 빠른 명령어 요약

### 전체 프로세스 (Linux/macOS)

```bash
# 1. Arduino CLI 설치
brew install arduino-cli  # macOS
# 또는
curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh  # Linux

# 2. GitHub에서 펌웨어 다운로드 (Actions 또는 Releases)

# 3. 업로드
cd arduino_ultrasonic/tools
./upload_firmware.sh /dev/ttyACM0 ~/Downloads/arduino_ultrasonic_uno_r4.hex

# 4. 시리얼 모니터 열기
arduino-cli monitor -p /dev/ttyACM0 -c baudrate=115200
```

### 전체 프로세스 (Windows)

```cmd
# 1. Arduino CLI 설치
winget install Arduino.ArduinoCLI

# 2. GitHub에서 펌웨어 다운로드 (Actions 또는 Releases)

# 3. 업로드
cd arduino_ultrasonic\tools
upload_firmware.bat COM3 "%USERPROFILE%\Downloads\arduino_ultrasonic_uno_r4.hex"

# 4. 시리얼 모니터 열기
arduino-cli monitor -p COM3 -c baudrate=115200
```

---

## 🐛 문제 해결

### "arduino-cli를 찾을 수 없습니다"

Arduino CLI가 PATH에 없습니다. 재설치하거나 전체 경로를 사용하세요.

### "포트에 접근할 수 없습니다"

1. Arduino IDE나 다른 시리얼 모니터 프로그램을 닫으세요
2. Arduino를 재연결하세요
3. Linux: 사용자를 dialout 그룹에 추가 (`sudo usermod -a -G dialout $USER`)

### "업로드 실패"

1. 올바른 보드가 선택되었는지 확인 (Arduino Uno R4)
2. 올바른 포트가 선택되었는지 확인
3. USB 케이블이 데이터 전송을 지원하는지 확인 (충전 전용 케이블 X)
4. 부트로더 버튼을 두 번 빠르게 눌러 부트로더 모드로 진입

### 빌드가 실패하는 경우

1. [GitHub Actions 로그](../../actions) 확인
2. 코드 문법 오류 확인
3. Issue 생성하여 도움 요청

---

## 📚 추가 리소스

- [Arduino CLI 문서](https://arduino.github.io/arduino-cli/)
- [Arduino Uno R4 문서](https://docs.arduino.cc/hardware/uno-r4-wifi)
- [GitHub Actions 문서](https://docs.github.com/en/actions)

---

## 💡 팁

1. **GitHub Actions Artifacts는 30일 후 삭제됩니다.** Release를 만들어서 영구 보관하세요.

2. **Release 만들기:**
   ```bash
   git tag -a v1.0.0 -m "First stable release"
   git push origin v1.0.0
   ```
   GitHub에서 Release를 생성하면 자동으로 펌웨어가 첨부됩니다.

3. **로컬에서 직접 빌드:**
   ```bash
   arduino-cli compile --fqbn arduino:renesas_uno:minima \
     --output-dir build \
     --export-binaries \
     arduino_ultrasonic/arduino_ultrasonic.ino
   ```

---

**모든 준비 완료!** 이제 GitHub에서 컴파일된 펌웨어를 다운로드하여 Arduino에 바로 업로드할 수 있습니다. 🎉
