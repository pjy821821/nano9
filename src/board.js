// 순수 로직: 렌더링과 분리되어 있어 단독 테스트가 가능합니다.
import { GRID_SIZE } from './config.js';

export class Board {
  constructor(size = GRID_SIZE) {
    this.size = size;
    this.cells = Array.from({ length: size }, () => Array(size).fill(0)); // 0 = 비어 있음, 그 외 = 색상값
  }

  canPlace(shape, row, col) {
    for (const [r, c] of shape) {
      const rr = row + r, cc = col + c;
      if (rr < 0 || cc < 0 || rr >= this.size || cc >= this.size) return false;
      if (this.cells[rr][cc] !== 0) return false;
    }
    return true;
  }

  place(shape, row, col, color) {
    for (const [r, c] of shape) this.cells[row + r][col + c] = color;
  }

  // 가득 찬 줄과 열을 찾아 제거하고, 제거된 좌표 목록을 반환합니다.
  clearLines() {
    const fullRows = [], fullCols = [];
    for (let i = 0; i < this.size; i++) {
      if (this.cells[i].every(v => v !== 0)) fullRows.push(i);
      if (this.cells.every(row => row[i] !== 0)) fullCols.push(i);
    }
    const cleared = [];
    for (const r of fullRows) for (let c = 0; c < this.size; c++) cleared.push([r, c]);
    for (const c of fullCols) for (let r = 0; r < this.size; r++) if (!fullRows.includes(r)) cleared.push([r, c]);
    for (const [r, c] of cleared) this.cells[r][c] = 0;
    return { lines: fullRows.length + fullCols.length, cleared };
  }

  anyPlacement(shape) {
    for (let r = 0; r < this.size; r++)
      for (let c = 0; c < this.size; c++)
        if (this.canPlace(shape, r, c)) return true;
    return false;
  }

  snapshot() {
    return this.cells.map(row => row.slice());
  }

  restore(cells) {
    this.cells = cells.map(row => row.slice());
  }

  filledCount() {
    let n = 0;
    for (const row of this.cells) for (const v of row) if (v !== 0) n++;
    return n;
  }

  // 채워진 칸의 색을 모두 모아 무작위 위치에 다시 배치합니다 (개수 유지).
  shuffle() {
    const colors = [];
    for (let r = 0; r < this.size; r++)
      for (let c = 0; c < this.size; c++)
        if (this.cells[r][c] !== 0) { colors.push(this.cells[r][c]); this.cells[r][c] = 0; }
    const spots = [];
    for (let r = 0; r < this.size; r++) for (let c = 0; c < this.size; c++) spots.push([r, c]);
    for (let i = spots.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [spots[i], spots[j]] = [spots[j], spots[i]];
    }
    colors.forEach((color, i) => { const [r, c] = spots[i]; this.cells[r][c] = color; });
  }

  clearRandomCells(count) {
    const filled = [];
    for (let r = 0; r < this.size; r++)
      for (let c = 0; c < this.size; c++)
        if (this.cells[r][c] !== 0) filled.push([r, c]);
    for (let i = filled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [filled[i], filled[j]] = [filled[j], filled[i]];
    }
    const removed = filled.slice(0, count);
    for (const [r, c] of removed) this.cells[r][c] = 0;
    return removed;
  }
}
