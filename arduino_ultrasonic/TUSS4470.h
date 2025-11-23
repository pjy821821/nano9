#ifndef TUSS4470_H
#define TUSS4470_H

#include <Arduino.h>
#include <SPI.h>

// TUSS4470 레지스터 주소
#define TUSS4470_DEV_STAT0 0x00
#define TUSS4470_DEV_STAT1 0x01
#define TUSS4470_DIG_CTRL 0x02
#define TUSS4470_FREQ_CTRL 0x03
#define TUSS4470_INIT_CTRL 0x04
#define TUSS4470_OUT_CTRL 0x05
#define TUSS4470_THR_CTRL0 0x06
#define TUSS4470_THR_CTRL1 0x07
#define TUSS4470_THR_CTRL2 0x08
#define TUSS4470_THR_CTRL3 0x09
#define TUSS4470_REC_LENGTH 0x0A
#define TUSS4470_NUM_AVG 0x0B
#define TUSS4470_TEMP_TRIM0 0x0C
#define TUSS4470_TEMP_TRIM1 0x0D
#define TUSS4470_TOF_CTRL 0x0E

// 핀 정의 (사용자의 실드에 맞게 수정 필요)
#define TUSS4470_CS_PIN 10
#define TUSS4470_TRIG_PIN 7
#define TUSS4470_READY_PIN 6

class TUSS4470 {
  public:
    TUSS4470(uint8_t csPin = TUSS4470_CS_PIN,
             uint8_t trigPin = TUSS4470_TRIG_PIN,
             uint8_t readyPin = TUSS4470_READY_PIN);

    bool begin();
    void trigger();
    bool isReady();
    uint32_t getTimeOfFlight(); // 마이크로초 단위
    float getDistance(float speedOfSound); // cm 단위

    void writeRegister(uint8_t reg, uint8_t value);
    uint8_t readRegister(uint8_t reg);

  private:
    uint8_t _csPin;
    uint8_t _trigPin;
    uint8_t _readyPin;

    void setupRegisters();
};

#endif
