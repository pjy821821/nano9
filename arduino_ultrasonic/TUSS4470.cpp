#include "TUSS4470.h"

TUSS4470::TUSS4470(uint8_t csPin, uint8_t trigPin, uint8_t readyPin) {
  _csPin = csPin;
  _trigPin = trigPin;
  _readyPin = readyPin;
}

bool TUSS4470::begin() {
  pinMode(_csPin, OUTPUT);
  pinMode(_trigPin, OUTPUT);
  pinMode(_readyPin, INPUT);

  digitalWrite(_csPin, HIGH);
  digitalWrite(_trigPin, LOW);

  SPI.begin();
  SPI.setDataMode(SPI_MODE0);
  SPI.setBitOrder(MSBFIRST);
  SPI.setClockDivider(SPI_CLOCK_DIV16); // 1MHz for safe operation

  delay(100);

  setupRegisters();

  delay(10);

  return true;
}

void TUSS4470::setupRegisters() {
  // TUSS4470 초기 설정
  // 주파수 설정 (40kHz for V40 AW14B transducer)
  writeRegister(TUSS4470_FREQ_CTRL, 0x28); // 40kHz 설정

  // 디지털 제어 설정
  writeRegister(TUSS4470_DIG_CTRL, 0x00);

  // 초기화 제어
  writeRegister(TUSS4470_INIT_CTRL, 0x88); // Burst count = 8

  // 출력 제어
  writeRegister(TUSS4470_OUT_CTRL, 0x00);

  // 임계값 설정
  writeRegister(TUSS4470_THR_CTRL0, 0x88);
  writeRegister(TUSS4470_THR_CTRL1, 0x88);
  writeRegister(TUSS4470_THR_CTRL2, 0x88);
  writeRegister(TUSS4470_THR_CTRL3, 0x88);

  // 수신 길이 설정
  writeRegister(TUSS4470_REC_LENGTH, 0xFF);

  // 평균 횟수 설정
  writeRegister(TUSS4470_NUM_AVG, 0x04); // 4회 평균

  // TOF 제어
  writeRegister(TUSS4470_TOF_CTRL, 0x01);
}

void TUSS4470::writeRegister(uint8_t reg, uint8_t value) {
  digitalWrite(_csPin, LOW);
  delayMicroseconds(1);

  SPI.transfer(reg & 0x7F); // Write bit = 0
  SPI.transfer(value);

  delayMicroseconds(1);
  digitalWrite(_csPin, HIGH);
  delayMicroseconds(10);
}

uint8_t TUSS4470::readRegister(uint8_t reg) {
  digitalWrite(_csPin, LOW);
  delayMicroseconds(1);

  SPI.transfer(reg | 0x80); // Read bit = 1
  uint8_t value = SPI.transfer(0x00);

  delayMicroseconds(1);
  digitalWrite(_csPin, HIGH);
  delayMicroseconds(10);

  return value;
}

void TUSS4470::trigger() {
  digitalWrite(_trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(_trigPin, LOW);
}

bool TUSS4470::isReady() {
  return digitalRead(_readyPin) == HIGH;
}

uint32_t TUSS4470::getTimeOfFlight() {
  // TOF 레지스터에서 시간 읽기 (구현은 TUSS4470 데이터시트 참조)
  // 이는 간소화된 버전이며, 실제로는 여러 레지스터에서 읽어야 함
  uint8_t tof_low = readRegister(0x10);
  uint8_t tof_mid = readRegister(0x11);
  uint8_t tof_high = readRegister(0x12);

  uint32_t tof = ((uint32_t)tof_high << 16) | ((uint32_t)tof_mid << 8) | tof_low;

  // 클럭 주파수에 따라 마이크로초로 변환 (8MHz 클럭 기준)
  return tof / 8;
}

float TUSS4470::getDistance(float speedOfSound) {
  uint32_t tof = getTimeOfFlight();

  // 거리 = (음속 * 시간) / 2
  // 음속: cm/s, 시간: μs
  // speedOfSound는 cm/s 단위
  float distance = (speedOfSound * tof * 0.000001) / 2.0;

  return distance;
}
