// Sleep timer: stop after a set time, at the end of this song, or at the end of the queue.
// With "fade out" on, the volume eases down over the last 30 seconds.

import { player } from "./player.js";
import { getPref } from "./prefs.js";
import { toast, fmtTime } from "./ui.js";

const FADE = 30;

class SleepTimer extends EventTarget {
  constructor() {
    super();
    this.mode = "off"; // off | time | track | queue
    this.endsAt = 0;
    this.timer = null;
    player.addEventListener("sleepdone", () => this.finish(false));
  }

  get active() { return this.mode !== "off"; }

  get remaining() {
    if (this.mode === "time") return Math.max(0, (this.endsAt - Date.now()) / 1000);
    if (this.mode === "track") return Math.max(0, player.duration - player.time);
    return null;
  }

  get label() {
    if (this.mode === "time") return fmtTime(this.remaining);
    if (this.mode === "track") return "End of song";
    if (this.mode === "queue") return "End of queue";
    return "";
  }

  start(minutes) {
    this.clear();
    this.mode = "time";
    this.endsAt = Date.now() + minutes * 60000;
    this.loop();
    toast(`Sleep timer set for ${minutes} min`);
    this.changed();
  }

  extend(minutes) {
    if (this.mode !== "time") return this.start(minutes);
    this.endsAt += minutes * 60000;
    toast(`Added ${minutes} min`);
    this.changed();
  }

  endOfTrack() {
    this.clear();
    this.mode = "track";
    player.stopAfter = "track";
    this.loop();
    toast("Stops after this song");
    this.changed();
  }

  endOfQueue() {
    this.clear();
    this.mode = "queue";
    player.stopAfter = "queue";
    this.loop();
    toast("Stops after the last song in the queue");
    this.changed();
  }

  cancel() {
    this.clear();
    toast("Sleep timer off");
    this.changed();
  }

  clear() {
    clearInterval(this.timer);
    this.timer = null;
    this.mode = "off";
    player.stopAfter = null;
    player.setSleepFactor(1);
  }

  loop() {
    this.timer = setInterval(() => {
      const left = this.remaining;
      const lastSong = this.mode === "track" || (this.mode === "queue" && player.index === player.queue.length - 1);
      const fadeLeft = this.mode === "time" ? left : lastSong ? player.duration - player.time : null;
      if (getPref("sleepFade") && fadeLeft != null && fadeLeft < FADE && player.playing) {
        player.setSleepFactor(Math.max(0, fadeLeft / FADE));
      }
      if (this.mode === "time" && left <= 0) this.finish(true);
      this.dispatchEvent(new Event("tick"));
    }, 500);
  }

  finish(pause) {
    if (pause) player.pause();
    this.clear();
    toast("Sleep timer ended. Goodnight.");
    this.changed();
  }

  changed() { this.dispatchEvent(new Event("change")); }
}

export const sleep = new SleepTimer();
