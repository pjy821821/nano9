import { GAME_WIDTH, GAME_HEIGHT, COLORS, FONT_DISPLAY, FONT_UI, STORAGE_KEY } from '../config.js';
import { makeHanjiTexture, makeSeal, drawPatch } from '../art.js';
import { Records } from '../records.js';
import { todayKey, formatKey } from '../rng.js';
import { sfx, haptic } from '../audio.js';

// 시작 화면: 모드 선택과 내 기록
export class MenuScene extends Phaser.Scene {
  constructor() { super('Menu'); }

  create() {
    makeHanjiTexture(this);
    this.add.image(0, 0, 'hanji').setOrigin(0).setDepth(-10);

    // 장식: 흩어진 천 조각
    const deco = this.add.graphics().setDepth(-5).setAlpha(0.9);
    const spots = [[70, 150, 0xd9463b], [600, 120, 0x2b6cb0], [120, 1130, 0xe8b63a], [610, 1150, 0x6b8e4e], [40, 700, 0x8a3b6b], [650, 760, 0xe78fa6]];
    for (const [x, y, c] of spots) drawPatch(deco, c, x, y, 64);

    this.add.text(GAME_WIDTH / 2 - 40, 300, '조각보', {
      fontFamily: FONT_DISPLAY, fontSize: '112px', color: COLORS.ink, fontStyle: 'bold',
    }).setOrigin(0.5);
    makeSeal(this, GAME_WIDTH / 2 + 150, 292, '조각\n보', 76);
    this.add.text(GAME_WIDTH / 2, 392, '천 조각을 붙여 한 땀 두 땀 보를 지어요', {
      fontFamily: FONT_UI, fontSize: '26px', color: COLORS.textDim,
    }).setOrigin(0.5);

    const best = this.loadBest();
    const day = todayKey();
    const todayBest = Records.dailyBest(day);

    this.modeButton(GAME_WIDTH / 2, 540, '무한 모드', best ? `최고 ${best.toLocaleString()}점` : '아직 기록이 없어요', 0x2b6cb0,
      () => this.scene.start('Game', { mode: 'endless' }));

    this.modeButton(GAME_WIDTH / 2, 700, `오늘의 조각보 · ${formatKey(day)}`,
      todayBest ? `오늘 최고 ${todayBest.score.toLocaleString()}점 · 다시 도전` : '모두 같은 조각 순서 · 3개를 다 써야 보충', 0xd9463b,
      () => this.scene.start('Game', { mode: 'daily', dayKey: day }));

    this.modeButton(GAME_WIDTH / 2, 860, '내 기록', '무한 모드 상위 10 · 최근 7일 데일리', 0x6b8e4e, () => this.showRecords());

    this.add.text(GAME_WIDTH / 2, GAME_HEIGHT - 60, '한국 조각보 테마 블록 퍼즐', {
      fontFamily: FONT_UI, fontSize: '20px', color: COLORS.textDim,
    }).setOrigin(0.5);
  }

  modeButton(x, y, title, sub, color, onClick) {
    const W = 560, H = 120;
    const c = this.add.container(x, y);
    const bg = this.add.graphics();
    bg.fillStyle(COLORS.frameDark, 1); bg.fillRoundedRect(-W / 2 + 3, -H / 2 + 4, W, H, 22);
    bg.fillStyle(color, 1); bg.fillRoundedRect(-W / 2, -H / 2, W, H, 22);
    bg.lineStyle(2, 0xf3e9d6, 0.55); bg.strokeRoundedRect(-W / 2 + 8, -H / 2 + 8, W - 16, H - 16, 16);
    const t = this.add.text(0, -18, title, { fontFamily: FONT_DISPLAY, fontStyle: 'bold', fontSize: '36px', color: '#f3e9d6' }).setOrigin(0.5);
    const s = this.add.text(0, 26, sub, { fontFamily: FONT_UI, fontSize: '22px', color: '#f3e9d6' }).setOrigin(0.5).setAlpha(0.85);
    c.add([bg, t, s]);
    c.setSize(W, H);
    c.setInteractive(new Phaser.Geom.Rectangle(0, 0, W, H), Phaser.Geom.Rectangle.Contains);
    c.on('pointerdown', () => {
      sfx.unlock(); sfx.button(); haptic.light();
      this.tweens.add({ targets: c, scale: 0.96, duration: 70, yoyo: true, onComplete: onClick });
    });
    return c;
  }

  showRecords() {
    const ui = this.add.container(0, 0).setDepth(100);
    const dim = this.add.rectangle(0, 0, GAME_WIDTH, GAME_HEIGHT, 0x2a2320, 0.6).setOrigin(0).setInteractive();
    const panel = this.add.graphics();
    panel.fillStyle(COLORS.frameDark, 1); panel.fillRoundedRect(54, 164, GAME_WIDTH - 100, 960, 28);
    panel.fillStyle(COLORS.frame, 1); panel.fillRoundedRect(50, 158, GAME_WIDTH - 100, 960, 28);
    ui.add([dim, panel]);

    const data = Records.all();
    const line = (y, text, size = 26, color = '#f3e9d6', style = {}) =>
      ui.add(this.add.text(GAME_WIDTH / 2, y, text, { fontFamily: FONT_UI, fontSize: `${size}px`, color, ...style }).setOrigin(0.5));

    line(212, '무한 모드 · 상위 10', 34, '#f3e9d6', { fontFamily: FONT_DISPLAY, fontStyle: 'bold' });
    if (data.endless.length === 0) line(270, '아직 기록이 없어요', 24, '#cdbb9c');
    data.endless.forEach((e, i) => {
      const d = new Date(e.at);
      const t = this.add.text(100, 262 + i * 40, `${i + 1}위`, { fontFamily: FONT_UI, fontSize: '24px', color: i < 3 ? '#e8b63a' : '#cdbb9c' }).setOrigin(0, 0.5);
      const sc = this.add.text(GAME_WIDTH - 230, 262 + i * 40, `${e.score.toLocaleString()}점`, { fontFamily: FONT_UI, fontSize: '26px', color: '#f3e9d6' }).setOrigin(1, 0.5);
      const dt = this.add.text(GAME_WIDTH - 100, 262 + i * 40, `${d.getMonth() + 1}/${d.getDate()}`, { fontFamily: FONT_UI, fontSize: '20px', color: '#cdbb9c' }).setOrigin(1, 0.5);
      ui.add([t, sc, dt]);
    });

    line(700, '오늘의 조각보 · 최근 7일', 34, '#f3e9d6', { fontFamily: FONT_DISPLAY, fontStyle: 'bold' });
    const recent = Records.recentDaily(7);
    if (recent.length === 0) line(758, '아직 기록이 없어요', 24, '#cdbb9c');
    recent.forEach((e, i) => {
      const t = this.add.text(100, 750 + i * 40, formatKey(e.day), { fontFamily: FONT_UI, fontSize: '24px', color: '#cdbb9c' }).setOrigin(0, 0.5);
      const sc = this.add.text(GAME_WIDTH - 230, 750 + i * 40, `${e.score.toLocaleString()}점`, { fontFamily: FONT_UI, fontSize: '26px', color: '#f3e9d6' }).setOrigin(1, 0.5);
      const st = this.add.text(GAME_WIDTH - 100, 750 + i * 40, e.maxStreak >= 2 ? `연속 ×${e.maxStreak}` : '', { fontFamily: FONT_UI, fontSize: '20px', color: '#e8b63a' }).setOrigin(1, 0.5);
      ui.add([t, sc, st]);
    });

    const close = this.add.text(GAME_WIDTH / 2, 1070, '닫기', { fontFamily: FONT_UI, fontSize: '30px', color: '#1d1a17', backgroundColor: '#e8b63a', padding: { x: 40, y: 10 } })
      .setOrigin(0.5).setInteractive();
    close.on('pointerdown', () => { sfx.button(); ui.destroy(); });
    dim.on('pointerdown', () => ui.destroy());
    ui.add(close);
  }

  loadBest() {
    try { return Number(localStorage.getItem(STORAGE_KEY)) || 0; } catch { return 0; }
  }
}
