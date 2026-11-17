# 배터리 모니터 앱: 경쟁 앱 분석과 1차 방향 검토

작성일: 2026-11-17
대상: 안드로이드 14 (API 34) 이상, 폰 자체 배터리 모니터 (1차). 테크브릿지 보조배터리 자동 인식은 2차.

## 0. 요약

- 이 시장은 **AccuBattery**(건강도·용량 측정), **Battery Guru**(충전 습관·알림·위젯), **GSam**(소모 원인 분석), **Charge Meter / Ampere 계열**(충전 속도 측정 단일 기능)의 네 갈래로 나뉜다.
- 네 갈래 모두 공통 약점이 있다. 화면이 수치 나열 위주로 투박하고, 배터리 측 전류를 어댑터 전력처럼 보여 사용자가 혼동하며, 배터리 건강도를 추정치인데 확정치처럼 표시해 불신을 산다.
- 제조사 기본 기능이 좁혀 들어오고 있다. 삼성은 One UI 7부터 S25 계열에, 구글은 Android 16부터 Pixel 8a 이후에 건강도 %와 사이클 수를 설정 메뉴에 넣었다. **"건강도 %를 알려준다"는 가치는 더 이상 차별점이 되기 어렵다.**
- 따라서 1차 방향은 **"충전 세션을 실시간으로, 정확한 단위와 정직한 해석으로, 보기 좋게 보여주는 앱"** 으로 잡는다. 건강도는 보조 지표로 두고, 충전기·케이블 비교와 충전 세션 기록을 핵심으로 삼는다. 이 방향은 2차의 보조배터리 인식(연결된 충전기가 누구인지 알고 그 성능을 기록)으로 자연스럽게 이어진다.

## 1. 안드로이드가 일반 앱에 허용하는 데이터 (설계 전제)

공식 `BatteryManager` 문서 기준. 권한 불필요.

| 항목 | API | 비고 |
|---|---|---|
| 잔량 %, 상태(충전/방전/완충), 연결 종류(USB/AC/무선/독), 전압(mV), 온도(0.1°C), 건강 등급, 기술(Li-ion 등) | 1 | `ACTION_BATTERY_CHANGED` 스티키 브로드캐스트 |
| 순간 전류, 평균 전류 (µA), 누적 충전량 (µAh), 잔여 에너지 (nWh), 잔량 % | 21 | `getIntProperty` / `getLongProperty` |
| 충전 상태 속성 | 26 | `BATTERY_PROPERTY_STATUS` |
| 완충까지 남은 시간 추정 | 28 | `computeChargeTimeRemaining()`; 데이터 부족이면 -1 |
| **충전 사이클 수** | **34** | `EXTRA_CYCLE_COUNT`. 제조사가 채워줄 때만 유효. 미지원 기기는 0 또는 누락 |
| **충전 정책 상태** | **34** | `EXTRA_CHARGING_STATUS` (적응형 충전, 장기 보관 등) |
| 배터리 건강도 % (설계 용량 대비) | 34 | `BATTERY_PROPERTY_STATE_OF_HEALTH`는 `BATTERY_STATS` 권한 필요. **일반 앱 불가** |
| 협상된 급속충전 프로토콜(PD/PPS/QC), USB 입력 전압 | 없음 | 공개 API 없음. 일부 기기의 sysfs는 접근 제한. **기능으로 약속 불가** |

기기별 편차 (실측으로 확인 필요):
- 전류 단위: 규격은 µA이고 충전이 +인데, 일부 삼성 기기는 mA로 보고하고 일부 샤오미 기기는 충전 중에도 음수를 준다. 앱은 첫 실행 시 자동 보정(절대값 크기로 단위 판별, 연결 상태로 부호 판별)을 해야 한다. 경쟁 앱들이 "기기마다 수치가 다르다"는 불만을 받는 지점이다.
- 사이클 수: Android 14 공개 API이지만 S25·Pixel 8a 이후처럼 제조사가 지원하는 기기에서만 의미 있는 값이 온다.

백그라운드 동작 제약 (Android 8 이후, 14에서 강화):
- `ACTION_POWER_CONNECTED`는 매니페스트 등록이 안 되는 암시적 브로드캐스트다. 예외 목록에도 없다. 프로세스가 살아 있을 때 `registerReceiver`로만 받는다.
- 따라서 "꽂으면 자동으로 기록 시작"은 **알림을 띄우는 포그라운드 서비스**가 필요하다. Android 14부터는 서비스 타입을 선언해야 하고, 배터리 모니터에 맞는 전용 타입이 없어 `specialUse`로 선언하고 Play 콘솔에 용도를 설명해야 한다. AccuBattery, Battery Guru 모두 같은 방식이다.
- 서비스가 죽으면 세션이 끊긴다. 경쟁 앱 리뷰에서 가장 많은 불만이 "백그라운드에서 멈춤", "완충 감지 실패"다. 재시작 전략(WorkManager 충전 제약 작업으로 보조)과 세션 복구 로직이 품질을 좌우한다.

## 2. 경쟁 앱 분석

### 2.1 AccuBattery (Digibites)

- 포지션: "과학적 측정"을 내세운 건강도·용량 앱. 평점 약 4.7, 평가 수 57만 건 이상으로 이 분야 1위.
- 핵심 기능: 충전 세션마다 mAh 측정 → 설계 용량 대비 건강도 추정, 80% 충전 알람(이 앱이 대중화시킨 습관), 방전 속도(화면 켬/꿈 분리), 앱별 소모, CPU 오버레이, 알림에 상세 수치.
- 수익: 광고 + 일회성 Pro(광고 제거, 다크/AMOLED 테마, 1일 이전 세션 열람, 알림 상세).
- 강점: 신뢰 브랜드, 단순한 4탭 구조(충전·방전·건강·기록), 꾸준한 업데이트(2.1.0에서 건강도 계산을 60% 이상 충전 세션으로 제한, 건강도 그래프 추가).
- 약점: 디자인이 2016년 Material 1 느낌으로 노후. 건강도가 기기 충전 컨트롤러 정확도에 좌우되어 삼성 내장 수치와 7%p 이상 차이 나는 사례가 보고됨. 백그라운드 멈춤·완충 미감지 불만 증가. 신규 사용자에게 "어떤 수치가 어댑터 전력인지" 설명이 없다.

### 2.2 Battery Guru (Paget96)

- 포지션: 충전 습관 관리 + 알림 + 위젯의 종합형. 평점 약 4.3, 평가 수 약 3만 건.
- 핵심 기능: 충전 속도 테스트(최대 도달 전력 기록, 충전기·케이블 비교), 온도·충전 한계 알람, 딥슬립 대비 깨어 있는 시간, 상태바 아이콘 커스터마이즈, 오버레이, 블루투스 기기 배터리 표시, 듀얼 배터리 기기 지원.
- 수익: 광고 + Pro(광고 제거, 긴 기록).
- 강점: 기능이 많고 커스터마이즈 폭이 넓다. 충전기 비교가 핵심에 가깝다는 점에서 우리 2차 방향과 가장 겹친다.
- 약점: 설정이 과다해 처음 쓰는 사람에겐 복잡. 1인 개발자라 지원이 Telegram 채널. 일부 글에 Play 스토어 제거 이력이 언급되나 확인되지 않음(확인 필요).

### 2.3 GSam Battery Monitor

- 포지션: 소모 원인 분석(웨이크락, 알람 횟수, 앱별 CPU). Pro 2.49달러 일회성. 평점 약 3.9.
- 강점: 전력 소모 원인 추적은 여전히 최고.
- 약점: Android 8 이후 `BATTERY_STATS` 권한이 막혀 핵심 데이터를 ADB로 열어야 한다. 일반 사용자 접근성이 낮고 UI가 오래됐다. **우리가 가지 않을 방향.**

### 2.4 Charge Meter (Heartinz) / Ampere (Braintrapp) / AmpereFlow·AccuCharge (Androxify)

- 포지션: "지금 몇 mA로 충전되나" 단일 기능. Charge Meter는 설치 약 470만, 평점 4.6, 평가 8만 건. Ampere는 이 장르의 원조로 광고 기반.
- 핵심 기능: 큰 숫자 하나 + 색상 게이지, 충전기·케이블 비교, 간단한 용량 측정, 온도. Androxify 계열은 AOD(상시 표시) 충전 애니메이션과 속도계형 게이지로 "예쁜 화면"을 판다.
- 강점: 한 화면에서 끝나는 단순함. 설치 수가 보여주듯 수요는 "복잡한 분석"보다 "지금 빨리 충전되는지"에 있다.
- 약점: Ampere 개발자 스스로 "절대값이 아닌 상대 비교용"이라 밝힘. 기록·세션 비교가 약하고 광고가 많다. 디자인은 화려하나 정보 설계가 없다.

### 2.5 제조사 기본 기능 (대체재)

- 삼성: One UI 7, S25 계열에서 설정 > 배터리 > 배터리 정보에 건강도 %, 사이클 수, 제조일, 최초 사용일. 지역·모델별 순차 적용, S24 이하 미확인. `*#9900#` 덤프 메뉴는 Auto Blocker에 막힐 수 있음.
- 구글: Android 16, Pixel 8a·9 이후에서 설정 > 배터리 > 배터리 건강에 추정 용량 %. Pixel 8 이하는 메뉴만 있고 팁만 표시. 사이클 수는 휴대전화 정보 > 배터리 정보.
- 시사점: 건강도 %는 앞으로 OS가 직접 보여준다. 서드파티 앱의 자리는 **실시간 전류·전력, 세션 기록, 충전기 비교, 알림·위젯, 보기 좋은 화면**이다.

### 2.6 비교표

| | AccuBattery | Battery Guru | GSam | Charge Meter류 | **우리 1차** |
|---|---|---|---|---|---|
| 실시간 전류·전력 | ○ | ○ | △ | ◎ (핵심) | ◎ (핵심, 단위 보정) |
| 충전 세션 기록·비교 | ○ | ○ | × | △ | ◎ (충전기별 비교) |
| 건강도 추정 | ◎ (핵심) | ○ | × | △ | ○ (보조, 추정치 명시) |
| 사이클 수 (API 34) | × | ○ | × | × | ○ |
| 소모 원인 분석 | △ | ○ | ◎ | × | × (범위 외) |
| 알람 (충전 한계, 온도) | ○ | ◎ | △ | △ | ○ |
| 위젯·AOD | △ | ◎ | ○ | ◎ | ○ (M3 위젯) |
| 디자인 | 노후 | 과밀 | 노후 | 화려·얕음 | **M3 Expressive, 다이내믹 컬러** |
| 수익 모델 | 광고+일회성 | 광고+Pro | 일회성 | 광고 | 광고 없음 + 일회성 Pro (아래) |

## 3. 우리 방향

### 3.1 한 문장

**"충전할 때마다 열어보고 싶은 배터리 계기판."** 꽂으면 바로 알림과 위젯에 전력이 뜨고, 앱을 열면 세션이 그래프로 쌓이고, 어떤 충전기가 내 폰을 가장 잘 채우는지 비교해 준다.

### 3.2 차별화 원칙

1. **정직한 단위.** 배터리 측 전력(V×I)과 어댑터 표기 전력을 구분해 표시하고, 전류는 기기별 자동 보정 후 보여준다. "이 수치는 배터리에 실제 들어가는 전력이며 어댑터 표기보다 10~30% 낮은 게 정상"을 첫 세션에 한 번 설명한다.
2. **세션 중심.** 꽂았다 뺄 때까지가 하나의 세션. 세션마다 시작·끝 %, 들어간 mAh, 평균·최대 전력, 온도 피크, 소요 시간, 사용한 충전기(사용자가 이름 붙임, 2차에서 자동 인식)를 기록한다.
3. **추정치는 추정치로.** 건강도는 "최근 N세션 기반 추정, ±5%"처럼 범위와 근거를 함께 보여준다. OS가 사이클 수나 건강도를 주는 기기에서는 그 값을 우선 표시한다.
4. **설정 없이도 완성.** 첫 실행에서 알림 권한 하나만 받고 바로 동작. 고급 설정은 한 화면에 모으되 기본값으로 충분하게.
5. **광고 없음.** 경쟁 앱의 공통 불만이 광고다. 무료 범위를 넉넉히 두고 Pro는 일회성 결제(긴 기록 보관, 세션 내보내기, 위젯 테마)로 한다. 보조배터리 사용자 접점이 목적이므로 광고 수익보다 평점과 설치가 중요하다.

### 3.3 1차 기능 범위

필수 (출시 조건):
- 홈: 큰 잔량 링 게이지, 상태(충전 중·방전 중·완충), 실시간 전력(W)·전류(mA)·전압(V)·온도, 완충까지 남은 시간. 충전 중엔 게이지가 Android 16 배터리 아이콘처럼 상태색(충전 초록, 절전 노랑, 부족 빨강)으로 바뀌고 잔잔한 펄스 애니메이션.
- 실시간 그래프: 현재 세션의 전력·전류·온도 60초/10분/전체 보기.
- 세션 기록: 세션 리스트와 상세, 충전기 이름 태그, 세션 간 비교(같은 충전기 평균).
- 포그라운드 서비스 + 상시 알림(전력·잔량·남은 시간), 꽂으면 자동 세션 시작, 뺄 때 종료.
- 홈 화면 위젯 1종(잔량·전력·상태, 다이내믹 컬러).
- 알람: 충전 한계(기본 80%, 변경 가능), 온도 경고.
- 기기 정보: 사이클 수(API 34, 지원 기기), 건강 등급, 기술, 설계 용량(기기 DB 또는 사용자 입력).
- 기기별 전류 단위·부호 자동 보정과 수동 재보정.

권장 (1차 후반):
- 방전 통계: 화면 켬/꿈 시간당 %와 mA, 하루 요약.
- 건강도 추정(60% 이상 충전 세션 기반, 범위 표시) + OS 제공 값 우선.
- 세션 CSV 내보내기(Pro).

제외 (1차):
- 앱별 소모 분석(권한 문제, GSam 영역), 절전 모드 전환 기능(Play 정책 리스크), 급속충전 프로토콜 표시(API 없음).

### 3.4 디자인 방향

- **Material 3 Expressive + 다이내믹 컬러.** 사용자의 월페이퍼 색을 따라가되 상태색(충전·경고·부족)은 고정 의미색으로. Pixel 배터리 위젯과 Android 16 배터리 아이콘이 참고 기준.
- **한 화면에 숫자 하나.** 홈은 잔량 링과 전력 W 하나가 주인공. 나머지는 작은 칩으로. Charge Meter류의 "큰 숫자" 매력을 가져오되 정보 설계를 더한다.
- **그래프는 읽히게.** 전력은 면 그래프, 온도는 선, 세션 경계는 세로 점선. 축 단위 명시. 다크 모드와 AMOLED 블랙 기본 제공.
- **보조배터리 2차를 위한 자리.** 홈 상단에 "연결된 충전기" 카드를 두고 1차에선 사용자가 이름을 붙인 충전기를 표시한다. 2차에서 이 카드가 테크브릿지 제품 사진과 잔량으로 바뀐다.
- 한국어 우선, 영어 동시 지원. 숫자 단위는 W, mA, V, °C로 통일.

### 3.5 기술 스택

- Kotlin, Jetpack Compose, Material 3. minSdk 34.
- 데이터: Room(세션·샘플), DataStore(설정). 샘플은 1초 간격 수집 후 세션 종료 시 10초 단위로 다운샘플링해 저장 용량 억제.
- 백그라운드: 포그라운드 서비스(`specialUse`, Play 콘솔에 "충전 세션 실시간 기록" 용도 기재) + WorkManager 충전 제약 작업으로 서비스 사망 시 복구.
- 그래프: Vico(Compose 네이티브) 또는 자체 Canvas. 위젯: Glance.
- 테스트: 전류 단위·부호 보정 로직과 세션 분할 로직은 단위 테스트, 서비스 생존은 실기기(삼성·픽셀 각 1대 이상) 테스트 필수.
- 패키지명: `kr.techbridge.battery` (조각보 `kr.techbridge.jogakbo`와 같은 규칙).

### 3.6 리스크

| 리스크 | 대응 |
|---|---|
| 제조사별 전류 단위·부호 편차 | 자동 보정 + 사용자 재보정 + 기기 모델별 보정표를 앱 업데이트로 배포 |
| 포그라운드 서비스 사망(삼성 절전) | 배터리 최적화 제외 안내 화면, WorkManager 보조, 세션 복구 |
| `specialUse` 타입 Play 심사 | 용도 설명을 구체적으로, 서비스 없이도 앱이 동작하게(수동 모드) |
| 건강도 불신 | 추정치 범위 표기, OS 값 우선, 측정 조건(60% 이상 충전) 명시 |
| 제조사 기본 기능과의 경쟁 | 건강도를 핵심에서 빼고 세션·비교·디자인에 집중 |

## 4. 다음 단계

1. 이 문서로 방향 확정.
2. 화면 와이어프레임 5장(홈, 그래프, 세션 목록, 세션 상세, 설정)과 위젯 1종.
3. 프로젝트 골격: Compose 앱, 포그라운드 서비스, BatteryManager 수집기, Room 스키마, 단위 보정 로직과 테스트.
4. 삼성·픽셀 실기기에서 전류 단위·부호 확인 후 보정표 1판.

## 참고 자료

- Android `BatteryManager` 공식 문서: https://developer.android.com/reference/android/os/BatteryManager
- 암시적 브로드캐스트 예외 목록: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- 포그라운드 서비스 타입(Android 14): https://developer.android.com/develop/background-work/services/fgs/service-types
- Android 16 Pixel 배터리 건강 페이지 범위: https://www.androidcentral.com/apps-software/android-os/google-wont-bring-android-16s-battery-health-page-to-older-pixels , https://9to5google.com/2025/05/06/pixel-battery-health-android-16/
- 삼성 S25 배터리 정보: https://www.sammobile.com/news/galaxy-s25-battery-health-charge-cycle-count/ , https://www.androidauthority.com/galaxy-s25-battery-health-data-3519722/
- 삼성 기기 전류 단위 이슈: https://forum.developer.samsung.com/t/charging-current-battery-property-current-now/13517
- 전류 부호 규격(AOSP): https://source.android.com/docs/core/power/device
- AccuBattery 시작 가이드: https://accubattery.zendesk.com/hc/en-us/articles/210224685-Getting-started-guide
- AccuBattery 변경 이력: https://apkmirror.com/apk/digibites/accubattery/accubattery-2-1-0-release
- Battery Guru 스토어 정보: https://appgoblin.info/apps/com.paget96.batteryguru
- Charge Meter 스토어 정보: https://appgoblin.info/apps/dev.km.android.chargemeter
- GSam Pro 가격: https://apppricinglab.com/app/google_play/com.gsamlabs.bbm.pro
- Ampere 측정 방식 설명: https://www.phonearena.com/news/Ampere-app-tells-you-the-juicing-quality-from-one-phone-charger-or-cable-to-the-next_id65403
- Android 16 배터리 아이콘·Material 3 Expressive: https://www.androidauthority.com/google-material-3-expressive-redesign-battery-icon-3554579
- 비교 기사: https://en.androidayuda.com/applications/recommended/Accubatery-or-Battery-Guru%3A-The-definitive-comparison-to-measure-and-care-for-your-battery./

주의: 스토어 평점·설치 수는 2025~2026년 서드파티 집계 사이트 기준이며 시점에 따라 다르다. 이 세션에서는 Google Play 페이지를 직접 열 수 없어 집계 사이트 수치를 썼다.
