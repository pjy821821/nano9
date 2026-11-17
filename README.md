# nano9

주식회사 테크브릿지의 프로젝트 모음 저장소입니다. 프로젝트마다 최상위 폴더를 하나씩 둡니다.

| 폴더 | 내용 |
|---|---|
| [`jogakbo/`](jogakbo/) | 조각보 (Jogakbo): 한국 조각보 테마의 세로 화면 블록 퍼즐 (Phaser 3). 실행과 테스트 방법은 폴더 안 README 참고. |
| [`arduino_ultrasonic/`](arduino_ultrasonic/) | Arduino Uno R4 초음파 거리 측정 펌웨어. TUSS4470 드라이버와 HDC1080 온습도 보정, PCB 250903JA0 기준. GitHub Actions(`.github/workflows/arduino-build.yml`)로 자동 빌드. 개요는 폴더 안 OVERVIEW.md, 빠른 시작은 QUICK_START.md 참고. |
| [`battery-app/`](battery-app/) | 폰 배터리 모니터 앱 (안드로이드 14 이상, Kotlin/Compose). 1차는 충전 세션 실시간 기록과 충전기 비교, 2차는 테크브릿지 보조배터리 자동 인식. `core`(순수 Kotlin, 테스트 포함)와 `app`(안드로이드) 두 모듈. 기획은 `docs/`, 빌드는 폴더 안 README 참고. |
| `helloworld.js` | 저장소 초기 연습용 스크립트. |

새 프로젝트를 추가할 때는 같은 방식으로 폴더를 만들고 이 표에 한 줄을 추가합니다.
