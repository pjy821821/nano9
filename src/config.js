// 전역 게임 설정. 숫자를 바꾸면 난이도와 레이아웃이 함께 바뀝니다.
export const GAME_WIDTH = 720;
export const GAME_HEIGHT = 1280;

export const GRID_SIZE = 8;        // 8x8 보드
export const CELL = 76;            // 셀 한 변(px)
export const GRID_X = (GAME_WIDTH - GRID_SIZE * CELL) / 2;
export const GRID_Y = 250;

export const TRAY_Y = GRID_Y + GRID_SIZE * CELL + 170; // 조각 대기열 중심 y
export const TRAY_SCALE = 0.55;                         // 대기열에서 조각 축소 비율

export const COLORS = {
  bg: 0x1b1f3a,
  gridLine: 0x2a2f55,
  cellEmpty: 0x242a4d,
  ghostOk: 0x7fe3a5,
  ghostBad: 0xff6b6b,
  text: '#ffffff',
  textDim: '#9aa3c7',
  accent: '#ffd166',
};

// 조각 색상 팔레트 (블록 색은 조각마다 랜덤 선택)
export const PIECE_COLORS = [0xff6b6b, 0xffd166, 0x06d6a0, 0x4cc9f0, 0xb388ff, 0xff9f1c, 0xf72585];

// 점수 규칙
export const SCORE_PER_CELL = 1;          // 블록 1칸 놓을 때
export const SCORE_PER_LINE = 10;         // 줄 1개 제거
export const COMBO_BONUS = 10;            // 동시 제거 줄 수 2개 이상일 때 추가 (줄 수 * 보너스)

export const STORAGE_KEY = 'blockdrop.best';
