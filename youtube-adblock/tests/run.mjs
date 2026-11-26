// 확장 프로그램 동작 테스트
// - Playwright의 요청 가로채기(route)로 www.youtube.com 요청에 가짜 보기 페이지(fake-watch.html)와 3초 광고 영상(ad.mp4)을 돌려준다.
//   (실제 유튜브에 접속하지 않는다. 확장의 content_scripts는 URL이 유튜브이면 그대로 주입된다.)
// - 확장을 로드한 Chromium에서 광고 요소 제거 / 영상 광고 건너뛰기 / 경고 팝업 닫기 / 켬·끔 토글 / 네트워크 차단 규칙을 확인한다.
// 실행: node tests/run.mjs   (playwright가 프로젝트에 없으면 PLAYWRIGHT_PATH=<playwright 패키지 경로>)
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
// 이 세션 환경에는 playwright가 전역 경로에 있어 require로 찾는다 (프로젝트에 설치했다면 그대로 동작)
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');

const here = path.dirname(fileURLToPath(import.meta.url));
const extDir = path.resolve(here, '..');

const html = fs.readFileSync(path.join(here, 'fake-watch.html'));
const mp4 = fs.readFileSync(path.join(here, 'ad.mp4'));

// 압축해제 확장의 ID는 절대경로 SHA-256 앞 32자리를 a~p로 바꾼 값
const extId = crypto.createHash('sha256').update(extDir).digest('hex').slice(0, 32)
  .replace(/[0-9a-f]/g, (c) => String.fromCharCode('a'.charCodeAt(0) + parseInt(c, 16)));

const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ytab-'));
const context = await chromium.launchPersistentContext(userDataDir, {
  channel: 'chromium',
  headless: true,
  args: [
    `--disable-extensions-except=${extDir}`,
    `--load-extension=${extDir}`,
    '--autoplay-policy=no-user-gesture-required',
  ],
});

// 가짜 유튜브: 보기 페이지와 광고 영상만 돌려주고 나머지는 204
await context.route('http://www.youtube.com/**', (route) => {
  const url = new URL(route.request().url());
  if (url.pathname === '/ad.mp4') {
    // <video>가 탐색(seek)할 수 있도록 Range 요청(206)을 지원한다
    const range = /bytes=(\d*)-(\d*)/.exec(route.request().headers()['range'] || '');
    const start = range && range[1] ? Number(range[1]) : 0;
    const end = range && range[2] ? Math.min(Number(range[2]), mp4.length - 1) : mp4.length - 1;
    return route.fulfill({
      status: range ? 206 : 200,
      headers: {
        'Content-Type': 'video/mp4',
        'Accept-Ranges': 'bytes',
        'Content-Length': String(end - start + 1),
        ...(range ? { 'Content-Range': `bytes ${start}-${end}/${mp4.length}` } : {}),
      },
      body: mp4.subarray(start, end + 1),
    });
  }
  if (url.pathname.startsWith('/watch')) return route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: html });
  return route.fulfill({ status: 204, body: '' });
});

const results = [];
const check = (name, ok, extra = '') => { results.push({ name, ok, extra }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name} ${extra}`); };

try {
  const page = await context.newPage();
  await page.goto('http://www.youtube.com/watch?v=test', { waitUntil: 'load' });

  // 광고 상태가 해제될 때까지 (최대 5초; 영상 광고는 3초짜리)
  await page.waitForFunction(() => !document.getElementById('movie_player').classList.contains('ad-showing'), null, { timeout: 5000 }).catch(() => {});

  const snap = await page.evaluate(() => ({
    attr: document.documentElement.getAttribute('data-tb-ytab'),
    adShowing: document.getElementById('movie_player').classList.contains('ad-showing'),
    masthead: !!document.getElementById('masthead-ad'),
    overlay: !!document.querySelector('.ytp-ad-overlay-container'),
    adItem: !!document.getElementById('ad-item'),
    normalItem: !!document.getElementById('normal-item'),
    enf: !!document.getElementById('enf-dialog'),
    backdrop: !!document.querySelector('tp-yt-iron-overlay-backdrop'),
    log: window.__log,
  }));

  check('콘텐츠 스크립트 주입(data-tb-ytab=on)', snap.attr === 'on', snap.attr);
  check('영상 광고 종료', !snap.adShowing, JSON.stringify(snap.log));
  check('광고 종료가 1초 안에 일어남(건너뛰기/끝으로 이동)', snap.log.some((l) => l.startsWith('ad-ended')));
  check('마스트헤드 광고 제거', !snap.masthead);
  check('플레이어 오버레이 광고 제거', !snap.overlay);
  check('피드 광고 카드 제거', !snap.adItem);
  check('일반 영상 카드는 유지', snap.normalItem);
  check('광고 차단 경고 팝업 제거', !snap.enf && !snap.backdrop);

  // 건너뛰기 버튼이 없는 광고: 영상을 끝으로 보내서 3초 광고가 1.5초 안에 끝나야 함
  const pageNoSkip = await context.newPage();
  await pageNoSkip.goto('http://www.youtube.com/watch?v=noskip', { waitUntil: 'load' });
  await pageNoSkip.waitForFunction(() => !document.getElementById('movie_player').classList.contains('ad-showing'), null, { timeout: 5000 }).catch(() => {});
  const noSkipLog = await pageNoSkip.evaluate(() => window.__log);
  const elapsed = Number((noSkipLog.find((l) => l.startsWith('elapsed-ms:')) || 'elapsed-ms:99999').split(':')[1]);
  check('스킵 불가 광고: 끝으로 이동해 종료', noSkipLog.includes('ad-ended:video-ended'), JSON.stringify(noSkipLog));
  check('스킵 불가 광고: 1.5초 안에 종료', elapsed < 1500, `${elapsed}ms`);
  await pageNoSkip.close();

  // 팝업 페이지: 건너뛴 횟수 2, 토글 켬
  const popup = await context.newPage();
  await popup.goto(`chrome-extension://${extId}/popup.html`);
  await popup.waitForFunction(() => document.getElementById('count').textContent !== '', null, { timeout: 3000 });
  await popup.waitForTimeout(300);
  const count = await popup.evaluate(() => document.getElementById('count').textContent);
  const enabled = await popup.evaluate(() => document.getElementById('enabled').checked);
  check('팝업 건너뛴 횟수 = 2', count === '2', count);
  check('팝업 토글 기본값 켬', enabled === true);

  // 토글을 끄면 광고 요소를 지우지 않아야 함
  await popup.click('.switch .slider');
  await popup.waitForTimeout(300);
  const page2 = await context.newPage();
  await page2.goto('http://www.youtube.com/watch?v=test2', { waitUntil: 'load' });
  await page2.waitForTimeout(800);
  const offSnap = await page2.evaluate(() => ({
    attr: document.documentElement.getAttribute('data-tb-ytab'),
    masthead: !!document.getElementById('masthead-ad'),
  }));
  check('끔 상태에서는 광고 요소 유지', offSnap.attr === 'off' && offSnap.masthead, JSON.stringify(offSnap));

  // 네트워크 차단 규칙 확인: 차단 대상 URL 요청이 실패해야 함
  const blocked = await page2.evaluate(async () => {
    const tryFetch = async (u) => { try { await fetch(u, { mode: 'no-cors' }); return 'ok'; } catch (e) { return 'blocked'; } };
    return {
      ptracking: await tryFetch('http://www.youtube.com/ptracking?x=1'),
      normal: await tryFetch('http://www.youtube.com/ad.mp4'),
    };
  });
  // 끔 상태이므로 둘 다 통과해야 함
  check('끔 상태: 네트워크 규칙도 비활성', blocked.ptracking === 'ok' && blocked.normal === 'ok', JSON.stringify(blocked));

  await popup.click('.switch .slider');
  await popup.waitForTimeout(300);
  const blockedOn = await page2.evaluate(async () => {
    const tryFetch = async (u) => { try { await fetch(u, { mode: 'no-cors' }); return 'ok'; } catch (e) { return 'blocked'; } };
    return {
      ptracking: await tryFetch('http://www.youtube.com/ptracking?x=1'),
      statsAds: await tryFetch('http://www.youtube.com/api/stats/ads?x=1'),
      normal: await tryFetch('http://www.youtube.com/ad.mp4'),
    };
  });
  check('켬 상태: 광고 추적 요청 차단, 일반 요청 통과',
    blockedOn.ptracking === 'blocked' && blockedOn.statsAds === 'blocked' && blockedOn.normal === 'ok', JSON.stringify(blockedOn));
} finally {
  await context.close();
  fs.rmSync(userDataDir, { recursive: true, force: true });
}

const failed = results.filter((r) => !r.ok).length;
console.log(`\n${results.length - failed}/${results.length} 통과`);
process.exit(failed ? 1 : 0);
