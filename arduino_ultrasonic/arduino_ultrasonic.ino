/*
 * Arduino Uno R4 초음파 거리 측정 시스템
 * 온도/습도 보정 기능 포함
 *
 * 하드웨어:
 * - Arduino Uno R4
 * - TUSS4470 초음파 드라이버
 * - HDC1080 온습도 센서
 * - V40 AW14B 초음파 트랜스듀서 (40kHz)
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
  Serial.println();

  // HDC1080 초기화
  Serial.print("Initializing HDC1080 sensor... ");
  if (tempHumiditySensor.begin()) {
    Serial.println("OK");
  } else {
    Serial.println("FAILED");
    Serial.println("Please check I2C connections!");
    while (1) {
      delay(1000);
    }
  }

  // TUSS4470 초기화
  Serial.print("Initializing TUSS4470 driver... ");
  if (ultrasonicSensor.begin()) {
    Serial.println("OK");
  } else {
    Serial.println("FAILED");
    Serial.println("Please check SPI connections!");
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
  Serial.println("Time(ms)\tTemp(°C)\tHumidity(%)\tSpeed(cm/s)\tDistance(cm)\tStatus");
  Serial.println("------------------------------------------------------------------------");
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

    // 3. 초음파 측정 트리거
    ultrasonicSensor.trigger();

    // 4. 측정 완료 대기
    unsigned long timeout = millis();
    while (!ultrasonicSensor.isReady()) {
      if (millis() - timeout > 100) { // 100ms 타임아웃
        Serial.print(currentMillis);
        Serial.print("\t");
        Serial.print(temperature, 2);
        Serial.print("\t\t");
        Serial.print(humidity, 2);
        Serial.print("\t\t");
        Serial.print(speedOfSound, 2);
        Serial.print("\t\t");
        Serial.print("---");
        Serial.print("\t\t");
        Serial.println("TIMEOUT");
        return;
      }
      delayMicroseconds(100);
    }

    // 5. 거리 계산
    float distance = ultrasonicSensor.getDistance(speedOfSound);

    // 6. 결과 출력
    Serial.print(currentMillis);
    Serial.print("\t\t");
    Serial.print(temperature, 2);
    Serial.print("\t\t");
    Serial.print(humidity, 2);
    Serial.print("\t\t");
    Serial.print(speedOfSound, 2);
    Serial.print("\t\t");

    // 거리 유효성 검사
    if (distance >= MIN_DISTANCE && distance <= MAX_DISTANCE) {
      Serial.print(distance, 2);
      Serial.print("\t\t");
      Serial.println("OK");
    } else if (distance < MIN_DISTANCE) {
      Serial.print(distance, 2);
      Serial.print("\t\t");
      Serial.println("TOO CLOSE");
    } else {
      Serial.print(distance, 2);
      Serial.print("\t\t");
      Serial.println("OUT OF RANGE");
    }
  }
}

/**
 * 온도와 습도를 고려한 음속 계산
 *
 * @param temp 온도 (°C)
 * @param hum 상대습도 (%)
 * @return 음속 (cm/s)
 *
 * 사용된 공식:
 * 1. 건조 공기에서의 음속 (Laplace-Newton 공식):
 *    v = 331.3 * sqrt(1 + T/273.15)
 *
 * 2. 습도 보정 (간소화된 근사):
 *    v_corrected = v_dry * (1 + 0.0016 * humidity)
 *
 * 또는 더 간단한 선형 근사:
 *    v = 331.3 + 0.606 * T + 0.0124 * RH (m/s)
 */
float calculateSpeedOfSound(float temp, float hum) {
  // 방법 1: 정확한 계산
  // float tempK = temp + 273.15;
  // float v_dry = 331.3 * sqrt(1.0 + temp / 273.15);
  // float v_corrected = v_dry * (1.0 + 0.0016 * hum);
  // return v_corrected * 100.0; // m/s to cm/s

  // 방법 2: 간소화된 선형 근사 (계산 속도가 빠름)
  float v_ms = 331.3 + (0.606 * temp) + (0.0124 * hum);
  return v_ms * 100.0; // m/s to cm/s
}

/**
 * 시리얼 명령어 처리 (선택사항)
 *
 * 사용 가능한 명령어:
 * - 'i': 현재 정보 출력
 * - 's': 단일 측정
 * - 'c': 보정 정보 출력
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

      case '\n':
      case '\r':
        // 개행 문자 무시
        break;

      default:
        Serial.println("Unknown command. Available commands:");
        Serial.println("  i - System information");
        Serial.println("  s - Single measurement");
        Serial.println("  c - Calibration information");
        break;
    }
  }
}

void printSystemInfo() {
  Serial.println("\n========== System Information ==========");
  Serial.println("Hardware:");
  Serial.println("  - Arduino Uno R4");
  Serial.println("  - TUSS4470 Ultrasonic Driver");
  Serial.println("  - HDC1080 Temperature & Humidity Sensor");
  Serial.println("  - V40 AW14B Ultrasonic Transducer (40kHz)");
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

  ultrasonicSensor.trigger();
  delay(50);

  if (ultrasonicSensor.isReady()) {
    float distance = ultrasonicSensor.getDistance(speedOfSound);
    Serial.print("Distance: ");
    Serial.print(distance, 2);
    Serial.println(" cm");
  } else {
    Serial.println("Measurement timeout!");
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
  Serial.println("\nMeasurement Range:");
  Serial.print("  Minimum: ");
  Serial.print(MIN_DISTANCE);
  Serial.println(" cm");
  Serial.print("  Maximum: ");
  Serial.print(MAX_DISTANCE);
  Serial.println(" cm");
  Serial.println("=============================================\n");
}
