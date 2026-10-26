// RelliReels: the app shell. Sidebar (computer) or bottom tabs (phone), and a tiny hash router:
// #/home, #/browse/<library>, #/item/<id>, #/live, #/search, #/mystuff, #/settings

import * as api from "./api.js";
import * as player from "./player.js";
import { h, icon, go, toast, button, timeLeft, episodeCode } from "./ui.js";
import { homeView } from "./views/home.js";
import { browseView } from "./views/browse.js";
import { detailView } from "./views/detail.js";
import { liveView } from "./views/live.js";
import { searchView } from "./views/search.js";
import { myStuffView } from "./views/mystuff.js";
import { settingsView } from "./views/settings.js";
import { loginView } from "./views/login.js";
import { getPref } from "./prefs.js";

document.documentElement.dataset.subs = getPref("subtitleSize");

const TABS = [
  ["home", "home", "Home"],
  ["browse", "movie", "Browse"],
  ["live", "live", "Live TV"],
  ["search", "search", "Search"],
  ["mystuff", "bookmark", "My Stuff"],
];

const root = document.getElementById("app");
let main;
let renderToken = 0;

// Where tapping things goes.
go.open = (i) => {
  switch (i.Type) {
    case "Episode": case "Video": case "Recording": return go.play(i);
    case "TvChannel": return go.channel(i);
    case "Program": return i.ChannelId && player.play(i.ChannelId, { live: true });
    case "Season": return navigate(`#/item/${i.SeriesId || i.Id}?season=${i.Id}`);
    case "CollectionFolder": case "UserView": return navigate(`#/browse/${i.Id}`);
    default: return navigate(`#/item/${i.Id}`);
  }
};
go.play = (i, fromStart = false) => { dismissed.add(i.Id); player.play(i.Id, { startMs: fromStart ? 0 : -1 }); };
go.channel = (ch) => player.play(ch.Id, { live: true });

export function navigate(hash) {
  if (location.hash === hash) render(); else location.hash = hash;
}

function shell() {
  const nav = (cls) => h(`nav.${cls}`, { "aria-label": "Main" },
    cls === "side" ? h("a.brand", { href: "#/home" }, h("img", { src: "brand/logo.svg", alt: "" }), h("span", "RelliReels")) : null,
    TABS.map(([r, ic, label]) => h("a.tab", { href: `#/${r}`, "data-route": r }, icon(ic), h("span", label))),
    cls === "side" ? h("a.tab", { href: "#/settings", "data-route": "settings" }, icon("settings"), h("span", "Settings")) : null);
  main = h("main.main", { tabindex: "-1" });
  root.replaceChildren(nav("side"), h("div.content", main, banner), nav("bottom"));
}

function markTab(route) {
  root.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.route === route));
}

async function render() {
  if (!api.signedIn()) {
    root.replaceChildren(loginView(() => { shell(); render().then(checkBanner); }));
    return;
  }
  if (!main) shell();
  const [path, query = ""] = (location.hash.replace(/^#\/?/, "") || "home").split("?");
  const [route, id] = path.split("/");
  const params = new URLSearchParams(query);
  markTab(route === "item" ? "" : route);
  const token = ++renderToken;
  const keepScroll = render.sameRoute === location.hash;
  const scroll = main.scrollTop;
  let view;
  try {
    switch (route) {
      case "browse": view = browseView(id); break;
      case "item": view = detailView(id, params.get("season")); break;
      case "live": view = liveView(); break;
      case "search": view = searchView(); break;
      case "mystuff": view = myStuffView(); break;
      case "settings": view = settingsView(() => { api.clearSession(); main = null; render(); }); break;
      default: view = homeView();
    }
    const el = await view;
    if (token !== renderToken) return;
    main.replaceChildren(el);
    main.scrollTop = keepScroll ? scroll : 0;
    render.sameRoute = location.hash;
  } catch (e) {
    if (token !== renderToken) return;
    if (e.status === 401) { api.clearSession(); main = null; return render(); }
    main.replaceChildren(h("div.empty.pad", h("h2", "This didn't load"), h("p", e.message || String(e)), button("Try again", null, render, true)));
  }
}

window.addEventListener("hashchange", () => { render.sameRoute = null; render(); });
// After watching something, reload the screen (Continue watching, progress bars) without jumping.
window.addEventListener("rellireels:refresh", () => { if (!player.isOpen()) render(); checkBanner(); });

// ---------- "Pick up where you left off" ----------
// If you were watching on another device (TV, phone) in the last 30 minutes, offer to continue here.

const dismissed = new Set();
const banner = h("div.continue-banner", { hidden: true });

async function checkBanner() {
  if (!api.signedIn() || player.isOpen() || document.hidden) return;
  try {
    const list = await api.resume(3);
    const i = list.find((x) => x.UserData?.LastPlayedDate && Date.now() - Date.parse(x.UserData.LastPlayedDate) < 30 * 60e3 && !dismissed.has(x.Id));
    if (!i) { banner.hidden = true; return; }
    banner.replaceChildren(
      h("div.cb-text", h("span.cb-label", "Pick up where you left off"),
        h("strong", [i.SeriesName, episodeCode(i), i.SeriesName ? null : i.Name, timeLeft(i)].filter(Boolean).join(" · "))),
      button("Play", "play", () => { banner.hidden = true; go.play(i); }, true),
      h("button.icon-btn", { type: "button", "aria-label": "Not now", onclick: () => { dismissed.add(i.Id); banner.hidden = true; } }, icon("close")));
    banner.hidden = false;
  } catch {}
}
setInterval(checkBanner, 20000);
document.addEventListener("visibilitychange", checkBanner);

render().then(checkBanner);
window.toast = toast;
