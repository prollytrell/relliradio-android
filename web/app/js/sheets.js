// All the pop-up sheets: More actions, Equalizer/DSP, Speed and pitch, Sleep timer,
// Smart queue/flow, song Details, Lyrics settings, Rating, and Add to playlist.

import * as api from "./api.js";
import { player } from "./player.js";
import { engine, EQ_FREQS, EQ_PRESETS, customPresets } from "./audio.js";
import { getPref, setPref, defaultPref } from "./prefs.js";
import { h, icon, toast, openSheet, actionRow, chips, toggle, fmtTime, fmtLength, promptDialog, plural } from "./ui.js";
import { QUEUE_MODES, FLOW_MODES, setSmartQueue, setSmartFlow, instantMix, radioMix } from "./smart.js";
import { sleep } from "./sleep.js";
import { download, remove as removeDownload, isDownloaded, offlineUrl, fmtBytes } from "./offline.js";
import { toggleStar, go } from "./components.js";

// Now Playing registers here so sheets can switch its visual.
export const hooks = { showVisual: null };

const artistsOf = (song) => {
  const list = Array.isArray(song.artists) && song.artists.length ? song.artists : [{ id: song.artistId, name: song.artist }];
  return list.filter((a) => a.id && a.name);
};

const speedLabel = (v) => `${Number(v.toFixed(2))}x`;

// ---------- More actions ----------

export function moreActions(song, { nowPlaying = false } = {}) {
  if (!song) return;
  let sheet;
  const swap = (fn) => () => { sheet.close(true); fn(); };
  const leave = (fn) => () => { sheet.close(); fn(); };
  const eq = getPref("eq");
  const rows = [];

  if (nowPlaying) {
    rows.push(
      actionRow("eq", "Equalizer and DSP", swap(equalizerSheet), eq.enabled ? eq.preset : "Off"),
      actionRow("gauge", `Speed: ${speedLabel(getPref("speed"))}`, swap(speedSheet)),
      actionRow("moon", "Sleep timer", swap(sleepSheet), sleep.active ? sleep.label : null),
      actionRow("smart", "Smart flow and Smart queue", swap(smartSheet)),
    );
  }
  if (!song.radio) {
    rows.push(
      actionRow("mix", "Instant mix", leave(() => instantMix(song).catch((e) => toast(e.message)))),
      actionRow("radio", "Radio mix", leave(() => radioMix(song).catch((e) => toast(e.message)))),
    );
    if (!nowPlaying) {
      rows.push(
        actionRow("next", "Play next", leave(() => { player.playNext(song); toast("Plays next"); })),
        actionRow("queue", "Add to queue", leave(() => { player.addToQueue(song); toast("Added to queue"); })),
      );
    }
    rows.push(
      actionRow(song.starred ? "heartFill" : "heart", song.starred ? "Remove from favorites" : "Add to favorites", leave(() => toggleStar(song))),
      actionRow("star", "Rate", swap(() => ratingSheet(song)), song.userRating ? "★".repeat(song.userRating) : null),
      actionRow("playlist", "Add to playlist", swap(() => playlistPicker([song]))),
      song.albumId ? actionRow("disc", "Go to album", leave(() => { hooks.closeNowPlaying?.(); go(`#/album/${song.albumId}`); })) : null,
      ...artistsOf(song).map((a) => actionRow("person", `Go to ${a.name}`, leave(() => { hooks.closeNowPlaying?.(); go(`#/artist/${a.id}`); }))),
      song.genre ? actionRow("tag", `Go to ${song.genre}`, leave(() => { hooks.closeNowPlaying?.(); go(`#/genre/${encodeURIComponent(song.genre)}`); })) : null,
      actionRow("info", "Details", swap(() => detailsSheet(song))),
      actionRow("web", "Web search", leave(() => window.open(`https://www.google.com/search?q=${encodeURIComponent(`${song.artist} ${song.title}`)}`, "_blank", "noopener"))),
      actionRow("lyrics", "Genius search", leave(() => window.open(`https://genius.com/search?q=${encodeURIComponent(`${song.artist} ${song.title}`)}`, "_blank", "noopener"))),
    );
    if (nowPlaying) {
      rows.push(
        actionRow("lyrics", "Lyrics", leave(() => hooks.showVisual?.("lyrics"))),
        actionRow("settings", "Lyrics settings", swap(lyricsSettingsSheet)),
      );
    }
    rows.push(
      isDownloaded(song.id)
        ? actionRow("cloudDone", "Remove download", leave(() => removeDownload(song.id).then(() => toast("Removed from this device"))))
        : actionRow("cloud", "Download for offline", leave(() => download(song))),
      actionRow("share", "Share", leave(() => shareSong(song))),
      actionRow("upload", "Share file", leave(() => shareFile(song))),
    );
  }
  sheet = openSheet({ title: "More actions", ic: "note", body: h("div.action-list", rows), cls: "actions" });
}

async function shareSong(song) {
  const text = `${song.title} by ${song.artist}${song.album ? `, from ${song.album}` : ""}`;
  try {
    if (navigator.share) await navigator.share({ title: song.title, text });
    else { await navigator.clipboard.writeText(text); toast("Copied to clipboard"); }
  } catch {}
}

async function shareFile(song) {
  try {
    toast("Preparing the file…");
    let blob;
    const local = await offlineUrl(song.id);
    if (local) { blob = await (await fetch(local)).blob(); URL.revokeObjectURL(local); }
    else blob = await (await fetch(api.downloadUrl(song.id))).blob();
    const file = new File([blob], `${song.artist} - ${song.title}.${song.suffix || "mp3"}`, { type: blob.type || "audio/mpeg" });
    if (navigator.canShare?.({ files: [file] })) await navigator.share({ files: [file], title: song.title });
    else {
      const a = h("a", { href: URL.createObjectURL(file), download: file.name });
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    }
  } catch (e) {
    if (e?.name !== "AbortError") toast("Couldn't share the file.");
  }
}

// ---------- Equalizer and DSP ----------

function slider({ min, max, step, value, label, format, vertical, oninput }) {
  const out = h("output.slider-value", format(value));
  const input = h("input.range", { type: "range", min, max, step, value, "aria-label": label });
  const paint = () => input.style.setProperty("--pct", `${((input.value - min) / (max - min)) * 100}%`);
  input.addEventListener("input", () => { out.textContent = format(+input.value); paint(); oninput(+input.value); });
  paint();
  return h(`div.slider${vertical ? ".vertical" : ""}`, vertical ? [out, h("div.vslot", input), h("span.slider-label", label)] : [h("div.slider-top", h("span.slider-label", label), out), input]);
}

export function equalizerSheet() {
  const eq = structuredClone(getPref("eq"));
  const save = () => { setPref("eq", eq); engine.applyEq(); };
  const dbfmt = (v) => `${v > 0 ? "+" : ""}${v} dB`;
  const presets = () => ({ ...EQ_PRESETS, ...customPresets() });

  const status = !getPref("audioFx")
    ? h("div.notice", h("p", "Audio effects are off on this device, so the equalizer, ReplayGain, pitch and visualizer won't apply."),
        h("button.btn.small", { type: "button", onclick: () => { setPref("audioFx", true); engine.start(); sheet.close(); toast("Audio effects on. They start with the next play."); } }, "Turn on"))
    : !engine.possible
      ? h("div.notice", h("p", "Effects need RelliRadio and Navidrome at the same address (the HTTPS setup in the README)."))
      : null;

  const bandWrap = h("div.eq-bands");
  const presetChips = h("div.eq-presets");
  const renderBands = () => bandWrap.replaceChildren(...EQ_FREQS.map((f, i) => slider({
    min: -12, max: 12, step: 1, value: eq.bands[i], vertical: true,
    label: f >= 1000 ? `${f / 1000}k` : String(f), format: (v) => `${v > 0 ? "+" : ""}${v}`,
    oninput: (v) => { eq.bands[i] = v; eq.preset = "Custom"; eq.enabled = true; enabledToggle.querySelector("input").checked = true; renderPresets(); save(); },
  })));
  const renderPresets = () => presetChips.replaceChildren(chips(
    [...Object.keys(presets()).map((p) => [p, p]), ...(eq.preset === "Custom" ? [["Custom", "Custom"]] : [])],
    eq.preset,
    (p) => { if (p === "Custom") return; eq.preset = p; eq.bands = presets()[p].slice(); eq.enabled = true; enabledToggle.querySelector("input").checked = true; renderBands(); save(); }
  ));

  const enabledToggle = toggle("Equalizer", eq.enabled, (on) => { eq.enabled = on; save(); });
  const fx = structuredClone(getPref("fx"));
  const saveFx = () => { setPref("fx", fx); engine.applyEffects(); };

  renderBands();
  renderPresets();

  const body = h("div.stack-form",
    status,
    enabledToggle,
    presetChips,
    bandWrap,
    slider({ min: -12, max: 6, step: 1, value: eq.preamp, label: "Preamp", format: dbfmt, oninput: (v) => { eq.preamp = v; save(); } }),
    h("div.row-actions",
      h("button.btn.small", { type: "button", onclick: async () => {
        const name = await promptDialog({ title: "Save preset", label: "Preset name", placeholder: "My headphones", confirm: "Save preset" });
        if (!name) return;
        setPref("eqCustom", { ...customPresets(), [name]: eq.bands.slice() });
        eq.preset = name; save(); renderPresets(); toast(`Saved “${name}”`);
      } }, icon("plus"), "Save as preset"),
      h("button.btn.small.ghost", { type: "button", onclick: () => { Object.assign(eq, defaultPref("eq"), { enabled: eq.enabled }); renderBands(); renderPresets(); save(); } }, "Reset")
    ),
    h("h3.form-heading", "ReplayGain"),
    h("p.hint", "Evens out volume between songs using the loudness tags in your files."),
    chips([["off", "Off"], ["track", "Track"], ["album", "Album"], ["auto", "Auto"]], getPref("replayGain"), (v) => { setPref("replayGain", v); player.applyLevel(); }),
    slider({ min: -6, max: 12, step: 1, value: getPref("rgPreamp"), label: "ReplayGain preamp", format: dbfmt, oninput: (v) => { setPref("rgPreamp", v); player.applyLevel(); } }),
    toggle("Prevent clipping", getPref("rgClip"), (on) => { setPref("rgClip", on); player.applyLevel(); }, "Lowers the boost when a song would distort."),
    h("h3.form-heading", "DSP"),
    h("span.field-caption", "Volume boost"),
    chips([[0, "Off"], [3, "+3 dB"], [6, "+6 dB"], [9, "+9 dB"], [12, "+12 dB"]], fx.boost, (v) => { fx.boost = v; saveFx(); }),
    h("span.field-caption", "Crossfeed (headphones)"),
    chips([[0, "Off"], [1, "Low"], [2, "Medium"], [3, "High"]], fx.crossfeed, (v) => { fx.crossfeed = v; saveFx(); }),
    toggle("Mono", fx.mono, (on) => { fx.mono = on; saveFx(); }, "Plays both channels in both ears."),
    h("h3.form-heading", "Transitions"),
    slider({ min: 0, max: 12, step: 1, value: getPref("crossfade"), label: "Crossfade", format: (v) => (v ? `${v} s` : "Off (gapless)"), oninput: (v) => setPref("crossfade", v) })
  );
  const sheet = openSheet({ title: "Equalizer and DSP", ic: "eq", body, cls: "wide" });
}

// ---------- Speed and pitch ----------

export function speedSheet() {
  const pitchNote = engine.canPitch ? null : h("p.hint", "Pitch needs audio effects on and RelliRadio and Navidrome at the same address.");
  let speedSlider, pitchSlider;
  const build = () => {
    speedSlider = slider({ min: 0.5, max: 2, step: 0.05, value: getPref("speed"), label: "Speed", format: speedLabel, oninput: (v) => player.setSpeed(v) });
    pitchSlider = slider({ min: -12, max: 12, step: 0.5, value: getPref("pitch"), label: "Pitch", format: (v) => (v ? `${v > 0 ? "+" : ""}${v} semitones` : "Normal"), oninput: (v) => player.setPitch(v) });
  };
  build();
  const body = h("div.stack-form",
    speedSlider,
    chips([[0.75, "0.75x"], [1, "1x"], [1.25, "1.25x"], [1.5, "1.5x"], [2, "2x"]], getPref("speed"), (v) => { player.setSpeed(v); refresh(); }),
    pitchSlider,
    pitchNote,
    h("button.btn.small", { type: "button", onclick: () => { player.setSpeed(1); player.setPitch(0); refresh(); } }, "Reset to normal")
  );
  const refresh = () => {
    const old = [speedSlider, pitchSlider];
    build();
    old[0].replaceWith(speedSlider);
    old[1].replaceWith(pitchSlider);
  };
  openSheet({ title: "Speed and pitch", ic: "gauge", body });
}

// ---------- Sleep timer ----------

export function sleepSheet() {
  const status = h("div.sleep-status");
  const renderStatus = () => {
    if (!sleep.active) return status.replaceChildren(h("p.hint", "Pick when playback should stop."));
    status.replaceChildren(
      h("p.sleep-left", sleep.label),
      h("div.row-actions",
        sleep.mode === "time" ? h("button.btn.small", { type: "button", onclick: () => sleep.extend(5) }, "+5 min") : null,
        h("button.btn.small.ghost", { type: "button", onclick: () => sleep.cancel() }, "Turn off"))
    );
  };
  const custom = h("input.field.small-field", { type: "number", min: "1", max: "600", placeholder: "Minutes", "aria-label": "Custom minutes" });
  const body = h("div.stack-form",
    status,
    h("div.chips", [5, 10, 15, 30, 45, 60, 90].map((m) => h("button.chip", { type: "button", onclick: () => sleep.start(m) }, `${m} min`))),
    h("div.row-actions",
      h("button.btn.small", { type: "button", onclick: () => sleep.endOfTrack() }, "End of this song"),
      h("button.btn.small", { type: "button", onclick: () => sleep.endOfQueue() }, "End of queue")),
    h("form.custom-row", { onsubmit: (e) => { e.preventDefault(); const m = +custom.value; if (m > 0) sleep.start(m); } }, custom, h("button.btn.small", { type: "submit" }, "Set")),
    toggle("Fade out", getPref("sleepFade"), (on) => setPref("sleepFade", on), "Eases the volume down over the last 30 seconds.")
  );
  renderStatus();
  const onChange = () => renderStatus();
  sleep.addEventListener("change", onChange);
  sleep.addEventListener("tick", () => { const p = status.querySelector(".sleep-left"); if (p) p.textContent = sleep.label; });
  openSheet({ title: "Sleep timer", ic: "moon", body });
}

// ---------- Smart queue and Smart flow ----------

function modeList(modes, value, onpick) {
  const list = h("div.mode-list", { role: "radiogroup" });
  const render = (v) => list.replaceChildren(...modes.map(([id, label, hint]) =>
    h("button.mode-row", { type: "button", role: "radio", "aria-checked": String(id === v), onclick: () => { render(id); onpick(id); } },
      h("span.mode-dot"), h("span.mode-text", h("span", label), h("span.hint", hint)))));
  render(value);
  return list;
}

export function smartSheet() {
  const body = h("div.stack-form",
    h("h3.form-heading", "Smart queue"),
    h("p.hint", "Keeps the music going by adding songs when the queue is about to end."),
    modeList(QUEUE_MODES, getPref("smartQueue"), setSmartQueue),
    h("h3.form-heading", "Smart flow"),
    h("p.hint", "Reshapes the queue as it plays."),
    modeList(FLOW_MODES, getPref("smartFlow"), setSmartFlow)
  );
  openSheet({ title: "Smart flow and Smart queue", ic: "smart", body });
}

// ---------- Details ----------

export async function detailsSheet(song) {
  let full = song;
  try { if (!song.radio) full = { ...song, ...(await api.getSong(song.id)) }; } catch {}
  const rg = full.replayGain || {};
  const rows = [
    ["Title", full.title], ["Artist", full.displayArtist || full.artist], ["Album", full.album], ["Album artist", full.displayAlbumArtist],
    ["Track", full.track ? `${full.track}${full.discNumber ? `, disc ${full.discNumber}` : ""}` : null],
    ["Year", full.year], ["Genre", full.genre], ["Length", full.duration ? fmtTime(full.duration) : null],
    ["Format", full.suffix ? full.suffix.toUpperCase() : null],
    ["Bitrate", full.bitRate ? `${full.bitRate} kbps` : null],
    ["Sample rate", full.samplingRate ? `${(full.samplingRate / 1000).toFixed(1)} kHz` : null],
    ["Bit depth", full.bitDepth ? `${full.bitDepth}-bit` : null],
    ["Channels", full.channelCount],
    ["File size", full.size ? fmtBytes(full.size) : null],
    ["Plays", full.playCount],
    ["Rating", full.userRating ? "★".repeat(full.userRating) : null],
    ["ReplayGain", rg.trackGain != null ? `Track ${rg.trackGain} dB${rg.albumGain != null ? `, album ${rg.albumGain} dB` : ""}` : null],
    ["Downloaded", isDownloaded(full.id) ? "Yes, on this device" : null],
    ["Path", full.path],
  ].filter(([, v]) => v != null && v !== "");
  openSheet({ title: "Details", ic: "info", body: h("dl.details", rows.map(([k, v]) => [h("dt", k), h("dd", String(v))])) });
}

// ---------- Lyrics settings ----------

export function lyricsSettingsSheet() {
  const offset = h("output.offset-value");
  const showOffset = () => { const v = getPref("lyricsOffset") / 1000; offset.textContent = v ? `${v > 0 ? "+" : ""}${v.toFixed(2)} s` : "In sync"; };
  const nudge = (ms) => { setPref("lyricsOffset", getPref("lyricsOffset") + ms); showOffset(); };
  showOffset();
  openSheet({ title: "Lyrics settings", ic: "settings", body: h("div.stack-form",
    h("span.field-caption", "Text size"),
    chips([["s", "Small"], ["m", "Medium"], ["l", "Large"]], getPref("lyricsSize"), (v) => setPref("lyricsSize", v)),
    h("span.field-caption", "Alignment"),
    chips([["left", "Left"], ["center", "Centered"]], getPref("lyricsAlign"), (v) => setPref("lyricsAlign", v)),
    h("span.field-caption", "Timing"),
    h("div.row-actions",
      h("button.btn.small", { type: "button", onclick: () => nudge(-250) }, "Later"),
      offset,
      h("button.btn.small", { type: "button", onclick: () => nudge(250) }, "Earlier"),
      h("button.btn.small.ghost", { type: "button", onclick: () => { setPref("lyricsOffset", 0); showOffset(); } }, "Reset")),
    h("p.hint", "Nudge the lines if they show up ahead of or behind the singer.")
  ) });
}

// ---------- Rating ----------

export function ratingSheet(song) {
  let sheet;
  const set = async (n) => {
    try {
      await api.setRating(song.id, n);
      song.userRating = n || undefined;
      toast(n ? `Rated ${n} star${n > 1 ? "s" : ""}` : "Rating cleared");
      sheet.close();
    } catch (e) { toast(e.message); }
  };
  const stars = h("div.stars", [1, 2, 3, 4, 5].map((n) =>
    h("button.star-btn", { type: "button", "aria-label": `${n} star${n > 1 ? "s" : ""}`, onclick: () => set(n) }, icon(n <= (song.userRating || 0) ? "starFill" : "star"))));
  sheet = openSheet({ title: `Rate “${song.title}”`, ic: "star", body: h("div.stack-form", stars, h("button.btn.small.ghost", { type: "button", onclick: () => set(0) }, "Clear rating")) });
}

// ---------- Add to playlist ----------

export async function playlistPicker(songs) {
  let lists = [];
  try { lists = (await api.getPlaylists()).filter((p) => !p.readonly); } catch (e) { return toast(e.message); }
  const ids = songs.map((s) => s.id);
  let sheet;
  const rows = [
    actionRow("plus", "New playlist", async () => {
      sheet.close();
      const name = await promptDialog({ title: "New playlist", label: "Playlist name", placeholder: "Late night drive", confirm: "Create playlist" });
      if (!name) return;
      try { await api.createPlaylist(name, ids); document.dispatchEvent(new Event("relli:playlists")); toast(`Created “${name}”`); }
      catch (e) { toast(e.message); }
    }),
    ...lists.map((p) => actionRow("playlist", p.name, async () => {
      sheet.close();
      try { await api.addToPlaylist(p.id, ids); toast(`Added ${plural(ids.length, "song")} to “${p.name}”`); }
      catch (e) { toast(e.message); }
    }, plural(p.songCount || 0, "song"))),
  ];
  sheet = openSheet({ title: "Add to playlist", ic: "playlist", body: h("div.action-list", rows), cls: "actions" });
}

// Save everything in the queue as a new playlist.
export async function saveQueueAsPlaylist() {
  const songs = player.queue.filter((s) => !s.radio);
  if (!songs.length) return toast("The queue is empty.");
  const name = await promptDialog({ title: "Save queue as playlist", label: "Playlist name", placeholder: "Tonight's queue", confirm: "Save playlist" });
  if (!name) return;
  try { await api.createPlaylist(name, songs.map((s) => s.id)); document.dispatchEvent(new Event("relli:playlists")); toast(`Saved ${plural(songs.length, "song")} to “${name}”`); }
  catch (e) { toast(e.message); }
}

