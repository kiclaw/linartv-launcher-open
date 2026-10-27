# Zmiany / Changelog

Każde wydanie ma tu wpis po polsku i po angielsku. Najnowsze na górze.
Every release gets an entry here in Polish and English. Newest first.

## 1.3.0 — zdjęcia / photos

**Polski**

Nowe
- **Przycisk „JUR / URBANOWICZ”** pod przyciskami systemowymi otwiera pełnoekranowy pokaz losowych zdjęć z portfolio Janusza Jurka (Adobe Portfolio, wszystkie galerie) i Marcina Urbanowicza (Reportaż). Połowa zdjęć pochodzi od każdego autora.
- Strzałki ◀ ▶ (i na pilocie) przechodzą między zdjęciami, także wstecz po historii; OK = następne, Wstecz zamyka. Bez dotykania pilota zdjęcie zmienia się co 10 s. Na dole autor i galeria.
- Lista zdjęć odświeża się raz w tygodniu i jest zapamiętywana; kolejne zdjęcie wczytuje się w tle.
- Odtwarzana muzyka ze Spotify pokazuje się w miejscu daty pod zegarem.

**English**

New
- **"JUR / URBANOWICZ" button** under the system buttons opens a full-screen slideshow of random photos from Janusz Jurek's portfolio (Adobe Portfolio, all galleries) and Marcin Urbanowicz's (Reportaż). Half of the photos come from each author.
- ◀ ▶ arrows (also on the remote) move between photos, including back through the history; OK = next, Back closes. Without input the photo changes every 10 s. Author and gallery are shown at the bottom.
- The photo list refreshes once a week and is remembered; the next photo loads in the background.
- Spotify now playing replaces the date under the clock.

## 1.2.0 — nowy wygląd / new look

**Polski**

Nowe
- **Nowy, ciemny wygląd ekranu głównego** według projektu „Launcher TV — ciemny”: duży zegar, pogoda z prognozą godzinową w nagłówku, szklane karty, font Manrope, tło „kosmos” (domyślne; inne tapety nadal w galerii).
- **Fokus pod pilota**: powiększenie 1,05, biała obwódka, niebieska poświata i przesuwający się połysk.
- **Dock: 8 okrągłych ikon z oryginalnymi logami aplikacji** (ikona aplikacji z systemu) i nazwą pod spodem. Szuflada „więcej aplikacji” ma 8 kafelków w rzędzie.
- **Karta „Start rakiety”** z Home Assistanta — ta sama funkcja co na pulpicie HA: najbliższy start z czujnika Rocket Launch Live (`sensor.rocket_launch_1`), rakieta, dostawca, wyrzutnia, godzina i odliczanie, tło `/local/rocket_off.jpg` / `rocket_on.jpg` (20 min przed startem). OK otwiera szczegóły i kolejne starty.
- Światła z HA jako „pigułki” z żarówką; włączone świecą na bursztynowo.

**English**

New
- **New dark home screen** following the "Launcher TV — dark" design: large clock, weather with hourly forecast in the header, glass cards, Manrope font, "cosmos" background (default; other wallpapers remain in the gallery).
- **Remote-friendly focus**: 1.05 scale, white ring, blue glow and a moving sheen.
- **Dock: 8 round tiles with the apps' original logos** (the app's own icon) and the name below. The "more apps" drawer shows 8 tiles per row.
- **"Rocket launch" card** from Home Assistant — the same feature as on the HA dashboard: next launch from the Rocket Launch Live sensor (`sensor.rocket_launch_1`), rocket, provider, pad, time and countdown, background `/local/rocket_off.jpg` / `rocket_on.jpg` (20 min before lift-off). OK opens details and the next launches.
- HA lights as pills with a bulb; lights that are on glow amber.

## 1.1.0 — fork kiclaw / kiclaw fork

**Polski**

Nowe
- **Światła z Home Assistanta** w rzędzie nad dockiem aplikacji (do 6 kafelków). Kliknięcie pilotem włącza lub wyłącza światło, stan odświeża się co 5 s. Obsługiwane encje `light.*` i `switch.*`.
- Konfiguracja z telefonu przez kod QR: adres HA, token długoterminowy i wybór świateł (Ustawienia → „Światła · Home Assistant” lub kafelek „+ Światła z Home Assistanta”). Szczegóły: [docs/HOME_ASSISTANT.md](docs/HOME_ASSISTANT.md).
- **Dock mieści 6 aplikacji** zamiast 4 (węższe kafelki, także w szufladzie aplikacji).
- Automatyczny build podpisanego APK w GitHub Actions i publikacja wydania dla tagów `vX.Y.Z`.

**English**

New
- **Home Assistant lights** in a row above the app dock (up to 6 tiles). Pressing OK toggles the light; state refreshes every 5 s. Supports `light.*` and `switch.*` entities.
- Set up from a phone via QR code: HA address, long-lived token and light selection (Settings → "Lights · Home Assistant" or the "+ Home Assistant lights" tile). Details: [docs/HOME_ASSISTANT.md](docs/HOME_ASSISTANT.md).
- **The dock holds 6 apps** instead of 4 (narrower tiles, also in the app drawer).
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
