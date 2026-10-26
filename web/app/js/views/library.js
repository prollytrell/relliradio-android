import * as api from "../api.js";
import { player } from "../player.js";
import { getPref, setPref } from "../prefs.js";
import { h, icon, iconButton, cover, toast, onNearBottom, plural, promptDialog } from "../ui.js";
import { albumTile, artistTile, playlistTile, songRow, songList, emptyState, go } from "../components.js";
import { downloadedSongs, removeAll, storageUsed, fmtBytes, offlineSupported } from "../offline.js";

const TABS = [
  ["albums", "Albums"],
  ["artists", "Artists"],
  ["songs", "Songs"],
  ["playlists", "Playlists"],
  ["genres", "Genres"],
  ["favorites", "Favorites"],
  ["radio", "Radio"],
  ["downloads", "Downloads"],
];

const ALBUM_SORTS = [
  ["newest", "Recently added"],
  ["recent", "Recently played"],
  ["frequent", "Most played"],
  ["alphabeticalByName", "Title"],
  ["alphabeticalByArtist", "Artist"],
  ["random", "Random"],
];

function tabs(active) {
  return h("nav.tabs", { "aria-label": "Library sections" },
    TABS.map(([id, label]) => h("a.tab", { href: `#/library/${id}`, "aria-current": id === active ? "page" : null }, label))
  );
}

async function albums(query) {
  const sort = ALBUM_SORTS.some(([v]) => v === query.get("sort")) ? query.get("sort") : "newest";
  const select = h("select.select", { "aria-label": "Sort albums", onchange: (e) => go(`#/library/albums?sort=${e.target.value}`) },
    ALBUM_SORTS.map(([v, label]) => h("option", { value: v, selected: v === sort }, label)));
  let layout = getPref("albumLayout");
  const gridEl = h(layout === "list" ? "div.album-list" : "div.grid");
  const layoutBtn = iconButton(layout === "list" ? "grid" : "list", layout === "list" ? "Show as grid" : "Show as list", () => {
    setPref("albumLayout", layout === "list" ? "grid" : "list");
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  }, "layout-btn");
  const sentinel = h("div.sentinel");
  let offset = 0;
  const page = 60;
  const loadMore = async () => {
    const items = await api.getAlbumList(sort, { size: page, offset });
    offset += items.length;
    items.forEach((a) => gridEl.append(layout === "list" ? albumRow(a) : albumTile(a)));
    if (!offset) gridEl.replaceWith(emptyState("No albums yet", "Add music to your Navidrome folder and they'll show up here."));
    return items.length === page && sort !== "random";
  };
  await loadMore();
  onNearBottom(sentinel, loadMore);
  return h("div", h("div.toolbar", h("label.sort", "Sort by", select), layoutBtn), gridEl, sentinel);
}

function albumRow(a) {
  return h("a.album-row", { href: `#/album/${a.id}` },
    cover(a.coverArt || a.id, 120, "row-cover"),
    h("div.row-text", h("span.row-title", a.name), h("span.row-sub", [a.artist, a.year].filter(Boolean).join("  ·  "))),
    h("span.row-time", a.songCount ? plural(a.songCount, "song") : ""));
}

async function artists() {
  const all = await api.getArtists();
  const gridEl = h("div.grid");
  const render = (q) => {
    const term = q.trim().toLowerCase();
    gridEl.replaceChildren(...all.filter((a) => !term || a.name.toLowerCase().includes(term)).map(artistTile));
  };
  const filter = h("input.field.filter", { type: "search", placeholder: "Filter artists", "aria-label": "Filter artists", oninput: (e) => render(e.target.value) });
  render("");
  return h("div", h("div.toolbar", filter, h("span.count", plural(all.length, "artist"))), all.length ? gridEl : emptyState("No artists yet"));
}

async function songs() {
  const list = h("ol.song-list", { role: "list" });
  const loaded = [];
  const sentinel = h("div.sentinel");
  const page = 100;
  const loadMore = async () => {
    const items = await api.getAllSongs(loaded.length, page);
    const start = loaded.length;
    loaded.push(...items);
    items.forEach((s, i) => list.append(songRow(s, start + i, loaded, { source: "All songs" })));
    return items.length === page;
  };
  await loadMore();
  onNearBottom(sentinel, loadMore);
  const shuffleAll = h("button.btn.primary", { type: "button", onclick: async () => {
    try { player.playList(await api.getRandomSongs(200), 0, { shuffle: false, source: "All songs" }); }
    catch (e) { toast(e.message); }
  } }, icon("shuffle"), "Shuffle all");
  return h("div", h("div.toolbar", shuffleAll), loaded.length ? list : emptyState("No songs yet"), sentinel);
}

async function playlists() {
  const all = await api.getPlaylists();
  const newBtn = h("button.btn", { type: "button", onclick: async () => {
    const name = await promptDialog({ title: "New playlist", label: "Playlist name", placeholder: "Sunday cleaning", confirm: "Create playlist" });
    if (!name) return;
    try {
      const p = await api.createPlaylist(name);
      document.dispatchEvent(new Event("relli:playlists"));
      if (p?.id) go(`#/playlist/${p.id}`);
      else location.reload();
    } catch (e) { toast(e.message); }
  } }, icon("plus"), "New playlist");
  return h("div",
    h("div.toolbar", newBtn),
    all.length ? h("div.grid", all.map(playlistTile)) : emptyState("No playlists yet", "Create one here, or use “Add to playlist” on any song.")
  );
}

async function genres() {
  const all = await api.getGenres();
  if (!all.length) return emptyState("No genres yet", "Genres come from the tags in your music files.");
  return h("ul.genre-list", all.map((g) =>
    h("li", h("a.genre", { href: `#/genre/${encodeURIComponent(g.value)}` },
      h("span.genre-name", g.value),
      h("span.genre-count", plural(g.songCount || 0, "song"))
    ))
  ));
}

async function favorites() {
  const { songs, albums, artists } = await api.getStarred();
  if (!songs.length && !albums.length && !artists.length) {
    return emptyState("No favorites yet", "Tap the heart on songs, albums, or artists to keep them here.");
  }
  return h("div.stack",
    songs.length ? h("section",
      h("div.section-head", h("h2", "Songs"),
        h("button.btn.small", { type: "button", onclick: () => player.shufflePlay(songs, "Favorites") }, icon("shuffle"), "Shuffle")),
      songList(songs, { source: "Favorites" })) : null,
    albums.length ? h("section", h("h2", "Albums"), h("div.grid", albums.map(albumTile))) : null,
    artists.length ? h("section", h("h2", "Artists"), h("div.grid", artists.map(artistTile))) : null
  );
}

async function radio() {
  const stations = await api.getRadioStations().catch(() => []);
  if (!stations.length) return emptyState("No radio stations", "Add internet radio stations in Navidrome (Radios, in the admin menu) and they'll appear here.");
  return h("ul.station-list", stations.map((s) => h("li",
    h("button.station", { type: "button", onclick: () => player.playRadio(s) },
      h("span.mix-icon", icon("radio")),
      h("span.row-text", h("span.row-title", s.name), h("span.row-sub", s.homePageUrl ? s.homePageUrl.replace(/^https?:\/\//, "") : "Live stream")),
      icon("play")))));
}

async function downloads() {
  if (!offlineSupported) return emptyState("Downloads aren't available", "This browser can't store music for offline use. Open RelliRadio over https.");
  const songs = downloadedSongs();
  const used = h("span.count", "");
  storageUsed().then((u) => { used.textContent = `${fmtBytes(u.total)} used`; });
  if (!songs.length) return emptyState("Nothing downloaded yet", "Open an album's menu and choose “Download for offline,” or use “Offline cache and download” on any song.");
  const clearBtn = h("button.btn.small", { type: "button", onclick: async () => {
    await removeAll();
    toast("Removed all downloads");
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  } }, icon("trash"), "Remove all");
  const shuf = h("button.btn.primary", { type: "button", onclick: () => player.shufflePlay(songs, "Downloads") }, icon("shuffle"), "Shuffle");
  return h("div", h("div.toolbar", shuf, clearBtn, used), songList(songs, { source: "Downloads" }));
}

const SECTIONS = { albums, artists, songs, playlists, genres, favorites, radio, downloads };

export async function libraryView([tab = "albums"], query) {
  const active = SECTIONS[tab] ? tab : "albums";
  const body = await SECTIONS[active](query);
  const label = TABS.find(([id]) => id === active)[1];
  return { title: label, el: h("div.library", h("h1.page-title", "Library"), tabs(active), body) };
}
