// Real waveforms for the seek bar.
// The song file is downloaded once in the background, decoded at a low sample rate,
// and boiled down to a few dozen loudness values that are remembered on this device.
// Downloaded (offline) songs are analysed without using any extra data.

import { streamUrl } from "./api.js";
import { getPref } from "./prefs.js";
import { offlineUrl } from "./offline.js";

const KEY = "relli.waves";
const BARS = 72;
const MAX_BYTES = 60 * 1024 * 1024;

let store = {};
try { store = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch {}

function remember(id, peaks) {
  store[id] = btoa(String.fromCharCode(...peaks));
  const ids = Object.keys(store);
  if (ids.length > 600) ids.slice(0, ids.length - 600).forEach((k) => delete store[k]);
  try { localStorage.setItem(KEY, JSON.stringify(store)); } catch {}
}

function recall(id) {
  const v = store[id];
  if (!v) return null;
  return Uint8Array.from(atob(v), (c) => c.charCodeAt(0));
}

const pending = new Map();

export function waveformFor(song) {
  if (!song || song.radio) return Promise.resolve(null);
  const hit = recall(song.id);
  if (hit) return Promise.resolve(hit);
  if (pending.has(song.id)) return pending.get(song.id);
  const job = analyse(song).finally(() => pending.delete(song.id));
  pending.set(song.id, job);
  return job;
}

async function analyse(song) {
  const local = await offlineUrl(song.id);
  if (!local && !getPref("waveform")) return null;
  if (!local && song.size > MAX_BYTES) return null;
  try {
    const res = await fetch(local || streamUrl(song.id, getPref("quality")));
    if (local) URL.revokeObjectURL(local);
    if (!res.ok) return null;
    const data = await res.arrayBuffer();
    const ctx = new OfflineAudioContext(1, 2, 8000);
    const audio = await ctx.decodeAudioData(data);
    const ch = audio.getChannelData(0);
    const per = Math.floor(ch.length / BARS) || 1;
    const rms = new Float32Array(BARS);
    for (let b = 0; b < BARS; b++) {
      let sum = 0;
      const start = b * per;
      for (let i = start; i < start + per && i < ch.length; i += 4) sum += ch[i] * ch[i];
      rms[b] = Math.sqrt(sum / (per / 4));
    }
    const max = Math.max(...rms) || 1;
    const peaks = Uint8Array.from(rms, (v) => Math.round(Math.pow(v / max, 0.8) * 255));
    remember(song.id, peaks);
    return peaks;
  } catch {
    return null;
  }
}
