// Seek bars.
// "wave": bars drawn from the song's real waveform, lit up to the current position,
//         with a faded reflection underneath (the Now Playing screen).
// default: a thin track with a fill and a round handle (desktop player bar, mini player).
// Drag or click anywhere; arrow keys move 5 seconds, Page Up/Down 30.

import { h, fmtTime } from "./ui.js";

export function createSeekBar({ onSeek, showTimes = true, size = "", wave = false }) {
  const fill = h("div.seek-fill");
  const thumb = h("div.seek-thumb");
  const canvas = wave ? h("canvas.seek-wave") : null;
  const track = wave ? h("div.seek-track.wave", canvas) : h("div.seek-track", fill, thumb);
  const elapsed = h("span.seek-time", "0:00");
  const middle = h("span.seek-label");
  const total = h("span.seek-time", "0:00");
  const el = h(`div.seek${size ? "." + size : ""}`, {
    role: "slider", tabindex: "0", "aria-label": "Song position",
    "aria-valuemin": "0", "aria-valuemax": "0", "aria-valuenow": "0",
  },
    h("div.seek-hit", track),
    showTimes ? h("div.seek-times", elapsed, middle, total) : null
  );

  let duration = 0, time = 0, dragging = false, peaks = null, live = false;

  function draw(pct) {
    if (!canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, hgt = canvas.clientHeight;
    if (!w || !hgt) return;
    if (canvas.width !== Math.round(w * dpr)) { canvas.width = Math.round(w * dpr); canvas.height = Math.round(hgt * dpr); }
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, hgt);
    const styles = getComputedStyle(el);
    const on = styles.getPropertyValue("--wave-on").trim() || "#f3e7d3";
    const off = styles.getPropertyValue("--wave-off").trim() || "rgba(243,231,211,.28)";
    const count = Math.max(24, Math.min(72, Math.floor(w / 9)));
    const gap = 3;
    const bw = (w - gap * (count - 1)) / count;
    const base = hgt * 0.68;
    for (let i = 0; i < count; i++) {
      let v = 0.18;
      if (peaks) v = 0.12 + 0.88 * (peaks[Math.floor((i / count) * peaks.length)] / 255);
      const bh = Math.max(3, v * base);
      const x = i * (bw + gap);
      const lit = (i + 0.5) / count <= pct;
      ctx.fillStyle = lit ? on : off;
      ctx.beginPath();
      ctx.roundRect(x, base - bh, bw, bh, Math.min(bw / 2, 3));
      ctx.fill();
      ctx.globalAlpha = 0.22;
      ctx.beginPath();
      ctx.roundRect(x, base + 2, bw, bh * 0.45, Math.min(bw / 2, 3));
      ctx.fill();
      ctx.globalAlpha = 1;
    }
  }

  function place(t) {
    const pct = duration ? Math.min(1, t / duration) : 0;
    if (wave) draw(pct);
    else { fill.style.width = `${pct * 100}%`; thumb.style.left = `${pct * 100}%`; }
    elapsed.textContent = fmtTime(t);
    total.textContent = live ? "Live" : duration ? `-${fmtTime(Math.max(0, duration - t))}` : "0:00";
    el.setAttribute("aria-valuenow", String(Math.floor(t)));
    el.setAttribute("aria-valuetext", `${fmtTime(t)} of ${fmtTime(duration)}`);
  }

  const timeAt = (e) => {
    const r = track.getBoundingClientRect();
    return Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)) * duration;
  };

  const hit = el.firstChild;
  hit.addEventListener("pointerdown", (e) => {
    if (!duration) return;
    dragging = true;
    hit.setPointerCapture(e.pointerId);
    el.classList.add("dragging");
    place(timeAt(e));
  });
  hit.addEventListener("pointermove", (e) => { if (dragging) place(timeAt(e)); });
  hit.addEventListener("pointerup", (e) => {
    if (!dragging) return;
    dragging = false;
    el.classList.remove("dragging");
    time = timeAt(e);
    place(time);
    onSeek(time);
  });
  hit.addEventListener("pointercancel", () => { dragging = false; el.classList.remove("dragging"); place(time); });

  el.addEventListener("keydown", (e) => {
    const steps = { ArrowRight: 5, ArrowUp: 5, ArrowLeft: -5, ArrowDown: -5, PageUp: 30, PageDown: -30 };
    let t;
    if (e.key in steps) t = time + steps[e.key];
    else if (e.key === "Home") t = 0;
    else if (e.key === "End") t = duration - 1;
    else return;
    e.preventDefault();
    time = Math.max(0, Math.min(duration, t));
    place(time);
    onSeek(time);
  });

  if (wave) new ResizeObserver(() => place(time)).observe(track);

  return {
    el,
    setDuration(d, isLive = false) {
      duration = d || 0;
      live = isLive;
      el.setAttribute("aria-valuemax", String(Math.floor(duration)));
      el.classList.toggle("live", isLive);
    },
    setTime(t) { time = t; if (!dragging) place(t); },
    setPeaks(p) { peaks = p; place(time); },
    setLabel(text) { middle.textContent = text || ""; },
  };
}
