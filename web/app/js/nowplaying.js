// The player dock (bottom bar on desktop, mini player on phones) and the
// Now Playing screen: cover, lyrics or visualizer, waveform seek bar, controls,
// and the action toolbar (sleep timer, radio mix, speed, queue, visual, equalizer, cast).
// The background and accents take their color from the album art.

import { coverUrl } from "./api.js";
import { player } from "./player.js";
import { getPref, setPref } from "./prefs.js";
import { h, icon, iconButton, cover, toast, clear } from "./ui.js";
import { createSeekBar } from "./seek.js";
import { createLyrics } from "./lyrics.js";
import { createVisualizer } from "./visualizer.js";
import { waveformFor } from "./waveform.js";
import { heartButton, go } from "./components.js";
import { sleep } from "./sleep.js";
import * as sheets from "./sheets.js";
import { radioMix } from "./smart.js";

const wide = () => window.matchMedia("(min-width: 900px)").matches;

// ---------- Album-art color ----------

function rgbToHsl(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let hue = 0, sat = 0;
  const light = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    sat = light > 0.5 ? d / (2 - max - min) : d / (max + min);
    hue = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    hue *= 60;
  }
  return [hue, sat * 100, light * 100];
}

async function artTone(id) {
  if (!id) return null;
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      try {
        const c = document.createElement("canvas");
        c.width = c.height = 32;
        const ctx = c.getContext("2d", { willReadFrequently: true });
        ctx.drawImage(img, 0, 0, 32, 32);
        const px = ctx.getImageData(0, 0, 32, 32).data;
        // Weight each pixel by how colorful it is, so the cover's main color wins over greys.
        let r = 0, g = 0, b = 0, w = 0;
        for (let i = 0; i < px.length; i += 4) {
          const max = Math.max(px[i], px[i + 1], px[i + 2]), min = Math.min(px[i], px[i + 1], px[i + 2]);
          const weight = 1 + (max - min) / 16;
          r += px[i] * weight; g += px[i + 1] * weight; b += px[i + 2] * weight; w += weight;
        }
        resolve(rgbToHsl(r / w, g / w, b / w));
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => resolve(null);
    img.src = coverUrl(id, 64);
  });
}

function applyTone(hsl) {
  const root = document.documentElement.style;
  if (!hsl) {
    ["--art-bg", "--art-deep", "--art-btn", "--art-pill"].forEach((v) => root.removeProperty(v));
    return;
  }
  const [hue, sat] = hsl;
  const s = Math.min(sat, 42);
  root.setProperty("--art-bg", `hsl(${hue} ${Math.min(s, 30)}% 21%)`);
  root.setProperty("--art-deep", `hsl(${hue} ${Math.min(s, 24)}% 10%)`);
  root.setProperty("--art-btn", `hsl(${hue} ${Math.min(s, 30)}% 40%)`);
  root.setProperty("--art-pill", `hsl(${hue} ${Math.min(s, 20)}% 36%)`);
}

// ---------- Quality label ("Lossless", "MP3 320 kbps") ----------

const LOSSLESS = ["flac", "alac", "wav", "aiff", "aif", "ape", "wv", "dsf", "dff"];
function qualityLabel(song) {
  if (!song || song.radio) return song?.radio ? "Live radio" : "";
  const q = getPref("quality");
  const suffix = (song.suffix || "").toLowerCase();
  if (q > 0 && (LOSSLESS.includes(suffix) || (song.bitRate || 0) > q)) return `MP3 ${q} kbps`;
  if (LOSSLESS.includes(suffix) || (suffix === "m4a" && (song.bitRate || 0) > 500)) {
    return song.bitDepth > 16 || song.samplingRate > 48000 ? "Hi-Res lossless" : "Lossless";
  }
  return [suffix.toUpperCase(), song.bitRate ? `${song.bitRate} kbps` : ""].filter(Boolean).join(" ");
}

// ---------- Queue panel ----------

function createQueue() {
  const list = h("ol.queue-list", { role: "list" });
  const head = h("div.queue-head",
    h("h2", "Up next"),
    h("div.queue-actions",
      h("button.link.small", { type: "button", onclick: () => sheets.smartSheet() }, "Smart queue"),
      h("button.link.small", { type: "button", onclick: () => sheets.saveQueueAsPlaylist() }, "Save"),
      h("button.link.small", { type: "button", onclick: () => player.clearUpcoming() }, "Clear"))
  );
  const el = h("div.queue", head, list);

  let dragFrom = null;
  function render() {
    clear(list);
    const start = Math.max(0, player.index);
    player.queue.slice(start, start + 200).forEach((song, k) => {
      const i = start + k;
      const isNow = i === player.index;
      const handle = isNow ? null : h("span.drag-handle", { "aria-hidden": "true", title: "Drag to reorder" }, icon("drag"));
      const row = h(`li.queue-row${isNow ? ".now" : ""}`, {
        tabindex: "0",
        draggable: isNow ? null : "true",
        onclick: (e) => { if (!isNow && !e.target.closest(".drag-handle")) player.jump(i); },
        onkeydown: (e) => {
          if (e.key === "Enter" && !isNow) player.jump(i);
          if (e.altKey && e.key === "ArrowUp" && i > player.index + 1) { e.preventDefault(); player.move(i, i - 1); }
          if (e.altKey && e.key === "ArrowDown" && i < player.queue.length - 1) { e.preventDefault(); player.move(i, i + 1); }
        },
        ondragstart: () => { dragFrom = i; },
        ondragover: (e) => { if (dragFrom != null && !isNow) e.preventDefault(); },
        ondrop: (e) => { e.preventDefault(); if (dragFrom != null && i !== dragFrom) player.move(dragFrom, i); dragFrom = null; },
      },
        handle,
        cover(song.coverArt || song.albumId, 96, "row-cover"),
        h("div.row-text",
          h("span.row-title", song.title),
          h("span.row-sub", song._smart ? `${song.artist || ""}  ·  Smart queue` : song._flow ? `${song.artist || ""}  ·  Smart flow` : song.artist || "")),
        isNow ? h("span.now-tag", "Playing") : h("button.icon-btn.row-more", { type: "button", "aria-label": `Remove ${song.title} from queue`, onclick: (e) => { e.stopPropagation(); player.removeAt(i); } }, icon("close"))
      );
      list.append(row);
    });
    if (player.queue.length - start <= 1) {
      list.append(h("li.queue-empty", getPref("smartQueue") !== "off" ? "Smart queue will add more songs shortly." : "Nothing else queued. Turn on Smart queue to keep music going."));
    }
  }

  player.addEventListener("queue", render);
  player.addEventListener("track", render);
  render();
  return { el };
}

// ---------- Controls shared by dock and Now Playing ----------

function transport(size = "") {
  const repeat = iconButton("repeat", "Repeat", () => player.cycleRepeat(), "mode");
  const prev = iconButton("prev", "Previous song", () => player.prev(), "skip-btn");
  const play = iconButton("play", "Play", () => player.toggle(), `play-btn ${size}`);
  const next = iconButton("next", "Next song", () => player.next(), "skip-btn");
  const shuffle = iconButton("shuffle", "Shuffle", () => player.setShuffle(!player.shuffle), "mode");

  const sync = () => {
    play.replaceChildren(icon(player.playing ? "pause" : "play"));
    play.setAttribute("aria-label", player.playing ? "Pause" : "Play");
    shuffle.classList.toggle("on", player.shuffle);
    shuffle.setAttribute("aria-pressed", String(player.shuffle));
    repeat.classList.toggle("on", player.repeat !== "off");
    repeat.replaceChildren(icon(player.repeat === "one" ? "repeatOne" : "repeat"));
    repeat.setAttribute("aria-label", { off: "Repeat is off", all: "Repeating the queue", one: "Repeating this song" }[player.repeat]);
    const radio = player.isRadio;
    [prev, next, shuffle, repeat].forEach((b) => { b.disabled = radio; });
  };
  ["state", "modes", "track"].forEach((ev) => player.addEventListener(ev, sync));
  sync();
  return h("div.transport", repeat, prev, play, next, shuffle);
}

function volumeControl() {
  const btn = iconButton("volume", "Mute", () => player.toggleMute(), "vol-btn");
  const range = h("input.volume", { type: "range", min: "0", max: "1", step: "0.01", "aria-label": "Volume", value: player.volume });
  range.addEventListener("input", () => player.setVolume(+range.value));
  const sync = () => {
    const muted = player.muted || player.volume === 0;
    btn.replaceChildren(icon(muted ? "mute" : "volume"));
    btn.setAttribute("aria-label", muted ? "Unmute" : "Mute");
    range.value = player.muted ? 0 : player.volume;
    range.style.setProperty("--pct", `${range.value * 100}%`);
  };
  player.addEventListener("volume", sync);
  sync();
  return h("div.volume-wrap", btn, range);
}

function castButton() {
  const el = player.audio;
  if (!el.remote || !window.isSecureContext) return null;
  const btn = iconButton("cast", "Cast to a speaker or TV", async () => {
    try { await player.audio.remote.prompt(); }
    catch (e) { if (e.name !== "AbortError") toast(e.name === "NotFoundError" ? "No cast devices found on your network." : "Casting isn't available for this song."); }
  }, "tool");
  return btn;
}

const VISUALS = { cover: ["image", "Showing the cover"], lyrics: ["lyrics", "Showing lyrics"], visualizer: ["visual", "Showing the visualizer"] };

// ---------- Build everything ----------

export function mountPlayer(root) {
  // ----- Dock -----
  const dockArt = h("button.dock-art", { type: "button", "aria-label": "Open Now Playing", onclick: () => open() });
  const dockTitle = h("span.dock-title");
  const dockArtist = h("span.dock-artist");
  const barSeek = createSeekBar({ onSeek: (t) => player.seek(t), size: "inline" });
  const miniSeek = createSeekBar({ onSeek: (t) => player.seek(t), showTimes: false, size: "mini" });
  const miniPlay = iconButton("play", "Play", () => player.toggle(), "mini-play");

  const dock = h("div.dock", { hidden: true },
    h("div.dock-now", { onclick: (e) => { if (!wide() && !e.target.closest(".dock-art, .mini-play")) open(); } },
      dockArt,
      h("div.dock-pill", h("div.dock-text", dockTitle, dockArtist), miniPlay, miniSeek.el)
    ),
    h("div.dock-center", transport(), barSeek.el),
    h("div.dock-right",
      iconButton("moon", "Sleep timer", () => sheets.sleepSheet(), "dock-tool"),
      iconButton("eq", "Equalizer", () => sheets.equalizerSheet(), "dock-tool"),
      iconButton("lyrics", "Lyrics", () => open("lyrics"), "dock-tool"),
      iconButton("queue", "Queue", () => open("queue"), "dock-tool"),
      volumeControl()
    )
  );

  // ----- Now Playing -----
  const npArt = h("div.np-art");
  const npTitle = h("h2.np-title");
  const npArtists = h("p.np-artists");
  const npAlbum = h("a.np-album");
  const npSource = h("p.np-source");
  const npHeart = h("div.np-heart");
  const seek = createSeekBar({ onSeek: (t) => player.seek(t), wave: true, size: "big" });
  const viz = createVisualizer();
  const lyrics = createLyrics((found) => {
    if (found === lyricsFound) return;
    lyricsFound = found;
    if (mode === "lyrics") queueMicrotask(layout);
  });
  const queue = createQueue();
  const npMore = iconButton("more", "More actions", () => player.current && sheets.moreActions(player.current, { nowPlaying: true }), "np-more");

  let mode = getPref("visual"); // cover | lyrics | visualizer
  let queueOpen = false;
  let lyricsFound = true;

  const sleepBtn = iconButton("moon", "Sleep timer", () => sheets.sleepSheet(), "tool");
  const radioBtn = iconButton("radio", "Radio mix: endless songs like this one", () => player.current && radioMix(player.current).catch((e) => toast(e.message)), "tool");
  const speedBtn = iconButton("gauge", "Speed and pitch", () => sheets.speedSheet(), "tool");
  const queueBtn = iconButton("up", "Queue", () => { queueOpen = !queueOpen; layout(); }, "tool");
  const visualBtn = iconButton("lyrics", "Switch visual", () => {
    const order = ["cover", "lyrics", "visualizer"];
    mode = order[(order.indexOf(mode) + 1) % order.length];
    setPref("visual", mode);
    queueOpen = false;
    layout();
    toast(VISUALS[mode][1]);
  }, "tool");
  const eqBtn = iconButton("eq", "Equalizer and DSP", () => sheets.equalizerSheet(), "tool");
  const cast = castButton();
  const speedBadge = h("span.tool-badge");
  speedBtn.append(speedBadge);
  const sleepBadge = h("span.tool-badge");
  sleepBtn.append(sleepBadge);

  const stage = h("div.np-stage", npArt, lyrics.el, viz.el, queue.el);
  const npBody = h("div.np-body",
    stage,
    h("div.np-controls",
      h("div.np-meta",
        h("div.np-names", npTitle, npArtists, npAlbum),
        npHeart, npMore),
      seek.el,
      transport("big"),
      h("div.np-toolbar", sleepBtn, radioBtn, speedBtn, queueBtn, visualBtn, eqBtn, cast)
    )
  );
  const np = h("section.np", { hidden: true, "aria-label": "Now playing", role: "dialog", "aria-modal": "true" },
    h("header.np-head",
      iconButton("down", "Close Now Playing", () => close()),
      npSource,
      h("span.np-head-spacer")
    ),
    npBody
  );

  root.append(dock, np);

  function layout() {
    // What the main visual area shows
    let show = mode === "lyrics" && !lyricsFound ? "visualizer" : mode;
    if (queueOpen) show = "queue";
    // On desktop the cover always sits on the left; the right side shows lyrics, visualizer, or queue.
    if (wide() && show === "cover") show = "queue";
    np.dataset.show = show;
    npArt.classList.toggle("shown", show === "cover");
    lyrics.el.classList.toggle("shown", show === "lyrics");
    viz.el.classList.toggle("shown", show === "visualizer");
    queue.el.classList.toggle("shown", show === "queue");
    const [ic, label] = VISUALS[mode];
    visualBtn.replaceChildren(icon(ic));
    visualBtn.setAttribute("aria-label", `${label}. Tap to switch.`);
    visualBtn.classList.toggle("on", mode !== "cover");
    queueBtn.classList.toggle("on", queueOpen);
    if (!np.hidden && (mode === "lyrics" || wide())) lyrics.load(player.current);
    if (!np.hidden && show === "visualizer") viz.start(); else viz.stop();
  }

  let lastFocus = null;
  function open(panel) {
    if (!player.current) return;
    lastFocus = document.activeElement;
    if (panel === "lyrics") { mode = "lyrics"; queueOpen = false; }
    if (panel === "queue") queueOpen = true;
    np.hidden = false;
    document.body.classList.add("np-open");
    layout();
    requestAnimationFrame(() => np.classList.add("open"));
    history.pushState({ np: true }, "");
    np.querySelector(".np-head .icon-btn").focus();
  }
  function close(fromHistory) {
    if (np.hidden) return;
    np.classList.remove("open");
    document.body.classList.remove("np-open");
    viz.stop();
    setTimeout(() => { np.hidden = true; }, 240);
    if (!fromHistory && history.state?.np) history.back();
    lastFocus?.focus?.();
  }
  window.addEventListener("popstate", () => { if (!history.state?.np && !history.state?.sheet) close(true); });
  np.addEventListener("keydown", (e) => { if (e.key === "Escape" && !document.querySelector(".sheet-overlay")) close(); });
  sheets.hooks.showVisual = (m) => { mode = m; setPref("visual", m); queueOpen = false; layout(); };
  sheets.hooks.closeNowPlaying = () => close();
  document.addEventListener("relli:open-queue", () => open("queue"));
  document.addEventListener("relli:open-player", () => setTimeout(() => open(), 50));

  // Swipe down on the cover to close on phones.
  let startY = null;
  stage.addEventListener("touchstart", (e) => { if (np.dataset.show === "cover") startY = e.touches[0].clientY; }, { passive: true });
  stage.addEventListener("touchend", (e) => {
    if (startY != null && e.changedTouches[0].clientY - startY > 90) close();
    startY = null;
  });
  window.addEventListener("resize", () => { if (!np.hidden) layout(); });

  // ----- Keep everything in sync with the player -----
  let toneFor = null;
  function onTrack() {
    const s = player.current;
    dock.hidden = !s;
    document.body.classList.toggle("has-dock", !!s);
    if (!s) { close(); applyTone(null); return; }
    const art = s.coverArt || s.albumId;
    dockArt.replaceChildren(cover(art, 120));
    dockTitle.textContent = s.title;
    dockArtist.textContent = s.artist || "";
    npArt.replaceChildren(cover(art, 800, "np-cover"));
    npTitle.textContent = s.title;
    const artists = Array.isArray(s.artists) && s.artists.length ? s.artists : s.artistId ? [{ id: s.artistId, name: s.artist }] : [];
    npArtists.replaceChildren(...(artists.length
      ? artists.flatMap((a, i) => [i ? h("span.sep", ", ") : null, h("a.np-artist", { href: `#/artist/${a.id}`, onclick: () => close(true) }, a.name)])
      : [s.artist || ""]).filter(Boolean));
    npAlbum.textContent = s.album || "";
    npAlbum.href = s.albumId ? `#/album/${s.albumId}` : "#/";
    npAlbum.onclick = () => close(true);
    npSource.textContent = player.source ? `Playing from ${player.source}` : "Now playing";
    npHeart.replaceChildren(s.radio ? "" : heartButton(s));
    const d = s.duration || 0;
    seek.setDuration(d, !!s.radio);
    barSeek.setDuration(d, !!s.radio);
    miniSeek.setDuration(d, !!s.radio);
    seek.setLabel(qualityLabel(s));
    seek.setPeaks(null);
    waveformFor(s).then((peaks) => { if (player.current === s && peaks) seek.setPeaks(peaks); });
    lyricsFound = true;
    if (!np.hidden) layout();
    document.title = `${s.title} by ${s.artist || "Unknown artist"} | RelliRadio`;
    if (toneFor !== art) {
      toneFor = art;
      artTone(art).then((hsl) => { if (toneFor === art) applyTone(hsl); });
    }
  }

  function onTime() {
    const t = player.time;
    const d = player.duration;
    const live = player.isRadio;
    seek.setDuration(d, live); barSeek.setDuration(d, live); miniSeek.setDuration(d, live);
    seek.setTime(t); barSeek.setTime(t); miniSeek.setTime(t);
    lyrics.sync(t);
  }

  function onState() {
    miniPlay.replaceChildren(icon(player.playing ? "pause" : "play"));
    miniPlay.setAttribute("aria-label", player.playing ? "Pause" : "Play");
    dock.classList.toggle("is-playing", player.playing);
  }

  function onSpeed() {
    const v = getPref("speed"), p = getPref("pitch");
    speedBadge.textContent = v !== 1 ? `${Number(v.toFixed(2))}x` : p ? `${p > 0 ? "+" : ""}${p}` : "";
    speedBtn.classList.toggle("on", v !== 1 || p !== 0);
  }

  function onSleep() {
    sleepBtn.classList.toggle("on", sleep.active);
    sleepBadge.textContent = sleep.mode === "time" ? Math.ceil(sleep.remaining / 60) + "m" : sleep.active ? "•" : "";
  }

  player.addEventListener("track", onTrack);
  player.addEventListener("time", onTime);
  player.addEventListener("state", onState);
  player.addEventListener("track", onState);
  player.addEventListener("speed", onSpeed);
  sleep.addEventListener("change", onSleep);
  sleep.addEventListener("tick", onSleep);
  document.addEventListener("relli:pref", (e) => {
    if (e.detail.name === "eq") eqBtn.classList.toggle("on", e.detail.value.enabled);
    if (e.detail.name === "quality") seek.setLabel(qualityLabel(player.current));
  });
  eqBtn.classList.toggle("on", getPref("eq").enabled);
  onSpeed();
  onSleep();

  // ----- Keyboard shortcuts -----
  document.addEventListener("keydown", (e) => {
    const typing = e.target.closest("input, textarea, select, [contenteditable]");
    if (typing || e.metaKey || e.ctrlKey || e.altKey || document.querySelector(".sheet-overlay")) return;
    if (e.key === " " && !e.target.closest("button, a, [role=slider]")) { e.preventDefault(); player.toggle(); }
    else if (e.shiftKey && e.key === "ArrowRight") player.next();
    else if (e.shiftKey && e.key === "ArrowLeft") player.prev();
    else if (e.key === "/") { e.preventDefault(); go("#/search"); }
    else if (e.key.toLowerCase() === "l" && player.current) np.hidden ? open("lyrics") : sheets.hooks.showVisual("lyrics");
    else if (e.key.toLowerCase() === "q" && player.current) np.hidden ? open("queue") : (queueOpen = !queueOpen, layout());
  });

  return { open, close };
}
