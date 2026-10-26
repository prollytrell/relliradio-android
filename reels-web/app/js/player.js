// The video player: full screen over the app.
// Jellyfin decides how each video reaches you: the original file ("direct play") when this browser
// can handle it, otherwise converted on the fly into HLS (small video chunks), played with hls.js.
// Every 10 seconds it tells Jellyfin where you are, so any other device can pick up from there.

import * as api from "./api.js";
import { getPref, setPref, maxBitrate } from "./prefs.js";
import { h, icon, iconButton, button, img, fmtClock, episodeCode, msOf, toast } from "./ui.js";

const QUALITIES = [[null, "Auto (from Settings)"], [0, "Original"], [20, "20 Mbps · 1080p high"], [10, "10 Mbps · 1080p"], [6, "6 Mbps · 720p"], [3, "3 Mbps · 480p"], [1, "1 Mbps · data saver"]];
const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];
const SKIPPABLE = new Set(["Intro", "Recap", "Preview", "Commercial"]);

let current = null;
export const isOpen = () => !!current;

export function play(id, { startMs = -1, live = false } = {}) {
  current?.close(true);
  current = new Session(id, startMs, live);
}

class Session {
  constructor(id, startMs, live) {
    this.live = live;
    this.item = null;
    this.source = null;
    this.method = "DirectPlay";
    this.audioIndex = null;
    this.subtitleIndex = null;
    this.burnedIn = false;
    this.quality = null;
    this.triedTranscode = false;
    this.next = null;
    this.segments = [];
    this.skipped = new Set();
    this.upNext = null; // { left, timer }
    this.upNextDismissed = false;
    this.started = false;
    this.build();
    this.open(id, startMs);
  }

  // ---------- Screen ----------

  build() {
    this.video = h("video.video", { playsinline: true, crossorigin: "anonymous", preload: "auto" });
    this.video.volume = getPref("volume");
    this.title = h("h2.p-title");
    this.sub = h("p.p-sub");
    this.liveTag = h("span.live-tag", { hidden: true }, "LIVE");
    this.time = h("span.p-time", "0:00");
    this.left = h("span.p-time", "");
    this.bar = h("input.p-seek", { type: "range", min: 0, max: 1000, value: 0, step: 1, "aria-label": "Seek" });
    this.playBtn = h("button.p-big", { type: "button", "aria-label": "Play or pause", onclick: () => this.toggle() }, icon("pause"));
    this.nextBtn = h("button.p-opt", { type: "button", hidden: true, onclick: () => this.playNext() }, icon("next"), h("span", "Next episode"));
    this.audioBtn = h("button.p-opt", { type: "button", hidden: true, onclick: () => this.menu("audio") }, icon("audio"), h("span", "Audio"));
    this.fullBtn = iconButton("full", "Full screen (F)", () => this.fullscreen());
    this.skipBtn = h("button.pill.primary.p-skip", { type: "button", hidden: true, onclick: () => this.skip() }, icon("next"), h("span", "Skip intro"));
    this.upNextEl = h("div.p-upnext", { hidden: true });
    this.spinner = h("div.spinner.p-spin");
    this.error = h("div.p-error", { hidden: true });
    this.panel = h("div.p-panel", { hidden: true });

    this.controls = h("div.p-controls",
      h("header.p-top",
        iconButton("back", "Back (Esc)", () => this.close()),
        h("div.p-heading", this.title, this.sub), this.liveTag),
      h("div.p-middle",
        iconButton("back10", "Back 10 seconds (←)", () => this.seekBy(-10000), "p-jump"),
        this.playBtn,
        iconButton("fwd30", "Forward 30 seconds (→)", () => this.seekBy(30000), "p-jump")),
      h("footer.p-bottom",
        h("div.p-seekrow", this.time, this.bar, this.left),
        h("div.p-opts",
          h("button.p-opt", { type: "button", onclick: () => this.menu("subs") }, icon("cc"), h("span", "Subtitles")),
          this.audioBtn,
          h("button.p-opt", { type: "button", onclick: () => this.menu("quality") }, icon("hd"), h("span", "Quality")),
          h("button.p-opt.p-speed", { type: "button", onclick: () => this.menu("speed") }, icon("speed"), h("span", "Speed")),
          h("span.p-spacer"),
          this.nextBtn,
          this.fullBtn)));

    this.root = h("div.player", { role: "dialog", "aria-label": "Video player", tabindex: "-1" },
      this.video, this.spinner, this.controls, this.skipBtn, this.upNextEl, this.error, this.panel);
    document.body.append(this.root);
    document.body.classList.add("watching");
    this.root.focus();

    // Seeking with the bar
    this.bar.addEventListener("input", () => { this.seeking = true; this.time.textContent = fmtClock(this.barMs()); this.poke(); });
    this.bar.addEventListener("change", () => { this.video.currentTime = this.barMs() / 1000; this.seeking = false; });

    // Clicking the picture pauses; double-click goes full screen. Mouse movement shows the controls.
    this.video.addEventListener("click", () => { this.panel.hidden ? this.toggle() : this.closePanel(); });
    this.video.addEventListener("dblclick", () => this.fullscreen());
    this.root.addEventListener("mousemove", () => this.poke());
    this.root.addEventListener("touchstart", () => this.poke(), { passive: true });

    const v = this.video;
    v.addEventListener("playing", () => { this.spinner.hidden = true; this.playBtn.replaceChildren(icon("pause")); this.poke(); if (!this.started) { this.started = true; this.report(""); } else this.report("Progress"); });
    v.addEventListener("pause", () => { this.playBtn.replaceChildren(icon("play")); this.showControls(true); this.report("Progress"); });
    v.addEventListener("waiting", () => { this.spinner.hidden = false; });
    v.addEventListener("canplay", () => { this.spinner.hidden = true; });
    v.addEventListener("timeupdate", () => this.tick());
    v.addEventListener("volumechange", () => setPref("volume", v.volume));
    v.addEventListener("ended", () => this.ended());
    v.addEventListener("error", () => this.failed(v.error?.message || "media error"));

    this.onKey = (e) => this.key(e);
    document.addEventListener("keydown", this.onKey, true);
    this.onFs = () => this.fullBtn.replaceChildren(icon(document.fullscreenElement ? "unfull" : "full"));
    document.addEventListener("fullscreenchange", this.onFs);
    this.reporter = setInterval(() => { if (!this.video.paused) this.report("Progress"); }, 10000);
    history.pushState({ player: true }, "");
    this.onPop = () => this.close(true);
    window.addEventListener("popstate", this.onPop);
    this.poke();
  }

  barMs() { return (Number(this.bar.value) / 1000) * this.duration(); }
  duration() { return isFinite(this.video.duration) && this.video.duration > 0 ? this.video.duration * 1000 : msOf(this.item?.RunTimeTicks); }
  position() { return this.video.currentTime * 1000 + (this.offsetMs || 0); }

  poke() {
    this.showControls(true);
    clearTimeout(this.hideTimer);
    this.hideTimer = setTimeout(() => { if (!this.video.paused && this.panel.hidden) this.showControls(false); }, 3500);
  }

  showControls(on) {
    this.root.classList.toggle("idle", !on);
  }

  // ---------- Loading ----------

  async open(id, startMs) {
    try {
      this.item = await api.item(id);
      const i = this.item;
      this.title.textContent = i.SeriesName || i.Name;
      this.sub.textContent = this.live ? (i.CurrentProgram?.Name || "") : i.Type === "Episode" ? [episodeCode(i), i.Name].filter(Boolean).join(" · ") : (i.ProductionYear || "");
      this.liveTag.hidden = !this.live;
      this.root.classList.toggle("is-live", this.live);
      document.title = `${i.SeriesName ? i.SeriesName + " · " : ""}${i.Name} · RelliReels`;
      const pct = i.UserData?.PlayedPercentage || 0;
      const start = startMs < 0 ? (pct < 95 ? msOf(i.UserData?.PlaybackPositionTicks) : 0) : startMs;
      await this.load(start);
      if (i.Type === "Episode" && !this.live) api.nextEpisode(i).then((n) => { this.next = n; this.nextBtn.hidden = !n; }).catch(() => {});
      if (!this.live) api.segments(id).then((s) => { this.segments = s; });
    } catch (e) {
      this.showError(e.message || "Couldn't start playback.");
    }
  }

  bitrate() { return this.quality == null ? maxBitrate() : (this.quality > 0 ? this.quality * 1e6 : 200e6); }

  async load(startMs, { audio = this.audioIndex, burn = null, allowDirect = !this.triedTranscode } = {}) {
    const i = this.item;
    this.error.hidden = true;
    this.spinner.hidden = false;
    const oldLive = this.source?.LiveStreamId;
    let info;
    try {
      info = await api.playbackInfo(i.Id, { startMs, maxBitrate: this.bitrate(), audioIndex: audio, subtitleIndex: burn, mediaSourceId: this.source?.Id, live: this.live, allowDirect });
    } catch (e) { return this.showError(e.message); }
    const src = info.MediaSources?.[0];
    if (!src) return this.showError("Jellyfin has no playable file for this.");
    if (oldLive && oldLive !== src.LiveStreamId) api.closeLiveStream(oldLive);
    this.source = src;
    this.playSessionId = info.PlaySessionId;
    this.burnedIn = burn != null;
    if (this.audioIndex == null) this.audioIndex = src.DefaultAudioStreamIndex ?? null;
    this.audioBtn.hidden = src.MediaStreams.filter((s) => s.Type === "Audio").length < 2;

    const transcode = !!src.TranscodingUrl && (!(src.SupportsDirectPlay || src.SupportsDirectStream) || !allowDirect);
    this.method = transcode ? "Transcode" : src.SupportsDirectPlay ? "DirectPlay" : "DirectStream";
    const url = transcode ? api.absolute(src.TranscodingUrl) : api.directUrl(i.Id, src, this.playSessionId);
    const isHls = transcode || /\.m3u8/.test(url);
    // HLS playlists cover the whole video, so we seek inside them. A plain converted stream would
    // start at the requested spot instead, and the clock needs that offset added.
    this.offsetMs = 0;
    const seekInFile = isHls || !/StartTimeTicks=/i.test(url);

    this.hls?.destroy();
    this.hls = null;
    const v = this.video;
    v.querySelectorAll("track").forEach((t) => t.remove());
    let viaHlsJs = false;
    if (isHls && window.Hls && Hls.isSupported() && !v.canPlayType("application/vnd.apple.mpegurl")) {
      viaHlsJs = true;
      this.hls = new Hls({ startPosition: this.live ? -1 : startMs / 1000, maxBufferLength: 30, xhrSetup: (xhr) => { xhr.withCredentials = false; } });
      this.hls.loadSource(url);
      this.hls.attachMedia(v);
      this.hls.on(Hls.Events.ERROR, (_, d) => { if (d.fatal) this.failed(d.details); });
    } else {
      v.src = url;
    }
    if (!seekInFile) this.offsetMs = startMs;

    // Text subtitles: separate WebVTT files the browser draws over the picture.
    for (const s of src.MediaStreams.filter((s) => s.Type === "Subtitle" && s.DeliveryMethod === "External" && s.DeliveryUrl)) {
      v.append(h("track", { kind: "subtitles", src: api.absolute(s.DeliveryUrl), srclang: s.Language || "und", label: `jf:${s.Index}` }));
    }
    if (this.subtitleIndex == null && !this.burnedIn) this.subtitleIndex = this.initialSubtitle(src);
    if (this.burnedIn) this.subtitleIndex = burn;
    this.applySubtitle();

    const begin = () => {
      if (!this.live && !viaHlsJs && seekInFile && startMs > 0) v.currentTime = startMs / 1000;
      v.playbackRate = this.speed || 1;
      v.play().catch(() => { this.playBtn.replaceChildren(icon("play")); this.showControls(true); });
    };
    if (v.readyState >= 1) begin(); else v.addEventListener("loadedmetadata", begin, { once: true });
  }

  initialSubtitle(src) {
    const subs = src.MediaStreams.filter((s) => s.Type === "Subtitle");
    const lang = getPref("subtitleLang");
    switch (getPref("subtitles")) {
      case "off": return -1;
      case "on": return (subs.find((s) => s.Language === lang && !s.IsForced) || subs.find((s) => s.Index === src.DefaultSubtitleStreamIndex) || subs[0])?.Index ?? -1;
      default: return (src.DefaultSubtitleStreamIndex >= 0 ? src.DefaultSubtitleStreamIndex : null) ?? subs.find((s) => s.IsForced && (!s.Language || s.Language === lang))?.Index ?? -1;
    }
  }

  applySubtitle() {
    const want = this.burnedIn ? null : `jf:${this.subtitleIndex}`;
    const show = () => { for (const t of this.video.textTracks) t.mode = t.label === want ? "showing" : "disabled"; };
    show();
    setTimeout(show, 300); // tracks attach a moment after the video starts
  }

  failed(msg) {
    // The browser couldn't play the file as-is: ask the server to convert it instead.
    if (!this.triedTranscode && this.method !== "Transcode") {
      this.triedTranscode = true;
      this.load(this.position(), { allowDirect: false });
    } else this.showError(`This video couldn't play. (${msg})`);
  }

  showError(msg) {
    this.spinner.hidden = true;
    this.error.hidden = false;
    this.error.replaceChildren(h("p", msg), h("div.row", button("Try again", null, () => this.load(this.position()), true), button("Back", null, () => this.close())));
  }

  // ---------- Controls ----------

  toggle() {
    const v = this.video;
    if (v.paused || v.ended) v.play().catch(() => {}); else v.pause();
    this.poke();
  }

  seekBy(ms) {
    if (this.live) return;
    const target = Math.max(0, Math.min(this.duration(), this.position() + ms));
    if (this.offsetMs && target < this.offsetMs) { this.load(target); return; }
    this.video.currentTime = (target - (this.offsetMs || 0)) / 1000;
    this.poke();
  }

  fullscreen() {
    if (document.fullscreenElement) document.exitFullscreen?.();
    else this.root.requestFullscreen?.().catch(() => {});
  }

  tick() {
    const pos = this.position(), dur = this.duration();
    if (!this.seeking) {
      this.time.textContent = fmtClock(pos);
      this.bar.value = dur ? Math.round((pos / dur) * 1000) : 0;
    }
    this.left.textContent = dur ? "-" + fmtClock(dur - pos) : "";
    // Intro / recap markers
    const t = pos * api.TICKS;
    const seg = this.segments.find((s) => SKIPPABLE.has(s.Type) && t >= s.StartTicks && t < s.EndTicks - 2e7);
    this.activeSeg = seg;
    const mode = getPref("skipIntro");
    if (seg && mode === "auto" && !this.skipped.has(seg.StartTicks)) this.skip();
    this.skipBtn.hidden = !seg || mode === "off";
    if (seg) this.skipBtn.querySelector("span:last-child").textContent = { Recap: "Skip recap", Preview: "Skip preview", Commercial: "Skip ad" }[seg.Type] || "Skip intro";
    // Credits: offer the next episode
    const outro = this.segments.find((s) => s.Type === "Outro");
    const inCredits = outro ? t >= outro.StartTicks : dur > 0 && dur - pos < 25000;
    if (inCredits && this.next && !this.upNext && !this.upNextDismissed && !this.video.paused) this.startUpNext();
  }

  skip() {
    const s = this.activeSeg;
    if (!s) return;
    this.skipped.add(s.StartTicks);
    this.video.currentTime = (s.EndTicks / api.TICKS - (this.offsetMs || 0)) / 1000;
  }

  startUpNext() {
    const n = this.next;
    const auto = getPref("autoPlayNext");
    const label = h("span.up-label", "Up next");
    this.upNextEl.replaceChildren(
      img(api.landscape(n, 400), "up-art"),
      h("div.up-text", label, h("strong", [episodeCode(n), n.Name].filter(Boolean).join(" · ")),
        h("div.row", button("Play now", "play", () => this.playNext(), true), button("Cancel", null, () => this.cancelUpNext()))));
    this.upNextEl.hidden = false;
    this.upNext = { left: 15 };
    if (auto) {
      const step = () => {
        if (!this.upNext) return;
        label.textContent = `Up next in ${this.upNext.left}`;
        if (this.upNext.left-- <= 0) return this.playNext();
        this.upNext.timer = setTimeout(step, 1000);
      };
      step();
    }
    this.upNextEl.querySelector("button")?.focus();
  }

  cancelUpNext() {
    clearTimeout(this.upNext?.timer);
    this.upNext = null;
    this.upNextDismissed = true;
    this.upNextEl.hidden = true;
  }

  async playNext() {
    const n = this.next;
    if (!n) return;
    clearTimeout(this.upNext?.timer);
    this.upNext = null;
    this.upNextEl.hidden = true;
    await this.report("Stopped");
    Object.assign(this, { started: false, upNextDismissed: false, source: null, subtitleIndex: null, audioIndex: null, next: null, segments: [], triedTranscode: false });
    this.skipped.clear();
    this.nextBtn.hidden = true;
    this.open(n.Id, 0);
  }

  ended() {
    if (this.next && getPref("autoPlayNext") && !this.upNextDismissed) this.playNext();
    else this.close();
  }

  // ---------- Menus: subtitles, audio, quality, speed ----------

  menu(kind) {
    const streams = this.source?.MediaStreams || [];
    let title, choices;
    if (kind === "subs") {
      title = "Subtitles";
      choices = [["Off", null, (this.subtitleIndex ?? -1) < 0, () => this.chooseSubtitle(-1)],
        ...streams.filter((s) => s.Type === "Subtitle").map((s) => [s.DisplayTitle || s.Language || `Track ${s.Index}`,
          [s.IsForced ? "Forced" : null, s.DeliveryMethod !== "External" ? "Burned in by the server" : null].filter(Boolean).join(" · "),
          this.subtitleIndex === s.Index, () => this.chooseSubtitle(s.Index)])];
    } else if (kind === "audio") {
      title = "Audio";
      choices = streams.filter((s) => s.Type === "Audio").map((s) => [s.DisplayTitle || s.Language || `Track ${s.Index}`, null, this.audioIndex === s.Index, () => this.chooseAudio(s.Index)]);
    } else if (kind === "quality") {
      title = "Quality";
      choices = QUALITIES.map(([v, l]) => [l, null, this.quality === v, () => { this.quality = v; this.load(this.position(), { burn: this.burnedIn ? this.subtitleIndex : null }); }]);
    } else {
      title = "Speed";
      choices = SPEEDS.map((s) => [s === 1 ? "Normal" : `${s}x`, null, (this.speed || 1) === s, () => { this.speed = s; this.video.playbackRate = s; }]);
    }
    const now = kind === "quality" ? h("p.hint", "Now: " + ({ Transcode: "converted by the server", DirectStream: "original video, repackaged" }[this.method] || "original file")) : null;
    this.panel.replaceChildren(h("h3", title), now, h("div.p-choices", choices.map(([label, detail, on, pick]) =>
      h(`button.p-choice${on ? ".on" : ""}`, { type: "button", onclick: () => { pick(); this.closePanel(); } },
        h("span", h("strong", label), detail ? h("small", detail) : null), on ? icon("check") : null))));
    this.panel.hidden = false;
    this.panel.querySelector("button")?.focus();
    this.poke();
  }

  closePanel() { this.panel.hidden = true; this.root.focus(); }

  chooseSubtitle(index) {
    const s = this.source?.MediaStreams.find((x) => x.Index === index && x.Type === "Subtitle");
    this.subtitleIndex = index;
    if (s && s.DeliveryMethod !== "External") this.load(this.position(), { burn: index });
    else if (this.burnedIn) this.load(this.position(), { burn: null });
    else this.applySubtitle();
  }

  chooseAudio(index) {
    if (index === this.audioIndex) return;
    this.audioIndex = index;
    // Browsers can't switch audio tracks inside a file, so the server repackages it with the one you picked.
    this.load(this.position(), { audio: index, burn: this.burnedIn ? this.subtitleIndex : null, allowDirect: index === this.source?.DefaultAudioStreamIndex });
  }

  // ---------- Keyboard (and TV-browser remotes) ----------

  key(e) {
    if (e.target.closest?.("input:not(.p-seek)")) return;
    const k = e.key;
    const handled = () => { e.preventDefault(); e.stopPropagation(); };
    if (k === "Escape" || k === "Backspace" || k === "GoBack" || k === "BrowserBack") {
      handled();
      if (!this.panel.hidden) this.closePanel();
      else if (this.upNext) this.cancelUpNext();
      else if (document.fullscreenElement) document.exitFullscreen();
      else this.close();
      return;
    }
    if (!this.panel.hidden) return; // arrows move through the menu
    this.poke();
    if (k === " " || k === "k" || k === "MediaPlayPause") { handled(); this.toggle(); }
    else if (k === "ArrowLeft" || k === "j" || k === "MediaRewind") { if (document.activeElement === this.bar) return; handled(); this.seekBy(-10000); }
    else if (k === "ArrowRight" || k === "l" || k === "MediaFastForward") { if (document.activeElement === this.bar) return; handled(); this.seekBy(30000); }
    else if (k === "f") { handled(); this.fullscreen(); }
    else if (k === "m") { handled(); this.video.muted = !this.video.muted; toast(this.video.muted ? "Muted" : "Sound on"); }
    else if (k === "c") { handled(); this.menu("subs"); }
    else if (k === "n" || k === "MediaTrackNext") { handled(); this.playNext(); }
    else if (k === "s" && this.activeSeg) { handled(); this.skip(); }
    else if (k === "Enter" && document.activeElement === this.root) { handled(); this.toggle(); }
  }

  // ---------- Telling Jellyfin where you are ----------

  report(kind) {
    const i = this.item;
    if (!i) return Promise.resolve();
    return api.report(kind, {
      ItemId: i.Id, MediaSourceId: this.source?.Id || i.Id, PlaySessionId: this.playSessionId, LiveStreamId: this.source?.LiveStreamId,
      PositionTicks: Math.round(this.position() * api.TICKS), IsPaused: this.video.paused, PlayMethod: this.method, CanSeek: !this.live,
      AudioStreamIndex: this.audioIndex ?? undefined, SubtitleStreamIndex: this.subtitleIndex ?? undefined,
    });
  }

  close(fromHistory = false) {
    if (current !== this) return;
    current = null;
    this.report("Stopped");
    if (this.source?.LiveStreamId) api.closeLiveStream(this.source.LiveStreamId);
    clearInterval(this.reporter);
    clearTimeout(this.hideTimer);
    clearTimeout(this.upNext?.timer);
    document.removeEventListener("keydown", this.onKey, true);
    document.removeEventListener("fullscreenchange", this.onFs);
    window.removeEventListener("popstate", this.onPop);
    if (document.fullscreenElement) document.exitFullscreen?.();
    this.hls?.destroy();
    this.video.pause();
    this.video.removeAttribute("src");
    this.video.load();
    this.root.remove();
    document.body.classList.remove("watching");
    document.title = "RelliReels";
    if (!fromHistory && history.state?.player) history.back();
    // Rows like Continue watching reload with the new spot.
    setTimeout(() => window.dispatchEvent(new Event("rellireels:refresh")), 600);
  }
}
