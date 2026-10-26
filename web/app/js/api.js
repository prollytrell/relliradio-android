// RelliRadio <-> Navidrome, over the Subsonic / OpenSubsonic API.
// Every call is a plain HTTP GET to <server>/rest/<method>?u=&t=&s=&v=&c=&f=json
// The password itself is never sent or stored: only md5(password + salt) and the salt.

import { md5 } from "./md5.js";

const SESSION_KEY = "relli.session";
const API_VERSION = "1.16.1";
// How this device names itself to Navidrome, so other devices can offer to pick up from it.
const UA = navigator.userAgent;
export const CLIENT_NAME = window.relliDesktop ? `RelliRadio (${window.relliDesktop.platform === "win32" ? "Windows PC" : window.relliDesktop.platform === "darwin" ? "Mac" : "Linux PC"})`
  : /SMART-TV|SmartTV|Tizen|Web0S|webOS|BRAVIA|AFT|GoogleTV|Android TV/i.test(UA) ? "RelliRadio (TV)"
  : /Android|iPhone|iPad/i.test(UA) ? "RelliRadio (Phone browser)"
  : "RelliRadio (Desktop)";

export class ApiError extends Error {
  constructor(message, code) {
    super(message);
    this.code = code;
  }
}

let session = null;

export function loadSession() {
  try {
    session = JSON.parse(localStorage.getItem(SESSION_KEY)) || null;
  } catch {
    session = null;
  }
  return session;
}

export function getSession() {
  return session;
}

export function clearSession() {
  session = null;
  try { localStorage.removeItem(SESSION_KEY); } catch {}
}

function randomSalt() {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function normalizeServer(server) {
  const s = (server || "").trim().replace(/\/+$/, "");
  if (!s) return ""; // same origin: the app and /rest are served by the same Caddy site
  return /^https?:\/\//.test(s) ? s : `http://${s}`;
}

function authParams(s = session) {
  return { u: s.user, t: s.token, s: s.salt, v: API_VERSION, c: CLIENT_NAME, f: "json" };
}

function buildUrl(method, params = {}, s = session) {
  const qs = new URLSearchParams(authParams(s));
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    if (Array.isArray(v)) v.forEach((item) => qs.append(k, item));
    else qs.append(k, v);
  }
  return `${s.server}/rest/${method}?${qs}`;
}

let lastServerTime = 0; // the server's clock from the last response's Date header

async function call(method, params = {}, s = session) {
  if (!s) throw new ApiError("Sign in to continue.", "no-session");
  let res;
  try {
    res = await fetch(buildUrl(method, params, s));
  } catch {
    throw new ApiError("Can't reach the music server. Check that you're home or the VPN is on.", "network");
  }
  if (!res.ok) throw new ApiError(`The server answered with HTTP ${res.status}.`, res.status);
  lastServerTime = Date.parse(res.headers.get("date") || "") || 0;
  const body = await res.json();
  const r = body["subsonic-response"];
  if (!r) throw new ApiError("That address isn't a Navidrome or Subsonic server.", "bad-response");
  if (r.status !== "ok") {
    const code = r.error?.code;
    const msg = code === 40 ? "Wrong username or password." : r.error?.message || "The server refused the request.";
    throw new ApiError(msg, code);
  }
  return r;
}

const arr = (v) => (v == null ? [] : Array.isArray(v) ? v : [v]);

// ---------- Sign-in ----------

export async function login(server, user, password) {
  const salt = randomSalt();
  const candidate = { server: normalizeServer(server), user: user.trim(), salt, token: md5(password + salt) };
  const r = await call("ping", {}, candidate);
  candidate.serverType = r.type || "subsonic";
  candidate.serverVersion = r.serverVersion || r.version;
  candidate.openSubsonic = !!r.openSubsonic;
  session = candidate;
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  return session;
}

// ---------- URLs for <img> and <audio> ----------

export function coverUrl(id, size = 300) {
  if (!id || !session) return "";
  return buildUrl("getCoverArt", { id, size });
}

// Original file when maxBitRate is 0 (seekable with HTTP range requests).
// Otherwise Navidrome converts to MP3 on the fly; timeOffset starts that conversion mid-song.
export function streamUrl(id, maxBitRate = 0, timeOffset = 0) {
  const params = { id };
  if (maxBitRate > 0) {
    params.maxBitRate = maxBitRate;
    params.format = "mp3";
    if (timeOffset > 0) params.timeOffset = Math.floor(timeOffset);
  }
  return buildUrl("stream", params);
}

export function downloadUrl(id) {
  return buildUrl("download", { id });
}

// ---------- Library ----------

// type: newest | recent | frequent | random | starred | alphabeticalByName | alphabeticalByArtist | byYear | byGenre
export async function getAlbumList(type, { size = 30, offset = 0, genre, fromYear, toYear } = {}) {
  const r = await call("getAlbumList2", { type, size, offset, genre, fromYear, toYear });
  return arr(r.albumList2?.album);
}

export async function getAlbum(id) {
  const r = await call("getAlbum", { id });
  const album = r.album;
  album.song = arr(album.song);
  return album;
}

export async function getArtists() {
  const r = await call("getArtists");
  return arr(r.artists?.index).flatMap((i) => arr(i.artist));
}

export async function getArtist(id) {
  const r = await call("getArtist", { id });
  const artist = r.artist;
  artist.album = arr(artist.album);
  return artist;
}

export async function getArtistInfo(id) {
  try {
    const r = await call("getArtistInfo2", { id, count: 0 });
    return r.artistInfo2 || {};
  } catch {
    return {};
  }
}

export async function getTopSongs(artistName, count = 10) {
  try {
    const r = await call("getTopSongs", { artist: artistName, count });
    return arr(r.topSongs?.song);
  } catch {
    return [];
  }
}

export async function search(query, { artistCount = 8, albumCount = 12, songCount = 30, songOffset = 0 } = {}) {
  const r = await call("search3", { query, artistCount, albumCount, songCount, songOffset });
  const s = r.searchResult3 || {};
  return { artists: arr(s.artist), albums: arr(s.album), songs: arr(s.song) };
}

// Navidrome returns every song for an empty query, page by page.
export async function getAllSongs(offset = 0, size = 100) {
  const r = await call("search3", { query: "", artistCount: 0, albumCount: 0, songCount: size, songOffset: offset });
  return arr(r.searchResult3?.song);
}

export async function getRandomSongs(size = 50, genre) {
  const r = await call("getRandomSongs", { size, genre });
  return arr(r.randomSongs?.song);
}

export async function getGenres() {
  const r = await call("getGenres");
  return arr(r.genres?.genre).sort((a, b) => (b.albumCount || 0) - (a.albumCount || 0));
}

export async function getSongsByGenre(genre, count = 100, offset = 0) {
  const r = await call("getSongsByGenre", { genre, count, offset });
  return arr(r.songsByGenre?.song);
}

export async function getStarred() {
  const r = await call("getStarred2");
  const s = r.starred2 || {};
  return { artists: arr(s.artist), albums: arr(s.album), songs: arr(s.song) };
}

export async function setStarred(id, starred) {
  await call(starred ? "star" : "unstar", { id });
}

// Songs that sound like this one. Navidrome needs an info source (Last.fm, plugins)
// for this; with none it returns nothing and callers fall back to other picks.
export async function getSimilarSongs(id, count = 50) {
  try {
    const r = await call("getSimilarSongs2", { id, count });
    return arr(r.similarSongs2?.song);
  } catch {
    return [];
  }
}

export async function setRating(id, rating) {
  await call("setRating", { id, rating });
}

export async function getSong(id) {
  const r = await call("getSong", { id });
  return r.song;
}

export async function getRadioStations() {
  const r = await call("getInternetRadioStations");
  return arr(r.internetRadioStations?.internetRadioStation);
}

// Albums from a range of years (for "Era enthusiast")
export async function getAlbumsByYear(fromYear, toYear, size = 20) {
  return getAlbumList("byYear", { fromYear, toYear, size });
}

// The server-side copy of your queue, so another device can pick up where you left off.
export async function savePlayQueue(ids, current, positionMs) {
  try {
    await call("savePlayQueue", { id: ids, current, position: Math.floor(positionMs) });
    localStorage.setItem("relli.lastSync", String(Date.now()));
  } catch {}
}

export const SAVE_EVERY_MS = 3000; // how often a playing device saves its spot

function livePosition(position, changedAt, serverNow) {
  if (!serverNow || !changedAt) return position;
  const age = serverNow - changedAt + 500; // Date header is rounded down to the second
  return age >= 0 && age <= SAVE_EVERY_MS * 2 + 1500 ? position + age : position;
}

export async function getPlayQueue() {
  try {
    const r = await call("getPlayQueue");
    const q = r.playQueue;
    if (!q) return null;
    const changedAt = Date.parse(q.changed) || 0;
    return {
      songs: arr(q.entry), current: q.current, position: q.position || 0,
      changedBy: q.changedBy || "", changedAt,
      // Where that device is *now*: if it saved within the last few seconds it's still playing,
      // so add the time since its save (measured with the server's own clock).
      livePosition: livePosition(q.position || 0, changedAt, lastServerTime),
    };
  } catch {
    return null;
  }
}

// ---------- Playlists ----------

export async function getPlaylists() {
  const r = await call("getPlaylists");
  return arr(r.playlists?.playlist);
}

export async function getPlaylist(id) {
  const r = await call("getPlaylist", { id });
  const p = r.playlist;
  p.entry = arr(p.entry);
  return p;
}

export async function createPlaylist(name, songIds = []) {
  const r = await call("createPlaylist", { name, songId: songIds });
  return r.playlist;
}

export async function addToPlaylist(playlistId, songIds) {
  await call("updatePlaylist", { playlistId, songIdToAdd: songIds });
}

export async function removeFromPlaylist(playlistId, indexes) {
  await call("updatePlaylist", { playlistId, songIndexToRemove: indexes });
}

// ---------- Playback reporting ----------

export async function scrobble(id, submission) {
  try {
    await call("scrobble", { id, submission, time: Date.now() });
  } catch {
    // Play counts are nice to have; never interrupt playback over them.
  }
}

// ---------- Lyrics ----------
// Returns { synced: bool, lines: [{ time: ms|null, text }] } or null.

export async function getLyrics(song) {
  if (session?.openSubsonic) {
    try {
      const r = await call("getLyricsBySongId", { id: song.id });
      const list = arr(r.lyricsList?.structuredLyrics);
      const pick = list.find((l) => l.synced && arr(l.line).length) || list.find((l) => arr(l.line).length);
      if (pick) {
        const offset = pick.offset || 0;
        return {
          synced: !!pick.synced,
          lines: arr(pick.line).map((l) => ({
            time: pick.synced && l.start != null ? Math.max(0, l.start - offset) : null,
            text: l.value || "",
          })),
        };
      }
    } catch {
      // fall through to the classic endpoint
    }
  }
  try {
    const r = await call("getLyrics", { artist: song.artist, title: song.title });
    const text = r.lyrics?.value;
    return text ? parseLrc(text) : null;
  } catch {
    return null;
  }
}

// Turns "[01:23.45]line" text into timed lines; plain text comes back unsynced.
export function parseLrc(text) {
  const lines = [];
  let synced = false;
  for (const raw of text.split(/\r?\n/)) {
    const stamps = [...raw.matchAll(/\[(\d+):(\d+(?:\.\d+)?)\]/g)];
    const words = raw.replace(/\[[^\]]*\]/g, "").trim();
    if (stamps.length) {
      synced = true;
      for (const m of stamps) lines.push({ time: Math.round((+m[1] * 60 + +m[2]) * 1000), text: words });
    } else if (!/^\[[a-z]+:/i.test(raw)) {
      lines.push({ time: null, text: words });
    }
  }
  if (synced) {
    const timed = lines.filter((l) => l.time != null).sort((a, b) => a.time - b.time);
    return { synced: true, lines: timed };
  }
  return lines.some((l) => l.text) ? { synced: false, lines } : null;
}
