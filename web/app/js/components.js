// Building blocks shared by every screen: album tiles, song rows, shelves, menus.

import * as api from "./api.js";
import { player } from "./player.js";
import { getPref } from "./prefs.js";
import { h, icon, iconButton, cover, fmtTime, toast, plural, showMenu } from "./ui.js";
import * as sheets from "./sheets.js";
import { isDownloaded } from "./offline.js";

export const go = (hash) => { location.hash = hash; };

// ---------- Favorites ----------

export async function toggleStar(item, button) {
  const next = !item.starred;
  item.starred = next ? new Date().toISOString() : undefined;
  if (button) setHeart(button, next);
  try {
    await api.setStarred(item.id, next);
    document.dispatchEvent(new CustomEvent("relli:starred", { detail: { id: item.id, starred: next } }));
    toast(next ? "Added to favorites" : "Removed from favorites");
  } catch (e) {
    item.starred = next ? undefined : new Date().toISOString();
    if (button) setHeart(button, !next);
    toast(e.message);
  }
}

export function heartButton(item) {
  const btn = iconButton(item.starred ? "heartFill" : "heart", item.starred ? "Remove from favorites" : "Add to favorites", (e) => {
    e.stopPropagation();
    toggleStar(item, btn);
  }, "heart" + (item.starred ? " on" : ""));
  btn.dataset.starId = item.id;
  return btn;
}

export function setHeart(btn, on) {
  btn.classList.toggle("on", on);
  btn.replaceChildren(icon(on ? "heartFill" : "heart"));
  btn.setAttribute("aria-label", on ? "Remove from favorites" : "Add to favorites");
  btn.title = btn.getAttribute("aria-label");
}

document.addEventListener("relli:starred", (e) => {
  document.querySelectorAll(`[data-star-id="${CSS.escape(e.detail.id)}"]`).forEach((b) => setHeart(b, e.detail.starred));
});

// ---------- Song menu (the "More actions" sheet) ----------

export function addToPlaylistMenu(songs) {
  return sheets.playlistPicker([].concat(songs));
}

export function songMenu(song) {
  sheets.moreActions(song);
}

// ---------- Song rows ----------
// list: the songs this row belongs to (tapping plays the list from this song)

export function songList(songs, { numbered = false, covers = true, source = "", extraMenu } = {}) {
  const wrap = h("ol.song-list", { role: "list" });
  songs.forEach((song, i) => wrap.append(songRow(song, i, songs, { numbered, covers, source, extraMenu })));
  return wrap;
}

export function songRow(song, i, list, { numbered = false, covers = true, source = "", extraMenu } = {}) {
  const play = () => {
    player.playList(list, i, { shuffle: false, source });
    // Optional (Settings → Open Now Playing when you pick a song).
    if (getPref("openPlayerOnSelect")) document.dispatchEvent(new Event("relli:open-player"));
  };
  const more = iconButton("more", "More options", (e) => {
    e.stopPropagation();
    rowMenu(song, i, more, extraMenu);
  }, "row-more");

  const row = h("li.song-row", {
    dataset: { id: song.id },
    tabindex: "0",
    onclick: play,
    onkeydown: (e) => { if (e.key === "Enter") play(); },
    oncontextmenu: (e) => { e.preventDefault(); rowMenu(song, i, more, extraMenu); },
  },
    numbered
      ? h("span.row-num", song.track || i + 1)
      : covers ? cover(song.coverArt || song.albumId, 96, "row-cover") : null,
    h("div.row-text",
      h("span.row-title", song.title),
      h("span.row-sub", song.artist || "")
    ),
    numbered ? null : h("span.row-album", song.album || ""),
    isDownloaded(song.id) ? h("span.row-offline", { title: "Downloaded" }, icon("cloudDone")) : null,
    h("span.row-time", fmtTime(song.duration)),
    more
  );
  if (player.current?.id === song.id) row.classList.add("playing");
  return row;
}

// Rows with extra actions (like "Remove from this playlist") show those first.
function rowMenu(song, i, anchor, extraMenu) {
  const extra = extraMenu ? extraMenu(song, i) : [];
  if (extra.length) return showMenu(anchor, [...extra, { label: "More actions", icon: "more", onclick: () => songMenu(song) }], song.title);
  songMenu(song);
}

// Keep the "now playing" highlight in sync on every list on screen.
player.addEventListener("track", () => {
  const id = player.current?.id;
  document.querySelectorAll(".song-row").forEach((r) => r.classList.toggle("playing", r.dataset.id === id));
});

// ---------- Album and artist tiles ----------

export function albumTile(album) {
  const playBtn = iconButton("play", `Play ${album.name}`, async (e) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      const full = await api.getAlbum(album.id);
      player.playList(full.song, 0, { shuffle: false, source: full.name });
    } catch (err) { toast(err.message); }
  }, "tile-play");
  return h("a.tile", { href: `#/album/${album.id}` },
    h("div.tile-art", cover(album.coverArt || album.id, 300), playBtn),
    h("span.tile-title", album.name),
    h("span.tile-sub", album.artist || (album.year ? String(album.year) : ""))
  );
}

export function artistTile(artist) {
  const art = artist.coverArt || artist.id;
  return h("a.tile.artist", { href: `#/artist/${artist.id}` },
    h("div.tile-art.round", cover(art, 300)),
    h("span.tile-title", artist.name),
    h("span.tile-sub", artist.albumCount != null ? plural(artist.albumCount, "album") : "")
  );
}

export function playlistTile(p) {
  return h("a.tile", { href: `#/playlist/${p.id}` },
    h("div.tile-art", cover(p.coverArt || p.id, 300)),
    h("span.tile-title", p.name),
    h("span.tile-sub", plural(p.songCount || 0, "song"))
  );
}

export function grid(items, render) {
  return h("div.grid", items.map(render));
}

// Horizontal shelf with a title and optional "See all" link.
export function shelf(title, items, render, moreHref) {
  if (!items.length) return null;
  const row = h("div.shelf-row", items.map(render));
  const scrollBy = (dir) => row.scrollBy({ left: dir * row.clientWidth * 0.8, behavior: "smooth" });
  return h("section.shelf",
    h("div.shelf-head",
      h("h2", title),
      h("div.shelf-actions",
        moreHref ? h("a.link", { href: moreHref }, "See all") : null,
        iconButton("back", "Scroll left", () => scrollBy(-1), "shelf-arrow"),
        iconButton("back", "Scroll right", () => scrollBy(1), "shelf-arrow flip")
      )
    ),
    row
  );
}

// ---------- Page header for albums, artists, playlists ----------

export function hero({ art, round = false, kind, title, lines = [], actions = [] }) {
  return h("header.hero",
    h(`div.hero-art${round ? ".round" : ""}`, art),
    h("div.hero-text",
      kind ? h("p.hero-kind", kind) : null,
      h("h1", title),
      lines.filter(Boolean).map((l) => h("p.hero-line", l)),
      h("div.hero-actions", actions)
    )
  );
}

export function playButtons(getSongs, source) {
  const run = async (mode, btn) => {
    btn.disabled = true;
    try {
      const songs = await getSongs();
      if (!songs.length) return toast("There's nothing to play here yet.");
      if (mode === "shuffle") player.shufflePlay(songs, source);
      else player.playList(songs, 0, { shuffle: false, source });
    } catch (e) {
      toast(e.message);
    } finally {
      btn.disabled = false;
    }
  };
  const playBtn = h("button.btn.primary", { type: "button", onclick: () => run("play", playBtn) }, icon("play"), "Play");
  const shufBtn = h("button.btn", { type: "button", onclick: () => run("shuffle", shufBtn) }, icon("shuffle"), "Shuffle");
  return [playBtn, shufBtn];
}

export function emptyState(title, text, action) {
  return h("div.empty", h("h2", title), text ? h("p", text) : null, action || null);
}

export function errorState(err, retry) {
  return emptyState("This didn't load", err.message, retry ? h("button.btn", { type: "button", onclick: retry }, "Try again") : null);
}
