# DriveCinema — Media Download & Setup Guide

**Target Audience:** Users of DriveCinema on Android phones and Android TV, and on the desktop app (Linux, Windows; see §3.3).  
**In the app:** the same guidance is built in as the **Help Center** — on the phone and TV **Settings → Help Center**, on the desktop **Help Center** in the account menu; **? Setup guide** on the folder list opens its "Set up your folders" topic, and **How to set up your Drive folders** on the sign-in screen opens the Help Center.  
**Document Purpose:** Practical, plain-language guidance on organizing personal media libraries, selecting compatible video and audio formats, configuring Google Drive, and understanding device-specific playback realities.

---

## 1. Quick Start: Recommended Media Profile

For the smoothest experience with zero buffering, instant playback, and predictable audio across both Android phones and Android TVs, use the following media profile:

| Media Property | Recommended Setting | Compatibility Status | Notes |
| :--- | :--- | :--- | :--- |
| **Container** | **MP4 (`.mp4`)** or **MKV (`.mkv`)** | **Verified in Current Implementation** | Both containers are parsed and supported by DriveCinema's player pipeline. |
| **Video Codec** | **H.264 (AVC)** or **H.265 (HEVC)** | **Verified in Current Implementation** | Universally hardware-accelerated up to 4K on modern Android phones and Android TVs. |
| **Primary Audio** | **AAC Stereo (AAC-LC)** | **Tested Successfully (Universal)** | Plays everywhere: an AAC-LC decoder is required on every Android device, including Google Pixel phones. |
| **Alternative Audio 1** | **MP3 Stereo (`.mp3` audio stream)** | **Tested Successfully (Universal)** | Supported natively across Android devices in both MP4 and MKV containers. |
| **Alternative Audio 2** | **Opus / FLAC** | **Tested Successfully (Universal)** | Supported natively by Android AOSP; excellent for MKV containers. |
| **Conditional Audio 1** | **Dolby Digital (AC-3)** | **Conditional (Hardware Dependent)** | Plays natively on Android TVs with licensed Dolby hardware (e.g. TCL 55C6K). **On devices without an AC-3 decoder (e.g. Google Pixel 9a), DriveCinema's built-in software decoder plays it with sound** (checked on the Pixel 9a, 2026-10-20). |
| **Conditional Audio 2** | **Dolby Digital Plus (E-AC-3 / Atmos)** | **Conditional (Hardware Dependent)** | Supported on certified Android TVs and soundbars. On phones without a decoder (e.g. Pixel), the built-in software decoder handles it (not separately device-checked). |
| **Conditional Audio 3** | **DTS / DTS-HD / TrueHD** | **Conditional (AV Receiver / TV Passthrough)** | Passthrough needs an AV receiver or TV firmware that supports it. Without that, the built-in software decoder plays DTS and TrueHD (not separately device-checked). |

> [!TIP]
> **Golden Rule for Media Preparation:** An **AAC-LC stereo track** (or MP3 stereo) plays everywhere with no extra work. AC-3, E-AC-3 and DTS tracks also play on phones thanks to DriveCinema's built-in software decoder, but an AAC track remains the safest choice.

---

## 2. Understanding Containers vs. Codecs

A common source of confusion is the difference between a file extension and its internal encoding:

* **Container (`.mp4`, `.mkv`):** Think of the container as a digital box. It holds video tracks, audio tracks, and technical metadata together.
* **Video Codec (H.264, H.265, AV1, VP9):** The compression standard used for the video frames. Modern Android devices support hardware decoding for H.264 up to High Profile and H.265 up to Main 10.
* **Audio Codec Hierarchy:**
  1. **AAC-LC (Low Complexity):** The gold standard for mobile and TV compatibility. Supported everywhere with zero configuration.
  2. **MP3 (MPEG-1/2 Audio Layer 3):** Universal legacy support across all platforms. Ideal fallback in stereo.
  3. **Opus & FLAC:** Modern open formats natively supported by Android MediaCodec.
  4. **Dolby Digital (AC-3):** Traditional DVD/broadcast 5.1 surround format. Requires proprietary Dolby licensing fees. TV manufacturers (like TCL, Sony, LG) typically license it; stock AOSP and Pixel phones do not.
  5. **Dolby Digital Plus (E-AC-3) & Dolby Atmos (E-AC-3 JOC):** Streaming surround standard used by Netflix/Disney+. Same licensing requirements as AC-3.
  6. **DTS / DTS-HD Master Audio:** Common on Blu-ray rips. Rarely supported directly by mobile chipsets; requires passthrough to an external home-theater amplifier.

> [!IMPORTANT]
> Just because a file ends in `.mkv` or `.mp4` does **not** mean every device can play it. Playback depends on whether your physical device's operating system has a decoder for the specific **video and audio codecs** inside the file.

---

## 3. Device Compatibility Realities

### 3.1 Android Phones (e.g., Google Pixel 9a)
* **Video:** Hardware-accelerated H.264, H.265, and VP9 play smoothly up to 4K.
* **Audio:**
  * **AAC-LC, MP3, Opus, FLAC:** **Tested Successfully.** Crystal-clear audio playback.
  * **Dolby Digital (AC-3 / E-AC-3), DTS, TrueHD:** Google Pixel phones have no system decoder for these, so DriveCinema uses its **built-in software decoder** (FFmpeg) instead: such films play **with sound**. Checked with an AC-3 film on the Pixel 9a (2026-10-20); E-AC-3, DTS and TrueHD use the same decoder but weren't separately checked.
  * **If a track still can't be decoded** (a format the built-in decoder doesn't cover), DriveCinema picks another audio track, or plays the video without audio and shows a warning banner:  
    `⚠️ Audio format (…) is unsupported on this device. Playing video without audio.`  
    You can dismiss the banner or tap the audio track button to look for another track.

### 3.2 Android TV / Google TV (e.g., TCL 55C6K)
* **Video:** 4K HDR10 / Dolby Vision H.265 and H.264 playback via hardware decoders.
* **Audio:**
  * **AAC-LC, MP3, Opus:** **Tested Successfully.** Full stereo output.
  * **Dolby Digital (AC-3 / E-AC-3):** **Tested Successfully.** TCL and most major Android TV manufacturers license Dolby hardware decoders directly in the TV firmware (`OMX.dolby.ac3.decoder`). Full 5.1 surround sound and stereo downmixing work out of the box.
  * **DTS:** Conditional. Works if the TV or connected HDMI soundbar supports DTS passthrough.

### 3.3 Desktop app (Linux, Windows)
* Playback uses the VLC installed on the computer, which decodes AC-3, E-AC-3 and DTS itself, so these tracks play with sound on the desktop (see [`DESKTOP.md`](DESKTOP.md)).
* VLC must be installed: if it's missing, the player offers **Install VLC**.

---

## 4. Google Drive Folder Organization

DriveCinema automatically indexes movies and TV series based on standard directory structures. Organize your Google Drive as follows:

```text
Streaming/                     <-- Root library folder
├── Films/                     <-- All standalone movies
│   ├── Dune Part Two (2024)/
│   │   └── Dune.Part.Two.2024.1080p.mkv
│   ├── Spider-Man Homecoming (2017).mp4
│   └── The Dark Knight (2008).mkv
└── Series/                    <-- All episodic television shows
    └── Breaking Bad/
        ├── Season 01/
        │   ├── Breaking Bad - S01E01 - Pilot.mkv
        │   └── Breaking Bad - S01E02.mkv
        └── Season 02/
            ├── Breaking Bad - S02E01.mkv
            └── Breaking Bad - S02E02.mkv
```

### 4.1 Folder Naming Rules
1. **Root Folder:** Name the top-level folder **`Streaming`**. On first sign-in, DriveCinema automatically picks it when it is the only folder with that name your account can see; otherwise you choose from the folder list. Any other name works too.
2. **Films Subfolder:** Name it **`Films`** (capitalisation doesn't matter). Movie files can sit directly in it or in subfolders at any depth. Video files placed directly in the root folder are also indexed as movies.
3. **Series Subfolder:** Name it **`Series`**. Each folder inside is one show — **the folder name is the show name** (a year is allowed, e.g. `Doctor Who (2005)`).
4. **Season Folders:** Inside a show, use `Season 1`, `Season 01` or `S1`. The season number is taken from this folder name. Episodes may also sit directly in the show folder if their file name includes the show name (`Show - S02E05.mkv`); otherwise they count as season 1.
5. **Recognised video files:** `.mp4`, `.mkv`, `.avi`, `.mov`, `.wmv`, `.m4v`, `.webm`, `.ts`.

---

## 5. File Naming Conventions for Automated Artwork & Metadata

DriveCinema connects to The Movie Database (TMDB) to download official posters, backdrop banners, release years, synopses, and episode ratings. For accurate recognition:

### 5.1 Movies
* **Format:** `<Title> (<Year>).<ext>`
* **Examples:**
  * `Dune Part Two (2024).mkv` — *Best*
  * `Spider-Man Homecoming (2017).mp4` — *Best*
  * `Dune.Part.Two.2024.1080p.WEBRip.x264.mkv` — *Supported (the app automatically strips resolution and release tags)*

### 5.2 Television Episodes
* **Format:** `<ShowName> - S<Season>E<Episode> - <OptionalTitle>.<ext>`
* **Season & Episode numbering:** Always use two-digit numbers (`S01E01`, `S02E05`). The **episode number** comes from `SxxEyy` in the file name; files without one are all treated as episode 1.
* **Episode titles:** text after `SxxEyy` becomes the episode title until TMDB provides the official one — avoid leaving quality tags such as `1080p` right after `SxxEyy`.
* **Examples:**
  * `Breaking Bad - S01E01 - Pilot.mkv` — *Best*
  * `Stranger.Things.S04E09.mkv` — *Supported*

---

## 6. Google Account & Shared Library Access

### 6.1 Using Your Personal Google Drive
1. Sign in to DriveCinema with your Google account.
2. If you already have a `Streaming` folder, DriveCinema discovers it automatically.
3. If your folder has a different name, choose it from the **Select Streaming Library** screen.

### 6.2 Accessing a Friend's Shared Library
1. The owner opens Google Drive, right-clicks their library folder (e.g. `Streaming`) → **Share**, and adds your Google account (**Viewer** is enough).
2. Sign in to DriveCinema with that account. The folder list shows your own folders under **Your Drive** and the shared one under **Shared with you**, labelled with the owner's name.
3. Pick the library you want. You can switch between your own and the shared library at any time: **Account → Switch Library Folder** (desktop: account menu → **Change library folder**) — no sign-out needed.
4. You only ever see folders that were shared with your account; nothing else from the owner's Drive is visible.

> [!NOTE]
> **Drive Permissions Required:** Your Google account must have at least **Viewer** permission on the shared folder and its contents.

---

## 7. Local Storage Playback (Phone, Android TV, Desktop)

### 7.1 Current Status
* **Google Drive Streaming:** **Verified & Active.**
* **Desktop:** local folders are available and were checked on the Linux laptop and on Windows (account menu → **Local folders** → **Add folder**).
* **Phone and TV:** checked on 2026-10-20 on the developer's phone (adding a folder, local playback, merging a Drive and a local copy) and TV (the "not supported" message on a TV without a folder picker; local playback on a TV with one is untested). Still new, so please report problems:
  * **Choosing a local folder:** on phones via **Account → Add Local Folder**. On TVs without a system folder picker the app shows "Local Storage (Not Supported)".
  * **Playing local files from the library:** saved folders are scanned when you add them and every time the app starts.

### 7.1.1 How local folders work
* Use the same layout and naming as on Google Drive (`Films/`, `Series/<Show>/<Season n>/`).
* If a film or episode exists both on Drive and on the device, DriveCinema shows it **once** and plays the **local copy** (instant start, works offline). Watch progress carries over between both copies.
* A local file is linked automatically only when the match is certain (same title **and** year, or same show + season + episode). Otherwise it is added as its own title and listed under **Account → Review possible duplicates**, where you choose "same title" or "keep separate".
* Removing a folder (or losing access to it) hides its videos; nothing is deleted and watch history is kept.

### 7.2 Phones and TVs
* **Android Phones:** use Android's official folder picker (Storage Access Framework), so you pick specific media folders (like `/Movies`) without granting full storage permissions.
* **Android TV (USB Drives & Internal Storage):**
  * Works on TVs that include the Android system folder picker. Some Android TV models (including certain TCL, Sony and Google TV sets) ship without it (`DocumentsUI`).
  * DriveCinema checks this before offering local folders; on unsupported TVs it shows "Local Storage Not Supported" instead of failing or crashing.

### 7.3 Streaming from your PC (phone, TV, another computer)
The desktop app can share its local folders with your other devices at home, so they play those videos without
uploading them to Drive. Checked by the developer on 2026-10-25 (home PC sharing; phone, TV and laptop as clients).
* **On the PC:** account menu → **Local folders** (add your folders), then **Share with my devices** → turn on
  **Share my local folders**. The PC must stay on, with DriveCinema open. Windows asks once whether DriveCinema may
  communicate on networks: allow **Private networks** only (on public networks sharing stays blocked).
* **Pair each device once:** on the PC, **Pair a device** shows a 6-character code. Phone or TV: **Account → My PCs →
  Search this network** → enter the code → **Pair**. Another computer: account menu → **Play from another PC**.
* **In the library:** the PC's titles appear with the others; a title that is also on Drive stays one title and plays
  from the PC at home, from Drive when the PC is off. When the connection is too slow for a film, DriveCinema offers
  **Play from Drive** (if the film is also on Drive) or **Play from the PC anyway**.
* **Away from home:** only through a VPN such as Tailscale: install it on the PC and the phone (same Tailscale
  account), turn on **Allow streaming over a VPN** on the PC and enter the address it shows in **My PCs → Tailscale
  address** on the phone.
* **Privacy:** paired devices can only list and play the videos in the shared folders, nothing else on the PC, and
  get no Google sign-in. The connection is encrypted. Remove a device on the PC under **Paired devices → Remove**.

---

## 8. Common Troubleshooting

| Issue | Likely Cause | Solution |
| :--- | :--- | :--- |
| **Video plays but there is NO sound on phone** | The audio track uses a format neither the phone nor DriveCinema's built-in decoder can play (AC-3, E-AC-3, DTS and TrueHD are covered by it), or the phone's own decoder fails (reported on one phone, F-27). | In the playback overlay, tap the audio track button to pick another track (e.g. AAC stereo) if one exists. Otherwise, play the file on the TV or desktop, or use a version with an AAC track. |
| **"Folder must contain 'Films', 'Series', or direct video files"** | The selected Drive folder does not have subfolders named `Films` or `Series`, or has no video files. | Rename your subfolders in Google Drive to `Films` and `Series`, then tap **↻ Refresh** in the folder picker. |
| **Shared folder does not appear in the folder list** | The folder was shared with a different Google account than the one you signed in with. | Ask the owner to share it with exactly the account shown in DriveCinema's Account dialog, then tap **↻ Refresh**. |
| **Movie poster or synopsis is missing or incorrect** | Filename could not be matched against TMDB. | Rename the video file to include the release year in parentheses: `Movie Title (2024).mkv`. The app will backfill metadata on the next sync. |
| **TV pairing PIN fails or times out** | Devices are on different Wi-Fi subnets or the router blocks local peer-to-peer discovery (AP Isolation). | Ensure both your phone and TV are connected to the same Wi-Fi network. If mDNS discovery fails, enter the TV's direct IP address shown on the pairing screen. |
| **Search this network doesn't find the PC** | The PC and the phone/TV are on different networks (e.g. a guest Wi-Fi), the router keeps devices apart ("AP isolation" / "client isolation"), or the PC is on another subnet (e.g. behind a second router). | Put both on the same Wi-Fi; turn off AP isolation in the router settings; or type the PC's address by hand in **My PCs** (the PC shows it under **Pair a device**). |
| **The PC's films disappeared** | DriveCinema is closed on the PC, the PC is asleep or off, or sharing was turned off. | Start DriveCinema on the PC (sharing on), then **Refresh** on the phone/TV. Titles that are also on Drive keep playing from Drive meanwhile. |
| **"<PC> removed this device"** | The device was removed on the PC under **Paired devices**. | Pair again: on the PC **Pair a device**, on the phone/TV **My PCs**. |
| **Windows: the phone/TV can't connect although the PC is found** | The Windows firewall prompt was dismissed, or the network is set to **Public**. | Set the home network to **Private** in Windows settings, and allow DriveCinema on private networks (Windows Security → Firewall & network protection → Allow an app through firewall). |
| **"Slow connection to the PC"** | The film needs more bandwidth than the connection to the PC gives (weak Wi-Fi, or away from home over a VPN). | Choose **Play from Drive** if offered, move closer to the router or connect the PC by cable, or **Play from the PC anyway**. |
