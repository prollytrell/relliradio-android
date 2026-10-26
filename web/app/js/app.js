// App start-up: sign-in check, the page shell, and the hash router (#/album/123 and so on).

import * as api from "./api.js";
import { player } from "./player.js";
import { h, icon, clear, closeMenu, logo, fmtTime } from "./ui.js";
import { mountPlayer } from "./nowplaying.js";
import { errorState } from "./components.js";
import { loginView } from "./views/login.js";
import { homeView } from "./views/home.js";
import { libraryView } from "./views/library.js";
import { albumView, artistView, playlistView, genreView } from "./views/detail.js";
import { searchView } from "./views/search.js";
import { settingsView } from "./views/settings.js";
import "./install.js";
import { handoff, check as checkHandoff, playHere, dismiss as dismissHandoff } from "./handoff.js";
import * as handoffState from "./handoff.js";
const handoffOffer = () => handoffState.offer;

const ROUTES = [
  [/^\/?$/, homeView, "home"],
  [/^\/library(?:\/([a-z]+))?$/, libraryView, "library"],
  [/^\/album\/([^/]+)$/, albumView, "library"],
  [/^\/artist\/([^/]+)$/, artistView, "library"],
  [/^\/playlist\/([^/]+)$/, playlistView, "library"],
  [/^\/genre\/([^/]+)$/, genreView, "library"],
  [/^\/search$/, searchView, "search"],
  [/^\/settings$/, settingsView, "settings"],
];

const NAV = [
  ["home", "#/", "Home", "home"],
  ["library", "#/library", "Library", "library"],
  ["search", "#/search", "Search", "search"],
  ["settings", "#/settings", "Settings", "settings"],
];

const root = document.getElementById("app");
let main, view, backBtn, sidebarPlaylists;
const scrollMemory = new Map();
let renderToken = 0;
let lastScroll = 0;

function navLinks(cls) {
  return NAV.map(([id, href, label, ic]) => h(`a.${cls}`, { href, dataset: { nav: id } }, icon(ic), h("span", label)));
}

async function refreshSidebarPlaylists() {
  if (!sidebarPlaylists) return;
  try {
    const lists = await api.getPlaylists();
    sidebarPlaylists.replaceChildren(...lists.map((p) => h("a.side-playlist", { href: `#/playlist/${p.id}` }, p.name)));
  } catch {}
}

function buildShell() {
  clear(root);
  backBtn = h("button.icon-btn.back-btn", { type: "button", "aria-label": "Back", onclick: () => history.back() }, icon("back"));
  view = h("div.view", { id: "view" });
  main = h("main.main", { id: "main", tabindex: "-1" }, h("div.topbar", backBtn), view);
  sidebarPlaylists = h("div.side-playlists");

  const sidebar = h("aside.sidebar", { "aria-label": "Main" },
    h("a.side-brand", { href: "#/", "aria-label": "RelliRadio home" }, logo("side-logo"), h("span.wordmark", "RelliRadio")),
    h("nav.side-nav", navLinks("side-link")),
    h("div.side-section", h("h2.side-heading", "Playlists"), sidebarPlaylists)
  );
  const tabbar = h("nav.tabbar", { "aria-label": "Main" }, navLinks("tab-link"));

  root.append(h("a.skip", { href: "#main", onclick: (e) => { e.preventDefault(); main.focus(); } }, "Skip to content"), sidebar, main, tabbar);
  mountPlayer(root);
  refreshSidebarPlaylists();
  document.addEventListener("relli:playlists", refreshSidebarPlaylists);
}

async function route() {
  closeMenu();
  const raw = location.hash.replace(/^#/, "") || "/";
  const [path, qs = ""] = raw.split("?");
  const query = new URLSearchParams(qs);
  const match = ROUTES.map(([re, fn, nav]) => [path.match(re), fn, nav]).find(([m]) => m);

  if (!match) { location.hash = "#/"; return; }
  const [m, fn, nav] = match;

  document.querySelectorAll("[data-nav]").forEach((a) => a.setAttribute("aria-current", a.dataset.nav === nav ? "page" : "false"));
  backBtn.hidden = ["/", "/library", "/search", "/settings"].includes(path) || /^\/library\/[a-z]+$/.test(path);

  const key = location.hash;
  const mine = ++renderToken;
  view.setAttribute("aria-busy", "true");
  const loader = setTimeout(() => { if (mine === renderToken) view.replaceChildren(h("div.loading", { role: "status" }, h("span.loading-needle"), h("span.sr-only", "Loading"))); }, 150);

  try {
    const result = await fn(m.slice(1), query);
    if (mine !== renderToken) return;
    view.replaceChildren(result.el);
    if (!player.current) document.title = `${result.title} | RelliRadio`;
  } catch (err) {
    if (mine !== renderToken) return;
    if (err.code === 40 || err.code === "no-session") return signOutAndShowLogin();
    view.replaceChildren(errorState(err, route));
  } finally {
    clearTimeout(loader);
    view.removeAttribute("aria-busy");
  }
  main.scrollTop = scrollMemory.get(key) || 0;
  lastScroll = main.scrollTop;
}

function signOutAndShowLogin() {
  api.clearSession();
  showLogin();
}

function showLogin() {
  clear(root);
  root.append(loginView(start));
}

// "Pick up where you left off": if another device (phone app, TV) saved its queue to Navidrome
// more recently than this one, offer to continue from there.
function renderHandoff() {
  document.querySelector(".handoff")?.remove();
  const q = handoffOffer();
  if (!q) return;
  const song = q.songs.find((s) => s.id === q.current) || q.songs[0];
  const where = q.changedBy.replace(/^RelliRadio\s*/, "").replace(/[()]/g, "") || "your other device";
  const bar = h("div.handoff", { role: "status" },
    h("div.handoff-text",
      h("span.handoff-from", `Playing on ${where}`),
      h("span.handoff-song", `${song.title} · ${fmtTime((q.livePosition ?? q.position ?? 0) / 1000)}`)),
    h("button.btn.primary.small", { type: "button", onclick: () => { bar.remove(); playHere(); } }, "Play here"),
    h("button.icon-btn", { type: "button", "aria-label": "Not now", onclick: () => dismissHandoff() }, icon("close")));
  root.append(bar);
}

function start() {
  buildShell();
  player.restore();
  handoff.addEventListener("change", renderHandoff);
  checkHandoff();
  document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible" && !player.playing) checkHandoff(); });
  // Remember how far down each page you were, so Back returns to the same spot.
  window.addEventListener("hashchange", (e) => {
    if (e.oldURL) scrollMemory.set(new URL(e.oldURL).hash, lastScroll);
    route();
  });
  main.addEventListener("scroll", () => { lastScroll = main.scrollTop; }, { passive: true });
  route();
}

// ---------- Boot ----------

if ("serviceWorker" in navigator && window.isSecureContext && !window.relliDesktop) {
  navigator.serviceWorker.register("./sw.js").catch(() => {});
}

if (api.loadSession()) start();
else showLogin();
