import * as api from "../api.js";
import { h, go, shelf, posterCard, wideCard, empty } from "../ui.js";

// Your list (favorites), what you're in the middle of, and what you watched lately.
export async function myStuffView() {
  const [resume, movies, shows, watched] = await Promise.all([
    api.resume(30),
    api.items({ types: "Movie,BoxSet", favorites: true, limit: 100 }).then((r) => r.Items),
    api.items({ types: "Series", favorites: true, limit: 100 }).then((r) => r.Items),
    api.items({ types: "Movie,Episode", sortBy: "DatePlayed", sortOrder: "Descending", limit: 30 }).then((r) => r.Items.filter((i) => i.UserData?.LastPlayedDate)),
  ]);
  return h("div.mystuff",
    h("div.pad-x", h("h1.page-title", "My Stuff")),
    !resume.length && !movies.length && !shows.length ? h("div.pad-x", empty("Nothing saved yet", 'Click "My List" on any movie or show to keep it here.')) : null,
    shelf("Continue watching", resume, (i) => wideCard(i)),
    shelf("My List · Movies", movies, posterCard),
    shelf("My List · Shows", shows, posterCard),
    shelf("Recently watched", watched, (i) => wideCard(i, { onclick: () => go.open(i.Type === "Episode" && i.SeriesId ? { Type: "Series", Id: i.SeriesId } : i) })));
}
