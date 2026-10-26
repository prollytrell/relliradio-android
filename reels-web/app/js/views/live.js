import * as api from "../api.js";
import { h, go, chip, img, progressBar, programProgress, empty, iconButton, toast } from "../ui.js";
import { navigate } from "../app.js";

const FILTERS = [["all", "All channels"], ["favorites", "Favorites"], ["movies", "Movies"], ["series", "Shows"], ["news", "News"], ["sports", "Sports"], ["kids", "Kids"]];
let filter = "all";
const time = (iso) => new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });

// Live TV: every channel with what's on now and next, like a cable guide. Pick one to watch.
export async function liveView() {
  const channels = await api.channels(filter);
  const progs = await api.programs(channels.slice(0, 150).map((c) => c.Id)).catch(() => []);
  const byChannel = {};
  for (const p of progs) (byChannel[p.ChannelId] ||= []).push(p);

  return h("div.live.pad",
    h("h1.page-title", "Live TV"),
    h("div.chips", FILTERS.map(([k, label]) => chip(label, filter === k, () => { filter = k; navigate("#/live"); }))),
    !channels.length
      ? (filter === "all"
        ? empty("Live TV isn't set up yet", "In Jellyfin, open Dashboard → Live TV and add a tuner: an HDHomeRun for antenna channels, or an M3U playlist for free streaming channels. Then add a guide (XMLTV) so you can see what's on.")
        : h("p.hint", "No channels in this category."))
      : h("div.guide", channels.map((ch) => channelRow(ch, byChannel[ch.Id] || (ch.CurrentProgram ? [ch.CurrentProgram] : [])))));
}

function channelRow(ch, programs) {
  let fav = !!ch.UserData?.IsFavorite;
  const favBtn = iconButton(fav ? "heartFill" : "heart", fav ? "Remove favorite" : "Favorite channel", async () => {
    fav = !fav;
    favBtn.replaceChildren(...iconButton(fav ? "heartFill" : "heart", "").childNodes);
    favBtn.classList.toggle("on", fav);
    try { await api.setFavorite(ch.Id, fav); } catch (e) { toast(e.message); }
  }, fav ? "on" : "");
  const list = programs.length ? programs : [null];
  return h("div.channel",
    h("button.ch-id", { type: "button", onclick: () => go.channel(ch), title: `Watch ${ch.Name}` },
      img(api.channelLogo(ch, 200), "contain ch-logo", ch.ChannelNumber || ch.Name.slice(0, 3)),
      h("span.ch-text", h("strong", ch.ChannelNumber || ""), h("span", ch.Name))),
    favBtn,
    h("div.programs", list.map((p, k) =>
      h(`button.program${k === 0 ? ".now" : ""}`, { type: "button", onclick: () => go.channel(ch) },
        h("span.p-when", k === 0 ? `On now${p?.EndDate ? " · until " + time(p.EndDate) : ""}` : p?.StartDate ? time(p.StartDate) : ""),
        h("strong", p?.Name || ch.Name),
        p?.EpisodeTitle ? h("span.card-sub", p.EpisodeTitle) : null,
        k === 0 ? progressBar(programProgress(p)) : null))));
}
