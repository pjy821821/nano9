// 광고 추상화 계층.
// 지금은 웹 프로토타입이므로 즉시 성공을 반환하는 스텁입니다.
// Capacitor로 앱 포장 시 이 파일만 AdMob / AppLovin MAX 플러그인 호출로 교체하면 됩니다.
export const Ads = {
  rewardedReady() { return true; },

  // 보상형 광고: 시청 완료 시 resolve(true), 중도 이탈/실패 시 resolve(false)
  showRewarded() {
    return new Promise(resolve => setTimeout(() => resolve(true), 400));
  },

  // 전면 광고: 게임오버 N회마다 호출하는 용도
  showInterstitial() {
    return Promise.resolve();
  },
};
