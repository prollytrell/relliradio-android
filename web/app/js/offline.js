// Downloads for offline listening.
// Each song's original file is saved in the browser's Cache Storage (on this device only).
// The player checks here first, so downloaded songs play without the server.

import { downloadUrl } from "./api.js";
import { toast } from "./ui.js";

const CACHE = "relliradio-offline";
const META_KEY = "relli.offline";
const keyFor = (id) => `${location.origin}/offline/${encodeURIComponent(id)}`;

let meta = {};
try { meta = JSON.parse(localStorage.getItem(META_KEY) || "{}"); } catch {}

const saveMeta = () => {
  try { localStorage.setItem(META_KEY, JSON.stringify(meta)); } catch {}
  document.dispatchEvent(new Event("relli:offline"));
};

const KEEP = ["id", "title", "artist", "artistId", "album", "albumId", "coverArt", "duration", "track", "discNumber", "year", "suffix", "bitRate", "replayGain", "genre"];

export const offlineSupported = "caches" in window;

export function isDownloaded(id) {
  return !!meta[id];
}

export function downloadedSongs() {
  return Object.values(meta).sort((a, b) => (b.savedAt || 0) - (a.savedAt || 0));
}

const inFlight = new Set();

export async function download(songs) {
  if (!offlineSupported) return toast("Downloads need the HTTPS address of RelliRadio.");
  songs = [].concat(songs).filter((s) => !meta[s.id] && !inFlight.has(s.id));
  if (!songs.length) return toast("Already downloaded");
  navigator.storage?.persist?.().catch(() => {});
  const cache = await caches.open(CACHE);
  toast(songs.length === 1 ? `Downloading “${songs[0].title}”` : `Downloading ${songs.length} songs`);
  let done = 0, failed = 0;
  for (const song of songs) {
    inFlight.add(song.id);
    try {
      const res = await fetch(downloadUrl(song.id));
      if (!res.ok) throw new Error(res.status);
      const blob = await res.blob();
      await cache.put(keyFor(song.id), new Response(blob, { headers: { "Content-Type": blob.type || "audio/mpeg" } }));
      meta[song.id] = { ...Object.fromEntries(KEEP.filter((k) => song[k] != null).map((k) => [k, song[k]])), size: blob.size, savedAt: Date.now() };
      saveMeta();
      done++;
    } catch {
      failed++;
    } finally {
      inFlight.delete(song.id);
    }
  }
  toast(failed ? `Downloaded ${done}, ${failed} failed` : done === 1 ? "Downloaded for offline" : `Downloaded ${done} songs`);
}

export async function remove(ids) {
  const cache = await caches.open(CACHE);
  for (const id of [].concat(ids)) {
    await cache.delete(keyFor(id));
    delete meta[id];
  }
  saveMeta();
}

export async function removeAll() {
  await caches.delete(CACHE);
  meta = {};
  saveMeta();
}

// Returns a playable blob: URL for a downloaded song, or null.
export async function offlineUrl(id) {
  if (!meta[id] || !offlineSupported) return null;
  try {
    const res = await (await caches.open(CACHE)).match(keyFor(id));
    if (!res) { delete meta[id]; saveMeta(); return null; }
    return URL.createObjectURL(await res.blob());
  } catch {
    return null;
  }
}

export async function storageUsed() {
  const total = Object.values(meta).reduce((n, s) => n + (s.size || 0), 0);
  let free = null;
  try {
    const est = await navigator.storage.estimate();
    free = est.quota - est.usage;
  } catch {}
  return { total, free, count: Object.keys(meta).length };
}

export function fmtBytes(n) {
  if (n == null) return "unknown";
  const u = ["B", "KB", "MB", "GB", "TB"];
  let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(i > 1 ? 1 : 0)} ${u[i]}`;
}
