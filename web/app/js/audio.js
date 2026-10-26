// Audio processing chain (Web Audio API).
//
//   deck A ─┐
//            ├─ bus ─ pitch ─ preamp ─ 10-band EQ ─ crossfeed ─ mono ─ boost ─ master ─ analyser ─ speakers
//   deck B ─┘
//
// Two "decks" (audio elements) make crossfades and gapless transitions possible.
// The browser only lets us process audio that comes from the same address as the
// app (Caddy serves both), so when RelliRadio talks to a different server,
// processing switches off and playback uses the plain audio elements instead.

import { getPref, setPref } from "./prefs.js";
import { getSession } from "./api.js";

export const EQ_FREQS = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];

export const EQ_PRESETS = {
  Flat:           [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  "Bass boost":   [6, 5, 4, 2, 0, 0, 0, 0, 0, 0],
  "Bass reducer": [-6, -5, -4, -2, 0, 0, 0, 0, 0, 0],
  "Treble boost": [0, 0, 0, 0, 0, 1, 2, 4, 5, 6],
  "Treble reducer": [0, 0, 0, 0, 0, -1, -2, -4, -5, -6],
  Vocal:          [-2, -2, -1, 1, 3, 4, 3, 1, 0, -1],
  Loudness:       [5, 4, 1, 0, -1, 0, -1, 1, 4, 5],
  "Hip-Hop":      [5, 4, 1, 2, -1, -1, 1, 0, 1, 2],
  "R&B":          [3, 6, 5, 1, -2, -1, 2, 2, 3, 4],
  Pop:            [-1, 0, 2, 3, 3, 2, 0, -1, -1, -1],
  Rock:           [4, 3, 2, 0, -1, -1, 1, 2, 3, 4],
  Jazz:           [3, 2, 1, 2, -1, -1, 0, 1, 2, 3],
  Classical:      [4, 3, 2, 1, -1, -1, 0, 2, 3, 4],
  Electronic:     [4, 3, 1, 0, -2, 2, 1, 1, 3, 4],
  Reggae:         [0, 0, 0, -2, 0, 2, 3, 0, 0, 0],
  Acoustic:       [4, 4, 3, 1, 2, 1, 3, 3, 3, 2],
  "Small speakers": [-4, -2, 2, 3, 2, 1, 2, 3, 2, 1],
  "Late night":   [-3, -2, 0, 1, 2, 2, 1, 0, -2, -3],
};

export function eqSettings() {
  return getPref("eq");
}

export function customPresets() {
  return getPref("eqCustom") || {};
}

const db = (v) => Math.pow(10, v / 20);

class AudioEngine {
  constructor() {
    this.ctx = null;
    this.elements = [];
    this.levels = [1, 1];
    this.volume = getPref("volume");
    this.muted = false;
    this.pitchSemis = 0;
    this.listeners = new Set();
  }

  // Processing only works when the music comes from this app's own address.
  get possible() {
    const s = getSession();
    if (!s) return false;
    if (window.relliDesktop) return true; // the desktop app can read any server's audio
    if (!s.server) return true;
    try { return new URL(s.server).origin === location.origin; } catch { return false; }
  }

  get active() {
    return !!this.ctx;
  }

  get wanted() {
    return getPref("audioFx") && this.possible && "AudioContext" in window;
  }

  onChange(fn) { this.listeners.add(fn); }
  changed() { this.listeners.forEach((fn) => fn()); }

  attach(elements) {
    this.elements = elements;
  }

  // Called from a click/tap (browsers only allow audio processing to start after one).
  async start() {
    if (this.ctx) {
      if (this.ctx.state === "suspended") this.ctx.resume().catch(() => {});
      return true;
    }
    if (!this.wanted) return false;
    let ctx;
    try { ctx = new AudioContext({ latencyHint: "playback" }); } catch { return false; }
    this.ctx = ctx;

    this.bus = ctx.createGain();
    this.decks = this.elements.map((el) => {
      const src = ctx.createMediaElementSource(el);
      const gain = ctx.createGain();
      src.connect(gain).connect(this.bus);
      return gain;
    });
    this.elements.forEach((el) => { el.volume = 1; });

    this.preamp = ctx.createGain();
    this.bands = EQ_FREQS.map((f, i) => {
      const b = ctx.createBiquadFilter();
      b.type = i === 0 ? "lowshelf" : i === EQ_FREQS.length - 1 ? "highshelf" : "peaking";
      b.frequency.value = f;
      b.Q.value = 1.1;
      return b;
    });

    // Crossfeed: each ear hears a little of the other channel, filtered and delayed,
    // like speakers in a room. Makes hard-panned old recordings easier on headphones.
    this.cfIn = ctx.createGain();
    this.cfOut = ctx.createGain();
    const split = ctx.createChannelSplitter(2);
    const merge = ctx.createChannelMerger(2);
    this.cfDirect = ctx.createGain();
    this.cfCross = [0, 1].map((ch) => {
      const lp = ctx.createBiquadFilter();
      lp.type = "lowpass"; lp.frequency.value = 700;
      const delay = ctx.createDelay(0.01); delay.delayTime.value = 0.0003;
      const g = ctx.createGain(); g.gain.value = 0;
      split.connect(lp, ch).connect(delay).connect(g).connect(merge, 0, 1 - ch);
      return g;
    });
    this.cfIn.connect(this.cfDirect).connect(this.cfOut);
    this.cfIn.connect(split);
    merge.connect(this.cfOut);

    this.mono = ctx.createGain();
    this.boost = ctx.createGain();
    this.master = ctx.createGain();
    this.analyser = ctx.createAnalyser();
    this.analyser.fftSize = 2048;
    this.analyser.smoothingTimeConstant = 0.78;

    let node = this.preamp;
    for (const b of this.bands) { node.connect(b); node = b; }
    node.connect(this.cfIn);
    this.cfOut.connect(this.mono).connect(this.boost).connect(this.master).connect(this.analyser).connect(ctx.destination);
    this.bus.connect(this.preamp);

    this.applyAll();
    this.loadPitch();
    this.changed();
    return true;
  }

  async loadPitch() {
    try {
      await this.ctx.audioWorklet.addModule(new URL("./pitch-worklet.js", import.meta.url));
      this.pitch = new AudioWorkletNode(this.ctx, "pitch-shifter", { outputChannelCount: [2] });
      this.bus.disconnect();
      this.bus.connect(this.pitch).connect(this.preamp);
      this.setPitch(this.pitchSemis);
      this.changed();
    } catch {
      this.pitch = null;
    }
  }

  applyAll() {
    this.applyEq();
    this.applyEffects();
    this.applyLevels();
  }

  applyEq() {
    if (!this.ctx) return;
    const eq = getPref("eq");
    const t = this.ctx.currentTime;
    this.bands.forEach((b, i) => b.gain.setTargetAtTime(eq.enabled ? eq.bands[i] || 0 : 0, t, 0.02));
    this.preamp.gain.setTargetAtTime(eq.enabled ? db(eq.preamp || 0) : 1, t, 0.02);
  }

  applyEffects() {
    if (!this.ctx) return;
    const fx = getPref("fx");
    const t = this.ctx.currentTime;
    const cf = [0, 0.25, 0.4, 0.55][fx.crossfeed] || 0;
    this.cfCross.forEach((g) => g.gain.setTargetAtTime(cf, t, 0.02));
    this.cfDirect.gain.setTargetAtTime(1 - cf * 0.35, t, 0.02);
    this.mono.channelCountMode = fx.mono ? "explicit" : "max";
    this.mono.channelCount = fx.mono ? 1 : 2;
    this.boost.gain.setTargetAtTime(db(fx.boost || 0), t, 0.02);
  }

  // level = crossfade position × ReplayGain for each deck
  setDeckLevel(i, level) {
    this.levels[i] = level;
    this.applyLevels();
  }

  applyLevels() {
    const vol = this.muted ? 0 : this.volume;
    if (this.ctx) {
      const t = this.ctx.currentTime;
      this.decks.forEach((g, i) => g.gain.setTargetAtTime(this.levels[i], t, 0.015));
      this.master.gain.setTargetAtTime(vol, t, 0.015);
    } else {
      this.elements.forEach((el, i) => { el.volume = Math.max(0, Math.min(1, this.levels[i] * vol)); });
    }
  }

  setVolume(v) {
    this.volume = Math.max(0, Math.min(1, v));
    this.muted = false;
    setPref("volume", this.volume);
    this.applyLevels();
  }

  toggleMute() {
    this.muted = !this.muted;
    this.applyLevels();
  }

  setPitch(semis) {
    this.pitchSemis = semis;
    if (this.pitch) this.pitch.parameters.get("ratio").setValueAtTime(Math.pow(2, semis / 12), this.ctx.currentTime);
  }

  get canPitch() {
    return !!this.pitch;
  }
}

export const engine = new AudioEngine();
