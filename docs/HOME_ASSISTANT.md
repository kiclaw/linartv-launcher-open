# Home Assistant

## Polski

LinarTV pokazuje nad dockiem aplikacji rząd kafelków ze światłami z Home Assistanta (do 6). Kliknięcie kafelka pilotem przełącza światło (`homeassistant.toggle`). Stan jest odczytywany co 5 sekund przez REST API (`/api/states`). Obsługiwane są encje `light.*` i `switch.*`.

### Konfiguracja
1. W Home Assistancie: **Profil → Bezpieczeństwo → Tokeny długoterminowe → Utwórz token**. Skopiuj go.
2. Na TV: kafelek **„+ Światła z Home Assistanta”** albo **Ustawienia → Światła · Home Assistant**.
3. Zeskanuj kod QR telefonem (telefon, TV i HA w tej samej sieci).
4. Wpisz kod z TV, adres HA (np. `http://homeassistant.local:8123`) i token, potem **Połącz**.
5. Zaznacz światła w kolejności, w jakiej mają się pokazać, i naciśnij **Zapisz**. Zamknij okno na TV.

Przy kolejnej zmianie listy pole tokenu można zostawić puste – zostanie zapisany token.

### Start rakiety
Karta „Start rakiety” korzysta z tego samego adresu HA i tokenu. Pokazuje najbliższy start z integracji **Rocket Launch Live** (`sensor.rocket_launch_1` … `_5`), jak karta na pulpicie HA. Tło to obrazy `rocket_off.jpg` i `rocket_on.jpg` z folderu `www` w HA (`/local/…`); `rocket_on.jpg` pojawia się, gdy czujnik zgłasza `launch_20m_warning`. Dane odświeżają się co 2 min. OK na karcie otwiera szczegóły: dostawca, pojazd, wyrzutnia, lokalizacja, misje, opis i kolejne starty.

### Bezpieczeństwo
- Token jest przechowywany tylko w prywatnych danych aplikacji na TV.
- Strona konfiguracji działa tylko, gdy okno na TV jest otwarte (port 8766), i wymaga 6-cyfrowego kodu z ekranu. Po 5 błędnych kodach blokuje się do ponownego otwarcia okna.
- Połączenie z HA może być po `http` (aplikacja ma włączony ruch nieszyfrowany dla sieci domowej).

## English

LinarTV shows a row of Home Assistant light tiles (up to 6) above the app dock. Pressing OK on a tile toggles the light (`homeassistant.toggle`). State is read every 5 seconds through the REST API (`/api/states`). `light.*` and `switch.*` entities are supported.

### Setup
1. In Home Assistant: **Profile → Security → Long-lived access tokens → Create token**. Copy it.
2. On the TV: the **"+ Home Assistant lights"** tile or **Settings → Lights · Home Assistant**.
3. Scan the QR code with your phone (phone, TV and HA on the same network).
4. Enter the TV code, the HA address (e.g. `http://homeassistant.local:8123`) and the token, then **Connect**.
5. Tap the lights in the order they should appear and press **Save**. Close the TV window.

When changing the list later, leave the token field empty to keep the saved token.

### Rocket launch
The "Rocket launch" card uses the same HA address and token. It shows the next launch from the **Rocket Launch Live** integration (`sensor.rocket_launch_1` … `_5`), like the card on the HA dashboard. The background is `rocket_off.jpg` / `rocket_on.jpg` from HA's `www` folder (`/local/…`); `rocket_on.jpg` is used when the sensor reports `launch_20m_warning`. Data refreshes every 2 min. OK on the card opens details: provider, vehicle, pad, location, missions, description and the next launches.

### Security
- The token is stored only in the app's private data on the TV.
- The setup page runs only while the TV window is open (port 8766) and needs the 6-digit code from the screen. After 5 wrong codes it locks until the window is reopened.
- Plain `http` to HA is allowed (the app permits cleartext traffic for home networks).
