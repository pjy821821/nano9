// 효과음: 외부 파일 없이 Web Audio API로 합성합니다.
// 첫 터치 이후에만 AudioContext가 활성화되므로 unlock()을 입력 이벤트에서 호출합니다.
const MUTE_KEY = 'blockdrop.muted';

class Sfx {
  constructor() {
    this.ctx = null;
    this.muted = this.loadMuted();
  }

  unlock() {
    if (!this.ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      this.ctx = new AC();
    }
    if (this.ctx.state === 'suspended') this.ctx.resume();
  }

  toggleMute() {
    this.muted = !this.muted;
    try { localStorage.setItem(MUTE_KEY, this.muted ? '1' : '0'); } catch { /* ignore */ }
    return this.muted;
  }

  loadMuted() {
    try { return localStorage.getItem(MUTE_KEY) === '1'; } catch { return false; }
  }

  // 기본 톤 생성기. freq(Hz), dur(초), type(파형), vol(0~1), slide(종료 주파수)
  tone(freq, dur, { type = 'sine', vol = 0.25, slide = null, delay = 0 } = {}) {
    if (this.muted || !this.ctx) return;
    const t0 = this.ctx.currentTime + delay;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t0);
    if (slide) osc.frequency.exponentialRampToValueAtTime(slide, t0 + dur);
    gain.gain.setValueAtTime(vol, t0);
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(gain).connect(this.ctx.destination);
    osc.start(t0);
    osc.stop(t0 + dur + 0.02);
  }

  pickup() { this.tone(520, 0.06, { type: 'triangle', vol: 0.15 }); }
  place() { this.tone(300, 0.08, { type: 'square', vol: 0.12, slide: 180 }); }
  reject() { this.tone(160, 0.12, { type: 'sawtooth', vol: 0.08, slide: 120 }); }

  clear(lines) {
    // 줄 수만큼 음을 올려가며 아르페지오
    const base = [523, 659, 784, 1047, 1319];
    for (let i = 0; i < Math.min(lines + 1, 5); i++) {
      this.tone(base[i], 0.18, { type: 'triangle', vol: 0.2, delay: i * 0.06 });
    }
  }

  gameOver() {
    this.tone(392, 0.25, { type: 'triangle', vol: 0.2 });
    this.tone(330, 0.25, { type: 'triangle', vol: 0.2, delay: 0.2 });
    this.tone(262, 0.45, { type: 'triangle', vol: 0.2, delay: 0.4 });
  }

  button() { this.tone(880, 0.05, { type: 'sine', vol: 0.12 }); }
}

export const sfx = new Sfx();

// 햅틱: 안드로이드 Chrome은 navigator.vibrate 지원. iOS Safari는 무시됨(Capacitor Haptics로 대체 예정).
export const haptic = {
  light() { try { navigator.vibrate && navigator.vibrate(10); } catch { /* ignore */ } },
  medium() { try { navigator.vibrate && navigator.vibrate(25); } catch { /* ignore */ } },
  success(lines) { try { navigator.vibrate && navigator.vibrate(Array(lines).fill(30).flatMap(v => [v, 40])); } catch { /* ignore */ } },
  fail() { try { navigator.vibrate && navigator.vibrate([40, 30, 40]); } catch { /* ignore */ } },
};
