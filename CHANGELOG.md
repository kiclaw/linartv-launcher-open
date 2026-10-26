# Zmiany / Changelog

Każde wydanie ma tu wpis po polsku i po angielsku. Najnowsze na górze.
Every release gets an entry here in Polish and English. Newest first.

## 1.1.0 — fork kiclaw / kiclaw fork

**Polski**

Nowe
- **Światła z Home Assistanta** w rzędzie nad dockiem aplikacji (do 6 kafelków). Kliknięcie pilotem włącza lub wyłącza światło, stan odświeża się co 5 s. Obsługiwane encje `light.*` i `switch.*`.
- Konfiguracja z telefonu przez kod QR: adres HA, token długoterminowy i wybór świateł (Ustawienia → „Światła · Home Assistant” lub kafelek „+ Światła z Home Assistanta”). Szczegóły: [docs/HOME_ASSISTANT.md](docs/HOME_ASSISTANT.md).
- Automatyczny build podpisanego APK w GitHub Actions i publikacja wydania dla tagów `vX.Y.Z`.

**English**

New
- **Home Assistant lights** in a row above the app dock (up to 6 tiles). Pressing OK toggles the light; state refreshes every 5 s. Supports `light.*` and `switch.*` entities.
- Set up from a phone via QR code: HA address, long-lived token and light selection (Settings → "Lights · Home Assistant" or the "+ Home Assistant lights" tile). Details: [docs/HOME_ASSISTANT.md](docs/HOME_ASSISTANT.md).
- GitHub Actions builds a signed APK and publishes a release for `vX.Y.Z` tags.

## 1.0.1 — niewydane / unreleased

**Polski**

Nowe
- Pogoda dla **dowolnego miasta na świecie** (Open-Meteo) zamiast tylko polskich miast z IMGW. Godziny prognozy są w czasie lokalnym wybranego miasta.
- Nowe, kolorowe ikony pogody Meteocons, wbudowane w aplikację (działają bez internetu).
- **IV liga** — wszystkie 16 grup wojewódzkich. W kreatorze: liga → „IV liga” → województwo → klub.
- **Herby wszystkich klubów z kalendarzeligowe.pl**, zawsze aktualne. Nowy herb pobiera się sam, gdy serwis go zmieni. Drużyny bez herbu mają inicjały.
- LinarTV może być **domyślnym ekranem głównym**. Wtedy otwiera się po włączeniu telewizora i po wybudzeniu z czuwania, a nie np. telewizja na żywo.
- Ikona na liście aplikacji także na zwykłym Androidzie (np. projektory jak Wanbo T2 Max).

Ulepszone
- Większe ikony i temperatury na karcie pogody. Długie nazwy miast mieszczą się w jednej lub dwóch liniach, bez ucinania.
- Plik aplikacji jest o połowę mniejszy (ok. 6 MB), bo herby nie są już w nim zapisane.

Do wiadomości
- Po aktualizacji telewizor może zapytać, która aplikacja ma być ekranem głównym. Wybierz LinarTV i „Zawsze”.

**English**

New
- Weather for **any city in the world** (Open-Meteo) instead of Polish cities from IMGW only. Forecast hours use the chosen city's local time.
- New colourful Meteocons weather icons, bundled in the app (work offline).
- **IV liga** — all 16 regional groups. In the wizard: league → "IV liga" → region → club.
- **Crests of all clubs from kalendarzeligowe.pl**, always current. A new crest downloads by itself when the site changes it. Teams without a crest show initials.
- LinarTV can be the **default home screen**. It then opens after the TV is switched on and after waking from standby, instead of e.g. live TV.
- App icon in the app list on plain Android as well (e.g. projectors such as the Wanbo T2 Max).

Improved
- Larger icons and temperatures on the weather card. Long city names fit on one or two lines, never cut off.
- The app file is half the size (about 6 MB) because crests are no longer stored in it.

Good to know
- After the update the TV may ask which app should be the home screen. Choose LinarTV and "Always".

## 1.0.0 — 2026-10-25

Pierwsze wydanie: terminarz jednego lub dwóch klubów (Ekstraklasa – III liga, Puchar Polski) z herbami, pogoda IMGW dla polskiego miasta, dock aplikacji, tryb dzienny i nocny, tapety z telefonu, wygaszacz, pasek Spotify, język polski lub angielski.

First release: fixtures of one or two clubs (Ekstraklasa – III liga, Polish Cup) with crests, IMGW weather for a Polish city, app dock, day and night modes, wallpapers from your phone, screensaver, Spotify bar, Polish or English.
