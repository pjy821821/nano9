#ifndef TUSS4470_H
#define TUSS4470_H

#include <Arduino.h>
#include <SPI.h>

// TUSS4470 레지스터 주소 (데이터시트 기준)
#define TUSS4470_REG_DEV_STAT0      0x00
#define TUSS4470_REG_DEV_STAT1      0x01
#define TUSS4470_REG_FREQ_DIAG      0x02
#define TUSS4470_REG_DIS_FREQ       0x03
#define TUSS4470_REG_DECPL_TEMP     0x04
#define TUSS4470_REG_DEV_CTRL0      0x05
#define TUSS4470_REG_DEV_CTRL1      0x06
#define TUSS4470_REG_DEV_CTRL2      0x07
#define TUSS4470_REG_DEV_CTRL3      0x08
#define TUSS4470_REG_DEV_CTRL4      0x09
#define TUSS4470_REG_TOF_1          0x0A
#define TUSS4470_REG_TOF_2          0x0B
#define TUSS4470_REG_TOF_3          0x0C
#define TUSS4470_REG_ERROR_FLAGS    0x0D
#define TUSS4470_REG_TEMP_TRIM      0x0E
#define TUSS4470_REG_NUM_AVG        0x0F
#define TUSS4470_REG_REC_LENGTH     0x10

// SPI 통신 설정
#define TUSS4470_SPI_SPEED      1000000  // 1MHz
#define TUSS4470_SPI_MODE       SPI_MODE0
#define TUSS4470_SPI_BIT_ORDER  MSBFIRST

// 핀 정의 (PCB 회로도 기준 - 250903JA0)
// SPI 핀은 Arduino Uno R4 하드웨어 SPI 사용
// MOSI -> D11, MISO -> D12, SCLK -> D13
#define TUSS4470_NCS_PIN        SS    // D10 (칩 선택)
#define TUSS4470_IO1_PIN        A1    // IO1 핀 (회로도 기준)
#define TUSS4470_IO2_PIN        A2    // IO2 핀 (회로도 기준)
#define TUSS4470_VOUT_PIN       A0    // VOUT 아날로그 출력

// 트랜스듀서 설정 (V40 AW14B - 40kHz)
#define TRANSDUCER_FREQUENCY    40000  // 40kHz

class TUSS4470 {
  public:
    TUSS4470();

    bool begin();
    void startMeasurement();
    bool isMeasurementReady();
    uint32_t getTimeOfFlight();        // 나노초 단위
    float getDistance(float speedOfSound); // cm 단위
    uint16_t getVoutRaw();             // VOUT 핀의 ADC 값
    float getVoutVoltage();            // VOUT 전압 (V)

    void writeRegister(uint8_t reg, uint8_t value);
    uint8_t readRegister(uint8_t reg);
    void writeRegister16(uint8_t reg, uint16_t value);
    uint16_t readRegister16(uint8_t reg);

    void setNumAverages(uint8_t num);   // 평균 횟수 설정
    void setRecordLength(uint8_t len);  // 레코드 길이 설정
    uint8_t getErrorFlags();            // 에러 플래그 읽기

  private:
    void setupRegisters();
    void reset();
    bool checkDeviceID();
};

#endif
