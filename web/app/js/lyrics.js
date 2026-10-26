// Lyrics panel: synced lyrics follow the song and you can tap a line to jump to it.

import { getLyrics } from "./api.js";
import { h } from "./ui.js";
import { player } from "./player.js";
import { getPref } from "./prefs.js";

// onResult(found) tells Now Playing whether to show lyrics or the visualizer.
export function createLyrics(onResult = () => {}) {
  const el = h("div.lyrics", { "aria-live": "off" });
  const applyLook = () => {
    el.dataset.size = getPref("lyricsSize");
    el.dataset.align = getPref("lyricsAlign");
  };
  applyLook();
  document.addEventListener("relli:pref", (e) => { if (e.detail.name.startsWith("lyrics")) applyLook(); });
  let data = null, lines = [], active = -1, songId = null, userScrollUntil = 0, token = 0;

  el.addEventListener("wheel", () => (userScrollUntil = Date.now() + 4000), { passive: true });
  el.addEventListener("touchmove", () => (userScrollUntil = Date.now() + 4000), { passive: true });

  function message(text, hint) {
    el.textContent = "";
    el.append(h("div.lyrics-empty", h("p", text), hint ? h("p.hint", hint) : null));
  }

  async function load(song) {
    if (!song) { songId = null; return message("Nothing playing."); }
    if (song.id === songId) return onResult(lines.length > 0);
    if (song.radio) { songId = song.id; lines = []; message("Live radio has no lyrics."); return onResult(false); }
    songId = song.id;
    const mine = ++token;
    active = -1;
    message("Looking for lyrics…");
    const result = await getLyrics(song);
    if (mine !== token) return;
    data = result;
    if (!data || !data.lines.some((l) => l.text)) {
      lines = [];
      message("No lyrics for this song.", "Put a .lrc file with the same name next to the song in your music folder, then rescan in Navidrome.");
      return onResult(false);
    }
    el.textContent = "";
    lines = data.lines.map((l) =>
      h(data.synced ? "button.lyric-line" : "p.lyric-line", {
        type: data.synced ? "button" : null,
        onclick: data.synced && l.time != null ? () => player.seek(l.time / 1000 + 0.01) : null,
      }, l.text || "♪")
    );
    el.classList.toggle("synced", data.synced);
    el.append(h("div.lyrics-pad"), ...lines, h("div.lyrics-pad"));
    sync(player.time);
    onResult(true);
  }

  function sync(seconds) {
    if (!data?.synced || !lines.length) return;
    const ms = seconds * 1000 + 150 + (getPref("lyricsOffset") || 0);
    let lo = 0, hi = data.lines.length - 1, idx = -1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (data.lines[mid].time <= ms) { idx = mid; lo = mid + 1; } else hi = mid - 1;
    }
    if (idx === active) return;
    lines[active]?.classList.remove("active");
    active = idx;
    lines.forEach((ln, i) => ln.classList.toggle("past", i < idx));
    const line = lines[idx];
    if (!line) return;
    line.classList.add("active");
    if (Date.now() > userScrollUntil && el.offsetParent) {
      const top = line.offsetTop - el.clientHeight / 2 + line.clientHeight / 2;
      el.scrollTo({ top, behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
    }
  }

  return { el, load, sync, get found() { return lines.length > 0; } };
}
