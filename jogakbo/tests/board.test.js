// 실행: npm test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Board } from '../src/board.js';
import { SHAPES, shapeBounds } from '../src/shapes.js';

test('빈 보드에는 모든 조각을 놓을 수 있다', () => {
  const b = new Board(8);
  for (const s of SHAPES) assert.ok(b.canPlace(s, 0, 0));
});

test('경계 밖 또는 겹치는 위치는 거부한다', () => {
  const b = new Board(8);
  const bar5 = [[0, 0], [0, 1], [0, 2], [0, 3], [0, 4]];
  assert.equal(b.canPlace(bar5, 0, 4), false);
  b.place([[0, 0]], 0, 0, 1);
  assert.equal(b.canPlace([[0, 0]], 0, 0), false);
});

test('가득 찬 줄과 열을 함께 제거한다', () => {
  const b = new Board(8);
  for (let c = 0; c < 8; c++) b.cells[3][c] = 1;
  for (let r = 0; r < 8; r++) b.cells[r][5] = 1;
  const { lines, cleared } = b.clearLines();
  assert.equal(lines, 2);
  assert.equal(cleared.length, 15); // 8 + 8 - 교차점 1
  assert.ok(b.cells.every(row => row.every(v => v === 0)));
});

test('놓을 자리가 없으면 anyPlacement가 false', () => {
  const b = new Board(8);
  for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) b.cells[r][c] = (r + c) % 2 ? 1 : 0;
  assert.equal(b.anyPlacement([[0, 0], [0, 1]]), false);
  assert.equal(b.anyPlacement([[0, 0]]), true);
});

test('모든 조각은 8x8 안에 들어간다', () => {
  for (const s of SHAPES) {
    const { rows, cols } = shapeBounds(s);
    assert.ok(rows <= 8 && cols <= 8);
  }
});

test('snapshot/restore는 깊은 복사로 동작한다', () => {
  const b = new Board(8);
  b.place([[0, 0], [0, 1]], 2, 2, 7);
  const snap = b.snapshot();
  b.place([[0, 0]], 0, 0, 3);
  snap[5][5] = 9; // 스냅샷을 건드려도 보드에 영향 없어야 함
  assert.equal(b.cells[5][5], 0);
  b.restore(snap);
  assert.equal(b.cells[0][0], 0);
  assert.equal(b.cells[2][2], 7);
  assert.equal(b.cells[5][5], 9);
});

test('rotateShape는 시계 방향 90도 회전 후 정규화한다', async () => {
  const { rotateShape } = await import('../src/shapes.js');
  const L = [[0, 0], [1, 0], [2, 0], [2, 1]];       // L
  const r1 = rotateShape(L);
  assert.deepEqual(r1, [[0, 0], [0, 1], [0, 2], [1, 0]]);
  const r4 = rotateShape(rotateShape(rotateShape(r1)));
  assert.deepEqual(r4, L);                           // 4번 돌리면 원래대로
  assert.deepEqual(rotateShape([[0, 0]]), [[0, 0]]);
});

test('shuffle은 채워진 칸 수를 유지한다', () => {
  const b = new Board(8);
  for (let c = 0; c < 8; c++) b.cells[0][c] = 1;
  b.cells[4][4] = 2;
  b.shuffle();
  assert.equal(b.filledCount(), 9);
  const colors = b.cells.flat().filter(v => v).sort();
  assert.deepEqual(colors, [1, 1, 1, 1, 1, 1, 1, 1, 2]);
});
