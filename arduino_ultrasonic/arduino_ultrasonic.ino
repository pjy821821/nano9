/*
 * Arduino Uno R4 초음파 거리 측정 시스템
 * 온도/습도 보정 기능 포함
 *
 * 하드웨어:
 * - Arduino Uno R4
 * - TUSS4470 초음파 드라이버 (Transformer Drive)
 * - HDC1080 온습도 센서
 * - V40 AW14B 초음파 트랜스듀서 (40kHz)
 *
 * PCB Code: 250903JA0
 *
 * 핀 연결:
 * HDC1080 (I2C):
 *   - SDA -> A4 (D18)
 *   - SCL -> A5 (D19)
 *
 * TUSS4470 (SPI):
 *   - NCS  -> D10 (SS)
 *   - SCLK -> D13 (SCK)
 *   - SDI  -> D11 (MOSI)
 *   - SDO  -> D12 (MISO)
 *   - IO1  -> A1
 *   - IO2  -> A2
 *   - VOUT -> A0
 *
 * 작성자: Claude
 * 날짜: 2025-11-23
 */

#include "HDC1080.h"
#include "TUSS4470.h"
#include <Wire.h>
#include <SPI.h>

// 센서 객체 생성
HDC1080 tempHumiditySensor;
TUSS4470 ultrasonicSensor;

// 측정 설정
#define MEASUREMENT_INTERVAL 500  // 측정 간격 (ms)
#define MAX_DISTANCE 400          // 최대 측정 거리 (cm)
#define MIN_DISTANCE 2            // 최소 측정 거리 (cm)
#define MEASUREMENT_TIMEOUT 100   // 측정 타임아웃 (ms)

// 전역 변수
float temperature = 25.0;         // 온도 (°C)
float humidity = 50.0;            // 습도 (%)
float speedOfSound = 34300.0;     // 음속 (cm/s)
unsigned long lastMeasurement = 0;

void setup() {
  Serial.begin(115200);
  while (!Serial) {
    ; // 시리얼 포트 연결 대기
  }

  Serial.println("========================================");
  Serial.println("Arduino Uno R4 Ultrasonic Distance Meter");
  Serial.println("with Temperature & Humidity Compensation");
  Serial.println("========================================");
  Serial.println("PCB Code: 250903JA0");
  Serial.println("TUSS4470 Transformer Drive Mode");
  Serial.println();

  // HDC1080 초기화
  Serial.print("Initializing HDC1080 sensor... ");
  if (tempHumiditySensor.begin()) {
    Serial.println("OK");
    // 초기 온습도 읽기
    tempHumiditySensor.readTempHumidity(temperature, humidity);
    Serial.print("  Initial Temp: ");
    Serial.print(temperature, 2);
    Serial.println(" °C");
    Serial.print("  Initial Humidity: ");
    Serial.print(humidity, 2);
    Serial.println(" %");
  } else {
    Serial.println("FAILED");
    Serial.println("ERROR: Check I2C connections (SDA=A4, SCL=A5)");
    while (1) {
      delay(1000);
    }
  }

  // TUSS4470 초기화
  Serial.print("Initializing TUSS4470 driver... ");
  if (ultrasonicSensor.begin()) {
    Serial.println("OK");
    Serial.println("  Mode: Transformer Drive");
    Serial.println("  Frequency: 40kHz (V40 AW14B)");
    Serial.println("  SPI: NCS=D10, SCLK=D13, MOSI=D11, MISO=D12");
  } else {
    Serial.println("FAILED");
    Serial.println("ERROR: Check SPI connections and power");
    while (1) {
      delay(1000);
    }
  }

  Serial.println();
  Serial.println("System ready!");
  Serial.println("Measurements will start in 2 seconds...");
  Serial.println();
  delay(2000);

  // 컬럼 헤더 출력
  Serial.println("Time(ms)\tTemp(°C)\tHumidity(%)\tSpeed(cm/s)\tTOF(ns)\t\tDistance(cm)\tStatus");
  Serial.println("--------------------------------------------------------------------------------------------");
}

void loop() {
  unsigned long currentMillis = millis();

  // 지정된 간격마다 측정
  if (currentMillis - lastMeasurement >= MEASUREMENT_INTERVAL) {
    lastMeasurement = currentMillis;

    // 1. 온도와 습도 측정
    tempHumiditySensor.readTempHumidity(temperature, humidity);

    // 2. 음속 계산 (온도와 습도 보정 적용)
    speedOfSound = calculateSpeedOfSound(temperature, humidity);

    // 3. 초음파 측정 시작
    ultrasonicSensor.startMeasurement();

    // 4. 측정 완료 대기
    unsigned long timeout = millis();
    bool measurementComplete = false;

    while ((millis() - timeout) < MEASUREMENT_TIMEOUT) {
      if (ultrasonicSensor.isMeasurementReady()) {
        measurementComplete = true;
        break;
      }
      delayMicroseconds(100);
    }

    // 5. 결과 출력
    Serial.print(currentMillis);
    Serial.print("\t\t");
    Serial.print(temperature, 2);
    Serial.print("\t\t");
    Serial.print(humidity, 2);
    Serial.print("\t\t");
    Serial.print(speedOfSound, 2);
    Serial.print("\t\t");

    if (measurementComplete) {
      // TOF 및 거리 계산
      uint32_t tof_ns = ultrasonicSensor.getTimeOfFlight();
      float distance = ultrasonicSensor.getDistance(speedOfSound);

      Serial.print(tof_ns);
      Serial.print("\t\t");
      Serial.print(distance, 2);
      Serial.print("\t\t");

      // 에러 플래그 확인
      uint8_t errors = ultrasonicSensor.getErrorFlags();
      if (errors != 0) {
        Serial.print("ERROR:0x");
        Serial.println(errors, HEX);
      }
      // 거리 유효성 검사
      else if (distance >= MIN_DISTANCE && distance <= MAX_DISTANCE) {
        Serial.println("OK");
      } else if (distance < MIN_DISTANCE) {
        Serial.println("TOO CLOSE");
      } else {
        Serial.println("OUT OF RANGE");
      }
    } else {
      Serial.print("---");
      Serial.print("\t\t");
      Serial.print("---");
      Serial.print("\t\t");
      Serial.println("TIMEOUT");
    }
  }

  // 시리얼 명령어 처리
  if (Serial.available()) {
    serialEvent();
  }
}

/**
 * 온도와 습도를 고려한 음속 계산
 *
 * @param temp 온도 (°C)
 * @param hum 상대습도 (%)
 * @return 음속 (cm/s)
 *
 * 사용된 공식 (간소화된 선형 근사):
 *   v = 331.3 + 0.606 * T + 0.0124 * RH (m/s)
 *
 * 더 정확한 공식:
 *   v = 331.3 * sqrt(1 + T/273.15) * (1 + 0.0016 * RH)
 */
float calculateSpeedOfSound(float temp, float hum) {
  // 간소화된 선형 근사 (빠른 계산)
  float v_ms = 331.3 + (0.606 * temp) + (0.0124 * hum);
  return v_ms * 100.0; // m/s to cm/s

  // 또는 더 정확한 계산 (선택)
  // float tempK = temp + 273.15;
  // float v_dry = 331.3 * sqrt(1.0 + temp / 273.15);
  // float v_corrected = v_dry * (1.0 + 0.0016 * hum);
  // return v_corrected * 100.0; // m/s to cm/s
}

/**
 * 시리얼 명령어 처리
 *
 * 사용 가능한 명령어:
 * - 'i': 현재 정보 출력
 * - 's': 단일 측정
 * - 'c': 보정 정보 출력
 * - 'r': 레지스터 정보 출력 (디버깅)
 * - 'v': VOUT 전압 출력
 */
void serialEvent() {
  while (Serial.available()) {
    char cmd = Serial.read();

    switch (cmd) {
      case 'i':
      case 'I':
        printSystemInfo();
        break;

      case 's':
      case 'S':
        performSingleMeasurement();
        break;

      case 'c':
      case 'C':
        printCalibrationInfo();
        break;

      case 'r':
      case 'R':
        printRegisterInfo();
        break;

      case 'v':
      case 'V':
        printVoutInfo();
        break;

      case '\n':
      case '\r':
        // 개행 문자 무시
        break;

      default:
        Serial.println("\n=== Available Commands ===");
        Serial.println("  i - System information");
        Serial.println("  s - Single measurement");
        Serial.println("  c - Calibration information");
        Serial.println("  r - Register information (debug)");
        Serial.println("  v - VOUT voltage");
        Serial.println("==========================\n");
        break;
    }
  }
}

void printSystemInfo() {
  Serial.println("\n========== System Information ==========");
  Serial.println("Hardware:");
  Serial.println("  - Arduino Uno R4");
  Serial.println("  - TUSS4470 Ultrasonic Driver (Transformer Drive)");
  Serial.println("  - HDC1080 Temperature & Humidity Sensor");
  Serial.println("  - V40 AW14B Ultrasonic Transducer (40kHz)");
  Serial.println("  - PCB Code: 250903JA0");
  Serial.println("\nPin Configuration:");
  Serial.println("  HDC1080: SDA=A4, SCL=A5");
  Serial.println("  TUSS4470: NCS=D10, SCLK=D13, MOSI=D11, MISO=D12");
  Serial.println("            IO1=A1, IO2=A2, VOUT=A0");
  Serial.println("\nCurrent Status:");
  Serial.print("  Temperature: ");
  Serial.print(temperature, 2);
  Serial.println(" °C");
  Serial.print("  Humidity: ");
  Serial.print(humidity, 2);
  Serial.println(" %");
  Serial.print("  Speed of Sound: ");
  Serial.print(speedOfSound, 2);
  Serial.println(" cm/s");
  Serial.println("========================================\n");
}

void performSingleMeasurement() {
  Serial.println("\n--- Single Measurement ---");

  tempHumiditySensor.readTempHumidity(temperature, humidity);
  speedOfSound = calculateSpeedOfSound(temperature, humidity);

  Serial.print("Temperature: ");
  Serial.print(temperature, 2);
  Serial.println(" °C");

  Serial.print("Humidity: ");
  Serial.print(humidity, 2);
  Serial.println(" %");

  Serial.print("Speed of Sound: ");
  Serial.print(speedOfSound, 2);
  Serial.println(" cm/s");

  ultrasonicSensor.startMeasurement();
  delay(50);

  if (ultrasonicSensor.isMeasurementReady()) {
    uint32_t tof_ns = ultrasonicSensor.getTimeOfFlight();
    float distance = ultrasonicSensor.getDistance(speedOfSound);

    Serial.print("Time of Flight: ");
    Serial.print(tof_ns);
    Serial.println(" ns");

    Serial.print("Distance: ");
    Serial.print(distance, 2);
    Serial.println(" cm");

    uint8_t errors = ultrasonicSensor.getErrorFlags();
    Serial.print("Error Flags: 0x");
    Serial.println(errors, HEX);
  } else {
    Serial.println("ERROR: Measurement timeout!");
  }

  Serial.println("--------------------------\n");
}

void printCalibrationInfo() {
  Serial.println("\n========== Calibration Information ==========");
  Serial.println("Speed of Sound Calculation:");
  Serial.println("  Formula: v = 331.3 + 0.606*T + 0.0124*RH (m/s)");
  Serial.println("  where:");
  Serial.println("    T  = Temperature in °C");
  Serial.println("    RH = Relative Humidity in %");
  Serial.println("\nDistance Calculation:");
  Serial.println("  Formula: d = (v * t) / 2");
  Serial.println("  where:");
  Serial.println("    v = Speed of sound (cm/s)");
  Serial.println("    t = Time of flight (s)");
  Serial.println("    d = Distance (cm)");
  Serial.println("\nTUSS4470 Configuration:");
  Serial.println("  - Driver Mode: Transformer Drive");
  Serial.println("  - Burst Pulses: 8");
  Serial.println("  - Frequency: 40kHz");
  Serial.println("  - Averages: 4");
  Serial.println("  - TOF Clock: 8MHz (125ns resolution)");
  Serial.println("\nMeasurement Range:");
  Serial.print("  Minimum: ");
  Serial.print(MIN_DISTANCE);
  Serial.println(" cm");
  Serial.print("  Maximum: ");
  Serial.print(MAX_DISTANCE);
  Serial.println(" cm");
  Serial.println("=============================================\n");
}

void printRegisterInfo() {
  Serial.println("\n========== TUSS4470 Register Dump ==========");

  Serial.print("DEV_STAT0:   0x");
  Serial.println(ultrasonicSensor.readRegister(0x00), HEX);

  Serial.print("DEV_STAT1:   0x");
  Serial.println(ultrasonicSensor.readRegister(0x01), HEX);

  Serial.print("DEV_CTRL0:   0x");
  Serial.println(ultrasonicSensor.readRegister(0x05), HEX);

  Serial.print("DEV_CTRL1:   0x");
  Serial.println(ultrasonicSensor.readRegister(0x06), HEX);

  Serial.print("DEV_CTRL2:   0x");
  Serial.println(ultrasonicSensor.readRegister(0x07), HEX);

  Serial.print("DEV_CTRL3:   0x");
  Serial.println(ultrasonicSensor.readRegister(0x08), HEX);

  Serial.print("DEV_CTRL4:   0x");
  Serial.println(ultrasonicSensor.readRegister(0x09), HEX);

  Serial.print("ERROR_FLAGS: 0x");
  Serial.println(ultrasonicSensor.readRegister(0x0D), HEX);

  Serial.print("NUM_AVG:     0x");
  Serial.println(ultrasonicSensor.readRegister(0x0F), HEX);

  Serial.print("REC_LENGTH:  0x");
  Serial.println(ultrasonicSensor.readRegister(0x10), HEX);

  Serial.println("============================================\n");
}

void printVoutInfo() {
  Serial.println("\n--- VOUT Information ---");

  uint16_t vout_raw = ultrasonicSensor.getVoutRaw();
  float vout_voltage = ultrasonicSensor.getVoutVoltage();

  Serial.print("VOUT Raw (ADC): ");
  Serial.println(vout_raw);

  Serial.print("VOUT Voltage: ");
  Serial.print(vout_voltage, 3);
  Serial.println(" V");

  Serial.println("------------------------\n");
}
