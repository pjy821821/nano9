// 조각 모양 정의. 각 모양은 [row, col] 좌표 배열입니다.
// 하이브리드 캐주얼 블록 퍼즐 표준 세트(1~5칸, 회전형 포함).
export const SHAPES = [
  // 단일/직선
  [[0, 0]],
  [[0, 0], [0, 1]],
  [[0, 0], [1, 0]],
  [[0, 0], [0, 1], [0, 2]],
  [[0, 0], [1, 0], [2, 0]],
  [[0, 0], [0, 1], [0, 2], [0, 3]],
  [[0, 0], [1, 0], [2, 0], [3, 0]],
  [[0, 0], [0, 1], [0, 2], [0, 3], [0, 4]],
  [[0, 0], [1, 0], [2, 0], [3, 0], [4, 0]],
  // 정사각형
  [[0, 0], [0, 1], [1, 0], [1, 1]],
  [[0, 0], [0, 1], [0, 2], [1, 0], [1, 1], [1, 2], [2, 0], [2, 1], [2, 2]],
  // L 계열
  [[0, 0], [1, 0], [1, 1]],
  [[0, 1], [1, 0], [1, 1]],
  [[0, 0], [0, 1], [1, 0]],
  [[0, 0], [0, 1], [1, 1]],
  [[0, 0], [1, 0], [2, 0], [2, 1]],
  [[0, 1], [1, 1], [2, 1], [2, 0]],
  [[0, 0], [0, 1], [0, 2], [1, 0]],
  [[0, 0], [0, 1], [0, 2], [1, 2]],
  [[0, 0], [1, 0], [2, 0], [2, 1], [2, 2]],
  [[0, 0], [0, 1], [0, 2], [1, 0], [2, 0]],
  // T 계열
  [[0, 0], [0, 1], [0, 2], [1, 1]],
  [[0, 1], [1, 0], [1, 1], [1, 2]],
  [[0, 0], [1, 0], [1, 1], [2, 0]],
  [[0, 1], [1, 0], [1, 1], [2, 1]],
  // S/Z
  [[0, 1], [0, 2], [1, 0], [1, 1]],
  [[0, 0], [0, 1], [1, 1], [1, 2]],
];

export function randomShape(rng = Math.random) {
  return SHAPES[Math.floor(rng() * SHAPES.length)];
}

export function shapeBounds(shape) {
  let maxR = 0, maxC = 0;
  for (const [r, c] of shape) { if (r > maxR) maxR = r; if (c > maxC) maxC = c; }
  return { rows: maxR + 1, cols: maxC + 1 };
}

// 시계 방향 90도 회전: (r, c) -> (c, rows-1-r), 좌상단 기준으로 정규화
export function rotateShape(shape) {
  const { rows } = shapeBounds(shape);
  const rotated = shape.map(([r, c]) => [c, rows - 1 - r]);
  return normalize(rotated);
}

export function normalize(shape) {
  let minR = Infinity, minC = Infinity;
  for (const [r, c] of shape) { if (r < minR) minR = r; if (c < minC) minC = c; }
  return shape.map(([r, c]) => [r - minR, c - minC]).sort((a, b) => a[0] - b[0] || a[1] - b[1]);
}
