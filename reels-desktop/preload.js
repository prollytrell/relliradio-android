// Tells the app it's running as the desktop program (see reels-web/app/js/api.js).
const { contextBridge } = require("electron");
contextBridge.exposeInMainWorld("relliDesktop", { platform: process.platform });
