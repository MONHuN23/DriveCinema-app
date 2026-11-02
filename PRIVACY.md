# DriveCinema Privacy Policy

*Last updated: 27 October 2026*

DriveCinema is a free, open-source video player for the Android phone, Android TV and desktop. It plays films and
series that you keep in your own Google Drive, and, if you choose, in folders on your own devices. DriveCinema has
**no server**: the apps talk directly from your device to Google, to TMDB, to the intro-time services described
below, and to your own devices on your home network. The developer receives none of your data.

## Data DriveCinema accesses

**From your Google Account**, only after you sign in and agree on Google's consent screen:
- **Google Drive, read-only** (`https://www.googleapis.com/auth/drive.readonly`). DriveCinema lists the folders and
  video files in the library folder you choose, and streams those videos to play them. With this permission it
  can't create, change, move, delete or share anything in your Drive. The phone and TV apps only ever ask for this.
- **Google Drive, full access, desktop only and optional** (`https://www.googleapis.com/auth/drive`). The desktop app
  asks for it only when you turn on **Import to Drive**. It is used to find your library's Films and Series folders,
  create missing show and season folders in them, and upload new videos from the local folders you chose for import.
  DriveCinema never changes, moves, deletes or shares files that are already in your Drive. **Remove write access** on
  the import screen gives this permission back to Google (you then sign in again, read-only).
- **Your name, email address and profile picture**, to show which account is signed in.

**On your devices:** if you add local folders, DriveCinema reads the video files in them, read-only. On the desktop,
Import to Drive reads the files in the folders you chose for import in order to upload them; it never moves, renames
or deletes them. If you share your computer's local folders with your other devices (below), the computer serves
the video files in those folders to the devices you paired, read-only.

## Data stored, and where

Everything stays **on your devices**:
- The sign-in token: on Android encrypted with a key in the Android Keystore; on desktop in the system keyring
  (Windows Credential Manager, or the Secret Service keyring on Linux), or, when no keyring is available, in a
  file that only your user account can read.
- The library catalog (titles, seasons, episodes, artwork links, file sizes and video details) and your watch
  progress, in a local database.
- Cached posters and backdrops, and cached intro times (show, season, episode and the intro's start and end).
- On the desktop, when you use Import to Drive: the list of files to upload and their upload state.
- When you share a computer's folders: the computer keeps its own encryption key and certificate, and a list of the
  devices you paired (each device's name, pairing date, last use, and a one-way hash of its key, never the key
  itself), in files that only your user account can read. Each phone, TV or computer you paired keeps the computer's
  address, its certificate fingerprint and its own device key (on Android encrypted with a key in the Android
  Keystore; on desktop in a file that only your user account can read).

Signing out deletes the sign-in token on that device; your library and watch progress stay. Signing out doesn't
sign out your other devices, including a TV you paired. To end access everywhere, revoke it in your Google
Account (below).

Uninstalling the Android app deletes everything it stored. Uninstalling the desktop app keeps its data folder
(`~/.local/share/DriveCinema` on Linux, `%APPDATA%\DriveCinema` on Windows) and the keyring entry, so a reinstall
picks them up; delete them by hand to remove them.

## Data shared

- **Google:** your sign-in and Drive requests go to Google, under
  [Google's Privacy Policy](https://policies.google.com/privacy). Videos uploaded with Import to Drive go to your own
  Google Drive.
- **TMDB (The Movie Database):** to find posters and descriptions, DriveCinema sends the **titles and years** it
  reads from your file names to TMDB's API. No account data is sent. See
  [TMDB's privacy policy](https://www.themoviedb.org/privacy-policy). For Skip Intro (below), it also asks TMDB for
  each show's IMDb ID.
- **Intro times (SkipDB, IntroDB):** when you play an episode and "Skip Intro" is on, DriveCinema asks SkipDB
  (skipdb.tv) for that episode's intro times. It sends the show's IMDb ID, the season and episode number, and the
  video's length. If SkipDB has no usable data, the same question without the video length goes to IntroDB
  (introdb.app). Like any web request, each also reveals your IP address. Nothing else is sent: no file names, no
  account data, no watch history. Turn it off in Account → Skip Intro (phone/TV) or in the account menu → Skip intro
  (desktop).
- **Pairing a TV:** when you pair your TV with your phone, the phone sends its sign-in token to the TV over your
  local network, encrypted, after you enter the code shown on the TV. The TV can then read the same Drive library as
  the phone, read-only.
- **Sharing a computer's folders with your devices (off by default):** when you turn on **Share my local folders**
  in the desktop app, the computer answers DriveCinema's search on your home network with its name, and the devices
  you paired with it (with a 6-character code shown on the computer) can list and play the videos in the folders you
  share. Nothing else on the computer is visible to them, and no Google sign-in is shared with them. The connection
  is encrypted, and the computer only accepts devices on your home network, or through a VPN such as Tailscale if you
  turn on **Allow streaming over a VPN**. You can remove a paired device at any time (Share with my devices → Paired
  devices → Remove); turning sharing off stops the computer from listening.

DriveCinema does not sell or rent data, and has no ads, analytics or tracking. Nobody else receives your data.

## Google API Services User Data Policy

DriveCinema's use and transfer of information received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements.

## Revoking access

You can remove DriveCinema's access to your Google Account at any time at
[myaccount.google.com/permissions](https://myaccount.google.com/permissions).

## Children

DriveCinema is not directed at children under 13.

## Changes and contact

Changes to this policy are published on this page. Questions: open an issue on DriveCinema's GitHub repository.
