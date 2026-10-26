// The playback engine.
// Two decks (audio elements) take turns so the next song can be loaded early for
// gapless playback or faded in for a crossfade. A third element plays internet radio.
// Also handles the queue, shuffle/repeat, speed/pitch, ReplayGain, lock-screen
// controls, play counts, and resuming where you left off (on this device or another).

import * as api from "./api.js";
import { streamUrl, coverUrl, scrobble } from "./api.js";
import { getPref, setPref } from "./prefs.js";
import { engine } from "./audio.js";
import { offlineUrl } from "./offline.js";
import { toast } from "./ui.js";

const SAVE_KEY = "relli.queue";
const KEEP = ["id", "title", "artist", "artistId", "album", "albumId", "coverArt", "duration", "track", "discNumber", "year", "starred", "userRating", "suffix", "bitRate", "replayGain", "genre", "radio", "streamUrl", "_smart"];
const slim = (s) => Object.fromEntries(KEEP.filter((k) => s[k] != null).map((k) => [k, s[k]]));
const db = (v) => Math.pow(10, v / 20);

function shuffled(list) {
  const a = list.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

class Deck {
  constructor(n) {
    this.n = n;
    this.el = new Audio();
    if (window.relliDesktop) this.el.crossOrigin = "anonymous";
    this.el.preload = "auto";
    this.song = null;
    this.offset = 0;   // seconds skipped via timeOffset (converted streams)
    this.blob = null;  // object URL for downloaded songs
    this.ready = null; // promise resolved once src is set
  }
  reset() {
    this.el.pause();
    this.el.removeAttribute("src");
    this.el.load();
    if (this.blob) { URL.revokeObjectURL(this.blob); this.blob = null; }
    this.song = null;
    this.offset = 0;
  }
}

class Player extends EventTarget {
  constructor() {
    super();
    this.decks = [new Deck(0), new Deck(1)];
    this.radioDeck = new Deck(2);
    this.cur = 0;
    this.queue = [];
    this.index = -1;
    this.original = null;
    this.shuffle = getPref("shuffle");
    this.repeat = getPref("repeat");
    this.source = "";
    this.counted = false;
    this.errors = 0;
    this.sleepFactor = 1;
    this.fade = null;          // running crossfade
    this.preloaded = null;     // { deck, song, index }
    this.stopAfter = null;     // "track" | "queue": set by the sleep timer
    engine.attach(this.decks.map((d) => d.el));

    for (const d of [...this.decks, this.radioDeck]) {
      const a = d.el;
      const mine = () => d === this.deck;
      a.addEventListener("play", () => mine() && this.emit("state"));
      a.addEventListener("pause", () => mine() && this.emit("state"));
      a.addEventListener("waiting", () => mine() && this.emit("state"));
      a.addEventListener("playing", () => { if (mine()) { this.errors = 0; this.emit("state"); } });
      a.addEventListener("timeupdate", () => mine() && this.onTime());
      a.addEventListener("ended", () => { if (mine()) this.next(false); else if (!this.fade) d.reset(); });
      a.addEventListener("error", () => mine() && this.onError());
    }

    this.applySpeed();
    engine.setPitch(getPref("pitch"));
    this.setupMediaSession();
    window.addEventListener("pagehide", () => this.save(true));
    setInterval(() => { if (this.playing) this.save(); }, 10000);
  }

  emit(type) { this.dispatchEvent(new Event(type)); }

  get deck() { return this.current?.radio ? this.radioDeck : this.decks[this.cur]; }
  get audio() { return this.deck.el; }
  get current() { return this.queue[this.index] || null; }
  get playing() { return !this.deck.el.paused; }
  get isRadio() { return !!this.current?.radio; }
  get time() { return this.deck.offset + (this.deck.el.currentTime || 0); }
  get volume() { return engine.volume; }
  get muted() { return engine.muted; }

  get duration() {
    if (this.isRadio) return 0;
    const d = this.deck.el.duration;
    if (this.deck.offset === 0 && isFinite(d) && d > 0) return d;
    return this.current?.duration || 0;
  }

  // ---------- Starting playback ----------

  playList(songs, start = 0, { shuffle = this.shuffle, source = "" } = {}) {
    if (!songs.length) return;
    this.source = source;
    const list = songs.slice();
    this.shuffle = shuffle;
    setPref("shuffle", shuffle);
    if (shuffle) {
      this.original = list;
      const first = list[start];
      this.queue = [first, ...shuffled(list.filter((_, i) => i !== start))];
      this.index = 0;
    } else {
      this.original = null;
      this.queue = list;
      this.index = start;
    }
    this.load(true);
    this.emit("queue");
  }

  shufflePlay(songs, source = "") {
    if (!songs.length) return;
    this.playList(songs, Math.floor(Math.random() * songs.length), { shuffle: true, source });
  }

  playRadio(station) {
    const song = {
      id: `radio-${station.id}`, radio: true, title: station.name, artist: "Internet radio",
      streamUrl: station.streamUrl, coverArt: station.coverArt || null, duration: 0,
    };
    this.playList([song], 0, { shuffle: false, source: "Internet radio" });
  }

  async sourceFor(deck, song, startAt) {
    if (song.radio) return song.streamUrl;
    const local = await offlineUrl(song.id);
    if (local) { deck.blob = local; return local; }
    return streamUrl(song.id, getPref("quality"), startAt);
  }

  async prepare(deck, song, startAt = 0) {
    if (deck.blob) { URL.revokeObjectURL(deck.blob); deck.blob = null; }
    deck.song = song;
    const src = await this.sourceFor(deck, song, startAt);
    const converted = !song.radio && !deck.blob && getPref("quality") > 0;
    deck.offset = converted ? startAt : 0;
    deck.el.src = src;
    if (!converted && startAt > 0) {
      deck.el.addEventListener("loadedmetadata", () => { deck.el.currentTime = startAt; }, { once: true });
    }
    this.applySpeed(deck);
  }

  async load(autoplay, startAt = 0) {
    const song = this.current;
    if (!song) return;
    this.cancelFade();
    const token = (this._loadToken = (this._loadToken || 0) + 1);

    // Stop everything that isn't the deck we're about to use.
    if (song.radio) this.decks.forEach((d) => d.reset());
    else {
      this.radioDeck.reset();
      const pre = this.preloaded;
      if (pre && pre.song === song && pre.index === this.index && startAt === 0) {
        // Gapless: the next song is already buffered on the other deck.
        this.decks[this.cur].reset();
        this.cur = pre.deck.n;
        this.preloaded = null;
        this.finishLoad(song, autoplay);
        return;
      }
      this.decks[1 - this.cur].reset();
    }
    this.preloaded = null;

    const deck = this.deck;
    deck.el.pause();
    await this.prepare(deck, song, startAt);
    if (token !== this._loadToken) return;
    this.finishLoad(song, autoplay);
  }

  finishLoad(song, autoplay) {
    this.counted = false;
    this.applyLevel();
    if (autoplay) {
      engine.start();
      this.deck.el.play().catch(() => this.emit("state"));
      if (!song.radio) scrobble(song.id, false);
    }
    this.updateMetadata();
    this.emit("track");
    this.save();
  }

  // ---------- Transport ----------

  toggle() {
    if (!this.current) return;
    if (this.deck.el.paused) this.play();
    else this.pause();
  }

  play() {
    if (!this.current) return;
    engine.start();
    if (!this.deck.el.src) return this.load(true);
    this.deck.el.play().catch(() => {});
  }

  pause() {
    this.deck.el.pause();
    if (this.fade) { this.fade.from.el.pause(); this.cancelFade(true); }
    this.save(true); // so another device can pick up from exactly here
  }

  next(manual = true) {
    if (!this.queue.length) return;
    if (!manual && this.stopAfter === "track") {
      this.stopAfter = null;
      if (this.index < this.queue.length - 1) this.index++;
      this.load(false);
      this.dispatchEvent(new Event("sleepdone"));
      return;
    }
    if (!manual && this.repeat === "one") return this.load(true);
    this.dispatchEvent(new CustomEvent("advance", { detail: { manual } }));
    if (this.index < this.queue.length - 1) {
      this.index++;
      return this.load(true);
    }
    // End of the queue: loop if repeat is on, otherwise park on the first song, paused.
    this.dispatchEvent(new Event("queueend"));
    this.index = 0;
    const stop = this.stopAfter === "queue";
    if (stop) { this.stopAfter = null; this.dispatchEvent(new Event("sleepdone")); }
    this.load(this.repeat === "all" && !stop);
  }

  prev() {
    if (!this.queue.length) return;
    if (this.time > 3 || this.index === 0) {
      if (this.index === 0 && this.repeat === "all" && this.time <= 3) {
        this.index = this.queue.length - 1;
        return this.load(true);
      }
      return this.seek(0);
    }
    this.index--;
    this.load(true);
  }

  jump(i) {
    if (i < 0 || i >= this.queue.length) return;
    this.index = i;
    this.load(true);
  }

  seek(seconds) {
    if (!this.current || this.isRadio) return;
    const t = Math.max(0, Math.min(seconds, this.duration || seconds));
    if (this.deck.offset !== 0 || (getPref("quality") > 0 && !this.deck.blob)) {
      this.load(this.playing, t);
    } else {
      this.deck.el.currentTime = t;
    }
    this.emit("time");
  }

  setVolume(v) { engine.setVolume(v); this.applyRadioVolume(); this.emit("volume"); }
  toggleMute() { engine.toggleMute(); this.applyRadioVolume(); this.emit("volume"); }
  applyRadioVolume() { this.radioDeck.el.volume = engine.muted ? 0 : engine.volume * this.sleepFactor; }

  // ---------- Speed, pitch, loudness ----------

  setSpeed(rate) {
    setPref("speed", rate);
    this.applySpeed();
    this.emit("speed");
  }

  setPitch(semis) {
    setPref("pitch", semis);
    engine.setPitch(semis);
    this.emit("speed");
  }

  applySpeed(only) {
    const rate = getPref("speed");
    for (const d of only ? [only] : [...this.decks]) {
      d.el.preservesPitch = true;
      d.el.playbackRate = rate;
      d.el.defaultPlaybackRate = rate;
    }
  }

  // ReplayGain makes quiet and loud recordings play at similar volume.
  gainFor(song) {
    const mode = getPref("replayGain");
    const rg = song?.replayGain;
    if (mode === "off" || !rg) return 1;
    let useAlbum = mode === "album";
    if (mode === "auto") {
      const prev = this.queue[this.index - 1], next = this.queue[this.index + 1];
      useAlbum = !this.shuffle && [prev, next].some((s) => s && s.albumId === song.albumId);
    }
    const gain = (useAlbum ? rg.albumGain ?? rg.trackGain : rg.trackGain ?? rg.albumGain) ?? 0;
    const peak = (useAlbum ? rg.albumPeak ?? rg.trackPeak : rg.trackPeak ?? rg.albumPeak) || 0;
    let factor = db(gain + (getPref("rgPreamp") || 0));
    if (getPref("rgClip") && peak > 0) factor = Math.min(factor, 1 / peak);
    return factor;
  }

  applyLevel() {
    if (this.fade) return;
    this.decks.forEach((d) => engine.setDeckLevel(d.n, d === this.deck ? this.gainFor(d.song) * this.sleepFactor : 0));
    this.applyRadioVolume();
  }

  setSleepFactor(f) {
    this.sleepFactor = f;
    if (!this.fade) this.applyLevel();
  }

  // ---------- Crossfade and gapless preloading ----------

  upcomingIndex() {
    if (this.repeat === "one") return -1;
    if (this.index < this.queue.length - 1) return this.index + 1;
    return this.repeat === "all" && this.queue.length > 1 ? 0 : -1;
  }

  async preloadNext() {
    const i = this.upcomingIndex();
    const song = this.queue[i];
    if (!song || song.radio || this.current?.radio) return;
    if (this.preloaded && this.preloaded.song === song && this.preloaded.index === i) return;
    const deck = this.decks[1 - this.cur];
    deck.reset();
    engine.setDeckLevel(deck.n, 0);
    this.preloaded = { deck, song, index: i, loading: true };
    const ref = this.preloaded;
    await this.prepare(deck, song, 0);
    if (this.preloaded === ref) ref.loading = false;
  }

  startCrossfade(seconds) {
    const pre = this.preloaded;
    if (!pre || pre.loading) return;
    const from = this.deck;
    const to = pre.deck;
    const fromGain = this.gainFor(from.song) * this.sleepFactor;
    this.dispatchEvent(new CustomEvent("advance", { detail: { manual: false } }));
    this.index = pre.index;
    this.cur = to.n;
    this.preloaded = null;
    const toGain = this.gainFor(to.song) * this.sleepFactor;
    engine.setDeckLevel(to.n, 0);
    to.el.play().catch(() => {});
    const started = performance.now();
    const fade = { from, to, timer: null };
    this.fade = fade;
    fade.timer = setInterval(() => {
      const k = Math.min(1, (performance.now() - started) / (seconds * 1000));
      // equal-power curve keeps the overall loudness steady during the fade
      engine.setDeckLevel(from.n, fromGain * Math.cos(k * Math.PI / 2));
      engine.setDeckLevel(to.n, toGain * Math.sin(k * Math.PI / 2));
      if (k >= 1) this.cancelFade();
    }, 50);
    this.counted = false;
    scrobble(to.song.id, false);
    this.updateMetadata();
    this.emit("track");
    this.save();
  }

  cancelFade(keepFrom) {
    const f = this.fade;
    if (!f) return;
    clearInterval(f.timer);
    this.fade = null;
    if (!keepFrom) f.from.reset();
    this.applyLevel();
  }

  // ---------- Queue editing ----------

  playNext(songs) {
    songs = [].concat(songs);
    if (!this.current) return this.playList(songs);
    this.queue.splice(this.index + 1, 0, ...songs);
    this.original?.push(...songs);
    this.queueChanged();
  }

  addToQueue(songs) {
    songs = [].concat(songs);
    if (!this.current) return this.playList(songs);
    this.queue.push(...songs);
    this.original?.push(...songs);
    this.queueChanged();
  }

  insertAt(pos, songs) {
    songs = [].concat(songs);
    this.queue.splice(pos, 0, ...songs);
    this.original?.push(...songs);
    this.queueChanged();
  }

  removeAt(i) {
    if (i === this.index || i < 0 || i >= this.queue.length) return;
    const [song] = this.queue.splice(i, 1);
    if (this.original) {
      const j = this.original.indexOf(song);
      if (j >= 0) this.original.splice(j, 1);
    }
    if (i < this.index) this.index--;
    this.queueChanged();
  }

  move(from, to) {
    if (from === to) return;
    const [song] = this.queue.splice(from, 1);
    this.queue.splice(to, 0, song);
    if (from === this.index) this.index = to;
    else if (from < this.index && to >= this.index) this.index--;
    else if (from > this.index && to <= this.index) this.index++;
    this.queueChanged();
  }

  clearUpcoming() {
    this.queue.splice(this.index + 1);
    if (this.original) this.original = this.original.filter((s) => this.queue.includes(s));
    this.queueChanged();
  }

  queueChanged() {
    if (this.preloaded && this.queue[this.preloaded.index] !== this.preloaded.song) {
      this.preloaded.deck.reset();
      this.preloaded = null;
    }
    this.emit("queue");
    this.save();
  }

  // ---------- Modes ----------

  setShuffle(on) {
    this.shuffle = on;
    setPref("shuffle", on);
    const cur = this.current;
    if (cur) {
      if (on) {
        this.original = this.queue.slice();
        const before = this.queue.slice(0, this.index + 1);
        const after = shuffled(this.queue.slice(this.index + 1));
        this.queue = [...before, ...after];
      } else if (this.original) {
        this.queue = this.original;
        this.original = null;
        this.index = Math.max(0, this.queue.indexOf(cur));
      }
    }
    this.emit("modes");
    this.queueChanged();
  }

  cycleRepeat() {
    this.repeat = { off: "all", all: "one", one: "off" }[this.repeat];
    setPref("repeat", this.repeat);
    this.emit("modes");
  }

  // ---------- Internals ----------

  onTime() {
    const song = this.current;
    if (!song || song.radio) return this.emit("time");
    const t = this.time, d = this.duration;
    if (!this.counted) {
      const needed = Math.min(240, (d || 60) / 2);
      if (t >= needed) {
        this.counted = true;
        scrobble(song.id, true);
      }
    }
    const remaining = d - t;
    const xf = getPref("crossfade");
    if (d > 0 && remaining < Math.max(25, xf + 15) && !this.fade) this.preloadNext();
    const stopping = this.stopAfter === "track" || (this.stopAfter === "queue" && this.index === this.queue.length - 1);
    if (xf > 0 && !stopping && d > xf * 2 && remaining <= xf && remaining > 0.3 && !this.fade && this.preloaded && this.playing) {
      this.startCrossfade(xf);
    }
    this.emit("time");
    this.updatePosition();
  }

  onError() {
    if (!this.deck.el.getAttribute("src") || !this.current) return;
    this.errors++;
    if (this.errors >= 3) {
      toast("Playback stopped after several failures. Check your connection to the server.");
      this.errors = 0;
      this.emit("state");
      return;
    }
    toast(this.isRadio ? "That station isn't responding." : `Couldn't play “${this.current.title}”. Skipping it.`);
    if (!this.isRadio) setTimeout(() => this.next(true), 800);
  }

  setupMediaSession() {
    if (!("mediaSession" in navigator)) return;
    const ms = navigator.mediaSession;
    const set = (action, fn) => { try { ms.setActionHandler(action, fn); } catch {} };
    set("play", () => this.play());
    set("pause", () => this.pause());
    set("previoustrack", () => this.prev());
    set("nexttrack", () => this.next());
    set("seekbackward", (d) => this.seek(this.time - (d.seekOffset || 10)));
    set("seekforward", (d) => this.seek(this.time + (d.seekOffset || 10)));
    set("seekto", (d) => this.seek(d.seekTime));
    this.addEventListener("state", () => { ms.playbackState = this.playing ? "playing" : "paused"; });
  }

  updateMetadata() {
    if (!("mediaSession" in navigator) || !this.current) return;
    const s = this.current;
    const art = s.coverArt ? [96, 256, 512].map((n) => ({ src: new URL(coverUrl(s.coverArt, n), location.href).href, sizes: `${n}x${n}`, type: "image/jpeg" })) : [];
    navigator.mediaSession.metadata = new MediaMetadata({ title: s.title, artist: s.artist || "", album: s.album || "", artwork: art });
  }

  updatePosition() {
    if (!("mediaSession" in navigator) || !navigator.mediaSession.setPositionState) return;
    const now = performance.now();
    if (this._lastPos && now - this._lastPos < 1000) return;
    this._lastPos = now;
    const duration = this.duration;
    if (!duration) return;
    try {
      navigator.mediaSession.setPositionState({ duration, position: Math.min(this.time, duration), playbackRate: getPref("speed") });
    } catch {}
  }

  save(urgent) {
    try {
      if (!this.current) return localStorage.removeItem(SAVE_KEY);
      localStorage.setItem(SAVE_KEY, JSON.stringify({
        queue: this.queue.map(slim),
        index: this.index,
        time: Math.floor(this.time),
        source: this.source || "",
        savedAt: Date.now(),
      }));
    } catch {}
    // Keep a copy on the server too, so another device can resume.
    clearTimeout(this._serverSave);
    const push = () => {
      const songs = this.queue.filter((s) => !s.radio).slice(0, 500);
      const cur = this.current;
      if (!songs.length || !cur || cur.radio || this.passive) return; // another device took over (see handoff.js)
      api.savePlayQueue(songs.map((s) => s.id), cur.id, this.time * 1000);
    };
    if (urgent) push();
    else this._serverSave = setTimeout(push, 4000);
  }

  // Bring back the last queue, paused at the same spot.
  restore() {
    try {
      const saved = JSON.parse(localStorage.getItem(SAVE_KEY));
      if (!saved?.queue?.length) return;
      this.queue = saved.queue;
      this.index = Math.min(saved.index || 0, this.queue.length - 1);
      this.original = null;
      this.source = saved.source || "";
      this.load(false, saved.time || 0);
      this.emit("queue");
    } catch {}
  }

  // Take over the queue saved by another device.
  async resumeFromServer(autoplay = false) {
    const q = await api.getPlayQueue();
    if (!q || !q.songs.length) return false;
    const i = Math.max(0, q.songs.findIndex((s) => s.id === q.current));
    this.queue = q.songs;
    this.index = i;
    this.original = null;
    this.source = q.changedBy ? `Continued from ${q.changedBy.replace(/^RelliRadio\s*/, "").replace(/[()]/g, "") || "another device"}` : "Resumed from another device";
    this.load(autoplay, (q.position || 0) / 1000);
    try { localStorage.setItem("relli.lastSync", String(Date.now())); } catch {}
    this.emit("queue");
    return true;
  }

  forget() {
    this.cancelFade();
    [...this.decks, this.radioDeck].forEach((d) => d.reset());
    this.queue = [];
    this.index = -1;
    this.original = null;
    this.preloaded = null;
    try { localStorage.removeItem(SAVE_KEY); } catch {}
    this.emit("track");
    this.emit("queue");
  }
}

export const player = new Player();
