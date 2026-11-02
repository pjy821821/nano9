// 전역 게임 설정. 숫자를 바꾸면 난이도와 레이아웃이 함께 바뀝니다.
export const GAME_WIDTH = 720;
export const GAME_HEIGHT = 1280;

export const GRID_SIZE = 8;        // 8x8 보드
export const CELL = 76;            // 셀 한 변(px)
export const GRID_X = (GAME_WIDTH - GRID_SIZE * CELL) / 2;
export const GRID_Y = 262;

export const ITEM_Y = GRID_Y + GRID_SIZE * CELL + 70;  // 아이템 바 중심 y
export const TRAY_Y = GRID_Y + GRID_SIZE * CELL + 215; // 조각 대기열 중심 y
export const TRAY_SCALE = 0.55;                         // 대기열에서 조각 축소 비율

// ---------- 조각보 테마 ----------
// 바탕은 한지, 보드 테두리는 쪽빛 비단, 조각은 색동 천 조각.
export const COLORS = {
  bg: 0xf3e9d6,          // 한지
  hanjiFiber: 0xb59a74,  // 한지 섬유 결
  frame: 0x24375c,       // 쪽빛(indigo) 보드 틀
  frameDark: 0x182742,
  cellEmpty: 0xe6d8bf,   // 비어 있는 천 자리
  cellEmptyStitch: 0xcdbb9c,
  ghostOk: 0x6b8e4e,     // 쑥색
  ghostBad: 0xd9463b,    // 다홍
  ink: '#2a2320',        // 먹색 글씨
  text: '#2a2320',
  textDim: '#7a6a5a',
  textOnFrame: '#f3e9d6',
  accent: '#d9463b',     // 다홍
  seal: 0xc8392f,        // 낙관 인주색
  seal2: 0xa52c24,
};

// 색동 띠 순서 (전통 색동 배열을 바탕으로 채도를 맞춤)
export const SAEKDONG = [0xd9463b, 0xe8b63a, 0x2b6cb0, 0x6b8e4e, 0xf3e9d6, 0x8a3b6b, 0xe78fa6];

// 조각 색상: 오방색과 한복 색감 (조각마다 랜덤 선택)
export const PIECE_COLORS = [
  0xd9463b, // 다홍
  0xe8b63a, // 치자
  0x2b6cb0, // 쪽빛
  0x6b8e4e, // 쑥색
  0x8a3b6b, // 자주
  0xe78fa6, // 연분홍
  0x7fb2d6, // 하늘
  0xe07b39, // 주황(홍시)
];

export const FONT_DISPLAY = '"Gowun Batang", "Nanum Myeongjo", "Apple SD Gothic Neo", serif';
export const FONT_UI = '"Gowun Dodum", "Noto Sans KR", "Apple SD Gothic Neo", sans-serif';

// 점수 규칙
export const SCORE_PER_CELL = 1;          // 블록 1칸 놓을 때
export const SCORE_PER_LINE = 10;         // 줄 1개 제거
export const COMBO_BONUS = 10;            // 동시 제거 줄 수 2개 이상일 때 추가 (줄 수 * 보너스)

// 아이템: 1판당 기본 횟수. 다 쓰면 보상형 광고로 충전.
export const ITEMS = {
  rotate: { label: '회전', uses: 3, refill: 3 },
  shuffle: { label: '뒤섞기', uses: 1, refill: 1 },
  redraw: { label: '새 조각', uses: 1, refill: 1 },
};

// 연속 제거(스트릭) 배율: 줄을 지운 직후 다음 배치에서도 지우면 배율이 오릅니다. 못 지우면 초기화.
export const STREAK_MAX_MULT = 5;

// 줄 제거 문구: 바느질 "땀" 수로 표현
export const CLEAR_WORDS = ['한 땀!', '두 땀!!', '세 땀!!!', '네 땀!!!!', '다섯 땀!', '여섯 땀!'];

export const STORAGE_KEY = 'jogakbo.best';
export const TUTORIAL_KEY = 'jogakbo.tutorialDone';
export const MUTE_KEY = 'jogakbo.muted';
