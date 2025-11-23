#ifndef HDC1080_H
#define HDC1080_H

#include <Wire.h>

#define HDC1080_ADDRESS 0x40
#define HDC1080_TEMP_REG 0x00
#define HDC1080_HUMIDITY_REG 0x01
#define HDC1080_CONFIG_REG 0x02
#define HDC1080_SERIAL_ID_1 0xFB
#define HDC1080_SERIAL_ID_2 0xFC
#define HDC1080_SERIAL_ID_3 0xFD
#define HDC1080_MANUFACTURER_ID 0xFE
#define HDC1080_DEVICE_ID 0xFF

class HDC1080 {
  public:
    HDC1080();
    bool begin();
    float readTemperature();
    float readHumidity();
    void readTempHumidity(float &temp, float &humidity);

  private:
    void writeRegister(uint8_t reg, uint16_t value);
    uint16_t readRegister(uint8_t reg);
    void triggerMeasurement();
};

#endif
