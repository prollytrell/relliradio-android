import * as api from "../api.js";
import { player } from "../player.js";
import { h, icon, iconButton, cover, toast, fmtLength, plural, showMenu } from "../ui.js";
import { download } from "../offline.js";
import { hero, playButtons, songList, songRow, heartButton, albumTile, addToPlaylistMenu, emptyState, go } from "../components.js";

// ---------- Album ----------

export async function albumView([id]) {
  const album = await api.getAlbum(id);
  const songs = album.song;
  const discs = [...new Set(songs.map((s) => s.discNumber || 1))];

  const more = iconButton("more", "More options", () => showMenu(more, [
    { label: "Play next", icon: "next", onclick: () => { player.playNext(songs); toast("Album plays next"); } },
    { label: "Add to queue", icon: "queue", onclick: () => { player.addToQueue(songs); toast("Added to queue"); } },
    { label: "Add to playlist", icon: "playlist", onclick: () => addToPlaylistMenu(songs) },
    { label: "Download for offline", icon: "cloud", onclick: () => download(songs) },
    album.artistId && { label: "Go to artist", icon: "person", onclick: () => go(`#/artist/${album.artistId}`) },
  ], album.name));

  const artistLink = album.artistId ? h("a.link", { href: `#/artist/${album.artistId}` }, album.artist) : album.artist;
  const facts = [album.year, album.genre, plural(album.songCount || songs.length, "song"), fmtLength(album.duration)].filter(Boolean).join(", ");

  // Rows always get the whole album as their list, so tapping a song on disc 2
  // keeps playing through the rest of the album.
  const body = discs.length > 1
    ? discs.map((d) => h("section.disc",
        h("h2.disc-title", `Disc ${d}`),
        h("ol.song-list", { role: "list" },
          songs.map((s, i) => ((s.discNumber || 1) === d ? songRow(s, i, songs, { numbered: true, source: album.name }) : null)))))
    : songList(songs, { numbered: true, source: album.name });

  return {
    title: album.name,
    el: h("div.detail",
      hero({
        art: cover(album.coverArt || album.id, 600),
        kind: "Album",
        title: album.name,
        lines: [artistLink, facts],
        actions: [...playButtons(async () => songs, album.name), heartButton(album), more],
      }),
      body
    ),
  };
}

// ---------- Artist ----------

export async function artistView([id]) {
  const artist = await api.getArtist(id);
  const [info, top] = await Promise.all([api.getArtistInfo(id), api.getTopSongs(artist.name, 10)]);
  const albums = artist.album.slice().sort((a, b) => (b.year || 0) - (a.year || 0));

  const allSongs = async () => {
    const full = await Promise.all(albums.slice(0, 25).map((a) => api.getAlbum(a.id).then((x) => x.song).catch(() => [])));
    return full.flat();
  };

  const bio = (info.biography || "").replace(/<a [^>]*>.*?<\/a>/gi, "").replace(/<[^>]+>/g, "").trim();

  return {
    title: artist.name,
    el: h("div.detail",
      hero({
        art: cover(artist.coverArt || artist.id, 600),
        round: true,
        kind: "Artist",
        title: artist.name,
        lines: [plural(albums.length, "album")],
        actions: [...playButtons(allSongs, artist.name), heartButton(artist)],
      }),
      top.length ? h("section", h("h2", "Popular"), songList(top, { source: artist.name })) : null,
      h("section", h("h2", "Albums"), albums.length ? h("div.grid", albums.map(albumTile)) : emptyState("No albums")),
      bio ? h("section.bio", h("h2", "About"), h("p", bio)) : null
    ),
  };
}

// ---------- Playlist ----------

export async function playlistView([id]) {
  const p = await api.getPlaylist(id);
  const songs = p.entry;
  const list = songList(songs, {
    source: p.name,
    extraMenu: p.readonly ? null : (song, i) => [{
      label: "Remove from this playlist", icon: "close", onclick: async () => {
        try {
          await api.removeFromPlaylist(p.id, [i]);
          toast("Removed from playlist");
          window.dispatchEvent(new HashChangeEvent("hashchange"));
        } catch (e) { toast(e.message); }
      },
    }],
  });
  return {
    title: p.name,
    el: h("div.detail",
      hero({
        art: cover(p.coverArt || p.id, 600),
        kind: "Playlist",
        title: p.name,
        lines: [p.comment, `${plural(p.songCount || songs.length, "song")}, ${fmtLength(p.duration)}`],
        actions: playButtons(async () => songs, p.name),
      }),
      songs.length ? list : emptyState("This playlist is empty", "Open any song's menu and choose “Add to playlist.”")
    ),
  };
}

// ---------- Genre ----------

export async function genreView([name]) {
  const genre = decodeURIComponent(name);
  const [songs, albums] = await Promise.all([
    api.getSongsByGenre(genre, 50),
    api.getAlbumList("byGenre", { genre, size: 60 }),
  ]);
  const radio = h("button.btn.primary", { type: "button", onclick: async () => {
    try { player.playList(await api.getRandomSongs(60, genre), 0, { shuffle: false, source: `${genre} mix` }); }
    catch (e) { toast(e.message); }
  } }, icon("shuffle"), `Shuffle ${genre}`);
  return {
    title: genre,
    el: h("div.detail",
      h("header.page-head", h("h1.page-title", genre), h("div.hero-actions", radio)),
      albums.length ? h("section", h("h2", "Albums"), h("div.grid", albums.map(albumTile))) : null,
      songs.length ? h("section", h("h2", "Songs"), songList(songs, { source: genre })) : null
    ),
  };
}
