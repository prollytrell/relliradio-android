import * as api from "../api.js";
import { h, icon, go, button, chip, img, shelf, posterCard, progressBar, progressOf, metaLine, fmtClock, fmtRuntime, msOf, episodeCode, toast } from "../ui.js";

// A movie, show or collection: backdrop, details, buttons, seasons and episodes, cast, more like this.
export async function detailView(id, seasonId) {
  const i = await api.item(id);
  const isSeries = i.Type === "Series";
  const [similar, seasons, next, children] = await Promise.all([
    api.similar(id).catch(() => []),
    isSeries ? api.seasons(id).catch(() => []) : [],
    isSeries ? api.nextUp(1, id).then((l) => l[0] || null).catch(() => null) : null,
    i.Type === "BoxSet" ? api.items({ parentId: id, types: null, sortBy: "ProductionYear,SortName", limit: 200 }).then((r) => r.Items).catch(() => []) : [],
  ]);

  const target = isSeries ? next : i.Type === "BoxSet" ? null : i;
  const resumeAt = msOf(target?.UserData?.PlaybackPositionTicks);
  let fav = !!i.UserData?.IsFavorite;
  let watched = !!i.UserData?.Played;

  const favBtn = button(fav ? "In My List" : "My List", fav ? "check" : "plus", async () => {
    fav = !fav;
    favBtn.replaceChildren(icon(fav ? "check" : "plus"), h("span", fav ? "In My List" : "My List"));
    try { await api.setFavorite(i.Id, fav); } catch (e) { toast(e.message); }
  });
  const watchedBtn = i.Type === "BoxSet" ? null : button(watched ? "Watched" : "Mark watched", watched ? "check" : "eye", async () => {
    watched = !watched;
    watchedBtn.replaceChildren(icon(watched ? "check" : "eye"), h("span", watched ? "Watched" : "Mark watched"));
    try { await api.setPlayed(i.Id, watched); } catch (e) { toast(e.message); }
  });

  const playLabel = isSeries && target ? `${resumeAt > 0 ? "Resume" : "Play"} ${episodeCode(target) || ""}`.trim()
    : resumeAt > 0 ? `Resume from ${fmtClock(resumeAt)}` : "Play";
  const logo = api.logo(i);

  const header = h("section.detail-head",
    h("div.detail-backdrop", img(api.backdrop(i, 1920) || api.poster(i, 900), "hero-art")),
    h("div.detail-scrim"),
    h("div.detail-info",
      logo ? h("img.hero-logo", { src: logo, alt: i.Name }) : h("h1.hero-title", i.Name),
      h("p.hero-meta", metaLine(i)),
      i.Genres?.length ? h("div.tags", i.Genres.slice(0, 4).map((g) => h("span.tag", g))) : null,
      i.Taglines?.[0] ? h("p.tagline", i.Taglines[0]) : null,
      i.Overview ? h("p.overview", i.Overview) : null,
      h("div.row",
        target ? button(playLabel, "play", () => go.play(target), true) : null,
        target && resumeAt > 0 ? button("Start over", "replay", () => go.play(target, true)) : null,
        favBtn, watchedBtn),
      !isSeries && resumeAt > 0 ? h("div.detail-progress", progressBar(progressOf(i))) : null));

  return h("div.detail",
    header,
    isSeries ? seasonsBlock(i, seasons, seasonId || next?.SeasonId || seasons.find((s) => s.UserData?.UnplayedItemCount)?.Id || seasons[0]?.Id) : null,
    shelf("In this collection", children, posterCard),
    cast(i),
    shelf("More like this", similar, posterCard));
}

function seasonsBlock(series, seasons, initial) {
  if (!seasons.length) return null;
  const list = h("div.episodes");
  const chips = h("div.chips");
  const pick = async (sid) => {
    chips.replaceChildren(...seasons.map((s) => chip(s.Name, s.Id === sid, () => pick(s.Id))));
    list.replaceChildren(h("div.spinner"));
    try {
      const eps = await api.episodes(series.Id, sid);
      list.replaceChildren(...eps.map(episodeRow));
    } catch (e) { list.replaceChildren(h("p.hint", e.message)); }
  };
  pick(initial);
  return h("section.seasons.pad-x", chips, list);
}

function episodeRow(ep) {
  return h("button.episode", { type: "button", onclick: () => go.play(ep) },
    h("div.ep-art", img(api.landscape(ep, 480), "", ""), progressBar(progressOf(ep)), ep.UserData?.Played ? h("span.badge", "Watched") : null, h("span.play-badge", icon("play"))),
    h("div.ep-text",
      h("strong", `${ep.IndexNumber ?? ""}. ${ep.Name}`),
      h("span.card-sub", [ep.RunTimeTicks ? fmtRuntime(msOf(ep.RunTimeTicks)) : null, ep.PremiereDate?.slice(0, 4)].filter(Boolean).join(" · ")),
      ep.Overview ? h("p", ep.Overview) : null));
}

function cast(i) {
  const people = (i.People || []).filter((p) => p.Type === "Actor" || p.Type === "Director").slice(0, 16);
  return shelf("Cast & crew", people, (p) =>
    h("div.person", img(api.image(p.Id, "Primary", p.PrimaryImageTag, 200), "round", p.Name.slice(0, 1)), h("strong", p.Name), h("span.card-sub", p.Type === "Director" ? "Director" : p.Role || "")));
}
