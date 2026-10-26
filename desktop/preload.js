// Tells the app it's running as the desktop program (see web/app/js/api.js and audio.js).
const { contextBridge } = require("electron");
contextBridge.exposeInMainWorld("relliDesktop", { platform: process.platform });
