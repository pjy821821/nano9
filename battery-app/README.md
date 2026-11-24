# 배터리 모니터 (battery-app)

폰 배터리의 충전 세션을 실시간으로 기록하고, 충전기별로 비교해 보여주는 안드로이드 앱.
기획과 조사는 `docs/competitive-analysis.md`, 화면 명세는 `docs/wireframes.md`.

- 대상: Android 14 (API 34) 이상, targetSdk 36
- 스택: Kotlin 2.2, Jetpack Compose + Material 3(다이내믹 컬러), Room, DataStore, 포그라운드 서비스(specialUse), Glance 위젯
- 패키지: `kr.techbridge.battery`
- 광고 없음. Pro는 일회성 결제(결제 연동은 미구현).

## 모듈

| 모듈 | 내용 | 빌드 조건 |
|---|---|---|
| `core/` | 순수 Kotlin(JVM). 전류 단위·부호 보정(`CurrentCalibrator`), 세션 분할과 적분(`SessionTracker`), 용량·건강도 추정(`HealthEstimator`), 전력 구간(`PowerBand`). 안드로이드 의존성 없음. | JDK 17+ 어디서나 |
| `app/` | 안드로이드 앱. 데이터 계층(BatteryManager 읽기, Room, DataStore), 서비스, 알림, 위젯, Compose UI. | Android SDK 필요 |

`settings.gradle.kts`는 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 또는 `local.properties`의 `sdk.dir`가 있을 때만 `:app`을 포함한다.
SDK 없는 환경(CI, 헤드리스)에서는 `:core`만 빌드·테스트된다. 강제로 포함하려면 `-PwithApp`.

## 빌드

```bash
# core 테스트 (SDK 불필요)
./gradlew :core:test

# 앱 디버그 빌드 (Android SDK 필요, local.properties에 sdk.dir 설정)
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Android Studio에서는 `battery-app/` 폴더를 열면 된다.

### CI (GitHub Actions)

`.github/workflows/battery-app-build.yml`이 `battery-app/**` 변경을 푸시할 때마다 JDK 17 + 러너의 Android SDK로
`:core:test`와 `:app:assembleDebug`를 돌린다. 실행 페이지의 Artifacts에서 `battery-app-debug-apk`(디버그 APK)와
`battery-app-test-reports`(테스트 리포트, Room 스키마 JSON)를 내려받을 수 있다. 로컬에 SDK가 없어도 이 경로로 빌드 확인이 된다.

## 구조 (app)

```
kr.techbridge.battery
├── BatteryApp               Application. AppContainer 생성, 알림 채널
├── di/AppContainer          수동 DI. DB, 설정, BatteryReader, Repository, LiveBatteryState
├── data/battery/BatteryReader   BatteryManager → RawSample, 기기 정보(사이클 수, 건강 등급, 완충 예상)
├── data/db/                 Room: sessions, samples, chargers
├── data/repo/SessionRepository  세션 시작·갱신·종료(종료 시 샘플 10초 간격으로 다운샘플링)
├── data/settings/SettingsStore  DataStore: 알람, 테마, 보정값, 설계 용량
├── service/MonitorService   포그라운드 서비스(specialUse). POWER_CONNECTED/DISCONNECTED 런타임 등록
├── service/SessionRecorder  샘플링 루프(꽂힘 1초, 아니면 30초) → 보정 → SessionTracker → DB/LiveState/알림/위젯
├── service/BootReceiver     부팅 후 서비스 재시작
├── notify/Notifications     상시 알림, 충전 한계·온도 경고
├── widget/BatteryWidget     Glance 위젯 (2×2, 4×2)
└── ui/                      MainActivity, 테마, 내비게이션, 5개 화면, 공용 컴포넌트(BatteryRing, LineChart)
```

데이터 흐름: `BatteryReader.read()` → `Calibration.normalize()` → `SessionTracker.onSample()` → `SessionRepository` + `LiveBatteryState` → Compose 화면 / 위젯 / 알림.

## 설계 메모

- **전류 보정**: 규격은 µA·충전 +지만 일부 삼성은 mA, 일부 샤오미는 부호 반전. `CurrentCalibrator`가 충전 샘플 5개 이상 모이면 자동 추론해 DataStore에 저장한다. 설정의 재보정은 저장값을 지워 다시 추론시킨다.
- **배터리 측 전력**: 표시하는 W는 전압 × 전류로, 어댑터 표기보다 10~30% 낮다. 홈 화면에 항상 그 설명을 붙인다.
- **세션**: 꽂힌 샘플에서 시작, 뺀 샘플에서 종료. 꽂힌 상태에서 5분 넘게 샘플이 끊기면 서비스 사망으로 보고 미완료 세션으로 닫고 새로 시작.
- **건강도**: 잔량 변화 60% 이상인 완료 세션 3개 이상이 있을 때만 중앙값과 사분위 범위로 표시. OS가 사이클 수·건강 등급을 주면 나란히 표시.
- **포그라운드 서비스**: Android 14는 타입 선언이 필수인데 배터리 모니터용 타입이 없어 `specialUse`. 매니페스트 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`에 용도를 적었고, Play 콘솔 정책 > 앱 콘텐츠에도 같은 내용을 제출해야 한다.
- **백그라운드 시작 제약**: `ACTION_POWER_CONNECTED`는 매니페스트 등록이 안 된다. 서비스를 START_STICKY로 유지하고, 앱 실행과 부팅 시 시작한다. 서비스가 죽은 뒤 자동 복구(WorkManager 등)는 다음 단계.

## 현재 상태와 다음 단계

- [x] core 모듈: 구현 + 단위 테스트 22개 통과
- [x] app 모듈: 전체 골격 작성. 2026-11-24 GitHub Actions에서 첫 컴파일 성공(`assembleDebug`, 경고 0). **실기기에서는 아직 실행하지 않았다.**
- [ ] 실기기(삼성, 픽셀)에서 전류 단위·부호 확인, 보정표 1판
- [ ] ViewModel 도입(현재 화면이 Repository/StateFlow를 직접 구독)
- [ ] 서비스 사망 복구(WorkManager 충전 제약 작업), 배터리 최적화 제외 안내
- [ ] 세션 CSV 내보내기, Pro 결제(Play Billing)
- [ ] 2차: BLE 광고 기반 테크브릿지 보조배터리 자동 인식
