// Per-device settings, kept in this browser only.

const KEY = "relli.prefs";
const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

const defaults = {
  quality: 0,          // max bitrate in kbps; 0 = original file
  volume: 1,
  shuffle: false,
  repeat: "off",       // off | all | one
  speed: 1,            // playback speed
  pitch: 0,            // semitones
  crossfade: 0,        // seconds; 0 = gapless
  replayGain: "off",   // off | track | album | auto
  rgPreamp: 0,         // dB added on top of ReplayGain
  rgClip: true,        // lower the gain if it would clip
  // iPhones pause processed audio when the screen locks, so effects start off there.
  audioFx: !isIOS,
  eq: { enabled: false, preset: "Flat", bands: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0], preamp: 0 },
  eqCustom: {},
  fx: { crossfeed: 0, mono: false, boost: 0 },
  smartQueue: "off",   // off | random | genre | artist | similar
  smartFlow: "off",    // off | shuffle | double | artist | era
  sleepFade: true,
  waveform: true,      // download each song once to draw its real waveform
  visual: "cover",     // cover | lyrics | visualizer
  vizStyle: "bars",    // bars | wave | ring
  lyricsSize: "m",     // s | m | l
  lyricsAlign: "left", // left | center
  lyricsOffset: 0,     // ms; positive shows lines earlier
  albumLayout: "grid", // grid | list
  openPlayerOnSelect: true, // picking a song opens Now Playing
  homeSections: ["mixes", "favTracks", "newest", "rediscover", "recent", "randomArtists", "randomAlbums", "continue", "mostPlayed"],
};

let prefs = structuredClone(defaults);
try {
  const saved = JSON.parse(localStorage.getItem(KEY) || "{}");
  prefs = { ...prefs, ...saved };
  for (const k of ["eq", "fx"]) prefs[k] = { ...defaults[k], ...(saved[k] || {}) };
} catch {}

export function getPref(name) {
  return prefs[name];
}

export function setPref(name, value) {
  prefs[name] = value;
  try { localStorage.setItem(KEY, JSON.stringify(prefs)); } catch {}
  document.dispatchEvent(new CustomEvent("relli:pref", { detail: { name, value } }));
}

export function defaultPref(name) {
  return structuredClone(defaults[name]);
}
