#include "HDC1080.h"

HDC1080::HDC1080() {
}

bool HDC1080::begin() {
  Wire.begin();

  // HDC1080 설정: 온도와 습도를 순차적으로 측정 (14bit 해상도)
  writeRegister(HDC1080_CONFIG_REG, 0x1000);
  delay(15);

  // 제조사 ID 확인 (0x5449 = Texas Instruments)
  uint16_t manufacturerID = readRegister(HDC1080_MANUFACTURER_ID);
  if (manufacturerID != 0x5449) {
    return false;
  }

  return true;
}

void HDC1080::writeRegister(uint8_t reg, uint16_t value) {
  Wire.beginTransmission(HDC1080_ADDRESS);
  Wire.write(reg);
  Wire.write(value >> 8);
  Wire.write(value & 0xFF);
  Wire.endTransmission();
}

uint16_t HDC1080::readRegister(uint8_t reg) {
  Wire.beginTransmission(HDC1080_ADDRESS);
  Wire.write(reg);
  Wire.endTransmission();

  delay(10);

  Wire.requestFrom(HDC1080_ADDRESS, 2);
  if (Wire.available() >= 2) {
    uint8_t msb = Wire.read();
    uint8_t lsb = Wire.read();
    return (msb << 8) | lsb;
  }
  return 0;
}

void HDC1080::triggerMeasurement() {
  Wire.beginTransmission(HDC1080_ADDRESS);
  Wire.write(HDC1080_TEMP_REG);
  Wire.endTransmission();
}

float HDC1080::readTemperature() {
  triggerMeasurement();
  delay(15); // 측정 대기 시간

  Wire.requestFrom(HDC1080_ADDRESS, 2);
  if (Wire.available() >= 2) {
    uint8_t msb = Wire.read();
    uint8_t lsb = Wire.read();
    uint16_t rawTemp = (msb << 8) | lsb;

    // 온도 변환 공식: (rawTemp / 2^16) * 165 - 40
    return (rawTemp / 65536.0) * 165.0 - 40.0;
  }
  return 0.0;
}

float HDC1080::readHumidity() {
  triggerMeasurement();
  delay(15); // 온도 측정 대기

  Wire.requestFrom(HDC1080_ADDRESS, 4);
  if (Wire.available() >= 4) {
    // 온도 데이터 건너뛰기
    Wire.read();
    Wire.read();

    // 습도 데이터 읽기
    uint8_t msb = Wire.read();
    uint8_t lsb = Wire.read();
    uint16_t rawHumidity = (msb << 8) | lsb;

    // 습도 변환 공식: (rawHumidity / 2^16) * 100
    return (rawHumidity / 65536.0) * 100.0;
  }
  return 0.0;
}

void HDC1080::readTempHumidity(float &temp, float &humidity) {
  triggerMeasurement();
  delay(15); // 측정 대기 시간

  Wire.requestFrom(HDC1080_ADDRESS, 4);
  if (Wire.available() >= 4) {
    // 온도 데이터
    uint8_t tempMsb = Wire.read();
    uint8_t tempLsb = Wire.read();
    uint16_t rawTemp = (tempMsb << 8) | tempLsb;

    // 습도 데이터
    uint8_t humMsb = Wire.read();
    uint8_t humLsb = Wire.read();
    uint16_t rawHumidity = (humMsb << 8) | humLsb;

    temp = (rawTemp / 65536.0) * 165.0 - 40.0;
    humidity = (rawHumidity / 65536.0) * 100.0;
  } else {
    temp = 0.0;
    humidity = 0.0;
  }
}
