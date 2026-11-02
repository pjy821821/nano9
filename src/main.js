import { GAME_WIDTH, GAME_HEIGHT, COLORS } from './config.js';
import { GameScene } from './scenes/GameScene.js';

const config = {
  type: Phaser.AUTO,
  parent: 'game',
  width: GAME_WIDTH,
  height: GAME_HEIGHT,
  backgroundColor: COLORS.bg,
  scale: {
    mode: Phaser.Scale.FIT,
    autoCenter: Phaser.Scale.CENTER_BOTH,
  },
  input: { activePointers: 1 },
  scene: [GameScene],
};

// 웹폰트(고운바탕, 고운돋움)가 준비된 뒤 시작합니다. 1.5초 안에 안 오면 대체 폰트로 시작합니다.
const fonts = ['700 32px "Gowun Batang"', '400 32px "Gowun Batang"', '400 32px "Gowun Dodum"'];
const ready = document.fonts ? Promise.all(fonts.map(f => document.fonts.load(f))) : Promise.resolve();
const timeout = new Promise(r => setTimeout(r, 1500));
Promise.race([ready, timeout]).catch(() => {}).then(() => { window.game = new Phaser.Game(config); });
