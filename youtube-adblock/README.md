# 유튜브 광고 차단 (youtube-adblock)

유튜브 영상 광고를 자동으로 건너뛰고, 배너·오버레이·피드 광고를 숨기는 브라우저 확장 프로그램.
Chrome / Edge / Whale / Brave(Manifest V3)와 Firefox 121 이상에서 동작한다. 외부 서버와 통신하지 않고, 설정은 브라우저 안에만 저장된다.

> 안드로이드·iOS의 유튜브 **앱** 안 광고는 어떤 앱으로도 막을 수 없다(영상과 광고가 같은 서버에서 나온다).
> 모바일에서는 Firefox(안드로이드)에 이 확장을 설치해 브라우저로 보거나, 유튜브 프리미엄이 대안이다.

## 동작 방식

| 계층 | 파일 | 하는 일 |
|---|---|---|
| 네트워크 | `rules.json` | declarativeNetRequest로 광고 서버(doubleclick, googlesyndication 등)와 유튜브 광고 추적·미드롤 요청을 차단 |
| 영상 광고 | `content.js` | 플레이어가 광고 상태(`.ad-showing`)가 되면 "건너뛰기" 버튼을 누르고, 버튼이 없으면 광고 영상을 끝으로 보내서 즉시 종료 |
| 화면 광고 | `content.js`, `styles.css` | 마스트헤드·피드·검색·Shorts·모바일·유튜브 뮤직의 광고 요소를 숨기고 DOM에서 제거 |
| 경고 팝업 | `content.js` | "광고 차단기 감지" 팝업이 뜨면 닫고 멈춘 영상을 다시 재생 |
| 팝업 | `popup.html`, `popup.js` | 켬/끔 토글(네트워크 규칙도 함께 켬/끔), 건너뛴 영상 광고 횟수 |

## 설치

### Chrome / Edge / Whale / Brave

1. 이 폴더(`youtube-adblock/`)를 PC에 받아 둔다. (저장소 전체를 clone 하거나, GitHub에서 ZIP으로 받아 풀기)
2. 주소창에 `chrome://extensions` (Edge는 `edge://extensions`, Whale은 `whale://extensions`) 입력.
3. 오른쪽 위 **개발자 모드** 켬.
4. **압축해제된 확장 프로그램을 로드합니다** 클릭 → `youtube-adblock` 폴더 선택.
5. 유튜브 탭을 새로고침. 툴바의 빨간 아이콘을 누르면 켬/끔과 건너뛴 횟수가 보인다.

Chrome은 개발자 모드 확장에 대해 재시작할 때마다 "개발자 모드 확장 프로그램 사용 중지" 안내를 띄울 수 있다. 무시하거나 "유지"를 누르면 된다.

### Firefox (PC·안드로이드)

- 임시 설치(브라우저 재시작 시 사라짐): `about:debugging#/runtime/this-firefox` → **임시 부가 기능 로드** → `manifest.json` 선택.
- 영구 설치: Firefox Developer Edition / Nightly에서 `about:config`의 `xpinstall.signatures.required`를 `false`로 두고 `build/youtube-adblock-<버전>.zip`을 `about:addons`에서 설치. 일반 Firefox는 서명된 확장만 영구 설치되므로 [AMO](https://addons.mozilla.org/developers/) 자체 배포(unlisted) 서명이 필요하다.
- Firefox MV3는 사이트 권한을 설치 때 묻지 않는다. `about:addons` → 이 확장 → **권한** 탭에서 youtube.com 접근을 켜야 동작한다.

## 패키징

```bash
./build.sh            # build/youtube-adblock-1.0.0.zip 생성 (스토어 업로드·Firefox 설치용)
```

## 테스트

실제 유튜브에 접속하지 않고, Playwright가 `www.youtube.com` 요청을 가로채서 가짜 보기 페이지와 3초 광고 영상을 돌려준 뒤
확장을 로드한 Chromium에서 동작을 확인한다.

```bash
npm install playwright          # 처음 한 번 (Chromium 포함: npx playwright install chromium)
node tests/run.mjs
```

확인 항목: 콘텐츠 스크립트 주입, 건너뛰기 버튼 클릭, 스킵 불가 광고를 끝으로 이동(3초 광고가 0.1초 안에 종료), 마스트헤드·오버레이·피드 광고 제거,
일반 영상 카드 유지, 경고 팝업 제거, 팝업 횟수·토글, 끔 상태에서 광고 유지, 네트워크 규칙 켬/끔.

## 광고가 다시 보이면

유튜브는 광고 요소의 태그·클래스 이름과 플레이어 동작을 자주 바꾼다.

1. 영상 광고가 통과되면: 개발자 도구에서 플레이어(`#movie_player`)의 클래스와 건너뛰기 버튼 선택자를 확인해 `content.js`의 `SKIP_BUTTON_SELECTORS`에 추가.
2. 화면 광고가 남으면: 해당 요소의 태그 이름을 `content.js`의 `AD_SELECTORS`와 `styles.css`에 추가.
3. 영상이 아예 재생되지 않으면: `rules.json`의 11번(`googlevideo.com ... ctier=L|oad=`) 규칙을 지우고 확장을 새로고침해 본다. 이 규칙은 광고 영상 스트림 자체를 막는 것으로, 유튜브가 매개변수 이름을 바꾸면 본 영상에 영향을 줄 수 있다.
4. 저장소에 반영한 뒤 `chrome://extensions`에서 확장의 **새로고침** 버튼.

## 파일

```
youtube-adblock/
├── manifest.json     확장 정의 (MV3, Chrome·Firefox 공용)
├── rules.json        네트워크 차단 규칙
├── content.js        광고 건너뛰기·요소 제거
├── styles.css        광고 요소 즉시 숨김
├── popup.html/.js    툴바 팝업
├── icons/            아이콘 (16/48/128)
├── build.sh          zip 패키징
└── tests/            Playwright 동작 테스트와 가짜 유튜브 페이지
```
