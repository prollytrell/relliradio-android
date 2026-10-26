# RelliRadio for Android

A native Android app for your Navidrome server, built with Kotlin, Jetpack Compose and Media3 (Google's official media player). It has the same cocoa-and-gold look as the web app, but runs as a real app. That gives you things a web app can't do:

- **Your RR logo in the status bar** and on the media notification, lock screen and quick settings player.
- **Android Auto**: browse Mixes, Albums, Playlists and Favorites, and search, from the car screen.
- Reliable background play, headphone and Bluetooth controls, and "resume" from the lock screen or car after the app was closed.
- Your phone's built-in equalizer, bass boost, virtualizer and volume boost, plus true pitch shifting.

## How the build works

You don't need Android Studio. Every time code is pushed to GitHub, **GitHub Actions** (free build servers) compiles the app and attaches `RelliRadio.apk` to a new GitHub Release. You download the APK on your phone and install it.

*Concept:* an **APK** is an Android app package, the same thing the Play Store installs, just delivered by you instead of Google. Android only accepts updates signed with the **same key** as the installed version, which is why the signing key below matters.

## One-time setup

1. **Create a private repository** on github.com named `relliradio-android` (no README, no license).
2. **Upload this folder** to it. Either connect GitHub to Claude (claude.ai → Settings → Connectors → GitHub) and ask me to push it, or on your Fedora PC:
   ```
   cd relliradio-android
   git init -b main
   git add .
   git commit -m "RelliRadio for Android"
   git remote add origin git@github.com:YOUR-USERNAME/relliradio-android.git
   git push -u origin main
   ```
3. **Add the signing key** as two repository secrets (repo → Settings → Secrets and variables → Actions → New repository secret). The names and values are in `relliradio-signing-secrets.txt`:
   - `RELLI_KEYSTORE_PASSWORD`
   - `RELLI_KEYSTORE_B64`

   Keep that file and `relliradio-signing.jks` somewhere safe (a password manager is ideal). If you lose them, future builds can't update the installed app; you'd have to uninstall and reinstall.
4. **Run the build**: repo → Actions → "Build APK" → Run workflow. It takes about 5–8 minutes the first time.

## Installing on your phone

1. On your phone, open the repo's **Releases** page and tap `RelliRadio.apk`.
2. Android asks to allow installs from your browser: **Settings → Allow from this source**, then go back and tap **Install**.
3. Open RelliRadio, enter your server address (`https://radio.prollytrell.duckdns.org`), your Navidrome username and password.
4. Allow notifications when asked; that's what shows the player in your status bar.

Updates: download the newer APK from Releases and install it over the old one. Your sign-in and settings stay.

**Android Auto:** sideloaded apps are hidden in Android Auto until you allow them once. In the Android Auto app settings, tap **Version** about 10 times to unlock developer settings, then ⋮ → **Developer settings** → turn on **Unknown sources**. RelliRadio then appears in the car's app list.

## Features

- Home with your logo, Track / Album / Decade / genre mixes, and widgets you can turn on, off and reorder (Favorite tracks, Recently added, Rediscover, Last played, Random artists, Random albums, Continue album listening, Most played tracks and albums, Playlists, Internet radio).
- Library: albums (grid or list, six sort orders), artists, all songs, playlists, genres, favorites, radio.
- Now Playing with the background and play button tinted from the album art, a cover / lyrics / visualizer switch (lyrics mode shows the visualizer when a song has no lyrics), waveform seek bar with the quality label, and the Symfonium-style toolbar: sleep timer, radio mix, speed and pitch, queue, visual switch, equalizer, output device.
- More actions: equalizer and DSP, speed, sleep timer, Smart flow and Smart queue, instant mix, radio mix, play next, add to queue, favorite, rate, add to playlist, go to album / artist / genre, lyrics, details, web search, Genius search, share.
- Smart queue (random, same genre, same artist, similar songs) and Smart flow (Double shot).
- Sleep timer: minutes, end of song, or end of queue, with fade-out.
- ReplayGain (track, album, auto) with preamp. Speed 0.5–2× and pitch ±12 semitones. Skip silence.
- Separate streaming quality for Wi-Fi and mobile data.
- Play counts sent to Navidrome; queue saved to the server so you can resume on another device.

## Planned next

- Offline downloads
- Home-screen widget
- Chromecast (Google Cast) button
- Crossfade

## Good to know

- **Visualizer permission.** Android's visualizer needs "microphone" permission even though it only reads RelliRadio's own audio. Nothing is recorded. It's only asked the first time you open the visualizer.
- **The seek-bar waveform is decorative.** Navidrome doesn't provide real waveforms, so each song gets its own fixed shape.
- **Equalizer bands** come from your phone, usually 5 bands. Some phones offer more.
- **Password storage.** Your Navidrome password is stored in the app's private storage on the phone, which other apps can't read. Only a salted token is sent over the network.
- **Plain http.** The app allows `http://` addresses (for example `http://192.168.1.10:4533` at home), but use the `https://` address whenever you can.

## Project layout

| Path | What it is |
| --- | --- |
| `app/src/main/java/com/relli/radio/data/` | Navidrome (Subsonic API) client, data models, settings |
| `.../playback/PlaybackService.kt` | The background player, notification, and Android Auto browser |
| `.../playback/PlayerHub.kt` | ReplayGain, speed and pitch, equalizer, sleep timer, Smart queue, play counts, saved queue |
| `.../playback/Mixes.kt` | Track, album, decade, genre, instant and Smart-queue mixes |
| `.../ui/` | Screens (Compose): home, library, album, artist, playlist, genre, search, settings, Now Playing, sheets |
| `.../ui/theme/Theme.kt` | Colors and fonts |
| `app/src/main/res/drawable/` | Your RR logo as vector drawings (app icon, status-bar icon) |
| `.github/workflows/build.yml` | The GitHub Actions build |
