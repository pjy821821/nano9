// 유튜브 광고 차단 (TechBridge) - 콘텐츠 스크립트
// 1) 영상 광고: 플레이어가 광고 상태(.ad-showing)가 되면 광고 영상을 끝으로 넘기고 "건너뛰기" 버튼을 누른다.
// 2) 배너/오버레이/피드 광고: 광고 요소를 DOM에서 제거한다 (styles.css가 먼저 숨기고, 여기서 지운다).
// 3) 광고 차단 경고 팝업이 뜨면 닫고 멈춘 영상을 다시 재생한다.
(() => {
  'use strict';

  const api = typeof browser !== 'undefined' ? browser : chrome;

  const state = {
    enabled: true,
    adInProgress: false,
    pendingSkips: 0,
  };

  // 페이지에서 지울 광고 요소들
  const AD_SELECTORS = [
    // 플레이어 안
    '.ytp-ad-overlay-container',
    '.ytp-ad-text-overlay',
    '.ytp-ad-image-overlay',
    '.ytp-ad-action-interstitial',
    '.ytp-ad-player-overlay-layout',
    '.ytp-paid-content-overlay',
    '#player-ads',
    // 홈/검색/보기 페이지
    '#masthead-ad',
    'ytd-ad-slot-renderer',
    'ytd-display-ad-renderer',
    'ytd-in-feed-ad-layout-renderer',
    'ytd-banner-promo-renderer',
    'ytd-statement-banner-renderer',
    'ytd-promoted-sparkles-web-renderer',
    'ytd-promoted-sparkles-text-search-renderer',
    'ytd-compact-promoted-video-renderer',
    'ytd-promoted-video-renderer',
    'ytd-video-masthead-ad-v3-renderer',
    'ytd-primetime-promo-renderer',
    'ytd-merch-shelf-renderer',
    'ytd-player-legacy-desktop-watch-ads-renderer',
    'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]',
    // 피드 안 광고 카드 (:has 지원 브라우저만 동작)
    'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)',
    'ytd-rich-item-renderer:has(ytd-display-ad-renderer)',
    'ytd-rich-section-renderer:has(ytd-statement-banner-renderer)',
    'ytd-item-section-renderer:has(> #contents > ytd-ad-slot-renderer:only-child)',
    // Shorts 광고
    'ytd-reel-video-renderer:has(ytd-ad-slot-renderer)',
    'ytd-reel-video-renderer:has(.ytd-reel-player-overlay-renderer[is-ad])',
    // 모바일 (m.youtube.com)
    'ytm-promoted-video-renderer',
    'ytm-companion-ad-renderer',
    'ytm-promoted-sparkles-web-renderer',
    'ytm-rich-item-renderer:has(ytm-promoted-video-renderer)',
    '.ytm-companion-slot',
    // 유튜브 뮤직
    'ytmusic-mealbar-promo-renderer',
    'ytmusic-statement-banner-renderer',
  ];

  // 광고 건너뛰기 버튼들 (유튜브가 클래스 이름을 자주 바꿔 여러 개 둠)
  const SKIP_BUTTON_SELECTORS = [
    '.ytp-skip-ad-button',
    '.ytp-ad-skip-button',
    '.ytp-ad-skip-button-modern',
    '.ytp-ad-skip-button-slot button',
    '.ytp-ad-survey-answer-button',
    '.ytp-ad-overlay-close-button',
    'button.ytp-ad-skip-button-container',
    '.videoAdUiSkipButton',
    // 모바일
    '.ytm-ad-skip-button',
    'button[aria-label*="건너뛰기"]',
    'button[aria-label*="Skip"]',
  ];

  // 광고 차단 경고 팝업(강제 메시지)
  const ENFORCEMENT_SELECTORS = [
    'ytd-enforcement-message-view-model',
    'tp-yt-paper-dialog:has(ytd-enforcement-message-view-model)',
    'ytd-popup-container:has(ytd-enforcement-message-view-model) tp-yt-iron-overlay-backdrop',
  ];

  // :has 가 없는 브라우저에서 selector 전체가 깨지지 않도록 하나씩 검사
  const SUPPORTED_AD_SELECTORS = AD_SELECTORS.filter(isSelectorSupported);
  const SUPPORTED_ENFORCEMENT_SELECTORS = ENFORCEMENT_SELECTORS.filter(isSelectorSupported);
  const AD_QUERY = SUPPORTED_AD_SELECTORS.join(',');
  const ENFORCEMENT_QUERY = SUPPORTED_ENFORCEMENT_SELECTORS.join(',');
  const SKIP_QUERY = SKIP_BUTTON_SELECTORS.join(',');

  function isSelectorSupported(sel) {
    try {
      document.createDocumentFragment().querySelector(sel);
      return true;
    } catch (e) {
      return false;
    }
  }

  // ---- 설정 (켬/끔) ----
  function loadSettings() {
    try {
      api.storage.sync.get({ enabled: true }, (v) => {
        state.enabled = v.enabled !== false;
        applyEnabledClass();
      });
      api.storage.onChanged.addListener((changes, area) => {
        if (area === 'sync' && changes.enabled) {
          state.enabled = changes.enabled.newValue !== false;
          applyEnabledClass();
        }
      });
    } catch (e) {
      // 확장 컨텍스트가 아닌 곳(테스트 등)에서는 기본값 사용
    }
  }

  // styles.css는 html[data-tb-ytab="on"] 일 때만 광고를 숨긴다
  function applyEnabledClass() {
    const root = document.documentElement;
    if (!root) return;
    root.setAttribute('data-tb-ytab', state.enabled ? 'on' : 'off');
  }

  // ---- 통계 ----
  function flushSkipCount() {
    if (state.pendingSkips === 0) return;
    const n = state.pendingSkips;
    state.pendingSkips = 0;
    try {
      api.storage.local.get({ skipped: 0 }, (v) => {
        api.storage.local.set({ skipped: (v.skipped || 0) + n });
      });
    } catch (e) {
      /* 무시 */
    }
  }

  // ---- 영상 광고 ----
  function getPlayer() {
    return document.querySelector('#movie_player, .html5-video-player');
  }

  function skipVideoAd() {
    const player = getPlayer();
    if (!player) return;
    const adShowing = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');

    if (!adShowing) {
      if (state.adInProgress) {
        // 광고가 끝났다: 본 영상 설정 복구
        state.adInProgress = false;
        const video = player.querySelector('video');
        if (video && video.playbackRate > 1) video.playbackRate = 1;
      }
      return;
    }

    if (!state.adInProgress) {
      state.adInProgress = true;
      state.pendingSkips += 1;
      flushSkipCount();
    }

    // 1) 건너뛰기 버튼이 있으면 누른다
    const skipBtn = player.querySelector(SKIP_QUERY) || document.querySelector(SKIP_QUERY);
    if (skipBtn) {
      skipBtn.click();
    }

    // 2) 광고 영상을 끝으로 보낸다 (duration이 아직 없으면 빠르게 재생)
    const video = player.querySelector('video');
    if (video) {
      const d = video.duration;
      if (Number.isFinite(d) && d > 0) {
        if (video.currentTime < d - 0.1) video.currentTime = d;
      } else {
        video.playbackRate = 16;
      }
      if (video.paused) {
        const p = video.play();
        if (p && typeof p.catch === 'function') p.catch(() => {});
      }
    }
  }

  // ---- 광고 요소 제거 ----
  function removeAdElements() {
    if (!AD_QUERY) return;
    const nodes = document.querySelectorAll(AD_QUERY);
    for (const node of nodes) {
      node.remove();
    }
  }

  // ---- 광고 차단 경고 팝업 ----
  function dismissEnforcement() {
    if (!ENFORCEMENT_QUERY) return;
    const nodes = document.querySelectorAll(ENFORCEMENT_QUERY);
    if (nodes.length === 0) return;
    for (const node of nodes) node.remove();
    // 팝업과 함께 남은 배경막 제거
    document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach((b) => b.remove());
    // 팝업이 영상을 멈춰 두었으면 다시 재생
    const player = getPlayer();
    const video = player && player.querySelector('video');
    if (video && video.paused && !video.ended && video.currentTime > 0) {
      const p = video.play();
      if (p && typeof p.catch === 'function') p.catch(() => {});
    }
  }

  // ---- 스케줄링 ----
  let scheduled = false;
  function tick() {
    scheduled = false;
    if (!state.enabled) return;
    try {
      skipVideoAd();
      removeAdElements();
      dismissEnforcement();
    } catch (e) {
      /* 페이지 구조가 바뀌어도 스크립트가 죽지 않도록 */
    }
  }

  function schedule() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(tick);
  }

  function start() {
    applyEnabledClass();
    loadSettings();

    const observer = new MutationObserver(schedule);
    const observe = () => {
      if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
        schedule();
      } else {
        requestAnimationFrame(observe);
      }
    };
    observe();

    // 변화 이벤트를 놓쳐도 광고가 남지 않도록 주기적으로 한 번 더
    setInterval(tick, 500);

    // 유튜브는 SPA라서 페이지 이동 때도 한 번
    window.addEventListener('yt-navigate-finish', schedule, true);
    window.addEventListener('yt-page-data-updated', schedule, true);
  }

  start();
})();
