// Smart queue and Smart flow (after Symfonium's features of the same names).
//
// Smart queue keeps music going: when the queue is about to run out it adds more.
//   random   any songs from your library
//   genre    the genre of what's playing
//   artist   the artists of the last few songs (plus similar artists, if Navidrome knows any)
//   similar  songs similar to what's playing (needs Last.fm or a similar source in Navidrome)
//
// Smart flow reshapes the queue while it plays:
//   shuffle  Shuffle specialist: a random song between each song
//   double   Double shot: two songs in a row from each artist
//   artist   Artist fan: keeps adding songs from the current artist
//   era      Era enthusiast: keeps the queue in the current song's decade

import * as api from "./api.js";
import { player } from "./player.js";
import { getPref, setPref } from "./prefs.js";
import { toast } from "./ui.js";

export const QUEUE_MODES = [
  ["off", "Off", "Playback stops when the queue ends."],
  ["random", "Random", "Adds random songs from your whole library."],
  ["genre", "Genre", "Adds songs from the genre that's playing."],
  ["artist", "Artist", "Adds songs from the artists you've been playing and similar artists."],
  ["similar", "Similar songs", "Adds songs that sound like the current one. Needs Last.fm set up in Navidrome; falls back to Artist."],
];

export const FLOW_MODES = [
  ["off", "Off", "The queue plays as you built it."],
  ["shuffle", "Shuffle specialist", "Slips a random song in after each song."],
  ["double", "Double shot", "Plays two songs in a row from each artist."],
  ["artist", "Artist fan", "Stays on the current artist and keeps adding their songs."],
  ["era", "Era enthusiast", "Keeps the queue in the current song's decade."],
];

const pick = (list, n) => {
  const a = list.slice();
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
  return a.slice(0, n);
};

const tag = (songs, flag) => songs.map((s) => ({ ...s, [flag]: true }));

// ---------- Song sources ----------

const artistCache = new Map();
async function artistSongs(artistId) {
  if (!artistId) return [];
  if (artistCache.has(artistId)) return artistCache.get(artistId);
  const p = (async () => {
    try {
      const artist = await api.getArtist(artistId);
      const albums = pick(artist.album, 4);
      const lists = await Promise.all(albums.map((a) => api.getAlbum(a.id).then((x) => x.song).catch(() => [])));
      return lists.flat();
    } catch {
      return [];
    }
  })();
  artistCache.set(artistId, p);
  return p;
}

async function similarArtistIds(artistId) {
  try {
    const info = await api.getArtistInfo(artistId);
    return (info.similarArtist || []).map((a) => a.id).filter(Boolean);
  } catch {
    return [];
  }
}

async function eraSongs(year, count) {
  if (!year) return [];
  const from = Math.floor(year / 10) * 10;
  const albums = await api.getAlbumsByYear(from, from + 9, 30).catch(() => []);
  const chosen = pick(albums, 4);
  const lists = await Promise.all(chosen.map((a) => api.getAlbum(a.id).then((x) => x.song).catch(() => [])));
  return pick(lists.flat(), count);
}

const inEra = (song, year) => !song.year || !year || Math.floor(song.year / 10) === Math.floor(year / 10);

// ---------- Smart queue ----------

let filling = false;

function recentIds() {
  return new Set(player.queue.slice(-300).map((s) => s.id));
}

async function smartBatch(mode, seed) {
  const exclude = recentIds();
  const fresh = (list) => list.filter((s) => !exclude.has(s.id) && !s.radio);
  if (mode === "random") return fresh(await api.getRandomSongs(20));
  if (mode === "genre") {
    const genre = seed.genre;
    return fresh(genre ? await api.getRandomSongs(20, genre) : await api.getRandomSongs(20));
  }
  if (mode === "similar") {
    const similar = fresh(await api.getSimilarSongs(seed.id, 25));
    if (similar.length >= 3) return pick(similar, 12);
  }
  // artist (and the fallback for similar)
  const recent = player.queue.slice(Math.max(0, player.index - 3), player.index + 1).map((s) => s.artistId).filter(Boolean);
  const ids = new Set(recent);
  for (const id of recent.slice(-1)) (await similarArtistIds(id)).slice(0, 4).forEach((x) => ids.add(x));
  const lists = await Promise.all(pick([...ids], 3).map(artistSongs));
  return pick(fresh(lists.flat()), 12);
}

async function topUpQueue() {
  const mode = getPref("smartQueue");
  const cur = player.current;
  if (mode === "off" || filling || !cur || cur.radio || player.repeat !== "off") return;
  if (player.queue.length - 1 - player.index > 2) return;
  filling = true;
  try {
    const songs = await smartBatch(mode, cur);
    if (songs.length) player.addToQueue(tag(songs, "_smart"));
  } catch {
    // try again on the next song
  } finally {
    filling = false;
  }
}

// ---------- Smart flow ----------

let flowBusy = false;
let lastFlowFor = null;

async function applyFlow() {
  const mode = getPref("smartFlow");
  const cur = player.current;
  if (mode === "off" || flowBusy || !cur || cur.radio) return;
  if (lastFlowFor === cur) return;
  lastFlowFor = cur;
  flowBusy = true;
  const next = player.queue[player.index + 1];
  try {
    if (mode === "shuffle" && !cur._flow && !next?._flow) {
      const [song] = await api.getRandomSongs(1);
      if (song && player.current === cur) player.insertAt(player.index + 1, tag([song], "_flow"));
    } else if (mode === "double" && !cur._flow && next?.artistId !== cur.artistId) {
      const song = pick((await artistSongs(cur.artistId)).filter((s) => s.id !== cur.id && !recentIds().has(s.id)), 1)[0];
      if (song && player.current === cur) player.insertAt(player.index + 1, tag([song], "_flow"));
    } else if (mode === "artist" && player.queue.length - 1 - player.index <= 2) {
      const songs = pick((await artistSongs(cur.artistId)).filter((s) => !recentIds().has(s.id)), 8);
      if (songs.length) player.addToQueue(tag(songs, "_flow"));
    } else if (mode === "era" && cur.year) {
      // Drop upcoming songs from other decades, then top up from this one.
      const upcoming = player.queue.slice(player.index + 1);
      const keep = upcoming.filter((s) => inEra(s, cur.year));
      if (keep.length !== upcoming.length) {
        player.queue.splice(player.index + 1, upcoming.length, ...keep);
        player.queueChanged();
      }
      if (keep.length <= 2) {
        const songs = (await eraSongs(cur.year, 10)).filter((s) => !recentIds().has(s.id));
        if (songs.length) player.addToQueue(tag(songs, "_flow"));
      }
    }
  } catch {
    // flow is best-effort
  } finally {
    flowBusy = false;
  }
}

player.addEventListener("track", () => { applyFlow().then(topUpQueue); });
player.addEventListener("queue", () => topUpQueue());

export function setSmartQueue(mode) {
  setPref("smartQueue", mode);
  if (mode !== "off" && player.repeat !== "off") toast("Smart queue works when repeat is off.");
  topUpQueue();
}

export function setSmartFlow(mode) {
  setPref("smartFlow", mode);
  lastFlowFor = null;
  applyFlow();
}

// ---------- Mixes ----------

// Instant mix: songs like this one, right now.
export async function instantMix(song) {
  let songs = await api.getSimilarSongs(song.id, 40);
  if (songs.length < 5) {
    const own = await artistSongs(song.artistId);
    const genre = song.genre ? await api.getRandomSongs(25, song.genre) : await api.getRandomSongs(25);
    songs = pick([...own, ...genre].filter((s) => s.id !== song.id), 40);
  }
  player.playList([song, ...songs.filter((s) => s.id !== song.id)], 0, { shuffle: false, source: `Instant mix: ${song.title}` });
}

// Radio mix: an endless station built from this song.
export async function radioMix(song) {
  await instantMix(song);
  setPref("smartQueue", "similar");
  player.source = `Radio: ${song.title}`;
  toast("Radio mix on. Smart queue will keep it going.");
}

export async function trackMix() {
  player.playList(await api.getRandomSongs(80), 0, { shuffle: false, source: "Track mix" });
}

export async function albumMix() {
  const albums = await api.getAlbumList("random", { size: 6 });
  const lists = await Promise.all(albums.map((a) => api.getAlbum(a.id).then((x) => x.song).catch(() => [])));
  player.shufflePlay(lists.flat(), "Album mix");
}

export async function decadeMix() {
  const sample = await api.getRandomSongs(40);
  const years = sample.map((s) => s.year).filter(Boolean);
  if (!years.length) return trackMix();
  const year = years[Math.floor(Math.random() * years.length)];
  const songs = await eraSongs(year, 60);
  const decade = `${Math.floor(year / 10) * 10}s`;
  if (songs.length < 5) return trackMix();
  player.shufflePlay(songs, `${decade} mix`);
}
