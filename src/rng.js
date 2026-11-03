// 시드 난수와 날짜 키. "오늘의 조각보"는 같은 날짜면 누구에게나 같은 조각 순서가 나와야 합니다.

// mulberry32: 작고 빠른 32비트 시드 난수. 0 이상 1 미만을 돌려줍니다.
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// 기기 현지 날짜 기준 'YYYYMMDD' (한국 유저는 KST 자정에 바뀝니다)
export function todayKey(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}${m}${d}`;
}

// 날짜 키 → 시드. 날짜가 하루 다르면 전혀 다른 수열이 나오도록 섞습니다.
export function seedFromKey(key) {
  let h = 2166136261;
  for (const ch of String(key)) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619); }
  return h >>> 0;
}

export function dailyRng(key = todayKey()) {
  return mulberry32(seedFromKey(key));
}

// 표시용: 'YYYYMMDD' → '11월 3일'
export function formatKey(key) {
  return `${Number(key.slice(4, 6))}월 ${Number(key.slice(6, 8))}일`;
}
