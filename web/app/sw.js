// Service worker: keeps the app itself available offline and loads it instantly.
// It never touches /rest/ requests (music streams and library data always come
// live from Navidrome), except cover art, which is cached to save data.
// Bump VERSION whenever you change any app file so phones pick up the update.

const VERSION = "relliradio-v7";
const OFFLINE_CACHE = "relliradio-offline"; // downloaded songs: never cleared on update
const ART_CACHE = "relliradio-art";
const SHELL = [
  "./",
  "index.html",
  "manifest.webmanifest",
  "css/app.css",
  "fonts/manrope.woff2",
  "icons/icon.svg",
  "icons/icon-192.png",
  "js/app.js",
  "js/api.js",
  "js/md5.js",
  "js/prefs.js",
  "js/ui.js",
  "js/player.js",
  "js/seek.js",
  "js/lyrics.js",
  "js/components.js",
  "js/nowplaying.js",
  "js/install.js",
  "js/audio.js",
  "js/pitch-worklet.js",
  "js/offline.js",
  "js/smart.js",
  "js/sleep.js",
  "js/waveform.js",
  "js/visualizer.js",
  "js/sheets.js",
  "js/handoff.js",
  "brand/logo.svg",
  "js/views/login.js",
  "js/views/home.js",
  "js/views/library.js",
  "js/views/detail.js",
  "js/views/search.js",
  "js/views/settings.js",
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION && k !== ART_CACHE && k !== OFFLINE_CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin) return;

  if (url.pathname.includes("/rest/")) {
    if (!url.pathname.endsWith("/getCoverArt") && !url.pathname.endsWith("/getCoverArt.view")) return;
    // Cover art: cache by image id + size, ignoring the login token in the URL.
    const key = `${url.origin}/art/${url.searchParams.get("id")}/${url.searchParams.get("size") || ""}`;
    e.respondWith(
      caches.open(ART_CACHE).then(async (cache) => {
        const hit = await cache.match(key);
        if (hit) return hit;
        const res = await fetch(e.request);
        if (res.ok) cache.put(key, res.clone());
        return res;
      })
    );
    return;
  }

  // App files: serve from cache, refresh the cache in the background.
  e.respondWith(
    caches.open(VERSION).then(async (cache) => {
      const hit = await cache.match(e.request, { ignoreSearch: true });
      const network = fetch(e.request)
        .then((res) => { if (res.ok) cache.put(e.request, res.clone()); return res; })
        .catch(() => hit);
      return hit || network;
    })
  );
});
