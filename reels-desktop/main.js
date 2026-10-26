// RelliReels for Windows and Linux.
// A small Electron shell around the same app as the web version (reels-web/app), so both stay in sync.
// It talks straight to Jellyfin (http://192.168.1.10:8096 by default), no extra server needed.

const { app, BrowserWindow, protocol, net, shell, Menu } = require("electron");
const path = require("node:path");
const fs = require("node:fs");
const { pathToFileURL } = require("node:url");

const APP_DIR = path.join(__dirname, "app");

// The app's files are served from app://rellireels/ — a private, secure address inside the program.
protocol.registerSchemesAsPrivileged([
  { scheme: "app", privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true } },
]);

// One copy at a time: opening it again brings the existing window forward.
if (!app.requestSingleInstanceLock()) app.quit();

let win = null;
const stateFile = () => path.join(app.getPath("userData"), "window.json");

function loadBounds() {
  try { return JSON.parse(fs.readFileSync(stateFile(), "utf8")); } catch { return { width: 1320, height: 860 }; }
}

function saveBounds() {
  if (!win || win.isDestroyed()) return;
  try { fs.writeFileSync(stateFile(), JSON.stringify({ ...win.getNormalBounds(), maximized: win.isMaximized() })); } catch {}
}

function createWindow() {
  const b = loadBounds();
  win = new BrowserWindow({
    ...b,
    minWidth: 900,
    minHeight: 620,
    title: "RelliReels",
    backgroundColor: "#1b1410",
    autoHideMenuBar: true,
    icon: path.join(__dirname, "build", "icon.png"),
    show: false,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      // Lets the app talk to your Jellyfin server directly (videos, subtitles and pictures)
      // without needing Caddy in between.
      webSecurity: false,
      backgroundThrottling: false,
    },
  });
  if (b.maximized) win.maximize();
  win.once("ready-to-show", () => win.show());
  win.on("close", saveBounds);

  // Links open in your normal browser.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", (e, url) => {
    if (!url.startsWith("app://")) { e.preventDefault(); shell.openExternal(url); }
  });

  win.loadURL("app://rellireels/index.html");
}

app.on("second-instance", () => {
  if (!win) return;
  if (win.isMinimized()) win.restore();
  win.focus();
});

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  protocol.handle("app", (request) => {
    const { pathname } = new URL(request.url);
    const file = path.normalize(path.join(APP_DIR, decodeURIComponent(pathname)));
    if (!file.startsWith(APP_DIR)) return new Response("Not found", { status: 404 });
    return net.fetch(pathToFileURL(file).toString());
  });
  createWindow();
  app.on("activate", () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on("window-all-closed", () => app.quit());
