import {
  GRID_SIZE, CELL, GRID_X, GRID_Y, TRAY_Y, TRAY_SCALE, GAME_WIDTH, GAME_HEIGHT,
  COLORS, PIECE_COLORS, SCORE_PER_CELL, SCORE_PER_LINE, COMBO_BONUS, STORAGE_KEY,
} from '../config.js';
import { randomShape, shapeBounds } from '../shapes.js';
import { Board } from '../board.js';
import { Ads } from '../ads.js';

const TRAY_SLOTS = 3;
const TRAY_SLOT_X = [GAME_WIDTH * 0.2, GAME_WIDTH * 0.5, GAME_WIDTH * 0.8];

export class GameScene extends Phaser.Scene {
  constructor() { super('Game'); }

  create() {
    this.board = new Board();
    this.score = 0;
    this.best = this.loadBest();
    this.continueUsed = false;
    this.gameOverCount = 0;

    this.drawStaticGrid();
    this.cellSprites = Array.from({ length: GRID_SIZE }, () => Array(GRID_SIZE).fill(null));
    this.ghost = this.add.graphics().setDepth(5);

    this.scoreText = this.add.text(GAME_WIDTH / 2, 110, '0', {
      fontFamily: 'Arial Black, sans-serif', fontSize: '84px', color: COLORS.text,
    }).setOrigin(0.5);
    this.bestText = this.add.text(GAME_WIDTH / 2, 180, `BEST ${this.best}`, {
      fontFamily: 'Arial, sans-serif', fontSize: '30px', color: COLORS.textDim,
    }).setOrigin(0.5);

    this.tray = [null, null, null];
    this.refillTray();
    this.setupInput();
  }

  // ---------- 렌더링 ----------
  drawStaticGrid() {
    const g = this.add.graphics();
    g.fillStyle(0x151833, 1);
    g.fillRoundedRect(GRID_X - 12, GRID_Y - 12, GRID_SIZE * CELL + 24, GRID_SIZE * CELL + 24, 18);
    for (let r = 0; r < GRID_SIZE; r++) {
      for (let c = 0; c < GRID_SIZE; c++) {
        g.fillStyle(COLORS.cellEmpty, 1);
        g.fillRoundedRect(GRID_X + c * CELL + 3, GRID_Y + r * CELL + 3, CELL - 6, CELL - 6, 8);
      }
    }
  }

  cellRect(color, x, y, size, alpha = 1) {
    const g = this.add.graphics();
    g.fillStyle(color, alpha);
    g.fillRoundedRect(x + 3, y + 3, size - 6, size - 6, 8);
    g.fillStyle(0xffffff, 0.18 * alpha);
    g.fillRoundedRect(x + 8, y + 8, size - 16, (size - 16) * 0.35, 6);
    return g;
  }

  renderBoard() {
    for (let r = 0; r < GRID_SIZE; r++) {
      for (let c = 0; c < GRID_SIZE; c++) {
        const v = this.board.cells[r][c];
        const existing = this.cellSprites[r][c];
        if (v === 0 && existing) { existing.destroy(); this.cellSprites[r][c] = null; }
        if (v !== 0 && !existing) {
          this.cellSprites[r][c] = this.cellRect(v, GRID_X + c * CELL, GRID_Y + r * CELL, CELL).setDepth(2);
        }
      }
    }
  }

  // ---------- 조각 대기열 ----------
  makePiece(slot) {
    const shape = randomShape();
    const color = PIECE_COLORS[Math.floor(Math.random() * PIECE_COLORS.length)];
    const { rows, cols } = shapeBounds(shape);
    const container = this.add.container(TRAY_SLOT_X[slot], TRAY_Y).setDepth(10);
    const w = cols * CELL, h = rows * CELL;
    for (const [r, c] of shape) {
      container.add(this.cellRect(color, c * CELL - w / 2, r * CELL - h / 2, CELL));
    }
    container.setSize(w, h);
    container.setScale(TRAY_SCALE);
    container.setInteractive(new Phaser.Geom.Rectangle(-w / 2 - 20, -h / 2 - 20, w + 40, h + 40), Phaser.Geom.Rectangle.Contains);
    this.input.setDraggable(container);
    container.setData({ shape, color, slot, rows, cols });
    container.setAlpha(0).setScale(0.2);
    this.tweens.add({ targets: container, alpha: 1, scale: TRAY_SCALE, duration: 220, ease: 'Back.Out' });
    return container;
  }

  refillTray() {
    for (let i = 0; i < TRAY_SLOTS; i++) if (!this.tray[i]) this.tray[i] = this.makePiece(i);
  }

  trayEmpty() { return this.tray.every(p => p === null); }

  // ---------- 입력 ----------
  setupInput() {
    this.input.on('dragstart', (pointer, obj) => {
      if (this.isGameOver) return;
      obj.setDepth(20);
      this.tweens.add({ targets: obj, scale: 1, duration: 120 });
    });
    this.input.on('drag', (pointer, obj, dragX, dragY) => {
      if (this.isGameOver) return;
      // 손가락에 가려지지 않도록 조각을 위로 띄움
      obj.x = dragX;
      obj.y = dragY - 110;
      this.updateGhost(obj);
    });
    this.input.on('dragend', (pointer, obj) => {
      if (this.isGameOver) return;
      this.ghost.clear();
      const pos = this.pieceGridPos(obj);
      const { shape, color } = obj.data.values;
      if (pos && this.board.canPlace(shape, pos.row, pos.col)) {
        this.commitPiece(obj, pos.row, pos.col, shape, color);
      } else {
        obj.setDepth(10);
        this.tweens.add({ targets: obj, x: TRAY_SLOT_X[obj.data.values.slot], y: TRAY_Y, scale: TRAY_SCALE, duration: 180, ease: 'Quad.Out' });
      }
    });
  }

  // 조각 컨테이너의 좌상단 셀이 격자의 어느 칸에 해당하는지 계산
  pieceGridPos(obj) {
    const { rows, cols } = obj.data.values;
    const left = obj.x - (cols * CELL) / 2;
    const top = obj.y - (rows * CELL) / 2;
    const col = Math.round((left - GRID_X) / CELL);
    const row = Math.round((top - GRID_Y) / CELL);
    if (row < -1 || col < -1 || row > GRID_SIZE || col > GRID_SIZE) return null;
    return { row, col };
  }

  updateGhost(obj) {
    this.ghost.clear();
    const pos = this.pieceGridPos(obj);
    if (!pos) return;
    const { shape } = obj.data.values;
    const ok = this.board.canPlace(shape, pos.row, pos.col);
    this.ghost.fillStyle(ok ? COLORS.ghostOk : COLORS.ghostBad, 0.35);
    for (const [r, c] of shape) {
      const rr = pos.row + r, cc = pos.col + c;
      if (rr < 0 || cc < 0 || rr >= GRID_SIZE || cc >= GRID_SIZE) continue;
      this.ghost.fillRoundedRect(GRID_X + cc * CELL + 3, GRID_Y + rr * CELL + 3, CELL - 6, CELL - 6, 8);
    }
  }

  // ---------- 게임 규칙 ----------
  commitPiece(obj, row, col, shape, color) {
    this.board.place(shape, row, col, color);
    this.tray[obj.data.values.slot] = null;
    obj.destroy();
    this.renderBoard();
    this.addScore(shape.length * SCORE_PER_CELL);

    const { lines, cleared } = this.board.clearLines();
    if (lines > 0) {
      this.animateClear(cleared);
      let gain = lines * SCORE_PER_LINE;
      if (lines >= 2) gain += lines * COMBO_BONUS;
      this.addScore(gain);
      this.popText(lines >= 2 ? `COMBO x${lines}!` : 'LINE!', lines >= 2 ? COLORS.accent : '#7fe3a5');
    }

    if (this.trayEmpty()) this.refillTray();
    this.time.delayedCall(60, () => this.checkGameOver());
  }

  animateClear(cells) {
    for (const [r, c] of cells) {
      const spr = this.cellSprites[r][c];
      if (!spr) continue;
      this.cellSprites[r][c] = null;
      this.tweens.add({ targets: spr, alpha: 0, scaleX: 0.3, scaleY: 0.3, duration: 180, onComplete: () => spr.destroy() });
    }
  }

  addScore(n) {
    this.score += n;
    this.scoreText.setText(String(this.score));
    this.tweens.add({ targets: this.scoreText, scale: 1.15, duration: 80, yoyo: true });
    if (this.score > this.best) {
      this.best = this.score;
      this.bestText.setText(`BEST ${this.best}`);
      this.saveBest(this.best);
    }
  }

  popText(msg, color) {
    const t = this.add.text(GAME_WIDTH / 2, GRID_Y + GRID_SIZE * CELL / 2, msg, {
      fontFamily: 'Arial Black, sans-serif', fontSize: '64px', color,
    }).setOrigin(0.5).setDepth(30);
    this.tweens.add({ targets: t, y: t.y - 120, alpha: 0, duration: 700, ease: 'Quad.Out', onComplete: () => t.destroy() });
  }

  checkGameOver() {
    const anyMove = this.tray.some(p => p && this.board.anyPlacement(p.data.values.shape));
    if (anyMove) return;
    this.isGameOver = true;
    this.gameOverCount += 1;
    this.showGameOver();
  }

  // ---------- 게임오버 / 수익화 지점 ----------
  showGameOver() {
    const ui = this.add.container(0, 0).setDepth(100);
    const dim = this.add.rectangle(0, 0, GAME_WIDTH, GAME_HEIGHT, 0x000000, 0.65).setOrigin(0);
    const panel = this.add.graphics();
    panel.fillStyle(0x242a4d, 1);
    panel.fillRoundedRect(60, 380, GAME_WIDTH - 120, 520, 28);
    const title = this.add.text(GAME_WIDTH / 2, 450, '더 놓을 곳이 없어요', {
      fontFamily: 'Arial Black, sans-serif', fontSize: '44px', color: COLORS.text,
    }).setOrigin(0.5);
    const scoreLine = this.add.text(GAME_WIDTH / 2, 530, `점수 ${this.score}   최고 ${this.best}`, {
      fontFamily: 'Arial, sans-serif', fontSize: '32px', color: COLORS.textDim,
    }).setOrigin(0.5);
    ui.add([dim, panel, title, scoreLine]);

    // 보상형 광고로 이어하기: 1판당 1회. 핵심 수익 지점.
    if (!this.continueUsed && Ads.rewardedReady()) {
      ui.add(this.makeButton(GAME_WIDTH / 2, 640, '▶ 광고 보고 이어하기', 0x06d6a0, async () => {
        const ok = await Ads.showRewarded();
        if (!ok) return;
        this.continueUsed = true;
        ui.destroy();
        this.board.clearRandomCells(20);
        this.renderBoard();
        this.isGameOver = false;
        this.checkGameOver();
      }));
    }

    ui.add(this.makeButton(GAME_WIDTH / 2, 760, '다시 시작', 0x4cc9f0, async () => {
      // 전면 광고는 3판마다 1회. 빈도는 리텐션 데이터를 보고 조정.
      if (this.gameOverCount % 3 === 0) await Ads.showInterstitial();
      this.scene.restart();
    }));
  }

  makeButton(x, y, label, color, onClick) {
    const c = this.add.container(x, y);
    const bg = this.add.graphics();
    bg.fillStyle(color, 1);
    bg.fillRoundedRect(-240, -44, 480, 88, 22);
    const t = this.add.text(0, 0, label, { fontFamily: 'Arial Black, sans-serif', fontSize: '34px', color: '#102030' }).setOrigin(0.5);
    c.add([bg, t]);
    c.setSize(480, 88);
    c.setInteractive(new Phaser.Geom.Rectangle(-240, -44, 480, 88), Phaser.Geom.Rectangle.Contains);
    c.on('pointerdown', () => this.tweens.add({ targets: c, scale: 0.95, duration: 60, yoyo: true, onComplete: onClick }));
    return c;
  }

  // ---------- 저장 ----------
  loadBest() {
    try { return Number(localStorage.getItem(STORAGE_KEY)) || 0; } catch { return 0; }
  }
  saveBest(v) {
    try { localStorage.setItem(STORAGE_KEY, String(v)); } catch { /* 사생활 보호 모드 등 */ }
  }
}
