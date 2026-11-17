#!/bin/bash

# Arduino Uno R4 펌웨어 업로드 스크립트
# 사용법: ./upload_firmware.sh [포트] [펌웨어파일]

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================"
echo "Arduino Uno R4 Firmware Upload Tool"
echo "========================================"
echo ""

# Arduino CLI 설치 확인
if ! command -v arduino-cli &> /dev/null; then
    echo -e "${RED}ERROR: arduino-cli가 설치되어 있지 않습니다.${NC}"
    echo ""
    echo "설치 방법:"
    echo "  macOS: brew install arduino-cli"
    echo "  Linux: curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | sh"
    echo ""
    exit 1
fi

# 포트 자동 감지 또는 사용자 입력
if [ -z "$1" ]; then
    echo "사용 가능한 포트 검색 중..."
    arduino-cli board list
    echo ""
    read -p "Arduino가 연결된 포트를 입력하세요 (예: /dev/ttyACM0 또는 /dev/cu.usbmodem): " PORT
else
    PORT=$1
fi

# 펌웨어 파일 확인
if [ -z "$2" ]; then
    # 기본 펌웨어 파일 찾기
    if [ -f "arduino_ultrasonic_uno_r4.hex" ]; then
        FIRMWARE="arduino_ultrasonic_uno_r4.hex"
    elif [ -f "../build/arduino_ultrasonic_uno_r4.hex" ]; then
        FIRMWARE="../build/arduino_ultrasonic_uno_r4.hex"
    else
        read -p "펌웨어 파일 경로를 입력하세요: " FIRMWARE
    fi
else
    FIRMWARE=$2
fi

# 파일 존재 확인
if [ ! -f "$FIRMWARE" ]; then
    echo -e "${RED}ERROR: 펌웨어 파일을 찾을 수 없습니다: $FIRMWARE${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}펌웨어 업로드 시작...${NC}"
echo "  포트: $PORT"
echo "  파일: $FIRMWARE"
echo ""

# Arduino Uno R4 코어 설치 확인
if ! arduino-cli core list | grep -q "arduino:renesas_uno"; then
    echo -e "${YELLOW}Arduino Uno R4 코어 설치 중...${NC}"
    arduino-cli core update-index
    arduino-cli core install arduino:renesas_uno
fi

# 펌웨어 업로드
echo -e "${GREEN}업로드 중...${NC}"
arduino-cli upload -p $PORT --fqbn arduino:renesas_uno:minima --input-file $FIRMWARE

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ 업로드 완료!${NC}"
    echo ""
    echo "시리얼 모니터를 열려면 다음 명령을 실행하세요:"
    echo "  arduino-cli monitor -p $PORT -c baudrate=115200"
    echo ""
    echo "또는:"
    echo "  screen $PORT 115200"
else
    echo ""
    echo -e "${RED}✗ 업로드 실패${NC}"
    echo ""
    echo "문제 해결:"
    echo "  1. Arduino가 올바른 포트에 연결되어 있는지 확인"
    echo "  2. 다른 프로그램(Arduino IDE, 시리얼 모니터 등)이 포트를 사용 중인지 확인"
    echo "  3. Arduino를 재부팅하고 다시 시도"
    exit 1
fi
