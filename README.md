# DriveCinema

**Your own Netflix-style player for the films and series in your Google Drive.** Android phone, Android TV and
desktop (Linux; Windows planned).

You put your videos in a Google Drive folder; DriveCinema turns them into a library with posters, descriptions,
seasons and episodes, and plays them. There is no server and no account other than your own Google account: the
apps talk directly to Google Drive (read-only) and to TMDB for artwork.

> **Status: beta.** Built and used by one person so far. Expect rough edges, and please report them.

## Features

- Library with posters, backdrops and descriptions (from TMDB), films and series with seasons.
- **Continue Watching**: one card per series, with **Up next** after you finish an episode.
- **Watched** marks on episodes; resume where you stopped, on the same device.
- **Next Episode** button (and the remote's Next key).
- **Android TV**: remote-friendly 10-foot UI. Sign in on the TV by **pairing with your phone** (PIN, local network
  only), no typing.
- Audio track choice; a clear warning when a device can't decode a video or audio format.
- Desktop: local folders as an extra source, merged with the Drive library (phone storage folders: experimental).
- Read-only: DriveCinema never changes, moves or uploads your files (the planned download-folder upload will be
  opt-in).

**Not yet:** subtitles (planned, see below), and watch progress is not synced between devices (each device keeps
its own).

## Coming next

Planned, not built yet, in no fixed order:

- **Subtitles:** `.srt` / `.vtt` files next to the video, plus subtitle tracks inside MKV files.
- **Sound for Dolby / DTS on phones:** a software audio decoder for AC-3, E-AC-3 and DTS tracks that some phones
  (e.g. Pixel) can't play today. Today such films play muted with a warning, or with another audio track.
- **Play from phone to TV:** pick a film on the phone and it starts on the paired TV, at the same position.
- **Desktop download folders → Google Drive:** finished downloads in folders you choose on the computer are
  uploaded to the right Drive folder (Films / Series / Season) automatically.
- **Stream from the desktop's local folders:** phones and TVs at home play videos stored on your computer directly
  over Wi-Fi, without uploading them to Drive first.
- **Windows desktop app** with an installer.

## 1. Prepare your Google Drive

Create a folder called **`Streaming`** with a **`Films`** and a **`Series`** folder inside:

```
Streaming/
├── Films/
│   ├── Dune Part Two (2024).mkv
│   └── Inception (2010)/
│       └── Inception.2010.1080p.mkv
└── Series/
    └── Breaking Bad/
        ├── Season 01/
        │   ├── Breaking Bad - S01E01.mkv
        │   └── Breaking Bad - S01E02.mkv
        └── Season 02/
            └── Breaking Bad - S02E01.mkv
```

- Films: `Title (Year).mkv`. The year is what makes the poster match.
- Episodes: anything with `S01E02` in the name.
- The apps contain the full **Setup guide** (folder layout, naming, formats, sharing a library with someone). More
  detail: [`DOWNLOAD_GUIDE.md`](DOWNLOAD_GUIDE.md).

## 2. Google sign-in

DriveCinema reads Drive with Google's `drive.readonly` permission. Google treats that as a **restricted** scope, so
the app isn't verified by Google, and you'll see a **"Google hasn't verified this app"** screen when you sign in:
choose **Advanced → Go to DriveCinema** to continue. DriveCinema only ever reads your Drive.

- **Ready-made downloads** (APK, `.deb`) use the developer's Google project. It is unverified, so Google allows
  **at most 100 users in total** on it. If sign-in says the limit is reached, build DriveCinema with your own keys
  (below): it takes about 15 minutes and then nothing depends on anyone else's project.

## 3. Install

### Android phone
1. Download `DriveCinema-<version>.apk` from the [Releases](../../releases) page.
2. Open it and allow installing from this source when Android asks.
3. Open DriveCinema → **Sign in with Google** → it finds your `Streaming` folder.

### Android TV / Google TV
> **"Sign in with Google" on the TV doesn't work yet.** Use **Pair with Phone**: sign in on the phone first,
> then pair the TV with it.

- Install the same APK, e.g. with the *Downloader* app, or from a computer:
  `adb connect <tv-ip>:<port>` then `adb install DriveCinema-<version>.apk`.
- On the TV choose **Pair with Phone** and enter the PIN shown on the TV in the phone app (Account → Pair TV). Both
  must be on the same Wi-Fi.

### Linux desktop (Debian/Ubuntu)
```bash
sudo apt install vlc
```
```bash
sudo apt install ./drivecinema_<version>_amd64.deb
```
VLC is required for playback. If it's missing, the app offers to install it.

### Windows
Planned (an `.msi` installer). Windows will show **"Windows protected your PC"** for the unsigned installer:
**More info → Run anyway**.

## Build with your own keys

Building it yourself means using your own Google project and TMDB key, with no shared user limit. All keys go in
`local.properties` in the project root. It is git-ignored: **never commit it**.

### 1. Google Cloud project (once, about 15 minutes)

1. Open [Google Cloud Console](https://console.cloud.google.com/) → **New project** (e.g. `DriveCinema`).
2. **APIs & Services → Library** → search **Google Drive API** → **Enable**.
3. **Google Auth Platform** (older consoles: *OAuth consent screen*):
   - **Branding:** app name `DriveCinema`, your email as support and developer contact.
   - **Audience:** user type **External**, then **Publish app** (status **In production**). In *Testing* mode you
     have to sign in again every 7 days.
   - **Data access → Add or remove scopes:** add `https://www.googleapis.com/auth/drive.readonly`.
   - You don't need Google's verification for yourself: you'll see "Google hasn't verified this app" once →
     **Advanced → Go to DriveCinema**.
4. **Clients → Create client**, twice:
   - **Android** (for phone and TV):
     - package name `com.drivecinema.app`;
     - **SHA-1** of the key that signs your build:
       - **Debug builds:** run `./gradlew :app:signingReport` and copy the `SHA1` of the `debug` variant.
       - **Release builds:** run `keytool -list -v -keystore <your.jks> -alias <alias>`.
       - Create one Android client per key if you use both.
     - If sign-in on the phone fails with a "custom URI scheme" error, open the client → **Advanced settings** →
       enable **Custom URI scheme**.
   - **Desktop app** (for the desktop app): copy its **Client ID** and **Client secret**.

### 2. TMDB key (posters and descriptions)

Create a free account on [themoviedb.org](https://www.themoviedb.org/) → **Settings → API** → request a key
(personal/non-commercial use). Either the *API Key* or the *API Read Access Token* works.

### 3. `local.properties`

```properties
GOOGLE_ANDROID_CLIENT_ID=1234567890-android.apps.googleusercontent.com
GOOGLE_DESKTOP_CLIENT_ID=1234567890-desktop.apps.googleusercontent.com
GOOGLE_DESKTOP_CLIENT_SECRET=GOCSPX-...
TMDB_API_KEY=your-tmdb-key
```

The same names also work as environment variables.

### 4. Build

Requirements: JDK 17 and the Android SDK (API 36); Android Studio brings both.

- **Phone / TV (debug build, installs over USB or wireless adb):**
  ```bash
  ./gradlew :app:installDebug
  ```
- **Release APK** (signed with your own key; set `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
  `KEY_PASSWORD` first; without them you get an unsigned APK that won't install):
  ```bash
  ./gradlew :app:assembleRelease
  ```
- **Desktop** (needs VLC: `sudo apt install vlc`): run it from source, or build and install the Linux package:
  ```bash
  ./gradlew :desktop:run
  ```
  ```bash
  ./gradlew :desktop:packageDeb
  ```
  ```bash
  sudo apt install ./desktop/build/compose/binaries/main/deb/*.deb
  ```
  The Desktop client ID and secret from `local.properties` are built into the package.
- **Unit tests:**
  ```bash
  ./gradlew testDebugUnitTest :shared:test :desktop:test
  ```

The TV can also sign in without its own Google sign-in: choose **Pair with Phone** on the TV.

## Privacy

- No DriveCinema server, no analytics, no ads.
- Your Google sign-in token stays on the device (Android Keystore; desktop: system keyring).
- Requests go only to Google (sign-in, Drive) and TMDB (titles and years of your files, to find artwork).
- Full privacy policy: [`PRIVACY.md`](PRIVACY.md).

## Licence and credits

DriveCinema is free software under the **GNU GPL v3.0** ([`LICENSE`](LICENSE)). Libraries and their licences:
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

This product uses the TMDB API but is not endorsed or certified by TMDB. DriveCinema is not affiliated with Google.
