// Small DOM helpers, icons, formatting and shared pieces (cards, shelves, chips).

import * as api from "./api.js";

// h("div.class#id", { attrs, onclick }, ...children)
export function h(tag, props, ...children) {
  const [, name = "div", rest = ""] = tag.match(/^([a-z0-9-]*)(.*)$/i);
  const el = document.createElement(name || "div");
  for (const part of rest.match(/[.#][^.#]+/g) || []) {
    if (part[0] === ".") el.classList.add(part.slice(1));
    else el.id = part.slice(1);
  }
  if (props && (typeof props !== "object" || props instanceof Node || Array.isArray(props))) {
    children.unshift(props);
    props = null;
  }
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith("on") && typeof v === "function") el.addEventListener(k.slice(2), v);
    else if (k === "html") el.innerHTML = v;
    else if (k === "style" && typeof v === "object") Object.assign(el.style, v);
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const c of children.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

export const clear = (el) => { el.replaceChildren(); return el; };

const ICONS = {
  home: '<path d="M4 11l8-6.5 8 6.5"/><path d="M6 9.5V20h12V9.5"/><path d="M10 20v-5h4v5"/>',
  movie: '<rect x="3.5" y="5" width="17" height="14" rx="2.5"/><path d="M3.5 9h17M8 5l2 4M13 5l2 4"/>',
  live: '<rect x="3" y="7" width="18" height="12.5" rx="2.5"/><path d="M8 3l4 4 4-4"/><circle cx="12" cy="13.2" r="2" fill="currentColor"/>',
  search: '<circle cx="10.5" cy="10.5" r="6"/><path d="M15 15l5 5"/>',
  bookmark: '<path d="M6.5 4h11v16l-5.5-4-5.5 4z"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"/>',
  play: '<path d="M7 4.5v15l13-7.5z" fill="currentColor" stroke="none"/>',
  pause: '<rect x="6" y="4.5" width="4" height="15" rx="1" fill="currentColor" stroke="none"/><rect x="14" y="4.5" width="4" height="15" rx="1" fill="currentColor" stroke="none"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  arrow: '<path d="M9 5l7 7-7 7"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h.01"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  replay: '<path d="M4 12a8 8 0 1 0 2.4-5.7L4 8.5"/><path d="M4 4v4.5h4.5"/>',
  back10: '<path d="M4 12a8 8 0 1 0 2.4-5.7L4 8.5"/><path d="M4 4v4.5h4.5"/><text x="12.3" y="15.6" font-size="7.5" font-weight="800" text-anchor="middle" fill="currentColor" stroke="none" font-family="Manrope,sans-serif">10</text>',
  fwd30: '<path d="M20 12a8 8 0 1 1-2.4-5.7L20 8.5"/><path d="M20 4v4.5h-4.5"/><text x="11.7" y="15.6" font-size="7.5" font-weight="800" text-anchor="middle" fill="currentColor" stroke="none" font-family="Manrope,sans-serif">30</text>',
  next: '<path d="M5 5v14l10-7z" fill="currentColor" stroke="none"/><path d="M18 5v14"/>',
  cc: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="M10.5 10.3a2.2 2.2 0 1 0 0 3.4M17 10.3a2.2 2.2 0 1 0 0 3.4"/>',
  audio: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" fill="currentColor"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>',
  mute: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" fill="currentColor"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>',
  hd: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="M7 9v6M11 9v6M7 12h4M14 9v6h1.5a3 3 0 0 0 0-6z"/>',
  speed: '<path d="M4.5 16a8 8 0 1 1 15 0"/><path d="M12 13l4-4"/><circle cx="12" cy="13" r="1.2" fill="currentColor"/>',
  full: '<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>',
  unfull: '<path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"/>',
  heart: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z"/>',
  heartFill: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z" fill="currentColor"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
  logout: '<path d="M10 5H5v14h5"/><path d="M14 8l4 4-4 4M18 12H9"/>',
  left: '<path d="M15 5l-7 7 7 7"/>',
  right: '<path d="M9 5l7 7-7 7"/>',
};

export function icon(name, cls = "") {
  const span = document.createElement("span");
  span.className = `icon ${cls}`.trim();
  span.setAttribute("aria-hidden", "true");
  span.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${ICONS[name] || ""}</svg>`;
  return span;
}

export const iconButton = (name, label, onclick, cls = "") =>
  h(`button.icon-btn${cls ? "." + cls.split(" ").join(".") : ""}`, { type: "button", "aria-label": label, title: label, onclick }, icon(name));

export function button(text, ic, onclick, primary = false) {
  return h(`button.pill${primary ? ".primary" : ""}`, { type: "button", onclick }, ic ? icon(ic) : null, h("span", text));
}

export function chip(text, selected, onclick) {
  return h(`button.chip${selected ? ".on" : ""}`, { type: "button", onclick, "aria-pressed": selected ? "true" : "false" }, text);
}

let toastTimer;
export function toast(msg) {
  let t = document.querySelector(".toast");
  if (!t) { t = h("div.toast", { role: "status" }); document.body.append(t); }
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove("show"), 2800);
}

// ---------- Formatting ----------

export const msOf = (ticks) => (ticks || 0) / api.TICKS;

export function fmtRuntime(ms) {
  const m = Math.round(ms / 60000);
  return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m}m`;
}

export function fmtClock(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const hh = Math.floor(s / 3600), mm = Math.floor((s % 3600) / 60), ss = s % 60;
  return hh ? `${hh}:${String(mm).padStart(2, "0")}:${String(ss).padStart(2, "0")}` : `${mm}:${String(ss).padStart(2, "0")}`;
}

export const episodeCode = (i) => (i.IndexNumber != null ? `S${i.ParentIndexNumber ?? 1}:E${i.IndexNumber}` : null);

export function progressOf(i) {
  const u = i.UserData;
  if (!u) return 0;
  if (u.PlayedPercentage != null) return u.PlayedPercentage / 100;
  return i.RunTimeTicks ? (u.PlaybackPositionTicks || 0) / i.RunTimeTicks : 0;
}

export function timeLeft(i) {
  const pos = msOf(i.UserData?.PlaybackPositionTicks);
  if (!i.RunTimeTicks || pos <= 0) return null;
  return `${fmtRuntime(Math.max(60000, msOf(i.RunTimeTicks) - pos))} left`;
}

export function metaLine(i) {
  return [
    i.ProductionYear,
    i.OfficialRating,
    i.Type === "Series" ? (i.ChildCount ? `${i.ChildCount} season${i.ChildCount === 1 ? "" : "s"}` : null) : (i.RunTimeTicks ? fmtRuntime(msOf(i.RunTimeTicks)) : null),
    i.CommunityRating ? `★ ${i.CommunityRating.toFixed(1)}` : null,
  ].filter(Boolean).join("  ·  ");
}

// ---------- Pictures and cards ----------

export function img(url, cls = "", placeholder = "") {
  const wrap = h(`div.art${cls ? "." + cls.trim().split(/\s+/).join(".") : ""}`, placeholder ? h("span.art-text", placeholder) : null);
  if (url) {
    const im = h("img", { src: url, alt: "", loading: "lazy", decoding: "async", draggable: "false" });
    im.addEventListener("load", () => wrap.classList.add("loaded"));
    im.addEventListener("error", () => im.remove());
    wrap.append(im);
  }
  return wrap;
}

export function progressBar(p) {
  return p > 0.01 ? h("div.progress", h("div", { style: { width: `${Math.min(100, p * 100)}%` } })) : null;
}

// Where tapping something goes. Set by app.js (navigation and the player live there).
export const go = { open: () => {}, play: () => {}, channel: () => {} };

export function posterCard(i) {
  const u = i.UserData || {};
  return h("button.card.poster", { type: "button", onclick: () => go.open(i), title: i.Name },
    h("div.card-art", img(api.poster(i, 360), "", i.Name),
      u.Played ? h("span.badge", "Watched") : (i.Type === "Series" && u.UnplayedItemCount ? h("span.badge", u.UnplayedItemCount) : null),
      progressBar(progressOf(i))),
    h("span.card-title", i.Name),
    h("span.card-sub", i.ProductionYear || i.SeriesName || ""));
}

export function wideCard(i, { title, sub, onclick } = {}) {
  const s = sub ?? [i.Type === "Episode" ? `${episodeCode(i) || ""} · ${i.Name}` : null, timeLeft(i)].filter(Boolean).join(" · ");
  return h("button.card.wide", { type: "button", onclick: onclick || (() => go.play(i)), title: i.Name },
    h("div.card-art", img(api.landscape(i, 640), "", i.SeriesName || i.Name), progressBar(progressOf(i)), h("span.play-badge", icon("play"))),
    h("span.card-title", title || i.SeriesName || i.Name),
    s ? h("span.card-sub", s) : null);
}

export function programProgress(p) {
  if (!p?.StartDate || !p?.EndDate) return 0;
  const s = Date.parse(p.StartDate), e = Date.parse(p.EndDate);
  return e > s ? Math.min(1, Math.max(0, (Date.now() - s) / (e - s))) : 0;
}

export function liveCard(ch) {
  const p = ch.CurrentProgram;
  return h("button.card.wide.live", { type: "button", onclick: () => go.channel(ch), title: ch.Name },
    h("div.card-art.channel-art", img(api.channelLogo(ch, 320), "contain", ch.Name), h("span.live-tag", "LIVE"), progressBar(programProgress(p))),
    h("span.card-title", p?.Name || ch.Name),
    h("span.card-sub", [ch.ChannelNumber, ch.Name].filter(Boolean).join(" · ")));
}

// A titled, side-scrolling row, like every streaming app's home screen.
export function shelf(title, list, card, onTitle) {
  if (!list?.length) return null;
  const row = h("div.shelf-row", list.map(card));
  const scroll = (dir) => row.scrollBy({ left: dir * row.clientWidth * 0.85, behavior: "smooth" });
  return h("section.shelf",
    h("header.shelf-head",
      onTitle ? h("button.shelf-title.link", { type: "button", onclick: onTitle }, h("h2", title), h("span.see-all", "See all")) : h("h2.shelf-title", title),
      h("div.shelf-arrows", iconButton("left", "Scroll left", () => scroll(-1)), iconButton("right", "Scroll right", () => scroll(1)))),
    row);
}

export function spinner() { return h("div.spinner", { role: "status", "aria-label": "Loading" }); }

export function empty(title, text) {
  return h("div.empty", h("h2", title), text ? h("p", text) : null);
}
