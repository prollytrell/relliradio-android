// Small DOM helpers, icons, and shared UI pieces.

import { coverUrl } from "./api.js";

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
    else if (k === "dataset") Object.assign(el.dataset, v);
    else el.setAttribute(k, v === true ? "" : v);
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
}

export function clear(el) {
  while (el.firstChild) el.firstChild.remove();
  return el;
}

// ---------- Icons (24px, stroke-based) ----------

const ICONS = {
  play: '<path d="M7 4.5v15l13-7.5z" fill="currentColor" stroke="none"/>',
  pause: '<rect x="6" y="4.5" width="4" height="15" rx="1" fill="currentColor" stroke="none"/><rect x="14" y="4.5" width="4" height="15" rx="1" fill="currentColor" stroke="none"/>',
  next: '<path d="M5 5v14l10-7z" fill="currentColor" stroke="none"/><path d="M18 5v14"/>',
  prev: '<path d="M19 5v14L9 12z" fill="currentColor" stroke="none"/><path d="M6 5v14"/>',
  shuffle: '<path d="M3 7h3.5c2 0 3.2.9 4.3 2.6l2.4 4.8c1 1.7 2.3 2.6 4.3 2.6H21"/><path d="M3 17h3.5c1.4 0 2.4-.4 3.2-1.2M14.3 8.2C15.1 7.4 16.1 7 17.5 7H21"/><path d="M18 4l3 3-3 3M18 14l3 3-3 3"/>',
  repeat: '<path d="M4 11V9a3 3 0 0 1 3-3h13"/><path d="M17 3l3 3-3 3"/><path d="M20 13v2a3 3 0 0 1-3 3H4"/><path d="M7 21l-3-3 3-3"/>',
  repeatOne: '<path d="M4 11V9a3 3 0 0 1 3-3h13"/><path d="M17 3l3 3-3 3"/><path d="M20 13v2a3 3 0 0 1-3 3H4"/><path d="M7 21l-3-3 3-3"/><path d="M11.5 10.5l1.5-1v5"/>',
  heart: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z"/>',
  heartFill: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z" fill="currentColor"/>',
  more: '<circle cx="5.5" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="18.5" cy="12" r="1.6" fill="currentColor" stroke="none"/>',
  queue: '<path d="M4 6h12M4 11h12M4 16h7"/><path d="M15 15.5v4l4-2z" fill="currentColor"/>',
  lyrics: '<path d="M5 5h14v10H9l-4 4z"/><path d="M9 9h6M9 12h4"/>',
  search: '<circle cx="10.5" cy="10.5" r="6"/><path d="M15 15l5 5"/>',
  home: '<path d="M4 11l8-6.5 8 6.5"/><path d="M6 9.5V20h12V9.5"/><path d="M10 20v-5h4v5"/>',
  library: '<rect x="4" y="4" width="4" height="16" rx="1"/><rect x="10" y="4" width="4" height="16" rx="1"/><path d="M16.5 5.2l3.5 14.3"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"/>',
  volume: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" fill="currentColor"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>',
  mute: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z" fill="currentColor"/><path d="M16 9.5l5 5M21 9.5l-5 5"/>',
  down: '<path d="M6 9l6 6 6-6"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  radio: '<rect x="3" y="8" width="18" height="12" rx="2"/><path d="M7 8l9-5"/><circle cx="16" cy="14" r="2.5"/><path d="M6 12.5h4M6 15.5h4"/>',
  playlist: '<path d="M4 6h11M4 11h11M4 16h6"/><path d="M18 13v7M14.5 16.5h7"/>',
  disc: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2"/>',
  person: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/>',
  tag: '<path d="M4 4h7l9 9-7 7-9-9z"/><circle cx="8.5" cy="8.5" r="1.3" fill="currentColor"/>',
  install: '<path d="M12 4v11M7 10l5 5 5-5"/><path d="M5 19h14"/>',
  logout: '<path d="M10 5H5v14h5"/><path d="M14 8l4 4-4 4M18 12H9"/>',
  moon: '<path d="M19 15.5A8 8 0 0 1 8.5 5a8 8 0 1 0 10.5 10.5z"/>',
  gauge: '<path d="M4.5 16a8 8 0 1 1 15 0"/><path d="M12 13l4-4"/><circle cx="12" cy="13" r="1.2" fill="currentColor"/>',
  up: '<path d="M6 13l6-6 6 6M6 19l6-6 6 6"/>',
  eq: '<path d="M4 10v4M8 6v12M12 3v18M16 7v10M20 10v4"/>',
  cast: '<path d="M3 17a4 4 0 0 1 4 4M3 13a8 8 0 0 1 8 8M3 9.5V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-5"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h.01"/>',
  web: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.5 2.6 3.5 5.5 3.5 8.5s-1 5.9-3.5 8.5c-2.5-2.6-3.5-5.5-3.5-8.5s1-5.9 3.5-8.5z"/>',
  share: '<circle cx="6" cy="12" r="2.5"/><circle cx="17" cy="6" r="2.5"/><circle cx="17" cy="18" r="2.5"/><path d="M8.2 10.8l6.6-3.6M8.2 13.2l6.6 3.6"/>',
  upload: '<path d="M12 16V4M7 9l5-5 5 5"/><path d="M5 20h14"/>',
  cloud: '<path d="M7 18a4.5 4.5 0 1 1 .9-8.9A6 6 0 0 1 19 11a3.5 3.5 0 0 1-.5 7H7z"/><path d="M12 11v6M9.5 14.5L12 17l2.5-2.5"/>',
  cloudDone: '<path d="M7 18a4.5 4.5 0 1 1 .9-8.9A6 6 0 0 1 19 11a3.5 3.5 0 0 1-.5 7H7z"/><path d="M9.5 13.5l2 2 3.5-3.5"/>',
  star: '<path d="M12 4l2.4 5 5.4.6-4 3.7 1.1 5.3L12 16l-4.9 2.6 1.1-5.3-4-3.7 5.4-.6z"/>',
  starFill: '<path d="M12 4l2.4 5 5.4.6-4 3.7 1.1 5.3L12 16l-4.9 2.6 1.1-5.3-4-3.7 5.4-.6z" fill="currentColor"/>',
  mix: '<path d="M3 5h8l-4 5zM7 10v7M5 17h4"/><path d="M15 17V6l5-1.5v10"/><circle cx="13.5" cy="17" r="1.6" fill="currentColor"/><circle cx="18.5" cy="14.5" r="1.6" fill="currentColor"/>',
  calendar: '<rect x="4" y="5" width="16" height="15" rx="2.5"/><path d="M4 10h16M8 3v4M16 3v4"/><path d="M8.5 14h.01M12 14h.01M15.5 14h.01" stroke-width="2.4"/>',
  smart: '<path d="M20 12a8 8 0 0 1-13.7 5.6M4 12a8 8 0 0 1 13.7-5.6" stroke-dasharray="3 2.4"/><path d="M10 9v6l5-3z" fill="currentColor"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13 7l4 4"/>',
  grid: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
  list: '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4.5 6h.01M4.5 12h.01M4.5 18h.01" stroke-width="2.6"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  note: '<path d="M10 17V5l9-2v12"/><circle cx="7.5" cy="17" r="2.5"/><circle cx="16.5" cy="15" r="2.5"/>',
  visual: '<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M7 15v-2M10 15v-5M13 15V8M16 15v-3"/>',
  image: '<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><circle cx="9" cy="10" r="1.8"/><path d="M4 17l5-4.5 4 3.5 3-2.5 4 3.5"/>',
  arrow: '<path d="M9 5l7 7-7 7"/>',
  trash: '<path d="M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13"/>',
  drag: '<path d="M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01" stroke-width="2.8"/>',
};

export function icon(name, cls = "") {
  const span = document.createElement("span");
  span.className = `icon ${cls}`.trim();
  span.setAttribute("aria-hidden", "true");
  span.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${ICONS[name] || ""}</svg>`;
  return span;
}

export function iconButton(name, label, onclick, cls = "") {
  return h(`button.icon-btn${cls ? "." + cls.split(" ").join(".") : ""}`, { type: "button", "aria-label": label, title: label, onclick }, icon(name));
}

// ---------- Formatting ----------

export function fmtTime(sec) {
  if (!isFinite(sec) || sec < 0) sec = 0;
  sec = Math.floor(sec);
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  return h ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}` : `${m}:${String(s).padStart(2, "0")}`;
}

export function fmtLength(sec) {
  const m = Math.round((sec || 0) / 60);
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)} hr ${m % 60} min`;
}

export function plural(n, word) {
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}

// ---------- Cover art ----------

export function cover(id, size = 300, cls = "") {
  const wrap = h(`div.cover${cls ? "." + cls : ""}`);
  if (id) {
    const img = h("img", { src: coverUrl(id, size), alt: "", loading: "lazy", decoding: "async", draggable: "false" });
    img.addEventListener("error", () => img.remove());
    img.addEventListener("load", () => wrap.classList.add("loaded"));
    wrap.append(img);
  }
  return wrap;
}

// ---------- Logo ----------
// Drop your logo into app/brand/ as logo.svg (best) or logo.png, square, on a
// transparent background. Until then a labeled placeholder shows in its spot.

const LOGO_FILES = ["brand/logo.svg", "brand/logo.png"];

export function logo(cls = "") {
  const wrap = h(`div.logo${cls ? "." + cls : ""}`);
  const placeholder = () => wrap.replaceChildren(h("span.logo-placeholder", { title: "Logo placeholder: add app/brand/logo.svg" }, "RR"));
  const tryFile = (i) => {
    if (i >= LOGO_FILES.length) return placeholder();
    const img = h("img", { src: LOGO_FILES[i], alt: "RelliRadio", draggable: "false" });
    img.addEventListener("error", () => tryFile(i + 1), { once: true });
    wrap.replaceChildren(img);
  };
  tryFile(0);
  return wrap;
}

// ---------- Sheets (bottom sheets on phones, dialogs on desktop) ----------

let sheetStack = [];
// close(true) skips the history step; use it when another sheet opens right after.
export function openSheet({ title, ic = "note", body, cls = "" }) {
  const close = (handOff) => {
    overlay.classList.remove("open");
    setTimeout(() => overlay.remove(), 200);
    sheetStack = sheetStack.filter((s) => s !== api);
    if (!handOff && history.state?.sheet) history.back();
  };
  const panel = h(`div.sheet${cls ? "." + cls : ""}`, { role: "dialog", "aria-modal": "true", "aria-label": title },
    h("div.sheet-grip"),
    h("header.sheet-head", icon(ic), h("h2", title), iconButton("close", "Close", () => close(), "sheet-close")),
    h("div.sheet-body", body)
  );
  const overlay = h("div.sheet-overlay", { onclick: (e) => { if (e.target === overlay) close(); } }, panel);
  document.body.append(overlay);
  requestAnimationFrame(() => overlay.classList.add("open"));
  if (history.state?.sheet) history.replaceState({ sheet: true }, "");
  else history.pushState({ sheet: true }, "");
  const api = { close, el: panel, overlay };
  sheetStack.push(api);
  panel.querySelector("button, input, select")?.focus();
  return api;
}

window.addEventListener("popstate", () => {
  const top = sheetStack[sheetStack.length - 1];
  if (top && !history.state?.sheet) {
    sheetStack.pop();
    top.overlay.classList.remove("open");
    setTimeout(() => top.overlay.remove(), 200);
  }
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && sheetStack.length) { e.stopPropagation(); sheetStack[sheetStack.length - 1].close(); }
}, true);

// Big rounded action row, as in Symfonium's "More actions" sheet.
export function actionRow(ic, label, onclick, detail) {
  return h("button.action-row", { type: "button", onclick },
    icon(ic), h("span.action-label", label), detail ? h("span.action-detail", detail) : null);
}

// Labeled choice chips (single choice).
export function chips(options, value, onchange) {
  const wrap = h("div.chips", { role: "radiogroup" });
  const render = (v) => wrap.replaceChildren(...options.map(([val, label]) =>
    h("button.chip", { type: "button", role: "radio", "aria-checked": String(val === v), onclick: () => { render(val); onchange(val); } }, label)));
  render(value);
  return wrap;
}

export function toggle(label, checked, onchange, hint) {
  const input = h("input", { type: "checkbox", role: "switch", checked, onchange: () => onchange(input.checked) });
  return h("label.toggle-row", h("span.toggle-text", h("span", label), hint ? h("span.hint", hint) : null), h("span.switch", input, h("span.switch-ui")));
}

// ---------- Toasts ----------

let toastTimer;
export function toast(message) {
  let el = document.querySelector(".toast");
  if (!el) {
    el = h("div.toast", { role: "status", "aria-live": "polite" });
    document.body.append(el);
  }
  el.textContent = message;
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 2600);
}

// ---------- Popover menu ----------
// items: [{ label, icon, onclick }] ; anchor: element the menu opens from

let openMenu = null;
export function closeMenu() {
  if (openMenu) { openMenu.remove(); openMenu = null; }
}

export function showMenu(anchor, items, title) {
  closeMenu();
  const list = h("div.menu-list", { role: "menu" },
    items.filter(Boolean).map((it) =>
      h("button.menu-item", {
        type: "button", role: "menuitem",
        onclick: (e) => { e.stopPropagation(); closeMenu(); it.onclick(); },
      }, it.icon ? icon(it.icon) : null, h("span", it.label))
    )
  );
  const menu = h("div.menu", { onclick: (e) => { if (e.target === menu) closeMenu(); } },
    h("div.menu-sheet", title ? h("div.menu-title", title) : null, list)
  );
  document.body.append(menu);
  openMenu = menu;

  if (window.matchMedia("(min-width: 900px)").matches && anchor) {
    const r = anchor.getBoundingClientRect();
    const sheet = menu.firstChild;
    sheet.classList.add("floating");
    const w = 240;
    const left = Math.min(window.innerWidth - w - 12, Math.max(12, r.right - w));
    sheet.style.left = `${left}px`;
    const below = r.bottom + 6;
    const height = sheet.offsetHeight;
    sheet.style.top = `${below + height > window.innerHeight - 12 ? Math.max(12, r.top - height - 6) : below}px`;
  }
  list.querySelector("button")?.focus();
  return menu;
}

document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeMenu(); });

// ---------- Simple prompt dialog ----------

export function promptDialog({ title, label, placeholder = "", confirm = "Save" }) {
  return new Promise((resolve) => {
    const input = h("input.field", { type: "text", placeholder, "aria-label": label });
    const done = (value) => { overlay.remove(); resolve(value); };
    const form = h("form.dialog", {
      onsubmit: (e) => { e.preventDefault(); done(input.value.trim() || null); },
    },
      h("h2", title),
      h("label.field-label", label, input),
      h("div.dialog-actions",
        h("button.btn.ghost", { type: "button", onclick: () => done(null) }, "Cancel"),
        h("button.btn.primary", { type: "submit" }, confirm)
      )
    );
    const overlay = h("div.overlay", { onclick: (e) => { if (e.target === overlay) done(null); } }, form);
    document.body.append(overlay);
    input.focus();
  });
}

// ---------- Infinite scroll helper ----------

export function onNearBottom(sentinel, load) {
  const io = new IntersectionObserver(async (entries) => {
    if (!entries[0].isIntersecting) return;
    io.unobserve(sentinel);
    const more = await load();
    if (more && sentinel.isConnected) io.observe(sentinel);
  }, { rootMargin: "600px" });
  io.observe(sentinel);
  return io;
}
