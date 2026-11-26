// 팝업: 켬/끔 토글과 건너뛴 광고 횟수 표시
(() => {
  const api = typeof browser !== 'undefined' ? browser : chrome;
  const $enabled = document.getElementById('enabled');
  const $count = document.getElementById('count');
  const $status = document.getElementById('status');
  const $reset = document.getElementById('reset');

  function render(enabled) {
    $enabled.checked = enabled;
    $status.textContent = enabled
      ? '유튜브 탭에서 광고를 건너뛰고 있습니다.'
      : '꺼져 있습니다. 광고가 그대로 나옵니다.';
  }

  // 네트워크 차단 규칙(rules.json)도 토글과 함께 켬/끔
  function setRules(enabled) {
    const dnr = api.declarativeNetRequest;
    if (!dnr || !dnr.updateEnabledRulesets) return;
    const opts = enabled ? { enableRulesetIds: ['yt_ads'] } : { disableRulesetIds: ['yt_ads'] };
    const p = dnr.updateEnabledRulesets(opts);
    if (p && typeof p.catch === 'function') p.catch(() => {});
  }

  api.storage.sync.get({ enabled: true }, (v) => render(v.enabled !== false));
  api.storage.local.get({ skipped: 0 }, (v) => { $count.textContent = String(v.skipped || 0); });

  $enabled.addEventListener('change', () => {
    const enabled = $enabled.checked;
    api.storage.sync.set({ enabled });
    setRules(enabled);
    render(enabled);
  });

  $reset.addEventListener('click', () => {
    api.storage.local.set({ skipped: 0 });
    $count.textContent = '0';
  });

  api.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes.skipped) $count.textContent = String(changes.skipped.newValue || 0);
  });
})();
