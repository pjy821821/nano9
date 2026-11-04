# AdMob 연동 가이드 (조각보)

작성일: 2026-11-03
대상: Capacitor로 포장한 안드로이드 앱. iOS는 같은 코드에 Info.plist 설정만 더하면 됩니다.

## 0. 전체 흐름 한눈에

| 순서 | 어디서 | 할 일 | 소요 |
|---|---|---|---|
| 1 | AdMob 콘솔 | 계정 가입, 앱 추가, 광고 단위 3개 생성, 지급 정보 입력 | 30분 |
| 2 | 저장소 | Vite 빌드 추가, Capacitor 안드로이드 프로젝트 생성, AdMob 플러그인 설치 | 코드 작업 |
| 3 | 저장소 | `src/ads.js`를 플러그인 호출로 교체, 동의(UMP) 흐름 추가 | 코드 작업 |
| 4 | 안드로이드 설정 | 매니페스트에 앱 ID 등록 | 5분 |
| 5 | 기기 | 테스트 ID로 광고 확인, 테스트 기기 등록 | 1시간 |
| 6 | Play 콘솔 + 웹 | 개인정보처리방침, 데이터 안전, app-ads.txt, 스토어 연결 | 출시 직전 |
| 7 | AdMob 콘솔 | 실 광고 단위 ID로 교체, 앱 검토 통과 대기 | 출시 후 며칠 |

2단계와 3단계는 제가 코드로 진행할 수 있습니다. 1, 4(ID 값), 5(실기기), 6은 대표님 계정과 기기가 필요합니다.

## 0-1. 패키지 이름 정하기

안드로이드 앱은 Play 스토어 전체에서 유일한 ID가 필요하고, **출시 후에는 변경할 수 없습니다.** 바꾸면 다른 앱으로 취급되어 설치 수와 리뷰가 초기화됩니다. 그래서 회사 도메인을 거꾸로 쓰고 앱 이름을 붙이는 관례를 씁니다.

| 회사 도메인 | 패키지 이름 |
|---|---|
| techbridge.co.kr | kr.co.techbridge.jogakbo |
| techbridge.com | com.techbridge.jogakbo |
| 도메인 없음 | com.techbridge.jogakbo (겹치지 않으면 됨) |

규칙: 소문자와 숫자만, 점으로 구분, 각 마디는 숫자로 시작 불가, 하이픈과 한글 불가. 이 값은 AdMob과 무관하고 Play 콘솔과 Capacitor 설정에만 들어갑니다.

## 0-2. 콘솔 따라 하기 체크리스트

| 단계 | 메뉴 경로 | 결과물 |
|---|---|---|
| 가입 | admob.google.com → 시작하기 → 국가 대한민국(변경 불가) | 계정 |
| 앱 추가 | 앱 → 앱 추가 → Android → 스토어 등록 "아니요" → 이름 조각보 | 앱 ID (`~` 포함) |
| 광고 단위 | 앱 → 조각보 → 광고 단위 → 추가 ×3 (보상형 2, 전면 1) | 광고 단위 ID 3개 (`/` 포함) |
| 동의 메시지 | 개인정보 보호 및 메시지 → 유럽 규정 → 메시지 만들기 → 조각보 선택 → 게시 | 없음(자동 적용) |
| 지급 | 지급 → 결제 정보 → 계정 유형(개인/조직, 변경 불가) → 세금 양식 → 은행 계좌 | 없음 |
| 테스트 기기 | 기기 설정 → Google → 광고 → 광고 ID 복사 → AdMob 설정 → 테스트 기기 → 추가 | 없음 |
| 게시자 ID | 설정 → 계정 정보 | `pub-` 16자리 (app-ads.txt용) |

개발자에게 넘길 값: 앱 ID 1개, 광고 단위 ID 3개, 게시자 ID 1개, 패키지 이름. 모두 앱에 그대로 들어가는 공개 값입니다.

## 1. AdMob 콘솔에서 할 일

### 1-1. 가입

- https://admob.google.com 에서 Google 계정으로 가입합니다.
- **거주 국가는 가입 후 변경할 수 없습니다.** 대한민국으로 선택합니다.
- 시간대와 통화(KRW 또는 USD)를 고릅니다. 지급은 USD 기준으로 계산되므로 보고서 통화는 USD가 비교하기 편합니다.

### 1-2. 앱 추가

- 앱 > 앱 추가 > 플랫폼 Android > "앱이 지원되는 앱 스토어에 등록되어 있나요?"에서 **아니요**를 선택합니다. 출시 전이라도 앱 ID를 발급받을 수 있습니다.
- 앱 이름은 "조각보"로 입력합니다. 출시 후 스토어 연결 단계에서 Play 스토어 목록과 연결합니다.
- 생성되는 **앱 ID** 형식: `ca-app-pub-1234567890123456~1234567890` (물결표 `~`가 들어갑니다). SDK 초기화용이며 매니페스트에 넣습니다.

### 1-3. 광고 단위 생성

앱 > 광고 단위 > 광고 단위 추가. 조각보에는 세 개가 필요합니다.

| 광고 단위 이름 | 형식 | 쓰이는 곳 |
|---|---|---|
| jogakbo_rewarded_continue | 보상형 | 게임오버 후 "광고 보고 이어 짓기" |
| jogakbo_rewarded_item | 보상형 | 되돌리기, 회전/뒤섞기/새 조각 충전 |
| jogakbo_interstitial | 전면 | "새 보 짓기" 3판마다 1회 |

- 보상형을 둘로 나누는 이유는 보고서에서 어느 지점이 돈이 되는지 보기 위해서입니다. 단가는 같습니다.
- 보상형 설정에서 보상 금액과 항목은 아무 값이나 넣습니다(예: 1 continue). 앱 쪽에서 보상 이벤트만 받으면 됩니다.
- **광고 단위 ID** 형식: `ca-app-pub-1234567890123456/1234567890` (슬래시 `/`). 앱 ID와 구분하세요.

### 1-4. 지급 정보

- 지급 > 결제 정보. 계정 유형은 **개인 또는 조직 중 하나이며 나중에 바꿀 수 없습니다.** 테크브릿지 법인 명의로 받으려면 "조직"을 선택하고 사업자 정보를 입력합니다.
- 세금 정보: 한국 거주자는 미국 세금 정보(W-8BEN 또는 W-8BEN-E)를 제출합니다. 이 단계는 AdMob 콘솔 안내를 따르면 됩니다.
- 수취 은행은 USD 해외 송금을 받을 수 있는 계좌여야 합니다. 국내 은행 외화 계좌면 됩니다.
- 지급 기준액은 100달러이고, 누적 수익이 기준을 넘긴 달의 다음 달 21일 전후에 지급됩니다.
- 주소 확인: 수익이 일정액에 도달하면 Google이 우편으로 PIN을 보냅니다. PIN을 입력하기 전에는 지급이 보류되니 사업장 주소를 정확히 넣으세요.

### 1-5. 개인정보 보호 및 메시지 (UMP)

- 개인정보 보호 및 메시지 > 유럽 규정(GDPR) 메시지를 **반드시 만들어 게시**합니다. 2024년부터 EEA, 영국, 스위스 유저에게 광고를 보내려면 Google 인증 동의 관리 플랫폼이 필수이고, AdMob 자체 메시지가 가장 간단합니다.
- 한국에만 출시해도 만들어 두는 것이 안전합니다. 앱 코드에서 동의 흐름을 호출하면 해당 지역 유저에게만 자동으로 표시됩니다.
- 미국 주(州) 규정 메시지도 같은 화면에서 켤 수 있습니다. 영어권 출시 시 켭니다.

## 2. 프로젝트에 Capacitor와 플러그인 넣기

지금 저장소는 번들러 없이 ES 모듈을 바로 쓰는 구조입니다. npm 패키지인 AdMob 플러그인을 브라우저에서 불러오려면 번들러가 필요하므로 Vite를 추가합니다.

```bash
npm install -D vite
npm install @capacitor/core @capacitor/cli @capacitor/android
npm install @capacitor-community/admob
npx cap init 조각보 kr.co.techbridge.jogakbo --web-dir dist
npx cap add android
```

- 앱 ID(`kr.co.techbridge.jogakbo`)는 Play 스토어에서 영구적이라 처음에 정확히 정해야 합니다. 회사 도메인을 거꾸로 쓰는 관례입니다.
- 플러그인 버전: Capacitor 8.5 이상이면 `@capacitor-community/admob` 8.x, Capacitor 7이면 `@capacitor-community/admob@7`. 8.x는 Google Mobile Ads SDK 25.4.x를 고정합니다.
- 최소 요구: Android API 24(안드로이드 7.0) 이상.

`package.json` 스크립트:

```json
"build": "vite build",
"android": "vite build && npx cap sync android && npx cap open android"
```

빌드 결과물은 `dist/`에 생기고 `npx cap sync`가 이를 안드로이드 프로젝트 안으로 복사합니다.

## 3. 코드: `src/ads.js` 교체

현재 `src/ads.js`는 즉시 성공을 돌려주는 스텁입니다. 게임 코드는 `Ads.rewardedReady()`, `Ads.showRewarded()`, `Ads.showInterstitial()` 세 함수만 쓰므로 이 파일만 바꾸면 됩니다.

### 3-1. 광고 ID 설정

`src/config.js`에 추가:

```js
// 광고 ID. 빌드 모드에 따라 테스트 ID와 실제 ID를 자동으로 고릅니다.
const AD_TEST = {
  rewardedContinue: 'ca-app-pub-3940256099942544/5224354917',
  rewardedItem:     'ca-app-pub-3940256099942544/5224354917',
  interstitial:     'ca-app-pub-3940256099942544/1033173712',
};
const AD_PROD = {
  rewardedContinue: 'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX', // 1-3에서 만든 실제 ID
  rewardedItem:     'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
  interstitial:     'ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX',
};
export const AD_IDS = import.meta.env?.PROD ? AD_PROD : AD_TEST;
```

위 테스트 ID는 Google이 모든 개발자에게 공개한 공용 테스트 광고 단위입니다. 테스트 앱 ID는 `ca-app-pub-3940256099942544~3347511713`입니다.

### 3-2. 어댑터

```js
// src/ads.js  (Capacitor + AdMob 버전)
import { Capacitor } from '@capacitor/core';
import { AdMob, AdmobConsentStatus, RewardAdPluginEvents, InterstitialAdPluginEvents } from '@capacitor-community/admob';
import { AD_IDS } from './config.js';

const native = Capacitor.isNativePlatform();

let rewardedLoaded = false;
let interstitialLoaded = false;
let initialized = false;

async function init() {
  if (!native || initialized) return;
  initialized = true;

  // 1) 동의 흐름: EEA 등 해당 지역 유저에게만 양식이 뜨고, 그 외 지역은 바로 통과합니다.
  let consent = await AdMob.requestConsentInfo();
  if (consent.isConsentFormAvailable && consent.status === AdmobConsentStatus.REQUIRED) {
    consent = await AdMob.showConsentForm();
  }
  if (!consent.canRequestAds) return;

  // 2) SDK 초기화. 개발 중에는 initializeForTesting과 기기 ID를 넣습니다.
  await AdMob.initialize({
    initializeForTesting: !import.meta.env.PROD,
    testingDevices: [],  // logcat에 찍히는 "Use RequestConfiguration.Builder().setTestDeviceIds(...)" 값을 넣습니다
  });

  // 3) 상태 이벤트
  AdMob.addListener(RewardAdPluginEvents.Loaded, () => { rewardedLoaded = true; });
  AdMob.addListener(RewardAdPluginEvents.FailedToLoad, () => { rewardedLoaded = false; setTimeout(loadRewarded, 30000); });
  AdMob.addListener(InterstitialAdPluginEvents.Loaded, () => { interstitialLoaded = true; });
  AdMob.addListener(InterstitialAdPluginEvents.FailedToLoad, () => { interstitialLoaded = false; setTimeout(loadInterstitial, 30000); });

  // 4) 미리 로드해 두어야 버튼을 눌렀을 때 바로 나옵니다.
  loadRewarded();
  loadInterstitial();
}

async function loadRewarded(kind = 'continue') {
  rewardedLoaded = false;
  try { await AdMob.prepareRewardVideoAd({ adId: kind === 'item' ? AD_IDS.rewardedItem : AD_IDS.rewardedContinue }); }
  catch { /* FailedToLoad 리스너가 재시도 */ }
}

async function loadInterstitial() {
  interstitialLoaded = false;
  try { await AdMob.prepareInterstitial({ adId: AD_IDS.interstitial }); }
  catch { /* 재시도는 리스너에서 */ }
}

export const Ads = {
  init,

  rewardedReady() { return native ? rewardedLoaded : true; },

  // 보상형: 끝까지 봤을 때만 true. 중간에 닫으면 false.
  // 주의: Dismissed 이벤트는 보상과 무관하게 항상 옵니다. 보상은 Rewarded 이벤트로만 판단합니다.
  showRewarded(kind = 'continue') {
    if (!native) return new Promise(r => setTimeout(() => r(true), 400)); // 웹: 스텁
    if (!rewardedLoaded) return Promise.resolve(false);
    return new Promise(async (resolve) => {
      let rewarded = false;
      const onReward = await AdMob.addListener(RewardAdPluginEvents.Rewarded, () => { rewarded = true; });
      const onDismiss = await AdMob.addListener(RewardAdPluginEvents.Dismissed, () => {
        onReward.remove(); onDismiss.remove();
        resolve(rewarded);
        loadRewarded(kind);  // 다음 광고 미리 로드
      });
      try { await AdMob.showRewardVideoAd(); }
      catch { onReward.remove(); onDismiss.remove(); resolve(false); loadRewarded(kind); }
    });
  },

  // 전면: 닫힐 때까지 기다렸다가 다음 광고를 미리 로드
  showInterstitial() {
    if (!native || !interstitialLoaded) return Promise.resolve();
    return new Promise(async (resolve) => {
      const h = await AdMob.addListener(InterstitialAdPluginEvents.Dismissed, () => { h.remove(); resolve(); loadInterstitial(); });
      try { await AdMob.showInterstitial(); }
      catch { h.remove(); resolve(); loadInterstitial(); }
    });
  },
};
```

게임 쪽 변경은 두 곳뿐입니다.

- `src/main.js`에서 게임 시작 직후 `Ads.init()` 호출.
- `GameScene`에서 아이템 충전과 되돌리기는 `Ads.showRewarded('item')`, 이어 짓기는 `Ads.showRewarded('continue')`로 구분.

### 3-3. 동작 원칙

- **보상은 `Rewarded` 이벤트에서만 지급**합니다. `Dismissed`는 광고를 끝까지 봤든 중간에 껐든 항상 오기 때문에 여기서 보상을 주면 안 됩니다.
- 광고가 로드되지 않은 상태에서는 `rewardedReady()`가 false를 돌려주고, 게임은 이미 그 경우 버튼을 숨기도록 되어 있습니다.
- 광고를 보여준 뒤에는 반드시 다음 광고를 미리 로드합니다. 로드에 보통 1~3초가 걸려서 버튼을 누른 뒤 로드하면 유저가 기다리게 됩니다.

## 4. 안드로이드 설정

`android/app/src/main/AndroidManifest.xml`의 `<application>` 안:

```xml
<meta-data
  android:name="com.google.android.gms.ads.APPLICATION_ID"
  android:value="@string/admob_app_id" />
```

`android/app/src/main/res/values/strings.xml`:

```xml
<string name="admob_app_id">ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX</string>
```

- 이 값은 **앱 ID(물결표)** 입니다. 광고 단위 ID(슬래시)를 넣으면 앱이 시작하자마자 죽습니다.
- 개발 중에는 테스트 앱 ID `ca-app-pub-3940256099942544~3347511713`를 넣어도 됩니다.
- `AD_ID` 권한은 SDK가 자동으로 추가합니다. 따로 넣을 필요 없지만 Play 콘솔 데이터 안전 섹션에서 "광고 ID 수집"을 선언해야 합니다.

## 5. 테스트

- **개발 중에는 반드시 테스트 ID를 쓰거나 기기를 테스트 기기로 등록합니다.** 실제 광고 단위로 본인이 광고를 누르면 무효 트래픽으로 분류되어 계정이 정지될 수 있고, 정지되면 복구가 거의 안 됩니다.
- 테스트 광고는 화면 위에 "Test Ad" 라벨이 붙어 나옵니다.
- 실 ID로 테스트하려면 앱을 한 번 실행한 뒤 Android Studio logcat에서 `setTestDeviceIds` 문구와 함께 찍히는 기기 ID를 복사해 `testingDevices`에 넣습니다. 또는 AdMob 콘솔 > 설정 > 테스트 기기에 등록합니다. 등록 후 반영까지 15분에서 길게는 24시간 걸립니다.
- 체크 항목: 이어 짓기 광고가 나오는지, 중간에 끄면 이어 짓기가 안 되는지, 끝까지 보면 되는지, 전면 광고가 3판마다 나오는지, 비행기 모드에서 버튼이 숨겨지는지.

## 6. 출시 전 필수 항목

| 항목 | 내용 |
|---|---|
| 개인정보처리방침 URL | Play 콘솔과 AdMob 모두 요구. 광고 ID 수집, AdMob 사용, 동의 철회 방법을 적습니다. 회사 웹사이트에 페이지 하나면 됩니다. |
| Play 콘솔 데이터 안전 | "기기 또는 기타 ID > 광고 ID 수집, 광고 또는 마케팅 목적"으로 선언. AdMob이 수집하므로 빠뜨리면 심사에서 걸립니다. |
| app-ads.txt | Play 콘솔에 적은 개발자 웹사이트 도메인 루트에 `https://도메인/app-ads.txt`로 올립니다. 내용은 한 줄: `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0` (pub- 뒤는 AdMob 게시자 ID). 없어도 광고는 나오지만 일부 광고주 입찰에서 빠져 수익이 줄어듭니다. |
| 스토어 연결 | 출시 후 AdMob > 앱 > 앱 설정에서 Play 스토어 목록과 연결합니다. 연결 전에는 광고 요청이 제한될 수 있습니다. |
| 앱 검토 | 연결 후 AdMob이 앱을 검토합니다. "준비 완료"가 될 때까지 며칠 걸리고, 그동안 광고 게재가 제한될 수 있습니다. 출시 첫 주 수익이 낮게 나오는 흔한 이유입니다. |
| 광고 빈도 정책 | 앱 시작 직후 전면 광고 금지, 광고 클릭 유도 문구 금지, 보상형은 유저가 직접 선택할 때만. 현재 설계(게임오버 뒤 3판마다 전면, 나머지는 선택형)는 정책에 맞습니다. |

## 7. 출시 후 볼 지표

- AdMob 보고서에서 광고 단위별 **노출당 수익(eCPM)**, **노출 수**, **매치율**(요청 대비 광고가 채워진 비율)을 봅니다.
- 보상형 노출 수 대비 "이어 짓기" 버튼 노출 수를 비교해 전환율을 구합니다. 보통 보상형 전환율이 20~40% 나오면 정상입니다.
- 매치율이 낮으면(한국은 보통 80% 이상) 앱 검토가 안 끝났거나 app-ads.txt 문제입니다.
- 전면 광고 빈도를 올릴지는 D1 리텐션을 보고 정합니다. 리텐션이 떨어지면 빈도를 줄입니다.

## 출처

- [capacitor-community/admob README](https://github.com/capacitor-community/admob)
- [@capacitor-community/admob on npm](https://www.npmjs.com/package/@capacitor-community/admob)
- [Capawesome, Capacitor AdMob plugin comparison](https://capawesome.io/blog/alternative-to-capacitor-community-admob/)
- [Google AdMob 고객센터, AdMob에 가입하기](https://support.google.com/admob/answer/7356219?hl=ko)
- [Google AdMob 고객센터, 결제 세부정보 입력하기](https://support.google.com/admob/answer/12835021?hl=ko)
- [Google AdMob 고객센터, 시작 가이드](https://support.google.com/admob/answer/15948559?hl=ko)
- [Google Developers, Enable test ads (Android)](https://developers.google.com/admob/android/test-ads)
- [UndrAds, How to Set Up AdMob for the First Time](https://www.undrads.com/blogs/how-to-set-up-admob-for-the-first-time)

참고: Google 공식 문서 호스트는 이 작성 세션의 네트워크에서 접근이 막혀 있어, 테스트 ID와 콘솔 절차는 플러그인 README와 2차 자료, 기존 지식으로 작성했습니다. 콘솔 화면 문구는 실제와 약간 다를 수 있습니다.
