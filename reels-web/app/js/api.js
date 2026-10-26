// Everything that talks to your Jellyfin server.
// Jellyfin's API is plain HTTP + JSON. Each request carries an "Authorization" header naming this
// app and device, plus the access token Jellyfin hands out at sign-in (your password is never stored).

const KEY = "rellireels.session";
export const DESKTOP = !!window.relliDesktop;
export const TICKS = 10000; // Jellyfin counts time in 100-nanosecond "ticks": 10,000 per millisecond.

function deviceId() {
  let id = localStorage.getItem("rellireels.device");
  if (!id) { id = crypto.randomUUID ? crypto.randomUUID() : String(Math.random()).slice(2) + Date.now(); localStorage.setItem("rellireels.device", id); }
  return id;
}

const DEVICE = DESKTOP ? `RelliReels Desktop (${window.relliDesktop.platform})` : `RelliReels Web (${navigator.platform || "Browser"})`;

export function getSession() {
  try { return JSON.parse(localStorage.getItem(KEY)) || null; } catch { return null; }
}
export function setSession(s) { localStorage.setItem(KEY, JSON.stringify(s)); }
export function clearSession() {
  const s = getSession();
  if (s) setSession({ server: s.server, userName: s.userName });
}
export const signedIn = () => !!getSession()?.token;

const S = () => getSession() || {};

export class ServerError extends Error {
  constructor(message, status = 0) { super(message); this.status = status; }
}

function authHeader(token) {
  return `MediaBrowser Client="RelliReels", Device="${DEVICE.replace(/"/g, "")}", DeviceId="${deviceId()}", Version="1.0"` + (token ? `, Token="${token}"` : "");
}

function qs(params) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params || {})) if (v !== undefined && v !== null && v !== "") p.set(k, String(v));
  const s = p.toString();
  return s ? "?" + s : "";
}

async function call(method, path, { params, body, base = S().server, token = S().token } = {}) {
  let res;
  try {
    res = await fetch(base.replace(/\/$/, "") + path + qs(params), {
      method,
      headers: { Authorization: authHeader(token), Accept: "application/json", ...(body ? { "Content-Type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ServerError("Can't reach the server. At home? Check the address. Away? Turn on WireGuard.");
  }
  if (!res.ok) {
    throw new ServerError(
      res.status === 401 ? (path.includes("Authenticate") ? "Wrong username or password." : "Your sign-in expired. Sign in again.")
        : res.status === 403 ? "Your Jellyfin account isn't allowed to do that."
        : res.status === 404 ? "Not found on the server." : `The server answered ${res.status}.`, res.status);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

const get = (path, params) => call("GET", path, { params });
const U = () => S().userId;

export const FIELDS = "Overview,Genres,PrimaryImageAspectRatio,ChildCount,RecursiveItemCount,Taglines,DateCreated";

// ---------- Sign-in ----------

export function normalizeServer(input) {
  let a = input.trim().replace(/\/+$/, "");
  if (!/^https?:\/\//.test(a)) {
    const local = /^(\d{1,3}\.){3}\d{1,3}(:\d+)?(\/.*)?$/.test(a) || a.includes(":8096") || /\.local(:|$)/.test(a);
    a = (local ? "http://" : "https://") + a;
  }
  return a;
}

export const publicInfo = (base) => call("GET", "/System/Info/Public", { base, token: null });

export async function login(base, username, password) {
  return call("POST", "/Users/AuthenticateByName", { base, token: null, body: { Username: username, Pw: password } });
}

export async function quickConnectStart(base) {
  try { return await call("POST", "/QuickConnect/Initiate", { base, token: null }); }
  catch (e) {
    if (e.status === 401 || e.status === 403) throw new ServerError("Quick Connect is turned off on your server. Turn it on in Jellyfin's Dashboard → General.");
    return call("GET", "/QuickConnect/Initiate", { base, token: null });
  }
}
export const quickConnectState = (base, secret) => call("GET", "/QuickConnect/Connect", { base, token: null, params: { Secret: secret } });
export const quickConnectFinish = (base, secret) => call("POST", "/Users/AuthenticateWithQuickConnect", { base, token: null, body: { Secret: secret } });

export async function finishSignIn(base, auth) {
  let name = "Jellyfin";
  try { name = (await publicInfo(base)).ServerName || name; } catch {}
  setSession({ server: base, userId: auth.User.Id, userName: auth.User.Name, token: auth.AccessToken, serverName: name });
}

// ---------- Browsing ----------

export const views = async () => (await get(`/Users/${U()}/Views`)).Items || [];
export const resume = async (limit = 20) => (await get(`/Users/${U()}/Items/Resume`, { Limit: limit, MediaTypes: "Video", Fields: FIELDS, EnableTotalRecordCount: false })).Items || [];
export const nextUp = async (limit = 20, seriesId) => (await get("/Shows/NextUp", { UserId: U(), Limit: limit, Fields: FIELDS, SeriesId: seriesId, EnableResumable: false })).Items || [];
export const latest = (parentId, limit = 24) => get(`/Users/${U()}/Items/Latest`, { ParentId: parentId, Limit: limit, Fields: FIELDS, GroupItems: true });

export function items({ parentId, types = "Movie,Series", genre, sortBy = "SortName", sortOrder = "Ascending", start = 0, limit = 60, favorites, search, filters } = {}) {
  return get(`/Users/${U()}/Items`, {
    ParentId: parentId, IncludeItemTypes: types, Recursive: true, Genres: genre, SortBy: sortBy, SortOrder: sortOrder,
    StartIndex: start, Limit: limit, Fields: FIELDS, IsFavorite: favorites ? true : undefined, SearchTerm: search, Filters: filters, ImageTypeLimit: 1,
  });
}

export const item = (id) => get(`/Users/${U()}/Items/${id}`, { Fields: FIELDS + ",People,MediaStreams" });
export const seasons = async (seriesId) => (await get(`/Shows/${seriesId}/Seasons`, { UserId: U(), Fields: FIELDS })).Items || [];
export const episodes = async (seriesId, seasonId, startItemId, limit) =>
  (await get(`/Shows/${seriesId}/Episodes`, { UserId: U(), SeasonId: seasonId, StartItemId: startItemId, Limit: limit, Fields: FIELDS })).Items || [];
export async function nextEpisode(ep) {
  if (!ep.SeriesId) return null;
  const list = await episodes(ep.SeriesId, null, ep.Id, 2);
  return list[1] && list[1].Id !== ep.Id ? list[1] : null;
}
export const similar = async (id, limit = 16) => (await get(`/Items/${id}/Similar`, { UserId: U(), Limit: limit, Fields: FIELDS })).Items || [];
export const genres = async (parentId, types = "Movie,Series") => (await get("/Genres", { UserId: U(), ParentId: parentId, IncludeItemTypes: types, SortBy: "SortName" })).Items || [];
export const setFavorite = (id, on) => call(on ? "POST" : "DELETE", `/Users/${U()}/FavoriteItems/${id}`);
export const setPlayed = (id, on) => call(on ? "POST" : "DELETE", `/Users/${U()}/PlayedItems/${id}`);

// ---------- Live TV ----------

export async function channels(filter = "all", limit = 300) {
  const f = (k) => (filter === k ? true : undefined);
  return (await get("/LiveTv/Channels", {
    UserId: U(), AddCurrentProgram: true, EnableUserData: true, Limit: limit, SortBy: "SortName",
    IsFavorite: f("favorites"), IsMovie: f("movies"), IsNews: f("news"), IsSports: f("sports"), IsKids: f("kids"), IsSeries: f("series"),
  })).Items || [];
}
export async function programs(channelIds, hours = 4) {
  if (!channelIds.length) return [];
  const now = new Date();
  return (await get("/LiveTv/Programs", {
    UserId: U(), ChannelIds: channelIds.join(","), MinEndDate: now.toISOString(),
    MaxStartDate: new Date(now.getTime() + hours * 3600e3).toISOString(), SortBy: "StartDate", EnableImages: false, Fields: "Overview",
  })).Items || [];
}

// ---------- Playback ----------

// What this browser can play by itself. Anything else, the server converts on the fly (HLS, H.264 + AAC).
function deviceProfile(maxBitrate) {
  const v = document.createElement("video");
  const can = (t) => !!v.canPlayType(t) || (window.MediaSource && MediaSource.isTypeSupported(t));
  const video = ["h264"];
  if (can('video/mp4; codecs="hvc1.1.6.L93.B0"') || can('video/mp4; codecs="hev1.1.6.L93.B0"')) video.push("hevc");
  if (can('video/webm; codecs="vp9"')) video.push("vp9");
  if (can('video/mp4; codecs="av01.0.05M.08"')) video.push("av1");
  const audio = ["aac", "mp3"];
  if (can('audio/webm; codecs="opus"')) audio.push("opus");
  if (can('audio/flac') || can('audio/mp4; codecs="flac"')) audio.push("flac");
  if (can('audio/mp4; codecs="ac-3"')) audio.push("ac3");
  if (can('audio/mp4; codecs="ec-3"')) audio.push("eac3");
  return {
    Name: "RelliReels Web",
    MaxStreamingBitrate: maxBitrate,
    MaxStaticBitrate: maxBitrate,
    DirectPlayProfiles: [
      { Container: "mp4,m4v", Type: "Video", VideoCodec: video.join(","), AudioCodec: audio.join(",") },
      { Container: "webm", Type: "Video", VideoCodec: "vp8,vp9,av1", AudioCodec: "vorbis,opus" },
    ],
    TranscodingProfiles: [
      { Container: "ts", Type: "Video", VideoCodec: "h264", AudioCodec: "aac", Protocol: "hls", Context: "Streaming", MaxAudioChannels: "2", MinSegments: 1, BreakOnNonKeyFrames: true },
    ],
    ContainerProfiles: [],
    CodecProfiles: [],
    SubtitleProfiles: [
      { Format: "vtt", Method: "External" },
      { Format: "pgssub", Method: "Encode" },
      { Format: "dvdsub", Method: "Encode" },
    ],
  };
}

export function playbackInfo(id, { startMs = 0, maxBitrate, audioIndex, subtitleIndex, mediaSourceId, live = false, allowDirect = true }) {
  return call("POST", `/Items/${id}/PlaybackInfo`, {
    params: { UserId: U() },
    body: {
      UserId: U(), MaxStreamingBitrate: maxBitrate, StartTimeTicks: Math.round(startMs * TICKS),
      AudioStreamIndex: audioIndex ?? undefined, SubtitleStreamIndex: subtitleIndex ?? undefined, MediaSourceId: mediaSourceId ?? undefined,
      AutoOpenLiveStream: live, EnableDirectPlay: allowDirect, EnableDirectStream: allowDirect, EnableTranscoding: true,
      AllowVideoStreamCopy: true, AllowAudioStreamCopy: true, DeviceProfile: deviceProfile(maxBitrate),
    },
  });
}

export function directUrl(id, source, playSessionId) {
  return S().server + `/Videos/${id}/stream` + qs({
    static: true, MediaSourceId: source.Id || id, PlaySessionId: playSessionId, DeviceId: deviceId(),
    LiveStreamId: source.LiveStreamId, api_key: S().token,
  });
}

export const absolute = (path) => (/^https?:/.test(path) ? path : S().server + path);
export const report = (kind, body) => call("POST", "/Sessions/Playing" + (kind ? "/" + kind : ""), { body }).catch(() => {});
export const closeLiveStream = (liveStreamId) => call("POST", "/LiveStreams/Close", { params: { liveStreamId } }).catch(() => {});
export const segments = async (id) => { try { return (await get(`/MediaSegments/${id}`)).Items || []; } catch { return []; } };

// ---------- Images ----------

export function image(id, type = "Primary", tag, width = 400, index) {
  if (!id) return null;
  return S().server + `/Items/${id}/Images/${type}` + (index != null ? `/${index}` : "") + qs({ tag, maxWidth: width, quality: 90 });
}

export function poster(i, width = 400) {
  const t = i.ImageTags || {};
  if (i.Type === "Episode" && i.SeriesId) return image(i.SeriesId, "Primary", i.SeriesPrimaryImageTag, width);
  if (t.Primary) return image(i.Id, "Primary", t.Primary, width);
  if (i.SeriesId) return image(i.SeriesId, "Primary", i.SeriesPrimaryImageTag, width);
  return image(i.Id, "Primary", null, width);
}

export function backdrop(i, width = 1280) {
  if (i.BackdropImageTags?.length) return image(i.Id, "Backdrop", i.BackdropImageTags[0], width, 0);
  if (i.ParentBackdropItemId && i.ParentBackdropImageTags?.length) return image(i.ParentBackdropItemId, "Backdrop", i.ParentBackdropImageTags[0], width, 0);
  return null;
}

export function landscape(i, width = 640) {
  const t = i.ImageTags || {};
  if (i.Type === "Episode" && t.Primary) return image(i.Id, "Primary", t.Primary, width);
  if (t.Thumb) return image(i.Id, "Thumb", t.Thumb, width);
  if (i.ParentThumbItemId) return image(i.ParentThumbItemId, "Thumb", i.ParentThumbImageTag, width);
  return backdrop(i, width);
}

export function logo(i, width = 600) {
  const t = i.ImageTags || {};
  if (t.Logo) return image(i.Id, "Logo", t.Logo, width);
  if (i.ParentLogoItemId) return image(i.ParentLogoItemId, "Logo", i.ParentLogoImageTag, width);
  return null;
}

export function channelLogo(i, width = 240) {
  if (i.Type === "TvChannel") return image(i.Id, "Primary", i.ImageTags?.Primary, width);
  return i.ChannelId ? image(i.ChannelId, "Primary", i.ChannelPrimaryImageTag, width) : null;
}
