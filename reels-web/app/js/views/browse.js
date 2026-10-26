import * as api from "../api.js";
import { h, chip, posterCard, spinner, empty } from "../ui.js";
import { navigate } from "../app.js";
import { VIDEO_KINDS, typesFor } from "./home.js";

const SORTS = [
  ["Recently added", "DateCreated,SortName", "Descending"],
  ["A–Z", "SortName", "Ascending"],
  ["Newest", "PremiereDate,ProductionYear,SortName", "Descending"],
  ["Top rated", "CommunityRating,SortName", "Descending"],
  ["Random", "Random", "Ascending"],
];

// Remember the filters per library while the app is open.
const state = {};

// One library (Movies, Shows, Anime…) as a poster grid with genre and sort chips. Loads more as you scroll.
export async function browseView(viewId) {
  const views = (await api.views()).filter((v) => VIDEO_KINDS.has(v.CollectionType));
  if (!views.length) return h("div.pad", empty("No video libraries yet", "Add your Movies, Shows and Anime folders in Jellyfin's Dashboard → Libraries."));
  const view = views.find((v) => v.Id === viewId) || views[0];
  const st = (state[view.Id] ||= { sort: 0, genre: null });
  const genres = await api.genres(view.Id, typesFor(view)).catch(() => []);

  const grid = h("div.grid");
  const count = h("p.hint");
  const more = h("div.more", spinner());
  let loaded = 0, total = -1, busy = false, done = false;

  async function page() {
    if (busy || done) return;
    busy = true;
    try {
      const [, by, order] = SORTS[st.sort];
      const r = await api.items({ parentId: view.Id, types: typesFor(view), genre: st.genre, sortBy: by, sortOrder: order, start: loaded, limit: 60 });
      total = r.TotalRecordCount;
      loaded += r.Items.length;
      grid.append(...r.Items.map(posterCard));
      count.textContent = total === 1 ? "1 title" : `${total} titles`;
      done = loaded >= total || !r.Items.length;
      if (!total) grid.replaceChildren(h("p.hint", "Nothing here yet."));
    } catch (e) {
      done = true;
      grid.append(h("p.hint", e.message));
    } finally {
      busy = false;
      more.hidden = done;
    }
  }

  const io = new IntersectionObserver((entries) => { if (entries.some((e) => e.isIntersecting)) page(); }, { rootMargin: "800px" });
  io.observe(more);

  const reload = () => navigate(`#/browse/${view.Id}`);
  const el = h("div.browse.pad",
    h("h1.page-title", "Browse"),
    h("div.chips", views.map((v) => chip(v.Name, v.Id === view.Id, () => navigate(`#/browse/${v.Id}`)))),
    h("div.chips", SORTS.map(([label], k) => chip(label, k === st.sort, () => { st.sort = k; reload(); }))),
    genres.length ? h("div.chips.wrap", chip("All genres", !st.genre, () => { st.genre = null; reload(); }),
      genres.map((g) => chip(g.Name, st.genre === g.Name, () => { st.genre = st.genre === g.Name ? null : g.Name; reload(); }))) : null,
    count, grid, more);
  await page();
  return el;
}
