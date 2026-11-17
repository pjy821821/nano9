// 조각보 테마 드로잉 헬퍼. 이미지 파일 없이 Graphics/Canvas로 그립니다.
import { GAME_WIDTH, GAME_HEIGHT, CELL, COLORS, SAEKDONG, FONT_DISPLAY } from './config.js';

// 한지 바탕: 섬유 결과 작은 점을 캔버스 텍스처로 생성 (한 번만)
export function makeHanjiTexture(scene) {
  if (scene.textures.exists('hanji')) return;
  const tex = scene.textures.createCanvas('hanji', GAME_WIDTH, GAME_HEIGHT);
  const ctx = tex.getContext();
  ctx.fillStyle = '#f3e9d6';
  ctx.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
  // 섬유 결: 짧고 가는 선
  ctx.strokeStyle = 'rgba(181,154,116,0.16)';
  ctx.lineWidth = 1;
  for (let i = 0; i < 900; i++) {
    const x = Math.random() * GAME_WIDTH, y = Math.random() * GAME_HEIGHT;
    const len = 6 + Math.random() * 26, a = (Math.random() - 0.5) * 0.9;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x + Math.cos(a) * len, y + Math.sin(a) * len);
    ctx.stroke();
  }
  // 반점
  for (let i = 0; i < 350; i++) {
    ctx.fillStyle = `rgba(150,120,80,${0.04 + Math.random() * 0.08})`;
    ctx.beginPath();
    ctx.arc(Math.random() * GAME_WIDTH, Math.random() * GAME_HEIGHT, 0.6 + Math.random() * 1.6, 0, Math.PI * 2);
    ctx.fill();
  }
  // 가장자리 비네트(은은하게)
  const g = ctx.createRadialGradient(GAME_WIDTH / 2, GAME_HEIGHT / 2, GAME_HEIGHT * 0.35, GAME_WIDTH / 2, GAME_HEIGHT / 2, GAME_HEIGHT * 0.8);
  g.addColorStop(0, 'rgba(120,90,50,0)');
  g.addColorStop(1, 'rgba(120,90,50,0.14)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
  tex.refresh();
}

// 파편 파티클용 작은 천 조각 텍스처
export function makeScrapTexture(scene) {
  if (scene.textures.exists('scrap')) return;
  const g = scene.make.graphics({ x: 0, y: 0, add: false });
  g.fillStyle(0xffffff, 1);
  g.fillRoundedRect(0, 0, 14, 10, 2);
  g.generateTexture('scrap', 14, 10);
  g.destroy();
}

function shade(color, amt) {
  const c = Phaser.Display.Color.ValueToColor(color);
  const f = amt >= 0 ? (v) => v + (255 - v) * amt : (v) => v * (1 + amt);
  return Phaser.Display.Color.GetColor(f(c.red), f(c.green), f(c.blue));
}

// 천 조각 한 칸: 바탕색 + 결(사선) + 바느질 땀(점선 테두리) + 윗부분 광택
export function drawPatch(g, color, x, y, size) {
  const pad = 3, r = 6;
  const w = size - pad * 2, h = size - pad * 2;
  const x0 = x + pad, y0 = y + pad;
  g.fillStyle(color, 1);
  g.fillRoundedRect(x0, y0, w, h, r);
  // 천 결: 얇은 사선
  g.lineStyle(1, shade(color, -0.18), 0.35);
  for (let d = 10; d < w + h; d += 9) {
    const sx = Math.max(x0, x0 + d - h), sy = Math.min(y0 + h, y0 + d);
    const ex = Math.min(x0 + w, x0 + d), ey = Math.max(y0, y0 + d - w);
    g.lineBetween(sx, sy, ex, ey);
  }
  // 빛 받는 윗부분
  g.fillStyle(0xffffff, 0.14);
  g.fillRoundedRect(x0 + 4, y0 + 4, w - 8, h * 0.32, 4);
  // 바느질 땀: 안쪽 점선
  g.lineStyle(2, shade(color, 0.55), 0.9);
  dashedRect(g, x0 + 7, y0 + 7, w - 14, h - 14, 5, 4);
  // 테두리
  g.lineStyle(1.5, shade(color, -0.35), 0.8);
  g.strokeRoundedRect(x0, y0, w, h, r);
}

// 비어 있는 자리: 옅은 천 + 점선
export function drawEmptyCell(g, x, y, size) {
  const pad = 3;
  g.fillStyle(COLORS.cellEmpty, 1);
  g.fillRoundedRect(x + pad, y + pad, size - pad * 2, size - pad * 2, 6);
  g.lineStyle(1, COLORS.cellEmptyStitch, 0.9);
  dashedRect(g, x + pad + 6, y + pad + 6, size - pad * 2 - 12, size - pad * 2 - 12, 4, 4);
}

export function dashedRect(g, x, y, w, h, dash, gap) {
  const step = dash + gap;
  for (let i = 0; i < w; i += step) { const e = Math.min(i + dash, w); g.lineBetween(x + i, y, x + e, y); g.lineBetween(x + i, y + h, x + e, y + h); }
  for (let i = 0; i < h; i += step) { const e = Math.min(i + dash, h); g.lineBetween(x, y + i, x, y + e); g.lineBetween(x + w, y + i, x + w, y + e); }
}

// 보드 틀: 쪽빛 비단 + 위아래 색동 띠
export function drawBoardFrame(g, gx, gy, gridPx) {
  const m = 16, band = 12;
  g.fillStyle(COLORS.frameDark, 1);
  g.fillRoundedRect(gx - m - 3, gy - m + 3, gridPx + m * 2 + 6, gridPx + m * 2 + 6, 20); // 그림자
  g.fillStyle(COLORS.frame, 1);
  g.fillRoundedRect(gx - m, gy - m, gridPx + m * 2, gridPx + m * 2, 18);
  // 색동 띠 (위, 아래)
  const stripeW = (gridPx + m * 2 - 24) / SAEKDONG.length / 2;
  const drawBand = (yy) => {
    let x = gx - m + 12;
    for (let i = 0; i < SAEKDONG.length * 2; i++) {
      g.fillStyle(SAEKDONG[i % SAEKDONG.length], 1);
      g.fillRect(x, yy, stripeW + 0.5, band);
      x += stripeW;
    }
  };
  drawBand(gy - m + 2);
  drawBand(gy + gridPx + m - band - 2);
  // 틀 안쪽 바느질
  g.lineStyle(1.5, 0xf3e9d6, 0.35);
  dashedRect(g, gx - 6, gy - 6, gridPx + 12, gridPx + 12, 6, 5);
}

// 낙관(도장): 인주색 사각형에 흰 글씨
export function makeSeal(scene, x, y, text, size = 56) {
  const c = scene.add.container(x, y);
  const g = scene.add.graphics();
  g.fillStyle(COLORS.seal2, 1);
  g.fillRoundedRect(-size / 2 + 2, -size / 2 + 2, size, size, 6);
  g.fillStyle(COLORS.seal, 1);
  g.fillRoundedRect(-size / 2, -size / 2, size, size, 6);
  g.lineStyle(2, 0xf3e9d6, 0.7);
  g.strokeRoundedRect(-size / 2 + 5, -size / 2 + 5, size - 10, size - 10, 3);
  const t = scene.add.text(0, 1, text, {
    fontFamily: FONT_DISPLAY, fontSize: `${Math.round(size * 0.36)}px`, color: '#f3e9d6', align: 'center', lineSpacing: -6, fontStyle: 'bold',
  }).setOrigin(0.5);
  c.add([g, t]);
  c.setAngle(-6);
  return c;
}

export { CELL };
