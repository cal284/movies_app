# DataBounty Movies (Fire TV)

Browse free movies by country using TMDB, then open them in the streaming app that carries them (Tubi, Pluto TV, Plex, YouTube, Freevee and others). Built for Fire TV / Fire Stick remote navigation.

GitHub builds the APK for you — no Android Studio needed.

## 1. Get a TMDB key
1. Sign up at themoviedb.org → Settings → API → request a free Developer key.
2. Copy either the **API Key** (short) or the **API Read Access Token** (long). Both work.

## 2. Put the project on GitHub
1. Create a new **public** repository (public so the Fire Stick can download the APK without logging in).
2. Upload all files in this folder, including the hidden `.github` folder.
3. Repo → **Settings → Secrets and variables → Actions → New repository secret**
   - Name: `TMDB_API_KEY`
   - Value: your TMDB key

## 3. Build
Every push to `main` builds automatically. To build manually: **Actions → Build APK → Run workflow**.
When it finishes (about 3–5 minutes) the APK is attached to a new Release.

Direct download link (always the newest build):

```
https://github.com/<your-username>/<repo-name>/releases/latest/download/databounty-movies.apk
```

## 4. Install on the Fire Stick
1. Install **Downloader** (by AFTVnews) from the Amazon Appstore.
2. Settings → My Fire TV → Developer options → **Install unknown apps** → turn on for Downloader.
   (If Developer options is missing: Settings → My Fire TV → About → click the device name 7 times.)
3. Open Downloader, enter the download link above, and install.

New builds install over the old one — the project uses a fixed signing key, so no uninstall needed.

## Using the app
- **Region** — choose the country whose catalog you want. Set your VPN to the same country, or the streaming apps may refuse to play.
- **Years** — release-year range (default 2020–2023).
- **Free only ✓** — shows only titles on free / ad-supported services. Toggle to include subscription services.
- **Genres** row filters the grid; **Search** looks across all movies.
- Open a movie → pick a service → its app opens. Search for the title inside that app (most services don't support direct links to a title).
- **Back** on the main screen clears a search.

## Notes
- Movie data and posters come from TMDB. Streaming availability is powered by JustWatch via TMDB.
- The TMDB key is compiled into the APK, so anyone with the APK could extract it. Use a key you don't mind regenerating.
- Free VPNs are often blocked by Tubi / Pluto / Plex. If a service won't play, try another VPN server.
