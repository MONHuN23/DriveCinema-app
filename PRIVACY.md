# DriveCinema Privacy Policy

*Last updated: 18 October 2026*

DriveCinema is a free, open-source video player for the Android phone, Android TV and desktop. It plays films and
series that you keep in your own Google Drive. DriveCinema has **no server**: the apps talk directly to Google and
to TMDB from your device. The developer receives none of your data.

## Data DriveCinema accesses

**From your Google Account**, only after you sign in and agree on Google's consent screen:
- **Google Drive, read-only** (`https://www.googleapis.com/auth/drive.readonly`). DriveCinema lists the folders and
  video files in the library folder you choose, and streams those videos to play them. It never creates, changes,
  moves, deletes or shares anything in your Drive.
- **Your name, email address and profile picture**, to show which account is signed in.

**On your device:** if you add local folders, DriveCinema reads the video files in them, again read-only.

## Data stored, and where

Everything stays **on your device**:
- The sign-in token, encrypted: the Android Keystore on Android, the system keyring (or a private file) on desktop.
- The library catalog (titles, seasons, episodes, artwork links) and your watch progress, in a local database.
- Cached posters and backdrops.

Signing out deletes the sign-in token. Uninstalling the app deletes everything it stored.

## Data shared

- **Google:** your sign-in and Drive requests go to Google, under
  [Google's Privacy Policy](https://policies.google.com/privacy).
- **TMDB (The Movie Database):** to find posters and descriptions, DriveCinema sends the **titles and years** it
  reads from your file names to TMDB's API. No account data is sent. See
  [TMDB's privacy policy](https://www.themoviedb.org/privacy-policy).
- **Pairing a TV:** when you pair your TV with your phone, the phone sends its sign-in token to the TV over your
  local network, encrypted, after you enter the PIN shown on the TV.

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
