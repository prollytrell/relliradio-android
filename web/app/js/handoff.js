// Moving playback between devices (phone, TV, desktop).
// Every device saves its queue to Navidrome while playing; Navidrome notes who saved it and when
// (in the server's clock, so device clocks never need to agree).
// - Another device saved after this one: offer "Play here".
// - This device is playing and another takes over: pause here, so the music moves rather than doubles.

import * as api from "./api.js";
import { player } from "./player.js";

let ownAt = 0;            // newest save by this device seen on the server (server time)
let dismissedBy = null;
let dismissedUntil = 0;
let activeSince = 0;
let lastCheck = 0;
export const handoff = new EventTarget();
export let offer = null;

function setOffer(q) {
  offer = q;
  handoff.dispatchEvent(new Event("change"));
}

export async function check() {
  const q = await api.getPlayQueue();
  if (!q || !q.songs.length) return false;
  if (!q.changedBy || q.changedBy === api.CLIENT_NAME) {
    ownAt = Math.max(ownAt, q.changedAt);
    if (offer) setOffer(null);
    return false;
  }
  if (q.changedAt <= ownAt) return false;
  if (q.changedBy === dismissedBy && Date.now() < dismissedUntil) return true;
  setOffer(q);
  return true;
}

export function dismiss() {
  if (!offer) return;
  dismissedBy = offer.changedBy;
  dismissedUntil = Date.now() + 20 * 60_000;
  setOffer(null);
}

export async function playHere() {
  const q = offer;
  if (q) ownAt = Math.max(ownAt, q.changedAt);
  player.passive = false;
  setOffer(null);
  await player.resumeFromServer(true);
}

// Playing here makes this the active device.
let wasPlaying = false;
player.addEventListener("state", async () => {
  const now = player.playing;
  if (now && !wasPlaying) {
    activeSince = Date.now();
    player.passive = false; // playing here: this device saves again
    if (offer) { ownAt = Math.max(ownAt, offer.changedAt); setOffer(null); }
    const q = await api.getPlayQueue();
    if (q) ownAt = Math.max(ownAt, q.changedAt);
  }
  wasPlaying = now;
});

// Every 3 seconds: idle → look for another device to pick up from;
// playing (after an 8-second grace period) → pause if another device took over.
setInterval(async () => {
  if (document.visibilityState !== "visible" && !player.playing) return;
  const t = Date.now();
  if (t - lastCheck < 2_800) return;
  lastCheck = t;
  if (!player.playing) { await check(); return; }
  if (t - activeSince < 8_000) return;
  if (await check() && player.playing) {
    player.passive = true; // stop saving, or the other device would pause in turn
    player.pause();
  }
}, 1_000);
