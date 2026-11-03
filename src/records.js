// 내 기록 보관 (기기 로컬). 서버 없이 동작하며, 나중에 리더보드가 붙어도 이 구조는 그대로 둡니다.
const KEY = 'jogakbo.records';
export const MAX_RECORDS = 10;

function load() {
  try { return JSON.parse(localStorage.getItem(KEY)) || { endless: [], daily: {} }; }
  catch { return { endless: [], daily: {} }; }
}
function save(data) {
  try { localStorage.setItem(KEY, JSON.stringify(data)); } catch { /* 사생활 보호 모드 등 */ }
}

// 순수 함수: 목록에 점수를 넣고 순위를 돌려줍니다 (1부터, 10위 밖이면 0)
export function insertRecord(list, entry, max = MAX_RECORDS) {
  const next = [...list, entry].sort((a, b) => b.score - a.score || a.at - b.at).slice(0, max);
  const idx = next.findIndex(e => e === entry);
  return { list: next, rank: idx === -1 ? 0 : idx + 1 };
}

export const Records = {
  all() { return load(); },

  // 무한 모드: 상위 10개
  addEndless(score, maxStreak) {
    const data = load();
    const { list, rank } = insertRecord(data.endless, { score, maxStreak, at: Date.now() });
    data.endless = list;
    save(data);
    return rank;
  },

  // 오늘의 조각보: 날짜별 최고 점수 하나. 갱신되면 true.
  addDaily(dayKey, score, maxStreak) {
    const data = load();
    const prev = data.daily[dayKey];
    const improved = !prev || score > prev.score;
    if (improved) { data.daily[dayKey] = { score, maxStreak, at: Date.now() }; save(data); }
    return { improved, best: improved ? score : prev.score };
  },

  dailyBest(dayKey) { return load().daily[dayKey] || null; },

  // 최근 n일의 데일리 기록 (최신순)
  recentDaily(n = 7) {
    const d = load().daily;
    return Object.keys(d).sort().reverse().slice(0, n).map(k => ({ day: k, ...d[k] }));
  },

  // 오늘 점수가 과거 데일리 기록 중 몇 위인지 (1부터)
  dailyRankAmongDays(dayKey, score) {
    const d = load().daily;
    const others = Object.entries(d).filter(([k]) => k !== dayKey).map(([, v]) => v.score);
    return others.filter(s => s > score).length + 1;
  },
};
