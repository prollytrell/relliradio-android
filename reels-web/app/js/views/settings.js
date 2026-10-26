import * as api from "../api.js";
import { getPref, setPref } from "../prefs.js";
import { h, chip, button } from "../ui.js";

const group = (title, ...rows) => h("section.set-group", h("h2.set-title", title), h("div.set-card", rows));
const label = (text, hint) => h("div.set-text", h("strong", text), hint ? h("span.hint", hint) : null);

function choices(key, options, after) {
  const wrap = h("div.chips.wrap");
  const draw = () => wrap.replaceChildren(...options.map(([v, l]) => chip(l, getPref(key) === v, () => { setPref(key, v); draw(); after?.(); })));
  draw();
  return wrap;
}

function toggle(key, text, hint) {
  const input = h("input", { type: "checkbox", checked: getPref(key), onchange: () => setPref(key, input.checked) });
  return h("label.set-toggle", label(text, hint), h("span.switch", input, h("span.knob")));
}

export function settingsView(onSignOut) {
  const s = api.getSession();
  return h("div.settings.pad",
    h("h1.page-title", "Settings"),
    group("Streaming quality",
      label("Picture quality", "Original plays the file as-is, the best picture. Lower settings make the server shrink the video, which helps away from home."),
      choices("quality", [[0, "Original"], [20, "20 Mbps"], [10, "10 Mbps"], [6, "6 Mbps"], [3, "3 Mbps"], [1, "1 Mbps"]])),
    group("Watching",
      toggle("autoPlayNext", "Play the next episode automatically", "Starts the next one after a 15-second countdown when the credits roll."),
      label("Skip intro", "Needs intro markers on your server (the Intro Skipper plugin for Jellyfin)."),
      choices("skipIntro", [["button", "Show a button"], ["auto", "Skip automatically"], ["off", "Off"]])),
    group("Subtitles",
      choices("subtitles", [["auto", "Only when needed"], ["on", "Always on"], ["off", "Off"]]),
      label('"Only when needed" shows forced subtitles (for foreign-language scenes) and follows your Jellyfin subtitle settings.'),
      label("Size"),
      choices("subtitleSize", [["s", "Small"], ["m", "Medium"], ["l", "Large"]], () => document.documentElement.dataset.subs = getPref("subtitleSize"))),
    group("Keyboard",
      h("dl.keys",
        h("dt", h("kbd", "Space")), h("dd", "Play or pause"),
        h("dt", h("kbd", "←"), " ", h("kbd", "→")), h("dd", "Back 10 seconds / forward 30 seconds"),
        h("dt", h("kbd", "F")), h("dd", "Full screen"),
        h("dt", h("kbd", "C")), h("dd", "Subtitles"),
        h("dt", h("kbd", "S")), h("dd", "Skip intro"),
        h("dt", h("kbd", "N")), h("dd", "Next episode"),
        h("dt", h("kbd", "M")), h("dd", "Mute"),
        h("dt", h("kbd", "Esc")), h("dd", "Close the player"))),
    group("Account",
      label(`Signed in as ${s?.userName}`, `${s?.serverName || "Jellyfin"} at ${s?.server}`),
      h("div.row", button("Sign out", "logout", onSignOut))),
    h("p.hint.about", "RelliReels · talks only to your own Jellyfin server."));
}
