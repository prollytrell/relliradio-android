import * as api from "../api.js";
import { player } from "../player.js";
import { engine } from "../audio.js";
import { getPref, setPref } from "../prefs.js";
import { h, icon, toast, chips, toggle, actionRow } from "../ui.js";
import { canInstall, promptInstall } from "../install.js";
import { equalizerSheet, smartSheet, sleepSheet, speedSheet, lyricsSettingsSheet } from "../sheets.js";
import { editHomeSheet } from "./home.js";
import { removeAll, storageUsed, fmtBytes, offlineSupported } from "../offline.js";

const QUALITIES = [
  [0, "Original files", "Best sound. Uses the most data."],
  [320, "High (320 kbps MP3)", "Near-original sound with smaller files."],
  [192, "Standard (192 kbps MP3)", "Good for cellular data."],
  [128, "Data saver (128 kbps MP3)", "Smallest. Best on weak signal."],
];

const group = (title, ...rows) => h("section.set-group", h("h2.set-title", title), h("div.set-card", rows));
const row = (label, hint, control) => h("div.set-row", h("div.set-text", h("span", label), hint ? h("span.hint", hint) : null), control);

// "Sync library": ask Navidrome to scan, wait for it to finish, then reload what's on screen.
// (Navidrome also scans new files on its own a few seconds after they're added.)
function syncRow() {
  const detail = h("span.action-detail", "");
  const label = h("span.action-label", "Sync library");
  let busy = false;
  const row = h("button.action-row", { type: "button", onclick: async () => {
    if (busy) return;
    busy = true;
    try {
      detail.textContent = "Scanning…";
      let s = await api.startScan();
      const started = Date.now();
      while (s.scanning && Date.now() - started < 180000) {
        detail.textContent = s.count ? `Scanning… ${s.count} songs` : "Scanning…";
        await new Promise((r) => setTimeout(r, 1000));
        s = await api.getScanStatus();
      }
      detail.textContent = s.count ? `${s.count} songs` : "Up to date";
      toast("Library synced");
    } catch (e) {
      detail.textContent = "";
      toast(e.code === 50 ? "Only an admin account can start a scan. Navidrome scans new files on its own." : e.message);
    } finally {
      busy = false;
    }
  } }, icon("sync"), label, detail);
  return row;
}

export async function settingsView() {
  const s = api.getSession();
  const current = getPref("quality");

  const quality = h("fieldset.choices",
    h("legend.sr-only", "Streaming quality"),
    QUALITIES.map(([value, label, hint]) =>
      h("label.choice",
        h("input", { type: "radio", name: "quality", value, checked: value === current, onchange: () => {
          setPref("quality", value);
          toast(`Streaming quality set to ${label.split(" (")[0].toLowerCase()}. It applies from the next song.`);
        } }),
        h("span.choice-text", h("span", label), h("span.hint", hint))
      )
    )
  );

  const used = h("span.hint", "");
  storageUsed().then((u) => { used.textContent = `${u.count} songs, ${fmtBytes(u.total)}${u.free != null ? ` (${fmtBytes(u.free)} free)` : ""}`; });

  const installRow = canInstall()
    ? group("App", row("Install RelliRadio", "Adds RelliRadio to your home screen or app menu, with its own window and lock-screen controls.",
        h("button.btn.primary", { type: "button", onclick: promptInstall }, icon("install"), "Install")))
    : null;

  const fxHint = !engine.possible
    ? "Not available: the music server is on a different address than the app. Use the Caddy setup so both share one address."
    : "Needed for the equalizer, pitch, and visualizer. On iPhone it can stop music in the background, so it starts off there.";

  return {
    title: "Settings",
    el: h("div.settings",
      h("h1.page-title", "Settings"),
      installRow,
      group("Streaming", quality),
      group("Playback",
        toggle("Audio effects", getPref("audioFx"), (v) => { setPref("audioFx", v); if (v) engine.start(); toast(v ? "Audio effects on." : "Audio effects off. Reload to fully release them."); }, fxHint),
        actionRow("eq", "Equalizer, ReplayGain, crossfade", () => equalizerSheet()),
        actionRow("gauge", "Speed and pitch", () => speedSheet()),
        actionRow("moon", "Sleep timer", () => sleepSheet()),
        actionRow("smart", "Smart queue and Smart flow", () => smartSheet()),
        toggle("Fade out when the sleep timer ends", getPref("sleepFade"), (v) => setPref("sleepFade", v)),
        toggle("Real waveforms on the seek bar", getPref("waveform"), (v) => setPref("waveform", v), "Reads each song once to draw its shape. Turn off to save data on cellular."),
        row("Resume from another device", "Pick up the queue and position saved by RelliRadio or another Subsonic app.",
          h("button.btn.small", { type: "button", onclick: async () => {
            try { (await player.resumeFromServer()) ? toast("Picked up where you left off") : toast("No saved queue on the server yet."); }
            catch (e) { toast(e.message); }
          } }, icon("cloud"), "Resume"))
      ),
      group("Interface",
        actionRow("home", "Edit home widgets", () => editHomeSheet()),
        syncRow(),
        toggle("Open Now Playing when you pick a song", getPref("openPlayerOnSelect"), (v) => setPref("openPlayerOnSelect", v), "Clicking a song in a list jumps straight to the player."),
        actionRow("lyrics", "Lyrics size, alignment, and timing", () => lyricsSettingsSheet()),
        row("Now Playing shows", null, chips([["cover", "Cover"], ["lyrics", "Lyrics"], ["visualizer", "Visualizer"]], getPref("visual"), (v) => setPref("visual", v))),
        row("Visualizer style", null, chips([["bars", "Bars"], ["wave", "Wave"], ["ring", "Ring"]], getPref("vizStyle"), (v) => setPref("vizStyle", v))),
        row("Albums in Library", null, chips([["grid", "Grid"], ["list", "List"]], getPref("albumLayout"), (v) => setPref("albumLayout", v)))
      ),
      offlineSupported ? group("Downloads",
        row("Stored on this device", null, used),
        row("Remove all downloads", "Frees the space. Songs stay on your server.",
          h("button.btn.small", { type: "button", onclick: async () => { await removeAll(); used.textContent = "0 songs, 0 B"; toast("Removed all downloads"); } }, icon("trash"), "Remove"))
      ) : null,
      group("Account",
        row(`Signed in as ${s.user}`, `${s.serverType === "navidrome" ? "Navidrome" : "Subsonic server"} ${s.serverVersion || ""} at ${s.server || location.origin}`,
          h("button.btn", { type: "button", onclick: () => {
            player.forget();
            api.clearSession();
            location.hash = "#/";
            location.reload();
          } }, icon("logout"), "Sign out"))
      ),
      group("Keyboard shortcuts",
        h("dl.keys",
          h("dt", h("kbd", "Space")), h("dd", "Play or pause"),
          h("dt", h("kbd", "Shift"), " ", h("kbd", "→")), h("dd", "Next song"),
          h("dt", h("kbd", "Shift"), " ", h("kbd", "←")), h("dd", "Previous song"),
          h("dt", h("kbd", "/")), h("dd", "Search"),
          h("dt", h("kbd", "L")), h("dd", "Lyrics"),
          h("dt", h("kbd", "Q")), h("dd", "Queue")
        )
      ),
      h("p.hint.about", "RelliRadio 2.0. Self-hosted, talks only to your own server.")
    ),
  };
}
