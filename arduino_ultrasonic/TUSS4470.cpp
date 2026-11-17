#include "TUSS4470.h"

TUSS4470::TUSS4470() {
}

bool TUSS4470::begin() {
  // NCS 핀 설정
  pinMode(TUSS4470_NCS_PIN, OUTPUT);
  digitalWrite(TUSS4470_NCS_PIN, HIGH); // 비활성 상태

  // IO 핀 설정
  pinMode(TUSS4470_IO1_PIN, INPUT);
  pinMode(TUSS4470_IO2_PIN, INPUT);

  // VOUT 핀은 아날로그 입력으로 사용 (pinMode 불필요)

  // SPI 초기화
  SPI.begin();
  SPI.beginTransaction(SPISettings(TUSS4470_SPI_SPEED, TUSS4470_SPI_BIT_ORDER, TUSS4470_SPI_MODE));

  delay(100); // 초기화 대기

  // 디바이스 리셋
  reset();
  delay(50);

  // 레지스터 설정
  setupRegisters();
  delay(10);

  // 디바이스 확인
  if (!checkDeviceID()) {
    return false;
  }

  return true;
}

void TUSS4470::reset() {
  // 소프트웨어 리셋 (DEV_CTRL0 레지스터)
  writeRegister(TUSS4470_REG_DEV_CTRL0, 0x80);
  delay(10);
}

bool TUSS4470::checkDeviceID() {
  // TUSS4470의 상태 레지스터 읽기로 통신 확인
  uint8_t stat0 = readRegister(TUSS4470_REG_DEV_STAT0);
  uint8_t stat1 = readRegister(TUSS4470_REG_DEV_STAT1);

  // 통신이 정상이면 0xFF가 아닌 값이 읽혀야 함
  if (stat0 == 0xFF && stat1 == 0xFF) {
    return false;
  }

  return true;
}

void TUSS4470::setupRegisters() {
  // DEV_CTRL0: 측정 모드 설정
  // Bit[7]: SW_RST = 0 (리셋 해제)
  // Bit[6:4]: DRIVER_MODE = 001 (Transformer drive mode)
  // Bit[3:0]: BURST_PULSE = 1000 (8 펄스)
  writeRegister(TUSS4470_REG_DEV_CTRL0, 0x18);

  // DEV_CTRL1: 주파수 설정
  // 40kHz 트랜스듀서를 위한 설정
  // FREQ_SHIFT = 0, 기본 주파수 사용
  writeRegister(TUSS4470_REG_DEV_CTRL1, 0x00);

  // DEV_CTRL2: 드라이버 및 수신 설정
  // Bit[7:6]: VDRV_REXT = 00 (외부 저항 사용)
  // Bit[5:4]: REC_GAIN = 10 (중간 이득)
  // Bit[3:0]: REC_TIME = 1111 (최대 수신 시간)
  writeRegister(TUSS4470_REG_DEV_CTRL2, 0x2F);

  // DEV_CTRL3: 임계값 및 필터 설정
  // Bit[7:4]: THR_LEVEL = 0100 (임계값 레벨)
  // Bit[3:0]: FILTER = 0100 (필터 설정)
  writeRegister(TUSS4470_REG_DEV_CTRL3, 0x44);

  // DEV_CTRL4: TOF 및 출력 설정
  // Bit[7]: TOF_EN = 1 (TOF 측정 활성화)
  // Bit[6]: VOUT_EN = 1 (VOUT 출력 활성화)
  // Bit[5:0]: Reserved
  writeRegister(TUSS4470_REG_DEV_CTRL4, 0xC0);

  // NUM_AVG: 평균 횟수 설정 (4회)
  writeRegister(TUSS4470_REG_NUM_AVG, 0x04);

  // REC_LENGTH: 레코드 길이 설정 (최대)
  writeRegister(TUSS4470_REG_REC_LENGTH, 0xFF);

  // TEMP_TRIM: 온도 보정 설정 (기본값)
  writeRegister(TUSS4470_REG_TEMP_TRIM, 0x00);
}

void TUSS4470::writeRegister(uint8_t reg, uint8_t value) {
  digitalWrite(TUSS4470_NCS_PIN, LOW);
  delayMicroseconds(1);

  SPI.transfer(reg & 0x7F); // 쓰기: MSB = 0
  SPI.transfer(value);

  delayMicroseconds(1);
  digitalWrite(TUSS4470_NCS_PIN, HIGH);
  delayMicroseconds(10);
}

uint8_t TUSS4470::readRegister(uint8_t reg) {
  digitalWrite(TUSS4470_NCS_PIN, LOW);
  delayMicroseconds(1);

  SPI.transfer(reg | 0x80); // 읽기: MSB = 1
  uint8_t value = SPI.transfer(0x00);

  delayMicroseconds(1);
  digitalWrite(TUSS4470_NCS_PIN, HIGH);
  delayMicroseconds(10);

  return value;
}

void TUSS4470::writeRegister16(uint8_t reg, uint16_t value) {
  digitalWrite(TUSS4470_NCS_PIN, LOW);
  delayMicroseconds(1);

  SPI.transfer(reg & 0x7F);
  SPI.transfer((value >> 8) & 0xFF); // MSB
  SPI.transfer(value & 0xFF);        // LSB

  delayMicroseconds(1);
  digitalWrite(TUSS4470_NCS_PIN, HIGH);
  delayMicroseconds(10);
}

uint16_t TUSS4470::readRegister16(uint8_t reg) {
  digitalWrite(TUSS4470_NCS_PIN, LOW);
  delayMicroseconds(1);

  SPI.transfer(reg | 0x80);
  uint8_t msb = SPI.transfer(0x00);
  uint8_t lsb = SPI.transfer(0x00);

  delayMicroseconds(1);
  digitalWrite(TUSS4470_NCS_PIN, HIGH);
  delayMicroseconds(10);

  return (msb << 8) | lsb;
}

void TUSS4470::startMeasurement() {
  // DEV_CTRL0의 START_MEAS 비트 설정
  uint8_t ctrl0 = readRegister(TUSS4470_REG_DEV_CTRL0);
  ctrl0 |= 0x01; // Bit[0]: START_MEAS = 1
  writeRegister(TUSS4470_REG_DEV_CTRL0, ctrl0);
}

bool TUSS4470::isMeasurementReady() {
  // DEV_STAT0 레지스터의 MEAS_DONE 비트 확인
  uint8_t stat0 = readRegister(TUSS4470_REG_DEV_STAT0);
  return (stat0 & 0x01) != 0; // Bit[0]: MEAS_DONE
}

uint32_t TUSS4470::getTimeOfFlight() {
  // TOF 레지스터 3개 읽기 (24비트)
  uint8_t tof1 = readRegister(TUSS4470_REG_TOF_1); // LSB
  uint8_t tof2 = readRegister(TUSS4470_REG_TOF_2); // Mid
  uint8_t tof3 = readRegister(TUSS4470_REG_TOF_3); // MSB

  uint32_t tof = ((uint32_t)tof3 << 16) | ((uint32_t)tof2 << 8) | tof1;

  // TOF 값을 나노초로 변환
  // TUSS4470의 클럭은 8MHz, 각 카운트는 125ns
  return tof * 125; // 나노초 단위
}

float TUSS4470::getDistance(float speedOfSound) {
  uint32_t tof_ns = getTimeOfFlight();

  // 거리 계산: d = (v * t) / 2
  // speedOfSound: cm/s
  // tof_ns: 나노초
  // 1초 = 1,000,000,000 나노초

  float time_sec = tof_ns / 1000000000.0;
  float distance = (speedOfSound * time_sec) / 2.0;

  return distance;
}

uint16_t TUSS4470::getVoutRaw() {
  return analogRead(TUSS4470_VOUT_PIN);
}

float TUSS4470::getVoutVoltage() {
  uint16_t raw = getVoutRaw();
  // Arduino Uno R4는 14비트 ADC (0-16383)
  // 기준 전압 5V
  return (raw / 16383.0) * 5.0;
}

void TUSS4470::setNumAverages(uint8_t num) {
  writeRegister(TUSS4470_REG_NUM_AVG, num);
}

void TUSS4470::setRecordLength(uint8_t len) {
  writeRegister(TUSS4470_REG_REC_LENGTH, len);
}

uint8_t TUSS4470::getErrorFlags() {
  return readRegister(TUSS4470_REG_ERROR_FLAGS);
}
