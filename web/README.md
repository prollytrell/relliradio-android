# RelliRadio

Your own music app for Navidrome. It runs on the Pi, works in any desktop browser, and installs on your phone and computer like a regular app, with lock-screen controls and synced lyrics. No accounts, no tracking, no subscription. It talks only to your server.

## What's in this folder

| Path | What it is |
| --- | --- |
| `app/` | The app itself: plain HTML, CSS and JavaScript. No build step; edit a file and reload. |
| `Caddyfile` | Web server settings: serves `app/`, forwards `/rest/` to Navidrome, handles HTTPS. |
| `Dockerfile` | Builds Caddy with the DuckDNS plugin for certificates. |
| `compose.yaml` | Runs it all as one container. |
| `.env.example` | Template for your domain, DuckDNS token and Navidrome address. |

Inside `app/js/`:

| File | Job |
| --- | --- |
| `api.js` | Every call to Navidrome (Subsonic API), sign-in, lyrics, ratings, saved queue |
| `player.js` | Playback on two "decks" (crossfade, gapless), queue, ReplayGain, lock-screen controls, play counts |
| `audio.js` | Web Audio chain: equalizer, preamp, crossfeed, mono, boost, analyser for the visualizer |
| `pitch-worklet.js` | Pitch shifter that runs on the audio thread |
| `smart.js` | Smart queue, Smart flow, Instant/Radio/Track/Album/Decade mixes |
| `sleep.js` | Sleep timer (minutes, end of song, end of queue) with fade-out |
| `offline.js` | Downloads for offline listening |
| `waveform.js` | Real waveforms for the seek bar |
| `visualizer.js` | Audio visualizer (bars, wave, ring) |
| `sheets.js` | The pop-up sheets: More actions, Equalizer, Speed, Sleep, Smart queue, Details, Rate, Add to playlist |
| `seek.js`, `lyrics.js` | Seek bars; synced lyrics |
| `nowplaying.js` | Mini player, desktop player bar, and the Now Playing screen |
| `components.js` | Song rows, album tiles, shared buttons |
| `views/*.js` | One file per screen: home (widgets), library, album/artist/playlist, search, settings, sign-in |
| `css/app.css` | All styling. Colors and fonts are the variables at the top. |
| `sw.js` | Service worker: makes it installable and fast to open |

Your logo lives in `app/brand/logo.svg`; the home-screen icons in `app/icons/` were made from it (see `app/brand/README.txt`).

## How it fits together

```
 Phone / laptop ──HTTPS──▶ Caddy on the Pi (:443)
                              ├── /            → app files (app/)
                              └── /rest/...    → Navidrome (192.168.1.10:4533)
```

Concepts worth knowing:

- **Reverse proxy.** Caddy stands in front of Navidrome. Your phone only ever talks to Caddy, and Caddy passes `/rest/` requests along. That's why one address serves both the app and your music.
- **Same-origin policy and CORS.** Browsers block a page from reading data from a different "origin" (scheme + host + port) unless that server explicitly allows it. Serving the app and the API from one address sidesteps the problem entirely.
- **Why HTTPS matters here.** Browsers only allow service workers, and therefore app installs and offline loading, on HTTPS pages (or `localhost`). Plain `http://192.168.1.10` works in a browser tab but can't be installed.
- **DNS-01 challenge.** Let's Encrypt needs proof you control the name before issuing a certificate. Instead of opening a port so it can visit your server (the HTTP-01 challenge), Caddy writes a temporary TXT record through DuckDNS's API. Nothing new is exposed to the internet.
- **Split-horizon DNS.** On the internet, `prollytrell.duckdns.org` points to your public IP. Inside your network, Pi-hole answers `radio.prollytrell.duckdns.org` with the Pi's private address, 192.168.1.10. Same name, different answer depending on who asks. Over the VPN your phone uses Pi-hole too, so it gets the private address and the traffic goes through the tunnel.

## Setup

Do these on the Pi over SSH unless a step says otherwise.

### 1. Copy the folder to the Pi

From your Fedora PC, in the folder where you unzipped it:

```
rsync -avP relliradio/ terrell@192.168.1.10:~/relliradio/
```

### 2. Free up ports 80 and 443

Pi-hole's dashboard is using the standard web ports. Move it to 8081 (HTTP) and 8443 (HTTPS):

```
sudo pihole-FTL --config webserver.port "8081o,8443os,[::]:8081o,[::]:8443os"
sudo systemctl restart pihole-FTL
sudo ss -tlnp | grep -E ':(80|443|8081|8443)\b'
```

The last command should show `pihole-FTL` on 8081 and 8443, and nothing on 80 or 443. From now on the Pi-hole dashboard is at `http://192.168.1.10:8081/admin`.

*Concept:* only one program can listen on a given port at a time. `ss -tlnp` lists listening **TCP** sockets (`-t`, `-l`), with numbers instead of names (`-n`) and the owning program (`-p`).

### 3. Create your settings file

Regenerate your DuckDNS token first (duckdns.org, top of the page), since the old one was shared in chat. Update the token in your `crontab -e` line too.

```
cd ~/relliradio
cp .env.example .env
chmod 600 .env
nano .env
```

Fill in the token. `chmod 600` means only your user can read the file.

### 4. Point the name at the Pi inside your network

In Pi-hole, go to **Settings → Local DNS Records** and add:

- Domain: `radio.prollytrell.duckdns.org`
- IP: `192.168.1.10`

Check it from Fedora: `dig +short radio.prollytrell.duckdns.org` should print `192.168.1.10`.

### 5. Build and start

```
cd ~/relliradio
docker compose up -d --build
docker logs -f relliradio
```

The first build takes a few minutes on the Pi. In the log, wait for `certificate obtained successfully`, then press Ctrl+C. Getting the certificate can take a minute or two while DNS updates.

Quick test without HTTPS: open `http://192.168.1.10:8088` on your desktop.

### 6. Sign in and install

Open `https://radio.prollytrell.duckdns.org` and sign in with your Navidrome username and password. Then install it:

| Device | How |
| --- | --- |
| Fedora (Brave/Chrome) | Install icon at the right end of the address bar, or **Settings → Install** in RelliRadio |
| Android (Chrome) | Menu (⋮) → **Install app**. It becomes a real app in your app drawer. |
| iPhone (Safari) | Share → **Add to Home Screen** |

Away from home, turn on the WireGuard VPN first. The same address works because your phone's DNS goes through Pi-hole.

## Changing the app

- Files in `app/` are mounted straight into the container, so edits show up on reload. No rebuild needed.
- Installed copies cache the app. After you change anything, bump `VERSION` in `app/sw.js` (for example `relliradio-v4`) so phones pick up the new version. It applies after the app is closed and reopened.
- Colors and fonts: the variables at the top of `app/css/app.css`. Cocoa backgrounds are `--bg`, `--surface` and `--raise`; text is `--cream`; the gold is `--gold` and the metallic gradient `--gold-metal`.
- Logo: replace `app/brand/logo.svg`, and the PNGs in `app/icons/` for the home-screen icon. Bump `VERSION` in `app/sw.js` afterward.
- Changes to `Caddyfile` or `.env` need `docker compose up -d` (or `docker compose restart`).

## Features

Modeled on Symfonium's modern theme. Most of what Symfonium does is here; the gaps are listed at the end.

**Now Playing**
- Background and play button tinted from the album art; changes with every song.
- The visual area switches between album cover, synced lyrics, and an audio visualizer (button in the toolbar, or `L`). When a song has no lyrics, lyrics mode shows the visualizer instead.
- Waveform seek bar from the song's real shape, with elapsed time, quality (Lossless, MP3 320 kbps, …) and time left.
- Toolbar: sleep timer, radio mix, speed and pitch, queue, visual switch, equalizer, cast (where the browser supports it).
- More actions sheet: equalizer and DSP, speed, sleep timer, Smart flow/queue, instant mix, radio mix, favorite, rate, add to playlist, go to album/artist/genre, details, web/Genius search, lyrics and lyrics settings, offline download, share, share file.

**Playback**
- 10-band equalizer with 18 presets, your own saved presets, and preamp.
- ReplayGain (track, album, auto) with preamp and clipping protection.
- DSP: volume boost, headphone crossfeed, mono.
- Crossfade (0–12 s) or gapless.
- Speed 0.5×–2× and pitch ±12 semitones.
- Sleep timer: minutes, end of song, or end of queue, with a gentle fade.
- Smart queue (random, same genre, same artist, similar songs) keeps music going when the queue runs out. Smart flow (Shuffle specialist, Double shot, Artist fan, Era enthusiast) reshapes it as it plays.
- Instant mix and Radio mix from any song; Track, Album, Decade and genre mixes on Home.
- Internet radio stations you add in Navidrome.
- Downloads for offline listening (albums or single songs).
- Queue and position saved to Navidrome, so you can resume on another device (Settings → Resume).

**Home and library**
- Home shows your logo and widgets: Mixes, Favorite tracks, Recently added albums, Rediscover, Last played, Random artists, Random albums, Continue album listening, Most played tracks, Most played albums, Playlists, Internet radio, Downloaded. Tap the pencil to turn widgets on or off and reorder them.
- Library: albums (grid or list, 6 sort orders), artists, songs, playlists, genres, favorites, radio, downloads.
- Ratings (1–5 stars) and favorites sync to Navidrome.
- Keyboard: Space play/pause, Shift+→ / Shift+← next/previous, `/` search, `L` lyrics, `Q` queue.

**Not possible in a web app (or with Navidrome)**
- Android Auto, Wear OS, home-screen widgets, bit-perfect USB DAC output, and Chromecast casting that keeps working with the screen off. Those need a native Android app.
- Symfonium's "sonic analysis" mixes rely on Plex's audio analysis; Navidrome has no equivalent, so Radio mix uses Last.fm-style similar songs instead.
- The status-bar media chip on Android: the manifest includes a white RR "monochrome" icon, which Chrome uses where it can. On some phones the chip still shows Chrome's icon, because Chrome, not RelliRadio, posts that notification.
- On iPhone, the equalizer, pitch and visualizer are off by default (turning them on can stop music when the screen locks).
- Effects, waveforms and the visualizer need the app and Navidrome at the same address (the Caddy setup above).

## Troubleshooting

| Problem | Check |
| --- | --- |
| `docker compose up` says port 80 or 443 is in use | Step 2 didn't apply. Rerun the `ss` command to see what's holding it. |
| No certificate in the log | Token in `.env` is wrong or old. `docker compose restart` after fixing. |
| Browser says it can't find the site | Pi-hole Local DNS record missing, or your device isn't using Pi-hole for DNS. |
| Signs in but nothing loads | `NAVIDROME` in `.env` is wrong. On the Pi, `curl http://192.168.1.10:4533/rest/ping` should print a short reply (a login error in it is fine; it means Navidrome answered). |
| "Wrong username or password" | Use your Navidrome login, the same one as Navidrome's web page. |
| No lyrics | Put a `.lrc` file with the same name next to the song, then rescan in Navidrome. Lyrics mode shows the visualizer until then. |
| Equalizer or visualizer does nothing | Settings → Audio effects must be on, and you must be using the `https://radio…` address, not `:4533`. |
| Phone still shows the old version | Bump `VERSION` in `sw.js`, then fully close and reopen the app. |
