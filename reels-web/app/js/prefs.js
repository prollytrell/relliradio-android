// Settings kept in this browser.

const KEY = "rellireels.prefs";
const DEFAULTS = {
  quality: 0, // Mbps, 0 = original
  subtitles: "auto", // auto | on | off
  subtitleLang: "eng",
  subtitleSize: "m",
  autoPlayNext: true,
  skipIntro: "button", // button | auto | off
  volume: 1,
};

let prefs = { ...DEFAULTS };
try { prefs = { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) || "{}") }; } catch {}

export const getPref = (k) => prefs[k];
export function setPref(k, v) {
  prefs[k] = v;
  try { localStorage.setItem(KEY, JSON.stringify(prefs)); } catch {}
}
export const maxBitrate = () => (prefs.quality > 0 ? prefs.quality * 1e6 : 200e6);
