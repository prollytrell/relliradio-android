import * as api from "../api.js";
import { h, icon, go, button, chip, img, shelf, posterCard, wideCard, liveCard, metaLine, toast } from "../ui.js";
import { navigate } from "../app.js";

export const VIDEO_KINDS = new Set(["movies", "tvshows", "homevideos", "boxsets", "mixed", undefined, null]);

export function typesFor(view) {
  return { movies: "Movie", tvshows: "Series", boxsets: "BoxSet", homevideos: "Video" }[view?.CollectionType] || "Movie,Series";
}

// Same "random" picks all day, new ones tomorrow.
function seeded(list, seed) {
  const a = [...list];
  let s = seed;
  const rnd = () => ((s = (s * 9301 + 49297) % 233280) / 233280);
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
  return a;
}
const today = () => Math.floor(Date.now() / 86400e3);
const safe = (p, d = []) => p.catch(() => d);

export async function homeView() {
  const views = await api.views();
  const libs = views.filter((v) => VIDEO_KINDS.has(v.CollectionType) && v.CollectionType !== "boxsets");
  const hasLive = views.some((v) => v.CollectionType === "livetv");
  const [resume, next, latest, featured, onNow, favorites, genres] = await Promise.all([
    safe(api.resume()),
    safe(api.nextUp()),
    Promise.all(libs.map((v) => safe(api.latest(v.Id)).then((l) => [v, l]))),
    safe(api.items({ sortBy: "DateCreated,SortName", sortOrder: "Descending", limit: 30 }).then((r) => seeded(r.Items.filter((i) => i.BackdropImageTags?.length), today()).slice(0, 7))),
    hasLive ? safe(api.channels("all", 30).then((c) => c.filter((x) => x.CurrentProgram))) : [],
    safe(api.items({ favorites: true, sortBy: "DatePlayed,SortName", sortOrder: "Descending", limit: 30 }).then((r) => r.Items)),
    safe(api.genres(null).then((g) => Promise.all(seeded(g, today() + 7).slice(0, 5).map((x) =>
      api.items({ genre: x.Name, sortBy: "Random", limit: 20 }).then((r) => [x.Name, r.Items]).catch(() => [x.Name, []]))))),
  ]);

  return h("div.home",
    featured.length ? hero(featured) : h("div.page-head", h("h1", "Home")),
    h("div.chips.pad-x", libs.map((v) => chip(v.Name, false, () => navigate(`#/browse/${v.Id}`))), hasLive ? chip("Live TV", false, () => navigate("#/live")) : null),
    shelf("Continue watching", resume, (i) => wideCard(i)),
    shelf("Next up", next, (i) => wideCard(i)),
    shelf("On now", onNow, liveCard, () => navigate("#/live")),
    latest.map(([v, list]) => shelf(`New in ${v.Name}`, list, posterCard, () => navigate(`#/browse/${v.Id}`))),
    shelf("My List", favorites, posterCard, () => navigate("#/mystuff")),
    genres.filter(([, l]) => l.length >= 4).map(([name, list]) => shelf(name, list, posterCard)),
    libs.length ? null : h("p.hint.pad", "No movie or TV libraries yet. Add them in Jellyfin's Dashboard → Libraries."));
}

// The big rotating banner with Play, More info and My List.
function hero(items) {
  let index = 0, timer;
  const slides = items.map((i) => h("div.hero-slide", img(api.backdrop(i, 1920), "hero-art")));
  const info = h("div.hero-info");
  const dots = h("div.hero-dots");
  const el = h("section.hero", h("div.hero-slides", slides), h("div.hero-scrim"), info, dots);

  const show = (n) => {
    index = (n + items.length) % items.length;
    slides.forEach((s, k) => s.classList.toggle("on", k === index));
    const i = items[index];
    const logo = api.logo(i);
    let fav = !!i.UserData?.IsFavorite;
    const favBtn = button(fav ? "In My List" : "My List", fav ? "check" : "plus", async () => {
      fav = !fav;
      favBtn.replaceChildren(icon(fav ? "check" : "plus"), h("span", fav ? "In My List" : "My List"));
      try { await api.setFavorite(i.Id, fav); i.UserData = { ...(i.UserData || {}), IsFavorite: fav }; toast(fav ? "Added to My List" : "Removed from My List"); }
      catch (e) { fav = !fav; toast(e.message); }
    });
    info.replaceChildren(
      logo ? h("img.hero-logo", { src: logo, alt: i.Name }) : h("h1.hero-title", i.Name),
      h("p.hero-meta", metaLine(i)),
      i.Genres?.length ? h("p.hero-genres", i.Genres.slice(0, 3).join(" · ")) : null,
      i.Overview ? h("p.hero-overview", i.Overview) : null,
      h("div.row", button("Play", "play", () => (i.Type === "Series" ? go.open(i) : go.play(i)), true), button("More info", "info", () => go.open(i)), favBtn));
    dots.replaceChildren(...items.map((_, k) => h(`button.dot${k === index ? ".on" : ""}`, { type: "button", "aria-label": `Show ${k + 1}`, onclick: () => { show(k); restart(); } })));
  };
  const restart = () => {
    clearInterval(timer);
    timer = setInterval(() => { if (!document.body.contains(el)) return clearInterval(timer); if (!el.matches(":hover") && !el.contains(document.activeElement)) show(index + 1); }, 8000);
  };
  show(0);
  restart();
  return el;
}
