import * as api from "../api.js";
import { h, icon } from "../ui.js";
import { albumTile, artistTile, songList, emptyState, errorState } from "../components.js";

export async function searchView(_, query) {
  const initial = query.get("q") || "";
  const results = h("div.search-results");
  const input = h("input.field.search-input", {
    type: "search", value: initial, placeholder: "Artists, albums, or songs",
    "aria-label": "Search your library", autocomplete: "off", enterkeyhint: "search",
  });

  let timer, token = 0;
  async function run(q) {
    const mine = ++token;
    history.replaceState(null, "", q ? `#/search?q=${encodeURIComponent(q)}` : "#/search");
    if (!q.trim()) {
      results.replaceChildren(h("p.hint", "Search covers titles, artists, and albums in your library."));
      return;
    }
    try {
      const r = await api.search(q.trim());
      if (mine !== token) return;
      const none = !r.artists.length && !r.albums.length && !r.songs.length;
      results.replaceChildren(
        none ? emptyState(`Nothing matches “${q}”`, "Check the spelling, or try fewer words.") : "",
        r.songs.length ? h("section", h("h2", "Songs"), songList(r.songs.slice(0, 12), { source: `Search: ${q}` })) : "",
        r.artists.length ? h("section", h("h2", "Artists"), h("div.grid", r.artists.map(artistTile))) : "",
        r.albums.length ? h("section", h("h2", "Albums"), h("div.grid", r.albums.map(albumTile))) : ""
      );
    } catch (e) {
      if (mine === token) results.replaceChildren(errorState(e, () => run(q)));
    }
  }

  input.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(() => run(input.value), 250);
  });

  run(initial);
  queueMicrotask(() => input.focus());

  return {
    title: "Search",
    el: h("div.search",
      h("h1.page-title", "Search"),
      h("label.search-box", icon("search"), input),
      results
    ),
  };
}
