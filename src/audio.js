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

  noise(dur, { vol = 0.2, delay = 0 } = {}) {
    if (this.muted || !this.ctx) return;
    const t0 = this.ctx.currentTime + delay;
    const len = Math.floor(this.ctx.sampleRate * dur);
    const buf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const src = this.ctx.createBufferSource();
    src.buffer = buf;
    const filt = this.ctx.createBiquadFilter();
    filt.type = 'lowpass'; filt.frequency.setValueAtTime(1800, t0); filt.frequency.exponentialRampToValueAtTime(200, t0 + dur);
    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(vol, t0);
    gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    src.connect(filt).connect(gain).connect(this.ctx.destination);
    src.start(t0);
  }

  clear(lines) {
    // 1) 묵직한 타격: 저음 킥 + 노이즈 펑
    this.tone(140, 0.22, { type: 'sine', vol: 0.5, slide: 40 });
    this.noise(0.25, { vol: 0.25 });
    // 2) 줄 수만큼 음을 올려가며 아르페지오 (콤보일수록 길고 높게)
    const base = [523, 659, 784, 1047, 1319, 1568];
    const n = Math.min(lines + 2, base.length);
    for (let i = 0; i < n; i++) {
      this.tone(base[i], 0.22, { type: 'triangle', vol: 0.22, delay: 0.05 + i * 0.055 });
      this.tone(base[i] / 2, 0.22, { type: 'square', vol: 0.06, delay: 0.05 + i * 0.055 });
    }
    // 3) 콤보면 마지막에 반짝이는 고음
    if (lines >= 2) this.tone(2093, 0.35, { type: 'sine', vol: 0.18, delay: 0.05 + n * 0.055, slide: 2637 });
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
