# Block Drop

세로 화면 싱글 플레이 블록 퍼즐 게임 프로토타입입니다. Phaser 3로 만들었고 빌드 과정 없이 정적 파일로 실행됩니다.

장르 선정 근거와 수익 목표 계산은 [docs/market-research.md](docs/market-research.md)에 있습니다.

## 실행

```bash
npm start        # http://localhost:8080 에서 실행
npm test         # 보드 로직 단위 테스트
```

태블릿에서 바로 플레이하려면 GitHub 저장소 Settings > Pages에서 이 브랜치의 루트(/)를 배포 소스로 지정하면 됩니다.

## 조작

- 아래 3개 조각 중 하나를 끌어 보드에 놓습니다.
- 가로 또는 세로 한 줄이 가득 차면 지워집니다. 동시에 2줄 이상 지우면 콤보 보너스.
- 놓을 자리가 없으면 게임오버. 광고 보고 이어하기(1회) 또는 다시 시작.

## 구조

| 경로 | 역할 |
|---|---|
| `index.html` | 진입점. 세로 고정, 확대 방지 |
| `src/config.js` | 보드 크기, 셀 크기, 색상, 점수 규칙 |
| `src/shapes.js` | 조각 모양 27종 |
| `src/board.js` | 순수 게임 로직 (렌더링과 분리, 테스트 대상) |
| `src/ads.js` | 광고 추상화. 현재는 스텁, 앱 포장 시 교체 |
| `src/scenes/GameScene.js` | 렌더링, 드래그 입력, 게임오버 UI |
| `vendor/phaser.min.js` | Phaser 3.87.0 (오프라인 실행용) |
| `tests/` | Node 내장 테스트 러너 사용 |

## 다음 단계

1. 효과음, 햅틱, 되돌리기 1회, 튜토리얼
2. Capacitor로 안드로이드 APK 빌드, AdMob 연동
3. Google Play 내부 테스트 후 한국과 영어권 동시 출시
