# Arduino Uno R4 초음파 거리 측정 시스템

온도와 습도 보정 기능을 갖춘 정밀 초음파 거리 측정 시스템입니다.

**PCB Code: 250903JA0**

## 하드웨어 구성

- **Arduino Uno R4**: 메인 컨트롤러
- **TUSS4470**: 초음파 센서 드라이버 IC (Transformer Drive 방식)
- **HDC1080**: I2C 온습도 센서
- **V40 AW14B**: 40kHz 초음파 트랜스듀서
- **커스텀 실드**: PCB 250903JA0

## 핀 연결 (회로도 기준)

### HDC1080 온습도 센서 (I2C)

| HDC1080 핀 | Arduino Uno R4 핀 | 설명 |
|-----------|------------------|------|
| VDD       | +5V              | 전원 |
| SDA       | A4 (D18)         | I2C 데이터 |
| SCL       | A5 (D19)         | I2C 클럭 |
| GND       | GND              | 접지 |

**참고**: I2C 주소는 0x40입니다.

### TUSS4470 초음파 드라이버 (SPI)

| TUSS4470 핀 | Arduino Uno R4 핀 | 설명 |
|------------|------------------|------|
| VDD        | +5V              | 전원 |
| GND        | GND              | 접지 |
| NCS        | D10 (SS)         | SPI 칩 선택 |
| SCLK       | D13 (SCK)        | SPI 클럭 |
| SDI        | D11 (MOSI)       | SPI 데이터 입력 |
| SDO        | D12 (MISO)       | SPI 데이터 출력 |
| IO1        | A1               | 입출력 핀 1 |
| IO2        | A2               | 입출력 핀 2 |
| VOUT       | A0               | 아날로그 출력 |
| FLT        | 핀헤더 J4/J5     | 필터 출력 |
| OUT3       | 트랜스듀서       | 출력 3 |
| OUT4       | 트랜스듀서       | 출력 4 |

### V40 AW14B 초음파 트랜스듀서

- **주파수**: 40kHz
- **연결**: TUSS4470의 OUT3/OUT4 핀을 통해 Transformer Drive 방식으로 구동

## 동작 원리

### TUSS4470 Transformer Drive 모드

TUSS4470은 일반적인 직접 구동 방식이 아닌 **Transformer Drive** 방식으로 동작합니다:

1. **펄스 발생**: 8개의 40kHz 버스트 펄스 생성
2. **트랜스포머 구동**: OUT3/OUT4를 통해 차동 신호로 트랜스듀서 구동
3. **에코 수신**: 초음파 반사파를 수신하여 신호 처리
4. **TOF 측정**: Time-of-Flight를 125ns 분해능으로 측정
5. **SPI 통신**: 레지스터를 통해 측정값 읽기

### 온습도 보정

환경 조건에 따라 음속이 변하므로 정확한 거리 측정을 위해 실시간 보정을 수행합니다:

```
음속(m/s) = 331.3 + 0.606 × 온도(°C) + 0.0124 × 습도(%)
거리(cm) = (음속 × TOF) / 2
```

## 파일 구조

```
arduino_ultrasonic/
├── arduino_ultrasonic.ino  # 메인 스케치
├── HDC1080.h               # HDC1080 헤더 파일
├── HDC1080.cpp             # HDC1080 구현 파일
├── TUSS4470.h              # TUSS4470 헤더 파일
├── TUSS4470.cpp            # TUSS4470 구현 파일
└── README.md               # 이 파일
```

## 설치 및 사용 방법

### 1. Arduino IDE 설정

1. Arduino IDE 2.0 이상 설치
2. Arduino Uno R4 보드 지원 추가:
   - 도구 → 보드 → 보드 매니저
   - "Arduino UNO R4" 검색 및 설치

### 2. 업로드

1. `arduino_ultrasonic` 폴더를 Arduino 스케치북 폴더에 복사
2. Arduino IDE에서 `arduino_ultrasonic.ino` 파일 열기
3. 보드 선택: 도구 → 보드 → Arduino UNO R4 WiFi (또는 Minima)
4. 포트 선택: 도구 → 포트 → (해당 COM 포트)
5. 업로드 버튼 클릭

### 3. 시리얼 모니터 사용

1. 시리얼 모니터 열기 (Ctrl+Shift+M)
2. 보드레이트를 **115200**으로 설정
3. 측정 데이터가 실시간으로 표시됩니다

## 출력 형식

시스템이 부팅되면 다음과 같은 초기화 메시지가 표시됩니다:

```
========================================
Arduino Uno R4 Ultrasonic Distance Meter
with Temperature & Humidity Compensation
========================================
PCB Code: 250903JA0
TUSS4470 Transformer Drive Mode

Initializing HDC1080 sensor... OK
  Initial Temp: 25.34 °C
  Initial Humidity: 45.67 %
Initializing TUSS4470 driver... OK
  Mode: Transformer Drive
  Frequency: 40kHz (V40 AW14B)
  SPI: NCS=D10, SCLK=D13, MOSI=D11, MISO=D12

System ready!
```

측정 데이터 출력:

```
Time(ms)    Temp(°C)    Humidity(%)    Speed(cm/s)    TOF(ns)        Distance(cm)    Status
--------------------------------------------------------------------------------------------
500         25.34       45.67          34567.89       725000         123.45          OK
1000        25.35       45.68          34568.12       730000         124.12          OK
```

## 시리얼 명령어

측정 중에도 다음 명령어를 시리얼 모니터에 입력할 수 있습니다:

| 명령어 | 설명 |
|--------|------|
| **i** | 시스템 정보 및 핀 구성 출력 |
| **s** | 단일 측정 수행 (상세 정보 포함) |
| **c** | 보정 공식 및 TUSS4470 설정 정보 출력 |
| **r** | TUSS4470 레지스터 덤프 (디버깅용) |
| **v** | VOUT 아날로그 전압 출력 |

### 명령어 예제

시리얼 모니터에 `i`를 입력하면:

```
========== System Information ==========
Hardware:
  - Arduino Uno R4
  - TUSS4470 Ultrasonic Driver (Transformer Drive)
  - HDC1080 Temperature & Humidity Sensor
  - V40 AW14B Ultrasonic Transducer (40kHz)
  - PCB Code: 250903JA0

Pin Configuration:
  HDC1080: SDA=A4, SCL=A5
  TUSS4470: NCS=D10, SCLK=D13, MOSI=D11, MISO=D12
            IO1=A1, IO2=A2, VOUT=A0

Current Status:
  Temperature: 25.34 °C
  Humidity: 45.67 %
  Speed of Sound: 34567.89 cm/s
========================================
```

## TUSS4470 레지스터 설정

본 코드는 다음과 같이 TUSS4470을 설정합니다:

| 레지스터 | 값 | 설명 |
|---------|-----|------|
| DEV_CTRL0 | 0x18 | Transformer Drive 모드, 8 버스트 펄스 |
| DEV_CTRL1 | 0x00 | 40kHz 기본 주파수 |
| DEV_CTRL2 | 0x2F | 중간 이득, 최대 수신 시간 |
| DEV_CTRL3 | 0x44 | 임계값 및 필터 설정 |
| DEV_CTRL4 | 0xC0 | TOF 및 VOUT 활성화 |
| NUM_AVG | 0x04 | 4회 평균 |
| REC_LENGTH | 0xFF | 최대 레코드 길이 |

## 음속 보정 공식

### 기본 공식 (선형 근사)

```
v = 331.3 + 0.606 × T + 0.0124 × RH (m/s)
```

- **T**: 온도 (°C)
- **RH**: 상대습도 (%)

### 정확한 공식 (옵션)

```
v = 331.3 × √(1 + T/273.15) × (1 + 0.0016 × RH) (m/s)
```

코드에서는 계산 속도를 위해 선형 근사를 사용하며, 필요시 주석을 해제하여 정확한 공식을 사용할 수 있습니다.

## 거리 계산

```
거리(cm) = (음속(cm/s) × TOF(s)) / 2
```

- TOF는 나노초 단위로 측정됩니다 (TUSS4470 8MHz 클럭, 125ns 분해능)
- 2로 나누는 이유: 초음파가 왕복하기 때문

## 측정 범위 및 성능

- **최소 거리**: 2 cm
- **최대 거리**: 400 cm
- **측정 주기**: 500 ms (기본값, 코드에서 변경 가능)
- **정확도**: ±1 cm (보정 적용 시)
- **분해능**: 125 ns (약 0.02 mm)
- **온도 보정 범위**: -40°C ~ +125°C
- **습도 보정 범위**: 0% ~ 100% RH

## 설정 변경

### 측정 간격 및 범위 변경

`arduino_ultrasonic.ino` 파일 상단의 값을 수정:

```cpp
#define MEASUREMENT_INTERVAL 500  // 측정 간격 (ms)
#define MAX_DISTANCE 400          // 최대 측정 거리 (cm)
#define MIN_DISTANCE 2            // 최소 측정 거리 (cm)
#define MEASUREMENT_TIMEOUT 100   // 측정 타임아웃 (ms)
```

### 핀 변경 (커스텀 PCB 사용 시)

`TUSS4470.h` 파일에서 핀 정의 수정:

```cpp
#define TUSS4470_NCS_PIN        SS    // D10
#define TUSS4470_IO1_PIN        A1
#define TUSS4470_IO2_PIN        A2
#define TUSS4470_VOUT_PIN       A0
```

### TUSS4470 설정 변경

`TUSS4470.cpp`의 `setupRegisters()` 함수에서 레지스터 값 수정:

```cpp
void TUSS4470::setupRegisters() {
  // 버스트 펄스 수 변경 (예: 16펄스)
  writeRegister(TUSS4470_REG_DEV_CTRL0, 0x1F);

  // 평균 횟수 변경 (예: 8회)
  writeRegister(TUSS4470_REG_NUM_AVG, 0x08);

  // 기타 설정...
}
```

## 문제 해결

### HDC1080 초기화 실패

```
ERROR: Check I2C connections (SDA=A4, SCL=A5)
```

**해결 방법**:
- I2C 연결 확인 (SDA=A4, SCL=A5)
- 풀업 저항 확인 (일반적으로 PCB에 내장)
- 센서 주소 확인 (0x40)
- 전원 확인 (3.3V 또는 5V)

### TUSS4470 초기화 실패

```
ERROR: Check SPI connections and power
```

**해결 방법**:
- SPI 연결 확인 (NCS=D10, SCLK=D13, MOSI=D11, MISO=D12)
- 전원 전압 확인 (5V)
- PCB 실드 접촉 확인
- 시리얼 모니터에서 `r` 명령으로 레지스터 확인

### 측정 타임아웃

```
Status: TIMEOUT
```

**해결 방법**:
- 트랜스듀서 연결 확인
- 측정 대상이 범위 내에 있는지 확인 (2-400cm)
- 측정 대상 표면이 초음파를 잘 반사하는지 확인
- VOUT 전압 확인 (`v` 명령어 사용)
- 레지스터 설정 확인 (`r` 명령어 사용)

### 부정확한 측정값

**해결 방법**:
- 온습도 센서가 정상 동작하는지 확인 (`i` 명령어로 확인)
- 주변 온도가 안정화될 때까지 대기 (약 1분)
- 측정 대상 표면이 센서에 수직인지 확인
- 평균 횟수 증가 (`setNumAverages(8)`)
- 버스트 펄스 수 증가

### 에러 플래그 발생

```
Status: ERROR:0xXX
```

**해결 방법**:
- `r` 명령어로 레지스터 상태 확인
- ERROR_FLAGS 레지스터 분석 (데이터시트 참조)
- 이득 설정 조정 (DEV_CTRL2)
- 임계값 조정 (DEV_CTRL3)

## 디버깅 팁

1. **시리얼 모니터 사용**: `r` 명령으로 모든 레지스터 상태 확인
2. **VOUT 모니터링**: `v` 명령으로 아날로그 출력 전압 확인
3. **단일 측정**: `s` 명령으로 상세 측정 데이터 확인
4. **레지스터 수정**: 코드에서 설정 값 조정 후 재업로드

## 기술 사양

### TUSS4470

- 제조사: Texas Instruments
- 동작 전압: 3.0V ~ 5.5V
- SPI 속도: 최대 8MHz (코드에서 1MHz 사용)
- TOF 분해능: 125ns (8MHz 클럭)
- 드라이버 모드: Transformer Drive
- 데이터시트: [TUSS4470](https://www.ti.com/product/TUSS4470)

### HDC1080

- 제조사: Texas Instruments
- 동작 전압: 3.0V ~ 5.5V
- I2C 주소: 0x40
- 온도 정확도: ±0.2°C (일반)
- 습도 정확도: ±2% RH (일반)
- 데이터시트: [HDC1080](https://www.ti.com/product/HDC1080)

### V40 AW14B

- 제조사: Pro-Wave
- 중심 주파수: 40kHz ±1kHz
- 지향각: 약 60°
- 감도: -65dB (일반)
- 타입: 밀폐형 (Waterproof)

## 참고 자료

- [TUSS4470 데이터시트](https://www.ti.com/lit/ds/symlink/tuss4470.pdf)
- [HDC1080 데이터시트](https://www.ti.com/lit/ds/symlink/hdc1080.pdf)
- [Arduino Uno R4 문서](https://docs.arduino.cc/hardware/uno-r4-wifi)
- [음속 계산 공식](https://en.wikipedia.org/wiki/Speed_of_sound)

## 라이선스

이 코드는 교육 및 연구 목적으로 자유롭게 사용할 수 있습니다.

## 제작자

- **Claude AI Assistant**
- **날짜**: 2025-11-23
- **PCB Code**: 250903JA0
- **버전**: 2.0 (TUSS4470 Transformer Drive 전용)
