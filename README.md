# Arduino Uno R4 초음파 거리 측정 시스템

[![Arduino Build](https://github.com/pjy821821/nano9/actions/workflows/arduino-build.yml/badge.svg)](https://github.com/pjy821821/nano9/actions/workflows/arduino-build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

온도와 습도 보정 기능을 갖춘 정밀 초음파 거리 측정 시스템입니다.

**PCB Code: 250903JA0**

---

## 🚀 빠른 시작

### 방법 1: GitHub에서 컴파일된 펌웨어 다운로드 (가장 쉬움!)

1. **[Releases 페이지](../../releases)**에서 최신 버전의 `arduino_ultrasonic_uno_r4.hex` 다운로드
2. **Arduino CLI 설치**:
   - Windows: `winget install Arduino.ArduinoCLI`
   - macOS: `brew install arduino-cli`
   - Linux: `curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh`

3. **업로드 스크립트 실행**:
   - Windows: `arduino_ultrasonic\tools\upload_firmware.bat`
   - Linux/macOS: `./arduino_ultrasonic/tools/upload_firmware.sh`

**더 자세한 내용은 [QUICK_START.md](QUICK_START.md)를 참고하세요!**

### 방법 2: Arduino IDE 사용 (전통적인 방법)

자세한 설치 방법은 [arduino_ultrasonic/README.md](arduino_ultrasonic/README.md)를 참고하세요.

---

## 📦 프로젝트 구조

```
nano9/
├── .github/
│   └── workflows/
│       └── arduino-build.yml        # GitHub Actions 자동 빌드
├── arduino_ultrasonic/
│   ├── arduino_ultrasonic.ino       # 메인 스케치
│   ├── TUSS4470.h/.cpp              # TUSS4470 드라이버
│   ├── HDC1080.h/.cpp               # HDC1080 온습도 센서
│   ├── tools/
│   │   ├── upload_firmware.sh       # Linux/macOS 업로드 스크립트
│   │   └── upload_firmware.bat      # Windows 업로드 스크립트
│   └── README.md                    # 상세 하드웨어 문서
├── QUICK_START.md                   # 빠른 시작 가이드
└── README.md                        # 이 파일
```

---

## 🔧 하드웨어 구성

- **Arduino Uno R4** (WiFi 또는 Minima)
- **TUSS4470** - 초음파 센서 드라이버 IC (Transformer Drive)
- **HDC1080** - I2C 온습도 센서
- **V40 AW14B** - 40kHz 초음파 트랜스듀서
- **커스텀 실드** - PCB 250903JA0

### 주요 기능

✅ **실시간 온도/습도 보정** - 환경 조건에 따른 음속 자동 계산
✅ **Transformer Drive** - TUSS4470 전용 차동 신호 구동
✅ **정밀 TOF 측정** - 125ns 분해능 (8MHz 클럭)
✅ **대화형 디버깅** - 시리얼 명령어로 레지스터 및 상태 확인
✅ **자동 빌드** - GitHub Actions로 자동 컴파일 및 배포

---

## 📊 성능 사양

| 항목 | 사양 |
|------|------|
| 측정 범위 | 2cm ~ 400cm |
| 정확도 | ±1cm (보정 적용 시) |
| 분해능 | 125ns (약 0.02mm) |
| 측정 주기 | 500ms (설정 가능) |
| 온도 보정 범위 | -40°C ~ +125°C |
| 습도 보정 범위 | 0% ~ 100% RH |

---

## 🛠️ 시리얼 명령어

시리얼 모니터(115200 baud)에서 다음 명령을 사용할 수 있습니다:

| 명령어 | 기능 |
|--------|------|
| `i` | 시스템 정보 및 핀 구성 출력 |
| `s` | 단일 측정 (상세 정보 포함) |
| `c` | 보정 공식 및 설정 정보 |
| `r` | TUSS4470 레지스터 덤프 (디버깅) |
| `v` | VOUT 아날로그 전압 출력 |

---

## 🔄 GitHub Actions 자동 빌드

코드를 푸시할 때마다 자동으로 Arduino 펌웨어가 컴파일됩니다:

1. **코드 수정 후 푸시**:
   ```bash
   git add .
   git commit -m "Update sensor settings"
   git push
   ```

2. **[Actions 페이지](../../actions)**에서 빌드 진행 상황 확인

3. **빌드 완료 후 Artifacts에서 펌웨어 다운로드**

### Release 생성 시 자동 배포

태그를 만들어 Release를 생성하면 자동으로 펌웨어가 첨부됩니다:

```bash
git tag -a v1.0.0 -m "First stable release"
git push origin v1.0.0
```

GitHub에서 해당 태그로 Release를 생성하면 자동으로 컴파일된 펌웨어가 업로드됩니다.

---

## 📖 문서

- **[QUICK_START.md](QUICK_START.md)** - 빠른 시작 가이드 (펌웨어 다운로드 및 업로드)
- **[arduino_ultrasonic/README.md](arduino_ultrasonic/README.md)** - 상세 하드웨어 및 소프트웨어 문서
- **[회로도](docs/schematic_250903JA0.png)** - PCB 250903JA0 회로도 (제공된 경우)

---

## 🔌 핀 연결 (PCB 250903JA0)

### HDC1080 (I2C)
- SDA → A4 (D18)
- SCL → A5 (D19)

### TUSS4470 (SPI)
- NCS → D10 (SS)
- SCLK → D13 (SCK)
- SDI → D11 (MOSI)
- SDO → D12 (MISO)
- IO1 → A1
- IO2 → A2
- VOUT → A0

---

## 🧮 음속 보정 공식

```
음속(m/s) = 331.3 + 0.606 × 온도(°C) + 0.0124 × 습도(%)
거리(cm) = (음속 × TOF) / 2
```

---

## 🐛 문제 해결

### Arduino CLI 설치 문제

**Windows**:
```powershell
winget install Arduino.ArduinoCLI
```

**macOS**:
```bash
brew install arduino-cli
```

**Linux**:
```bash
curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh
```

### 업로드 실패

1. 올바른 포트 확인: `arduino-cli board list`
2. 다른 프로그램이 포트 사용 중인지 확인 (Arduino IDE, 시리얼 모니터 등)
3. Arduino 재부팅 후 재시도

### 측정 타임아웃

1. 트랜스듀서 연결 확인
2. `v` 명령으로 VOUT 전압 확인
3. `r` 명령으로 레지스터 상태 확인

더 자세한 문제 해결은 [arduino_ultrasonic/README.md](arduino_ultrasonic/README.md#문제-해결)를 참고하세요.

---

## 📚 참고 자료

- [TUSS4470 데이터시트](https://www.ti.com/lit/ds/symlink/tuss4470.pdf)
- [HDC1080 데이터시트](https://www.ti.com/lit/ds/symlink/hdc1080.pdf)
- [Arduino Uno R4 문서](https://docs.arduino.cc/hardware/uno-r4-wifi)
- [Arduino CLI 문서](https://arduino.github.io/arduino-cli/)

---

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 교육 및 연구 목적으로 자유롭게 사용할 수 있습니다.

---

## 👤 제작자

- **Claude AI Assistant**
- **PCB Code**: 250903JA0
- **버전**: 2.0 (TUSS4470 Transformer Drive 전용)
- **날짜**: 2025-11-23

---

## 🌟 기여하기

이슈 제출 및 Pull Request는 언제나 환영합니다!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

**Happy Coding! 🎉**
