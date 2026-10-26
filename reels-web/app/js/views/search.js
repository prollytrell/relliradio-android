import * as api from "../api.js";
import { h, icon, shelf, posterCard, wideCard, liveCard, empty, spinner } from "../ui.js";

let lastQuery = "";

export async function searchView() {
  const results = h("div.results");
  const input = h("input.search-input", { type: "search", placeholder: "Movies, shows, episodes, channels", value: lastQuery, "aria-label": "Search", autocomplete: "off" });
  let timer, token = 0;
  const run = async () => {
    const q = input.value.trim();
    lastQuery = input.value;
    const t = ++token;
    if (q.length < 2) return results.replaceChildren();
    results.replaceChildren(spinner());
    try {
      const r = (await api.items({ search: q, types: "Movie,Series,Episode,TvChannel,BoxSet", sortBy: "SortName", limit: 80 })).Items;
      if (t !== token) return;
      const of = (type) => r.filter((i) => i.Type === type);
      results.replaceChildren(...(r.length ? [
        shelf("Movies", of("Movie"), posterCard),
        shelf("Shows", of("Series"), posterCard),
        shelf("Collections", of("BoxSet"), posterCard),
        shelf("Episodes", of("Episode"), (i) => wideCard(i)),
        shelf("Channels", of("TvChannel"), liveCard),
      ].filter(Boolean) : [empty(`Nothing found for "${q}"`)]));
    } catch (e) { if (t === token) results.replaceChildren(empty("Search didn't work", e.message)); }
  };
  input.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(run, 300); });
  setTimeout(() => input.focus(), 50);
  if (lastQuery) run();
  return h("div.search",
    h("div.pad-x", h("h1.page-title", "Search"), h("label.search-box", icon("search"), input)),
    results);
}
