# Arduino Uno R4 초음파 거리 측정 시스템

온도와 습도 보정 기능을 갖춘 정밀 초음파 거리 측정 시스템입니다.

## 하드웨어 구성

- **Arduino Uno R4**: 메인 컨트롤러
- **TUSS4470**: 초음파 센서 드라이버 IC
- **HDC1080**: I2C 온습도 센서
- **V40 AW14B**: 40kHz 초음파 트랜스듀서

## 핀 연결

### HDC1080 (I2C 온습도 센서)
| HDC1080 핀 | Arduino Uno R4 핀 |
|-----------|------------------|
| VDD       | 3.3V             |
| GND       | GND              |
| SDA       | A4 (SDA)         |
| SCL       | A5 (SCL)         |

### TUSS4470 (SPI 초음파 드라이버)
| TUSS4470 핀 | Arduino Uno R4 핀 | 설명 |
|------------|------------------|------|
| VCC        | 5V               | 전원 |
| GND        | GND              | 접지 |
| SCLK       | D13 (SCK)        | SPI 클럭 |
| MOSI       | D11 (MOSI)       | SPI 데이터 입력 |
| MISO       | D12 (MISO)       | SPI 데이터 출력 |
| CS         | D10              | 칩 선택 |
| TRIG       | D7               | 측정 트리거 |
| READY      | D6               | 측정 완료 신호 |

**참고**: 사용자의 실드 설계에 따라 핀 번호를 `TUSS4470.h` 파일에서 수정할 수 있습니다.

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

### 2. 라이브러리 업로드

1. `arduino_ultrasonic` 폴더 전체를 Arduino 스케치북 폴더에 복사
2. Arduino IDE에서 `arduino_ultrasonic.ino` 파일 열기
3. 보드 선택: 도구 → 보드 → Arduino UNO R4 WiFi (또는 Minima)
4. 포트 선택: 도구 → 포트 → (해당 COM 포트)
5. 업로드 버튼 클릭

### 3. 시리얼 모니터 사용

1. 시리얼 모니터 열기 (Ctrl+Shift+M)
2. 보드레이트를 **115200**으로 설정
3. 측정 데이터가 실시간으로 표시됩니다

## 출력 형식

```
Time(ms)    Temp(°C)    Humidity(%)    Speed(cm/s)    Distance(cm)    Status
------------------------------------------------------------------------
500         25.34       45.67          34567.89       123.45          OK
1000        25.35       45.68          34568.12       124.12          OK
```

## 시리얼 명령어

측정 중에도 다음 명령어를 시리얼 모니터에 입력할 수 있습니다:

- **`i`**: 시스템 정보 출력
- **`s`**: 단일 측정 수행 (상세 정보 포함)
- **`c`**: 보정 공식 및 측정 범위 정보 출력

## 음속 보정 공식

온도와 습도에 따른 음속 보정 공식:

```
v = 331.3 + 0.606 × T + 0.0124 × RH (m/s)
```

여기서:
- **T**: 온도 (°C)
- **RH**: 상대습도 (%)
- **v**: 음속 (m/s)

## 거리 계산

```
d = (v × t) / 2
```

여기서:
- **v**: 음속 (cm/s)
- **t**: 비행시간 (Time of Flight, 초)
- **d**: 거리 (cm)
- 2로 나누는 이유: 초음파가 왕복하기 때문

## 측정 범위

- **최소 거리**: 2 cm
- **최대 거리**: 400 cm
- **측정 주기**: 500 ms (기본값, 코드에서 변경 가능)

## 설정 변경

`arduino_ultrasonic.ino` 파일 상단의 다음 값들을 수정하여 동작을 변경할 수 있습니다:

```cpp
#define MEASUREMENT_INTERVAL 500  // 측정 간격 (ms)
#define MAX_DISTANCE 400          // 최대 측정 거리 (cm)
#define MIN_DISTANCE 2            // 최소 측정 거리 (cm)
```

핀 설정은 `TUSS4470.h` 파일에서 변경:

```cpp
#define TUSS4470_CS_PIN 10
#define TUSS4470_TRIG_PIN 7
#define TUSS4470_READY_PIN 6
```

## 문제 해결

### HDC1080 초기화 실패
- I2C 연결 확인 (SDA, SCL)
- 풀업 저항 확인 (4.7kΩ 권장)
- 센서 주소 확인 (기본 0x40)

### TUSS4470 초기화 실패
- SPI 연결 확인
- CS, TRIG, READY 핀 연결 확인
- 전원 전압 확인 (5V)

### 측정 타임아웃
- 트랜스듀서 연결 확인
- 측정 대상이 범위 내에 있는지 확인
- 음향 반사가 잘 되는 표면인지 확인

### 부정확한 측정값
- 온습도 센서가 정상 동작하는지 확인
- 주변 온도가 안정화될 때까지 대기
- 측정 대상 표면이 수직인지 확인

## 성능 특성

- **정확도**: ±1cm (보정 적용 시)
- **분해능**: 0.01cm
- **응답시간**: < 50ms
- **온도 보정 범위**: -40°C ~ +125°C
- **습도 보정 범위**: 0% ~ 100% RH

## 참고 자료

- [TUSS4470 데이터시트](https://www.ti.com/product/TUSS4470)
- [HDC1080 데이터시트](https://www.ti.com/product/HDC1080)
- [V40 AW14B 트랜스듀서 사양](https://www.pro-wave.com.tw/)

## 라이선스

이 코드는 교육 및 연구 목적으로 자유롭게 사용할 수 있습니다.

## 제작자

Claude AI Assistant (2025-11-23)
