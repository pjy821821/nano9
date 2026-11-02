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

window.game = new Phaser.Game(config);
