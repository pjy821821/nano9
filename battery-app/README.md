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

로컬 환경 메모(회사 PC, 2026-11-24): Android Studio는 zip 배포판을 `%LOCALAPPDATA%\Programs\android-studio`에 풀어 설치했고
(winget 무음 설치는 관리자 권한 단계에서 조용히 실패함), SDK는 `%LOCALAPPDATA%\Android\Sdk`에 명령줄 도구로
platform-tools, platforms/android-36, build-tools/36.0.0만 설치했다. 시스템 JDK가 8이라 `~/.gradle/gradle.properties`에
`org.gradle.java.home`을 Android Studio의 `jbr`(Java 25)로 지정해 두었다. 새 PC에서는 Android Studio 설치 후 `local.properties`의
`sdk.dir`만 맞추면 된다.

Gradle은 9.5.1을 쓴다. 8.14는 Java 25에서 돌지 않고, 9.6 이상은 AGP 8.x가 쓰는 내부 API를 제거해 AGP 8.13과 맞지 않는다
(AGP 9.x로 올리면 9.6+ 가능). 프로젝트 경로에 한글이 있어 두 가지 우회가 필요하다.
- `gradle.properties`의 `android.overridePathCheck=true`: AGP의 비ASCII 경로 검사 해제 (저장소에 포함).
- `~/.gradle/gradle.properties`의 `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=COMPAT`: 이 PC 전용. 없으면 Gradle이 테스트
  워커 클래스패스를 UTF-8 argfile로 쓰고 Windows 런처가 CP949로 읽어 `ClassNotFoundException`이 난다. ASCII 경로로 옮기면 불필요.

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
├── ui/vm/AppViewModelFactory  모든 ViewModel을 AppContainer로 만드는 viewModelFactory
└── ui/                      MainActivity(+MainViewModel: 테마), 내비게이션, 화면 5개(각각 Screen + ViewModel), 공용 컴포넌트(BatteryRing, LineChart)
```

데이터 흐름: `BatteryReader.read()` → `Calibration.normalize()` → `SessionTracker.onSample()` → `SessionRepository` + `LiveBatteryState` → ViewModel(`StateFlow<UiState>`) → Compose 화면 / 위젯 / 알림.

화면 계층 규칙: Composable은 `vm.uiState`만 구독하고 사용자 동작은 `vm.xxx()`로 넘긴다. Repository·DataStore·BatteryReader에 직접 닿지 않는다. ViewModel은 `AppNav`의 각 목적지에서 `viewModel(factory = …)`로 만들어 그 백스택 항목에 묶이고, 세션 상세의 id는 `SavedStateHandle`에서 읽는다. 서비스 시작·중지 같은 부수 효과는 ViewModel이 `AppContainer.appContext`로 처리한다.

## 설계 메모

- **전류 보정**: 규격은 µA·충전 +지만 일부 삼성은 mA, 일부 샤오미는 부호 반전. `CurrentCalibrator`가 샘플 5개 이상 모이면 자동 추론해 DataStore에 저장한다. 설정의 재보정은 저장값을 지워 다시 추론시킨다. 단위는 충전 전류 중앙값 크기로 정한다. **부호는 "충전 중" 상태값으로 정하면 안 된다.** PC USB(≤0.5 A)처럼 공급이 소비보다 작으면 상태는 충전 중이어도 배터리 순전류는 실제로 음수라서, 정상 기기를 반전으로 오판한다(2026-11-25 Z Flip6에서 실제로 발생). 그래서 ① 충전 카운터(`BATTERY_PROPERTY_CHARGE_COUNTER`, µAh) 증감과 원전류 적분의 부호를 비교하고, ② 카운터가 없으면 비연결 샘플(반드시 음수여야 함)을 보고, ③ 그것도 없으면 1 A 이상의 강한 충전에서만 과반 부호를 믿는다. 약한 충전만 있으면 판정을 보류하고 규격값을 쓴다.
- **충전기 자동 인식**(2026-11-25): USB 호스트(PC)의 이름은 어떤 경로로도 폰에 오지 않는다(USB 규격상 호스트는 장치에 이름을 보내지 않고, ADB는 RSA 키 지문만, MTP도 마찬가지). 그래서 `ChargerFingerprint`(EXTRA_PLUGGED 연결 종류 + 배터리 브로드캐스트의 숨은 확장 `max_charging_current`/`max_charging_voltage`)와 측정 전력으로 구분한다. 세션 시작 때 지문을 세션에 저장하고, 사용자가 이름을 붙이면 충전기가 그 세션의 지문과 평균 전력을 학습한다(`SessionRepository.learnFingerprint`). 다음에 꽂히면 `ChargerMatcher`가 ① 연결 종류·협상값이 정확히 같은 충전기가 하나뿐이면 즉시, ② 아니면 1분 뒤 초반 전력 중앙값이 ±20% 안에 드는 충전기가 하나뿐이면 이름을 붙인다. 같은 종류·같은 전력인 충전기 둘(PC 포트 두 개)은 구분하지 못하고 이름 없는 충전기로 둔다. DB v2 마이그레이션으로 컬럼 추가. 예전의 "마지막에 쓴 충전기를 무조건 붙이는" 동작(`lastChargerId`)은 뺐다.
- **공급 부족 표시**: 꽂혀 있는데 순전류가 음수면 알림·홈·위젯에 "충전 중"이 아니라 "공급 부족 · 배터리 소모 중"으로 표시한다. 세션 적분은 원래부터 양의 전류만 더하므로 영향이 없다.
- **테마**: 시스템/라이트/다크는 Material 3 기본(다이내믹 컬러 선택). AMOLED는 다크 색상표에서 표면 계열 전부를 검정 계열로 바꾼다(배경·탭바 순검정, 카드 #111, 칩 #1A1A1A). 배경만 바꾸면 회색 덩어리가 얼룩진다. 상태바·내비게이션바 아이콘 색은 `enableEdgeToEdge` 기본값이 시스템 다크 모드만 보므로 `BatteryTheme`이 `WindowInsetsControllerCompat`으로 앱 테마에 맞춰 다시 정한다(2026-11-25, 시스템 라이트 + 앱 AMOLED에서 아이콘이 안 보이던 문제).
- **배터리 측 전력**: 표시하는 W는 전압 × 전류로, 어댑터 표기보다 10~30% 낮다. 홈 화면에 항상 그 설명을 붙인다.
- **세션**: 꽂힌 샘플에서 시작, 뺀 샘플에서 종료. 꽂힌 상태에서 5분 넘게 샘플이 끊기면 서비스 사망으로 보고 미완료 세션으로 닫고 새로 시작.
- **샘플 보관**: 세션이 끝나면 1초 샘플을 10초 간격으로 줄인다(`SampleThinning`, 첫·마지막 샘플 유지). 2026-11-25 실기기에서 `Long.MIN_VALUE` 오버플로로 마지막 1개만 남는 버그를 찾아 순수 함수로 빼고 테스트를 붙였다. 그 전에 끝난 세션은 샘플이 1개뿐이다.
- **건강도**: 잔량 변화 60% 이상인 완료 세션 3개 이상이 있을 때만 중앙값과 사분위 범위로 표시. OS가 사이클 수·건강 등급을 주면 나란히 표시.
- **포그라운드 서비스**: Android 14는 타입 선언이 필수인데 배터리 모니터용 타입이 없어 `specialUse`. 매니페스트 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`에 용도를 적었고, Play 콘솔 정책 > 앱 콘텐츠에도 같은 내용을 제출해야 한다.
- **백그라운드 시작 제약**: `ACTION_POWER_CONNECTED`는 매니페스트 등록이 안 된다. 서비스를 START_STICKY로 유지하고, 앱 실행과 부팅 시 시작한다.
- **서비스 사망 복구**(2026-11-25): 세 겹이다. ① 서비스가 다시 뜨면 `SessionRecorder.recoverOrphans()`가 끝맺지 못한 세션(completed = 0)을 찾아, 아직 꽂혀 있고 마지막 기록이 5분 이내면 `SessionTracker.resume()`으로 이어받아 세션 하나로 유지하고, 아니면 마지막 샘플 시각으로 닫는다(완료 아님 = "중단"). ② `MonitorWatchdogWorker`(WorkManager, 15분 주기, 충전 중 제약)가 서비스 생존을 확인하고 죽어 있으면 시작을 시도한다. Android 12+는 백그라운드 FGS 시작을 막지만 **배터리 최적화에서 제외된 앱은 허용**된다. 막히면 "탭하여 다시 시작" 알림을 올린다(알림 탭의 PendingIntent는 예외). ③ 설정 화면에 배터리 최적화 제외 여부와 시스템 설정 버튼(`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, 권한 불필요)을 둔다. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한은 Play 정책 위험이 있어 선언하지 않는다. 사용자가 앱을 강제 종료한 경우는 안드로이드가 모든 수단을 막으므로 복구할 수 없다.

### 실기기 보정표

| 기기 | OS | CURRENT_NOW 단위 | 충전 부호 | 충전 카운터 | 사이클 수(EXTRA_CYCLE_COUNT) | 협상 전류·전압(max_charging_*) | 비고 |
|---|---|---|---|---|---|---|---|
| Galaxy Z Flip6 (SM-F741N) | Android 17 / One UI 9.0 | µA | + (규격) | 있음, 약 3,580 µAh 눈금 | 0 (미제공) | 미제공(0) → 충전기 인식은 전력 매칭만 | PC USB·화면 켬이면 순전류 -200~-400 mA. `/sys/class/power_supply`는 adb shell에서 읽기 불가 |

뺐다 꽂기는 케이블 없이 `adb shell dumpsys battery unplug` → `adb shell dumpsys battery reset`으로 흉내 낼 수 있다(세션 종료·시작 테스트에 씀). 확인 방법: `adb shell dumpsys battery`의 삼성 확장 로그(`current_avg`, `cc`)와 앱 알림의 W 표시를 비교. 앱 저장값은 `adb exec-out run-as kr.techbridge.battery cat files/datastore/settings.preferences_pb | od -c`로 읽을 수 있다(`cal_scale`, `cal_flip`).

## 현재 상태와 다음 단계

- [x] core 모듈: 구현 + 단위 테스트 49개 통과(보정기 부호 판정 8개, 샘플 다운샘플 5개, 세션 이어받기 4개, 충전기 매칭 10개 추가)
- [x] app 모듈: 전체 골격 작성. 2026-11-24 GitHub Actions에서 첫 컴파일 성공(`assembleDebug`, 경고 0), 같은 날 회사 PC 로컬(Gradle 9.5.1 + JBR 25)에서도 테스트·APK 빌드 성공.
- [x] 실기기 첫 실행(2026-11-25, Galaxy Z Flip6): 크래시 없음, 포그라운드 서비스(`specialUse`)·상시 알림·충전 한계 알림 동작. 전류 부호 오판을 발견해 보정기를 고쳤다(설계 메모와 실기기 보정표 참고).
- [ ] 실기기 추가 확인: 벽 충전기(1 A 이상)로 충전 세션 한 번 끝까지 기록, 픽셀 1대, 보조배터리 1대
- [x] ViewModel 도입(2026-11-25): 화면 5개 + Main. 충전기 이름 변경이 홈·상세에 즉시 반영되고, 건강도가 세션 변화에 따라 다시 계산되도록 Flow로 묶었다.
- [x] 서비스 사망 복구(2026-11-25): 고아 세션 이어받기·닫기, WorkManager 감시 작업과 복구 알림, 배터리 최적화 제외 안내(설계 메모 참고)
- [x] 충전기 자동 인식(2026-11-25): 지문 + 전력 매칭, 이름 붙일 때 학습(설계 메모 참고)
- [ ] 샘플링 주기 보정: 루프가 `sampleNow()` 뒤 1초를 쉬므로 실제 주기는 약 1.6초다(DB 쓰기·알림 갱신 오버헤드, Z Flip6 실측 70초에 42샘플). 다음 틱 시각까지 기다리는 방식으로 바꾸면 1초가 된다. 전력 매칭 60샘플이 약 100초 걸리는 것도 이 때문.
- [ ] 세션 CSV 내보내기, Pro 결제(Play Billing)
- [ ] 2차: BLE 광고 기반 테크브릿지 보조배터리 자동 인식
