// Audio visualizer, drawn from the live sound (Web Audio analyser).
// Shown when a song has no lyrics, or whenever you pick it as the Now Playing visual.

import { engine } from "./audio.js";
import { getPref, setPref } from "./prefs.js";
import { h } from "./ui.js";

export const VIZ_STYLES = [["bars", "Bars"], ["wave", "Wave"], ["ring", "Ring"]];

export function createVisualizer() {
  const canvas = h("canvas.viz-canvas", { "aria-hidden": "true" });
  const note = h("p.viz-note");
  const switcher = h("div.viz-styles", VIZ_STYLES.map(([id, label]) =>
    h("button.viz-style", { type: "button", dataset: { style: id }, "aria-pressed": String(getPref("vizStyle") === id), onclick: () => {
      setPref("vizStyle", id);
      switcher.querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.style === id)));
    } }, label)));
  const el = h("div.viz", canvas, note, switcher);

  let raf = null, freq = null, wave = null, smooth = null;

  function frame() {
    raf = requestAnimationFrame(frame);
    const a = engine.analyser;
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, hgt = canvas.clientHeight;
    if (!w || !hgt) return;
    if (canvas.width !== Math.round(w * dpr)) { canvas.width = Math.round(w * dpr); canvas.height = Math.round(hgt * dpr); }
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, hgt);

    const css = getComputedStyle(el);
    const c1 = css.getPropertyValue("--viz-1").trim() || "#f4dfa8";
    const c2 = css.getPropertyValue("--viz-2").trim() || "#a8823e";

    const style = getPref("vizStyle");
    if (!a) {
      note.textContent = !getPref("audioFx")
        ? "Turn on audio effects in Settings to see the visualizer."
        : engine.possible ? "Press play to start the visualizer." : "The visualizer needs RelliRadio and Navidrome at the same address.";
      return;
    }
    note.textContent = "";
    if (!freq) { freq = new Uint8Array(a.frequencyBinCount); wave = new Uint8Array(a.fftSize); smooth = new Float32Array(64); }
    a.getByteFrequencyData(freq);

    const grad = ctx.createLinearGradient(0, hgt, 0, 0);
    grad.addColorStop(0, c2);
    grad.addColorStop(1, c1);

    if (style === "wave") {
      a.getByteTimeDomainData(wave);
      ctx.lineWidth = 3;
      ctx.strokeStyle = grad;
      ctx.lineJoin = "round";
      ctx.beginPath();
      for (let i = 0; i < wave.length; i += 4) {
        const x = (i / (wave.length - 1)) * w;
        const y = hgt / 2 + ((wave[i] - 128) / 128) * hgt * 0.38;
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
      }
      ctx.stroke();
      return;
    }

    // Log-spaced bands so bass and treble get fair room.
    const bands = style === "ring" ? 64 : Math.max(16, Math.min(48, Math.floor(w / 14)));
    const n = freq.length;
    for (let b = 0; b < bands; b++) {
      const lo = Math.floor(Math.pow(n, b / bands) * 0.9);
      const hi = Math.max(lo + 1, Math.floor(Math.pow(n, (b + 1) / bands) * 0.9));
      let m = 0;
      for (let i = lo; i < hi && i < n; i++) m = Math.max(m, freq[i]);
      // Dramatic: a noise floor keeps quiet parts low and a steep curve makes the hits tower.
      const v = Math.pow(Math.max(0, m / 255 - 0.3) / 0.7, 1.8);
      smooth[b] = Math.max(v, (smooth[b] || 0) * 0.93);
    }

    if (style === "ring") {
      const cx = w / 2, cy = hgt / 2, r = Math.min(w, hgt) * 0.26;
      ctx.strokeStyle = grad;
      ctx.lineCap = "round";
      ctx.lineWidth = Math.max(2, (Math.PI * 2 * r) / bands * 0.5);
      for (let b = 0; b < bands; b++) {
        const ang = (b / bands) * Math.PI * 2 - Math.PI / 2;
        const len = 4 + smooth[b] * Math.min(w, hgt) * 0.3;
        ctx.beginPath();
        ctx.moveTo(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r);
        ctx.lineTo(cx + Math.cos(ang) * (r + len), cy + Math.sin(ang) * (r + len));
        ctx.stroke();
      }
      return;
    }

    const gap = 4;
    const bw = (w - gap * (bands - 1)) / bands;
    const mid = hgt * 0.7;
    ctx.fillStyle = grad;
    for (let b = 0; b < bands; b++) {
      const bh = Math.max(4, smooth[b] * mid);
      const x = b * (bw + gap);
      ctx.beginPath();
      ctx.roundRect(x, mid - bh, bw, bh, Math.min(bw / 2, 6));
      ctx.fill();
      ctx.globalAlpha = 0.18;
      ctx.beginPath();
      ctx.roundRect(x, mid + 4, bw, bh * 0.5, Math.min(bw / 2, 6));
      ctx.fill();
      ctx.globalAlpha = 1;
    }
  }

  return {
    el,
    start() { if (!raf) frame(); },
    stop() { if (raf) cancelAnimationFrame(raf); raf = null; },
  };
}
