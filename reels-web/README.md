# RelliReels

Rell's streaming app for Jellyfin: movies, shows, anime and live TV, in the same cocoa-and-gold look as RelliRadio.

| Folder | What | Built by |
| --- | --- | --- |
| `reels/` | Android phone + Android TV app (Kotlin, Compose, Media3 ExoPlayer) | `.github/workflows/reels.yml` → `reels-N` releases |
| `reels-desktop/` | Windows + Linux app (Electron around the web app) | `.github/workflows/reels-desktop.yml` → `reels-desktop-N` releases |
| `reels-web/app/` | The web app (plain HTML/CSS/JS, hls.js for converted streams) | Served by `reels-web/compose.yaml` |

## How playback works

1. The app asks Jellyfin `POST /Items/{id}/PlaybackInfo` with a *device profile*: the video/audio formats this device can play.
2. If the file fits, Jellyfin says **direct play**, and the app streams the original file (`/Videos/{id}/stream?static=true`). Best quality, almost no work for the server.
3. If not (an unsupported codec, or over your quality limit), Jellyfin **transcodes**: converts on the fly into HLS (a playlist of short H.264/AAC chunks). The new server's RTX 2060 Super can do this with its hardware encoder (NVENC) once you turn on NVIDIA hardware acceleration in Jellyfin's Dashboard → Playback.
4. Every 10 seconds the app reports the position (`/Sessions/Playing/Progress`). That's what powers Continue watching and "Pick up where you left off" on every device.

Text subtitles arrive as separate WebVTT files; picture subtitles (Blu-ray PGS) are burned into the video by the server.

## Web version

```
cd reels-web
docker compose up -d
```

Open `http://192.168.1.10:8089` and sign in with server address `http://192.168.1.10:8089/jellyfin` (or straight to `http://192.168.1.10:8096`).
