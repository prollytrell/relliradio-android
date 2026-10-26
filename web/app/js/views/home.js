// Home: the RelliRadio logo, quick mixes, and a stack of widgets you can
// turn on, off, and reorder (Edit home). Layout follows Symfonium's modern theme.

import * as api from "../api.js";
import { player } from "../player.js";
import { getPref, setPref, defaultPref } from "../prefs.js";
import { h, icon, iconButton, cover, toast, logo, openSheet, toggle, plural } from "../ui.js";
import { songList, emptyState, go } from "../components.js";
import { trackMix, albumMix, decadeMix } from "../smart.js";
import { downloadedSongs } from "../offline.js";

// ---------- Widget registry ----------
// Each widget: title, where its ">" chevron leads, and a loader that returns an element (or null to hide).

const shuffled = (a) => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = (Math.random() * (i + 1)) | 0; [a[i], a[j]] = [a[j], a[i]]; } return a; };

async function playAlbum(album, btn) {
  if (btn) btn.disabled = true;
  try {
    const full = await api.getAlbum(album.id);
    player.playList(full.song, 0, { shuffle: false, source: full.name });
  } catch (e) { toast(e.message); }
  finally { if (btn) btn.disabled = false; }
}

function playBadge(album, cls = "w-play") {
  const b = iconButton("play", `Play ${album.name}`, (e) => { e.preventDefault(); e.stopPropagation(); playAlbum(album, b); }, cls);
  return b;
}

const albumHref = (a) => `#/album/${a.id}`;
const art = (a) => a.coverArt || a.id;

// Tile styles
const tileSmall = (a) => h("a.w-tile", { href: albumHref(a) },
  cover(art(a), 300), h("span.w-title", a.name), h("span.w-sub", a.artist || ""));
const tileLarge = (a) => h("a.w-tile.large", { href: albumHref(a) },
  cover(art(a), 500), h("span.w-title", a.name), h("span.w-sub", a.artist || ""));
const tileWideOverlay = (a) => h("a.w-wide", { href: albumHref(a) },
  cover(art(a), 500), h("div.w-wide-text", h("span.w-title", a.name), h("span.w-sub", a.artist || "")));
const tileBlurred = (a) => h("a.w-blur", { href: albumHref(a) },
  h("div.w-blur-bg", cover(art(a), 300)),
  h("div.w-blur-fg", cover(art(a), 300), h("div.w-blur-text", h("span.w-title", a.name), h("span.w-sub", a.artist || ""))),
  playBadge(a, "w-play solid"));
const tileContinue = (a) => h("a.w-tile.continue", { href: albumHref(a) },
  h("div.w-art", cover(art(a), 300), playBadge(a)), h("span.w-title", a.name), h("span.w-sub", a.artist || ""));
const tileArtist = (a) => h("a.w-artist", { href: `#/artist/${a.id}` }, cover(a.coverArt || a.id, 300), h("span.w-title", a.name));
const tilePlaylist = (p) => h("a.w-tile", { href: `#/playlist/${p.id}` }, cover(p.coverArt || p.id, 300), h("span.w-title", p.name), h("span.w-sub", plural(p.songCount || 0, "song")));

const row = (items, render, cls = "") => items.length ? h(`div.w-row${cls}`, items.map(render)) : null;

const WIDGETS = {
  mixes: { title: "Mixes", load: async () => {
    const genres = await api.getGenres().catch(() => []);
    const tile = (label, sub, ic, run, cls = "") => {
      const b = h(`button.mix-tile${cls}`, { type: "button" }, h("span.mix-icon", icon(ic)), h("span.mix-label", label), h("span.mix-sub", sub));
      b.addEventListener("click", async () => {
        b.disabled = true;
        try { await run(); } catch (e) { toast(e.message); } finally { b.disabled = false; }
      });
      return b;
    };
    const top = genres.slice().sort((a, b) => (b.songCount || 0) - (a.songCount || 0)).slice(0, 6);
    return h("div.mixes",
      h("div.mix-main",
        tile("Track mix", "Random songs", "mix", trackMix, ".gold"),
        tile("Album mix", "Whole albums", "disc", albumMix),
        tile("Decade mix", "One era", "calendar", decadeMix)),
      top.length ? h("div.mix-genres", top.map((g) => tile(g.value, "Genre mix", "tag", async () => {
        player.shufflePlay(await api.getRandomSongs(60, g.value), `${g.value} mix`);
      }, ".chipish"))) : null);
  } },
  favTracks: { title: "Favorite tracks", href: "#/library/favorites", load: async () => {
    const { songs } = await api.getStarred();
    return songs.length ? songList(shuffled(songs).slice(0, 5), { source: "Favorites" }) : null;
  } },
  newest: { title: "Recently added albums", href: "#/library/albums?sort=newest", load: async () =>
    row(await api.getAlbumList("newest", { size: 20 }), tileSmall, ".two") },
  rediscover: { title: "Rediscover albums", href: "#/library/albums?sort=random", load: async () =>
    row(await api.getAlbumList("random", { size: 12 }), tileLarge) },
  recent: { title: "Last played albums", href: "#/library/albums?sort=recent", load: async () =>
    row(await api.getAlbumList("recent", { size: 12 }), tileWideOverlay) },
  randomArtists: { title: "Random artists", href: "#/library/artists", load: async () =>
    row(shuffled(await api.getArtists()).slice(0, 16), tileArtist) },
  randomAlbums: { title: "Random albums", href: "#/library/albums?sort=random", load: async () =>
    row(await api.getAlbumList("random", { size: 10 }), tileBlurred) },
  continue: { title: "Continue album listening", href: "#/library/albums?sort=recent", load: async () =>
    row((await api.getAlbumList("recent", { size: 20 })).slice(0, 12), tileContinue) },
  mostPlayed: { title: "Most played tracks", href: "#/library/albums?sort=frequent", load: async () => {
    const albums = await api.getAlbumList("frequent", { size: 6 });
    const lists = await Promise.all(albums.map((a) => api.getAlbum(a.id).then((x) => x.song).catch(() => [])));
    const top = lists.flat().filter((s) => s.playCount).sort((a, b) => b.playCount - a.playCount).slice(0, 5);
    return top.length ? songList(top, { source: "Most played" }) : null;
  } },
  frequent: { title: "Most played albums", href: "#/library/albums?sort=frequent", load: async () =>
    row(await api.getAlbumList("frequent", { size: 16 }), tileSmall) },
  playlists: { title: "Playlists", href: "#/library/playlists", load: async () =>
    row(await api.getPlaylists(), tilePlaylist) },
  radio: { title: "Internet radio", href: "#/library/radio", load: async () => {
    const stations = await api.getRadioStations();
    return stations.length ? h("div.w-row", stations.map((s) =>
      h("button.radio-tile", { type: "button", onclick: () => player.playRadio(s) }, h("span.mix-icon", icon("radio")), h("span.w-title", s.name)))) : null;
  } },
  downloads: { title: "Downloaded", href: "#/library/downloads", load: async () => {
    const songs = downloadedSongs();
    return songs.length ? songList(songs.slice(0, 5), { source: "Downloads" }) : null;
  } },
};

export const HOME_WIDGETS = Object.entries(WIDGETS).map(([id, w]) => [id, w.title]);

function sectionIds() {
  const saved = getPref("homeSections") || [];
  const ids = saved.filter((id) => WIDGETS[id]);
  return ids.length ? ids : defaultPref("homeSections");
}

// ---------- Edit home sheet ----------

export function editHomeSheet(onDone) {
  let on = sectionIds();
  let order = [...on, ...Object.keys(WIDGETS).filter((id) => !on.includes(id))];
  const list = h("ol.edit-list", { role: "list" });
  const save = () => { setPref("homeSections", order.filter((id) => on.includes(id))); };
  const render = () => {
    list.replaceChildren(...order.map((id, i) => h("li.edit-row",
      toggle(WIDGETS[id].title, on.includes(id), (checked) => {
        on = checked ? [...on, id] : on.filter((x) => x !== id);
        save();
      }),
      iconButton("up", `Move ${WIDGETS[id].title} up`, () => { if (i) { [order[i - 1], order[i]] = [order[i], order[i - 1]]; save(); render(); list.children[i - 1]?.querySelector(".icon-btn")?.focus(); } }, "edit-move"),
      iconButton("up", `Move ${WIDGETS[id].title} down`, () => { if (i < order.length - 1) { [order[i + 1], order[i]] = [order[i], order[i + 1]]; save(); render(); list.children[i + 1]?.querySelectorAll(".icon-btn")[1]?.focus(); } }, "edit-move flip")
    )));
  };
  render();
  const reset = h("button.btn.small", { type: "button", onclick: () => {
    setPref("homeSections", defaultPref("homeSections"));
    on = sectionIds(); order = [...on, ...Object.keys(WIDGETS).filter((id) => !on.includes(id))];
    render();
  } }, "Reset to default");
  const sheet = openSheet({ title: "Edit home", ic: "edit", body: h("div", h("p.hint", "Turn widgets on or off and move them up or down. Changes save right away."), list, h("div.sheet-foot", reset)) });
  const obs = new MutationObserver(() => { if (!sheet.overlay.isConnected) { obs.disconnect(); onDone?.(); } });
  obs.observe(document.body, { childList: true });
}

// ---------- View ----------

function widgetSection(id) {
  const w = WIDGETS[id];
  const body = h("div.w-body", h("div.w-skeleton"));
  const head = id === "mixes" ? null : h("div.w-head",
    w.href ? h("a.w-heading", { href: w.href }, h("h2", w.title), icon("arrow")) : h("h2", w.title));
  const sec = h(`section.widget.w-${id}`, head, body);
  w.load().then((el) => {
    if (!el) sec.remove();
    else body.replaceChildren(el);
  }).catch(() => sec.remove());
  return sec;
}

export async function homeView() {
  const newest = await api.getAlbumList("newest", { size: 1 });
  const stack = h("div.widgets");
  const render = () => stack.replaceChildren(...sectionIds().map(widgetSection));
  render();

  const el = h("div.home",
    h("header.home-head",
      h("div.home-logo", logo("home-mark")),
      h("div.home-actions",
        iconButton("edit", "Edit home", () => editHomeSheet(render), "home-btn"),
        iconButton("queue", "Queue", () => player.current ? document.dispatchEvent(new Event("relli:open-queue")) : toast("Nothing is playing yet."), "home-btn"),
        iconButton("search", "Search", () => go("#/search"), "home-btn"))),
    newest.length ? stack : emptyState("Your library is empty", "Copy music into the folder Navidrome watches, wait for the scan, then reload this page.")
  );
  return { title: "Home", el };
}
