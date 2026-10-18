# Third-party notices

DriveCinema is licensed under the **GNU General Public License v3.0** (see [`LICENSE`](LICENSE)).
It is built on the following projects. Each keeps its own licence.

## Additional permission (GPL-3.0, section 7)

As an additional permission under section 7 of the GNU GPL version 3, the copyright holder of DriveCinema allows
you to combine the Android app with the **Google Play services** client libraries
(`com.google.android.gms:play-services-auth`) and to convey the result, even though those libraries are not
distributed under GPL-compatible terms. The source code of DriveCinema itself stays covered by the GPL.

## Libraries in the apps

| Project | Used for | Licence |
| :--- | :--- | :--- |
| Kotlin standard library, kotlinx.coroutines | Language and concurrency (all apps) | Apache-2.0 |
| AndroidX: Core, Activity, Lifecycle, Navigation 3, Compose, Compose for TV, Room, SQLite | Android app and shared database | Apache-2.0 |
| AndroidX Media3 (ExoPlayer, OkHttp data source, UI) | Android video playback | Apache-2.0 |
| Compose Multiplatform (JetBrains) | Desktop user interface | Apache-2.0 |
| OkHttp (Square) | HTTP for Google Drive and TMDB | Apache-2.0 |
| AppAuth for Android (OpenID Foundation) | Google sign-in on Android | Apache-2.0 |
| Coil | Poster and backdrop images on Android | Apache-2.0 |
| JSON-java (`org.json`) | JSON parsing on desktop (built into Android) | Public domain |
| Bouncy Castle (`bcprov-jdk18on`) | SRP-6a phone ↔ TV pairing | Bouncy Castle Licence (MIT-style) |
| JNA (`jna-jpms`) | Linux keyring and libVLC access on desktop | Apache-2.0 or LGPL-2.1 (dual) |
| vlcj (Caprica Software) | Desktop video playback | GPL-3.0 |
| libVLC (VideoLAN) | Desktop video decoding. Linux: installed separately (`sudo apt install vlc`), not shipped with DriveCinema | LGPL-2.1-or-later |
| Google Play services auth | Google sign-in on Android TV | Android SDK / Google APIs terms (proprietary; see the additional permission above) |

## Data and services

- **TMDB:** This product uses the TMDB API but is not endorsed or certified by TMDB. Posters, backdrops and
  descriptions come from [The Movie Database](https://www.themoviedb.org/).
- **Google Drive:** DriveCinema reads your own Google Drive through the Google Drive API (read-only scope). It is not
  affiliated with or endorsed by Google.
