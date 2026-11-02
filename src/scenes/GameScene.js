import {
  GRID_SIZE, CELL, GRID_X, GRID_Y, TRAY_Y, TRAY_SCALE, GAME_WIDTH, GAME_HEIGHT,
  COLORS, PIECE_COLORS, SCORE_PER_CELL, SCORE_PER_LINE, COMBO_BONUS, CLEAR_WORDS,
  STORAGE_KEY, TUTORIAL_KEY, FONT_DISPLAY, FONT_UI,
} from '../config.js';
import { randomShape, shapeBounds } from '../shapes.js';
import { Board } from '../board.js';
import { Ads } from '../ads.js';
import { sfx, haptic } from '../audio.js';
import { makeHanjiTexture, makeScrapTexture, drawPatch, drawEmptyCell, drawBoardFrame, makeSeal } from '../art.js';

const TRAY_SLOTS = 3;
const TRAY_SLOT_X = [GAME_WIDTH * 0.2, GAME_WIDTH * 0.5, GAME_WIDTH * 0.8];
const DRAG_LIFT = 100; // 드래그 중 손가락 위로 띄우는 거리(px)
// 대기열 슬롯 하나의 터치 영역(게임 좌표). 조각이 작아도 슬롯 전체가 잡히도록 넓게 잡습니다.
const SLOT_W = GAME_WIDTH * 0.3;
const SLOT_H = 300;

export class GameScene extends Phaser.Scene {
  constructor() { super('Game'); }

  create() {
    this.board = new Board();
    this.score = 0;
    this.best = this.loadBest();
    this.continueUsed = false;
    this.undoUsed = false;
    this.undoSnapshot = null;   // { cells, score, tray: [{shape,color,slot}|null] }
    this.gameOverCount = 0;
    this.isGameOver = false;
    this.moves = 0;

    makeHanjiTexture(this);
    makeScrapTexture(this);
    this.add.image(0, 0, 'hanji').setOrigin(0).setDepth(-10);
    this.drawStaticGrid();
    this.flash = this.add.rectangle(0, 0, GAME_WIDTH, GAME_HEIGHT, 0xffffff, 0).setOrigin(0).setDepth(40);
    this.cellSprites = Array.from({ length: GRID_SIZE }, () => Array(GRID_SIZE).fill(null));
    this.ghost = this.add.graphics().setDepth(5);

    this.createHeader();

    this.tray = [null, null, null];
    this.refillTray();
    this.setupInput();

    if (!this.tutorialDone()) this.startTutorial();
  }

  // ---------- 상단: 제목, 낙관, 점수, HUD ----------
  createHeader() {
    this.add.text(GAME_WIDTH / 2 - 30, 48, '조각보', {
      fontFamily: FONT_DISPLAY, fontSize: '46px', color: COLORS.ink, fontStyle: 'bold',
    }).setOrigin(0.5);
    makeSeal(this, GAME_WIDTH / 2 + 70, 46, '조각\n보', 52).setDepth(1);

    this.scoreText = this.add.text(GAME_WIDTH / 2, 128, '0', {
      fontFamily: FONT_DISPLAY, fontSize: '86px', color: COLORS.ink, fontStyle: 'bold',
    }).setOrigin(0.5);
    this.bestText = this.add.text(GAME_WIDTH / 2, 196, `최고 ${this.best}`, {
      fontFamily: FONT_UI, fontSize: '28px', color: COLORS.textDim,
    }).setOrigin(0.5);

    this.muteBtn = this.iconButton(GAME_WIDTH - 70, 70, sfx.muted ? '🔇' : '🔊', () => {
      const m = sfx.toggleMute();
      this.muteBtn.label.setText(m ? '🔇' : '🔊');
    });

    this.undoBtn = this.iconButton(70, 70, '↶', async () => {
      if (!this.undoSnapshot || this.undoUsed || this.isGameOver) return;
      if (!Ads.rewardedReady()) return;
      const ok = await Ads.showRewarded();
      if (!ok) return;
      this.performUndo();
    });
    this.undoBtn.badge = this.add.text(70, 110, '광고', {
      fontFamily: FONT_UI, fontSize: '16px', color: '#f3e9d6', backgroundColor: '#d9463b', padding: { x: 7, y: 2 },
    }).setOrigin(0.5).setDepth(51);
    this.refreshUndoButton();
  }

  iconButton(x, y, label, onClick) {
    const c = this.add.container(x, y).setDepth(50);
    const bg = this.add.graphics();
    bg.fillStyle(COLORS.frameDark, 1); bg.fillCircle(2, 3, 36);
    bg.fillStyle(COLORS.frame, 1); bg.fillCircle(0, 0, 36);
    bg.lineStyle(1.5, 0xf3e9d6, 0.5); bg.strokeCircle(0, 0, 30);
    const t = this.add.text(0, 0, label, { fontSize: '34px', color: COLORS.textOnFrame }).setOrigin(0.5);
    c.add([bg, t]);
    c.label = t;
    c.setSize(72, 72);
    // 컨테이너 히트 영역 원점은 setSize 상자의 좌상단 (makePiece 주석 참고)
    c.setInteractive(new Phaser.Geom.Rectangle(-4, -4, 80, 80), Phaser.Geom.Rectangle.Contains);
    c.on('pointerdown', () => {
      sfx.unlock(); sfx.button(); haptic.light();
      this.tweens.add({ targets: c, scale: 0.9, duration: 60, yoyo: true, onComplete: onClick });
    });
    return c;
  }

  refreshUndoButton() {
    const enabled = !!this.undoSnapshot && !this.undoUsed && !this.isGameOver;
    this.undoBtn.setAlpha(enabled ? 1 : 0.35);
    this.undoBtn.badge.setVisible(enabled);
  }

  // ---------- 렌더링 ----------
  drawStaticGrid() {
    const g = this.add.graphics();
    drawBoardFrame(g, GRID_X, GRID_Y, GRID_SIZE * CELL);
    for (let r = 0; r < GRID_SIZE; r++)
      for (let c = 0; c < GRID_SIZE; c++)
        drawEmptyCell(g, GRID_X + c * CELL, GRID_Y + r * CELL, CELL);
  }

  // 천 조각 한 칸을 Graphics로 그려 돌려줍니다. (x, y)는 칸의 좌상단.
  cellRect(color, x, y, size) {
    const g = this.add.graphics();
    drawPatch(g, color, x, y, size);
    return g;
  }

  renderBoard() {
    for (let r = 0; r < GRID_SIZE; r++) {
      for (let c = 0; c < GRID_SIZE; c++) {
        const v = this.board.cells[r][c];
        const existing = this.cellSprites[r][c];
        if (existing && (v === 0 || existing.colorValue !== v)) { existing.destroy(); this.cellSprites[r][c] = null; }
        if (v !== 0 && !this.cellSprites[r][c]) {
          const g = this.cellRect(v, GRID_X + c * CELL, GRID_Y + r * CELL, CELL).setDepth(2);
          g.colorValue = v;
          this.cellSprites[r][c] = g;
        }
      }
    }
  }

  // ---------- 조각 대기열 ----------
  makePiece(slot, shape = randomShape(), color = PIECE_COLORS[Math.floor(Math.random() * PIECE_COLORS.length)]) {
    const { rows, cols } = shapeBounds(shape);
    const container = this.add.container(TRAY_SLOT_X[slot], TRAY_Y).setDepth(10);
    const w = cols * CELL, h = rows * CELL;
    for (const [r, c] of shape) {
      container.add(this.cellRect(color, c * CELL - w / 2, r * CELL - h / 2, CELL));
    }
    container.setSize(w, h);
    // 히트 영역은 컨테이너 로컬 좌표(축소 전)로 지정되므로 TRAY_SCALE로 나눠 실제 슬롯 크기에 맞춥니다.
    // 주의: Phaser는 컨테이너 히트 영역의 원점을 setSize 상자의 좌상단(= -w/2, -h/2 위치)으로 잡습니다.
    // 따라서 중심 기준 사각형을 만들려면 (w - hw) / 2, (h - hh) / 2 만큼 옮겨 적어야 합니다.
    const hw = Math.max(w + 40, SLOT_W / TRAY_SCALE), hh = Math.max(h + 40, SLOT_H / TRAY_SCALE);
    container.setInteractive(new Phaser.Geom.Rectangle((w - hw) / 2, (h - hh) / 2, hw, hh), Phaser.Geom.Rectangle.Contains);
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

  trayData() {
    return this.tray.map(p => p ? { shape: p.data.values.shape, color: p.data.values.color, slot: p.data.values.slot } : null);
  }

  // ---------- 입력 ----------
  setupInput() {
    this.input.on('dragstart', (pointer, obj) => {
      if (this.isGameOver) return;
      sfx.unlock(); sfx.pickup(); haptic.light();
      this.stopTutorialHand();
      obj.setDepth(20);
      obj.setScale(1);
      obj.y -= DRAG_LIFT;
      this.updateGhost(obj);
    });
    this.input.on('drag', (pointer, obj, dragX, dragY) => {
      if (this.isGameOver) return;
      obj.x = dragX;
      obj.y = dragY - DRAG_LIFT;
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
        sfx.reject();
        obj.setDepth(10);
        this.tweens.add({ targets: obj, x: TRAY_SLOT_X[obj.data.values.slot], y: TRAY_Y, scale: TRAY_SCALE, duration: 180, ease: 'Quad.Out' });
        if (this.moves === 0 && !this.tutorialDone()) this.startTutorial();
      }
    });
  }

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
    this.ghost.fillStyle(ok ? COLORS.ghostOk : COLORS.ghostBad, 0.4);
    for (const [r, c] of shape) {
      const rr = pos.row + r, cc = pos.col + c;
      if (rr < 0 || cc < 0 || rr >= GRID_SIZE || cc >= GRID_SIZE) continue;
      this.ghost.fillRoundedRect(GRID_X + cc * CELL + 3, GRID_Y + rr * CELL + 3, CELL - 6, CELL - 6, 6);
    }
  }

  // ---------- 게임 규칙 ----------
  commitPiece(obj, row, col, shape, color) {
    // 되돌리기용 스냅샷: 배치 직전 상태
    this.undoSnapshot = { cells: this.board.snapshot(), score: this.score, tray: this.trayData() };

    this.board.place(shape, row, col, color);
    this.tray[obj.data.values.slot] = null;
    obj.destroy();
    this.renderBoard();
    this.addScore(shape.length * SCORE_PER_CELL);
    this.moves += 1;
    sfx.place(); haptic.medium();
    if (this.moves === 1) this.markTutorialDone();

    const { lines, cleared } = this.board.clearLines();
    if (lines > 0) {
      const b = shapeBounds(shape);
      const origin = { x: GRID_X + (col + b.cols / 2) * CELL, y: GRID_Y + (row + b.rows / 2) * CELL };
      this.animateClear(cleared, origin, lines);
      let gain = lines * SCORE_PER_LINE;
      if (lines >= 2) gain += lines * COMBO_BONUS;
      this.time.delayedCall(120, () => this.addScore(gain));
      sfx.clear(lines); haptic.success(lines);
      this.popScore(gain, origin, lines);
      this.popText(CLEAR_WORDS[Math.min(lines, CLEAR_WORDS.length) - 1], lines >= 2 ? COLORS.accent : '#2b6cb0', lines);
      this.cameras.main.shake(140 + 40 * lines, 0.006 + 0.004 * lines);
      if (lines >= 2) this.screenFlash(0.18 + 0.08 * Math.min(lines, 4));
    }

    if (this.trayEmpty()) this.refillTray();
    this.refreshUndoButton();
    this.time.delayedCall(60, () => this.checkGameOver());
  }

  performUndo() {
    const s = this.undoSnapshot;
    if (!s) return;
    this.board.restore(s.cells);
    this.score = s.score;
    this.scoreText.setText(String(this.score));
    for (const p of this.tray) if (p) p.destroy();
    this.tray = s.tray.map(d => d ? this.makePiece(d.slot, d.shape, d.color) : null);
    this.renderBoard();
    this.undoSnapshot = null;
    this.undoUsed = true;
    this.moves = Math.max(0, this.moves - 1);
    this.refreshUndoButton();
    this.popText('되돌리기', COLORS.accent);
  }

  // 놓은 위치에서 바깥으로 퍼지는 파동 순서로 셀이 하얗게 번쩍인 뒤 터지며 천 조각 파편을 뿌립니다.
  animateClear(cells, origin, lines) {
    for (const [r, c] of cells) {
      const spr = this.cellSprites[r][c];
      if (!spr) continue;
      this.cellSprites[r][c] = null;
      const cx = GRID_X + c * CELL + CELL / 2, cy = GRID_Y + r * CELL + CELL / 2;
      const dist = Math.hypot(cx - origin.x, cy - origin.y);
      const delay = Math.min(220, dist * 0.35);
      const color = spr.colorValue || 0xffffff;

      const white = this.add.image(cx, cy, 'scrap').setDisplaySize(CELL - 6, CELL - 6).setDepth(6).setAlpha(0);
      this.tweens.add({ targets: white, alpha: { from: 0, to: 1 }, duration: 60, delay, yoyo: true, hold: 40, onComplete: () => white.destroy() });

      this.tweens.add({
        targets: spr, delay, duration: 220, ease: 'Cubic.Out',
        alpha: 0, scaleX: 1.35, scaleY: 1.35, x: -(cx - GRID_X - c * CELL) * 0.35, y: -(cy - GRID_Y - r * CELL) * 0.35,
        onComplete: () => spr.destroy(),
      });

      this.time.delayedCall(delay + 40, () => {
        const emitter = this.add.particles(cx, cy, 'scrap', {
          speed: { min: 120, max: 320 + 60 * lines },
          angle: { min: 0, max: 360 },
          rotate: { min: 0, max: 360 },
          scale: { start: 1, end: 0.2 },
          alpha: { start: 1, end: 0 },
          lifespan: { min: 350, max: 650 },
          gravityY: 600,
          tint: [color, 0xf3e9d6, color],
          quantity: 6 + 2 * lines,
          emitting: false,
        }).setDepth(35);
        emitter.explode();
        this.time.delayedCall(800, () => emitter.destroy());
      });
    }
  }

  screenFlash(alpha) {
    this.flash.setAlpha(alpha);
    this.tweens.add({ targets: this.flash, alpha: 0, duration: 260, ease: 'Quad.Out' });
  }

  popScore(gain, origin, lines) {
    const t = this.add.text(origin.x, origin.y, `+${gain}`, {
      fontFamily: FONT_DISPLAY, fontStyle: 'bold', fontSize: `${44 + 8 * Math.min(lines, 4)}px`, color: '#f3e9d6',
      stroke: '#24375c', strokeThickness: 8,
    }).setOrigin(0.5).setDepth(36).setScale(0.4);
    this.tweens.add({ targets: t, scale: 1, duration: 160, ease: 'Back.Out' });
    this.tweens.add({ targets: t, y: origin.y - 140, alpha: 0, duration: 800, delay: 250, ease: 'Quad.In', onComplete: () => t.destroy() });
  }

  addScore(n) {
    this.score += n;
    this.scoreText.setText(String(this.score));
    this.tweens.add({ targets: this.scoreText, scale: 1.15, duration: 80, yoyo: true });
    if (this.score > this.best) {
      this.best = this.score;
      this.bestText.setText(`최고 ${this.best}`);
      this.saveBest(this.best);
    }
  }

  popText(msg, color, lines = 1) {
    const size = 60 + 12 * Math.min(Math.max(lines - 1, 0), 4);
    const t = this.add.text(GAME_WIDTH / 2, GRID_Y - 60, msg, {
      fontFamily: FONT_DISPLAY, fontStyle: 'bold', fontSize: `${size}px`, color,
      stroke: '#f3e9d6', strokeThickness: 10,
    }).setOrigin(0.5).setDepth(45).setScale(0.3).setAngle(-6 + Math.random() * 12);
    this.tweens.add({ targets: t, scale: 1.1, duration: 180, ease: 'Back.Out' });
    this.tweens.add({ targets: t, scale: 1, duration: 120, delay: 180 });
    this.tweens.add({ targets: t, y: t.y - 60, alpha: 0, duration: 500, delay: 650, ease: 'Quad.In', onComplete: () => t.destroy() });
  }

  checkGameOver() {
    const anyMove = this.tray.some(p => p && this.board.anyPlacement(p.data.values.shape));
    if (anyMove) return;
    this.isGameOver = true;
    this.gameOverCount += 1;
    this.refreshUndoButton();
    sfx.gameOver(); haptic.fail();
    this.showGameOver();
  }

  // ---------- 튜토리얼: 손가락 힌트 ----------
  tutorialDone() {
    try { return localStorage.getItem(TUTORIAL_KEY) === '1'; } catch { return true; }
  }
  markTutorialDone() {
    try { localStorage.setItem(TUTORIAL_KEY, '1'); } catch { /* ignore */ }
    this.stopTutorialHand();
  }

  startTutorial() {
    this.stopTutorialHand();
    const piece = this.tray.find(p => p);
    if (!piece) return;
    const { shape, rows, cols } = piece.data.values;
    let target = null;
    for (let r = 0; r < GRID_SIZE && !target; r++)
      for (let c = 0; c < GRID_SIZE && !target; c++)
        if (this.board.canPlace(shape, r, c)) target = { r, c };
    if (!target) return;

    const tx = GRID_X + target.c * CELL + (cols * CELL) / 2;
    const ty = GRID_Y + target.r * CELL + (rows * CELL) / 2 + DRAG_LIFT;

    this.tutorialHint = this.add.text(GAME_WIDTH / 2, GRID_Y - 28, '천 조각을 끌어다 보에 붙여 보세요', {
      fontFamily: FONT_UI, fontSize: '26px', color: COLORS.accent,
    }).setOrigin(0.5).setDepth(60);

    this.tutorialHand = this.add.text(piece.x, piece.y + 30, '👆', { fontSize: '72px' }).setOrigin(0.5, 0).setDepth(60);
    this.tutorialTween = this.tweens.chain({
      targets: this.tutorialHand,
      loop: -1,
      tweens: [
        { alpha: { from: 0, to: 1 }, duration: 250 },
        { scale: 0.85, duration: 150 },
        { x: tx, y: ty + 30, duration: 900, ease: 'Sine.InOut' },
        { scale: 1, duration: 150 },
        { alpha: 0, duration: 300, delay: 300 },
        { x: piece.x, y: piece.y + 30, duration: 1 },
        { duration: 400 },
      ],
    });
  }

  stopTutorialHand() {
    if (this.tutorialTween) { this.tutorialTween.destroy(); this.tutorialTween = null; }
    if (this.tutorialHand) { this.tutorialHand.destroy(); this.tutorialHand = null; }
    if (this.tutorialHint) { this.tutorialHint.destroy(); this.tutorialHint = null; }
  }

  // ---------- 게임오버 / 수익화 지점 ----------
  showGameOver() {
    const ui = this.add.container(0, 0).setDepth(100);
    const dim = this.add.rectangle(0, 0, GAME_WIDTH, GAME_HEIGHT, 0x2a2320, 0.55).setOrigin(0);
    const panel = this.add.graphics();
    panel.fillStyle(COLORS.frameDark, 1);
    panel.fillRoundedRect(64, 386, GAME_WIDTH - 120, 520, 28);
    panel.fillStyle(COLORS.frame, 1);
    panel.fillRoundedRect(60, 380, GAME_WIDTH - 120, 520, 28);
    panel.lineStyle(1.5, 0xf3e9d6, 0.4);
    panel.strokeRoundedRect(74, 394, GAME_WIDTH - 148, 492, 20);
    const title = this.add.text(GAME_WIDTH / 2, 452, '더 붙일 자리가 없어요', {
      fontFamily: FONT_DISPLAY, fontStyle: 'bold', fontSize: '44px', color: COLORS.textOnFrame,
    }).setOrigin(0.5);
    const scoreLine = this.add.text(GAME_WIDTH / 2, 530, `점수 ${this.score}   최고 ${this.best}`, {
      fontFamily: FONT_UI, fontSize: '30px', color: '#cdbb9c',
    }).setOrigin(0.5);
    ui.add([dim, panel, title, scoreLine]);

    if (!this.continueUsed && Ads.rewardedReady()) {
      ui.add(this.makeButton(GAME_WIDTH / 2, 640, '▶ 광고 보고 이어 짓기', 0x6b8e4e, async () => {
        const ok = await Ads.showRewarded();
        if (!ok) return;
        this.continueUsed = true;
        ui.destroy();
        this.board.clearRandomCells(20);
        this.renderBoard();
        this.isGameOver = false;
        this.undoSnapshot = null;
        this.refreshUndoButton();
        this.checkGameOver();
      }));
    }

    ui.add(this.makeButton(GAME_WIDTH / 2, 760, '새 보 짓기', 0xe8b63a, async () => {
      if (this.gameOverCount % 3 === 0) await Ads.showInterstitial();
      this.scene.restart();
    }));
  }

  makeButton(x, y, label, color, onClick) {
    const c = this.add.container(x, y);
    const bg = this.add.graphics();
    bg.fillStyle(0x000000, 0.25); bg.fillRoundedRect(-238, -40, 480, 88, 22);
    bg.fillStyle(color, 1); bg.fillRoundedRect(-240, -44, 480, 88, 22);
    bg.lineStyle(1.5, 0xf3e9d6, 0.6); bg.strokeRoundedRect(-232, -36, 464, 72, 16);
    const t = this.add.text(0, 0, label, { fontFamily: FONT_UI, fontSize: '32px', color: '#1d1a17' }).setOrigin(0.5);
    c.add([bg, t]);
    c.setSize(480, 88);
    c.setInteractive(new Phaser.Geom.Rectangle(0, 0, 480, 88), Phaser.Geom.Rectangle.Contains);
    c.on('pointerdown', () => {
      sfx.button(); haptic.light();
      this.tweens.add({ targets: c, scale: 0.95, duration: 60, yoyo: true, onComplete: onClick });
    });
    return c;
  }

  // ---------- 저장 ----------
  loadBest() {
    try { return Number(localStorage.getItem(STORAGE_KEY)) || 0; } catch { return 0; }
  }
  saveBest(v) {
    try { localStorage.setItem(STORAGE_KEY, String(v)); } catch { /* ignore */ }
  }
}
