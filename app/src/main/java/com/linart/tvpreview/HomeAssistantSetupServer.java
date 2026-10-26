package com.linart.tvpreview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Short-lived, PIN-protected page for configuring Home Assistant from a phone:
 * address, long-lived token and the lights shown on the home screen.
 * It runs only while its TV dialog is open.
 */
final class HomeAssistantSetupServer implements AutoCloseable {
    static final int PORT = 8766;
    static final int MAX_LIGHTS = 6;
    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_FAILED_PINS = 5;

    interface Listener {
        /** Called on the server thread after the phone saved a working configuration. */
        void onSaved(String url, String token, List<String> entities);
    }

    private final boolean english;
    private final String savedUrl;
    private final String savedToken;
    private final List<String> savedEntities;
    private final Listener listener;
    private final String pin = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1_000_000));
    private final ServerSocket server;
    private volatile boolean running = true;
    private int failedPins;

    HomeAssistantSetupServer(boolean english, String savedUrl, String savedToken,
            List<String> savedEntities, Listener listener) throws IOException {
        this.english = english;
        this.savedUrl = savedUrl == null ? "" : savedUrl;
        this.savedToken = savedToken == null ? "" : savedToken;
        this.savedEntities = new ArrayList<>(savedEntities);
        this.listener = listener;
        server = new ServerSocket(PORT);
        Thread worker = new Thread(this::serve, "home-assistant-setup");
        worker.setDaemon(true);
        worker.start();
    }

    String pin() { return pin; }
    private String tr(String polish, String englishText) { return english ? englishText : polish; }

    private void serve() {
        while (running) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(20_000);
                handle(socket);
            } catch (IOException ignored) {
                if (!running) return;
            }
        }
    }

    private void handle(Socket socket) throws IOException {
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
        OutputStream output = socket.getOutputStream();
        String request = readLine(input);
        if (request == null) return;
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(input)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    line.substring(colon + 1).trim());
        }
        if (request.startsWith("GET / ")) {
            respond(output, 200, "text/html; charset=utf-8", page());
            return;
        }
        boolean connect = request.startsWith("POST /connect ");
        boolean save = request.startsWith("POST /save ");
        if (!connect && !save) {
            respond(output, 404, "text/plain; charset=utf-8", tr("Nie znaleziono strony", "Page not found"));
            return;
        }
        if (failedPins >= MAX_FAILED_PINS) {
            respond(output, 429, "text/plain; charset=utf-8",
                    tr("Zbyt wiele błędnych kodów. Zamknij okno na TV i otwórz je ponownie.",
                            "Too many incorrect codes. Close and reopen the TV window."));
            return;
        }
        if (!pin.equals(headers.get("x-pin"))) {
            failedPins++;
            respond(output, 403, "text/plain; charset=utf-8", tr("Nieprawidłowy kod z telewizora", "Incorrect TV code"));
            return;
        }
        JSONObject body;
        try {
            body = new JSONObject(readBody(input, headers));
        } catch (JSONException | IOException e) {
            respond(output, 400, "text/plain; charset=utf-8", tr("Błędne dane", "Invalid data"));
            return;
        }
        String url = HomeAssistantClient.normalize(body.optString("url", ""));
        String token = body.optString("token", "").trim();
        if (token.isEmpty()) token = savedToken;
        if (url.isEmpty() || token.isEmpty()) {
            respond(output, 400, "text/plain; charset=utf-8",
                    tr("Podaj adres Home Assistanta i token.", "Enter the Home Assistant address and token."));
            return;
        }
        HomeAssistantClient client = new HomeAssistantClient(url, token);
        if (connect) {
            try {
                JSONArray list = new JSONArray();
                List<HomeAssistantClient.Entity> entities = client.controllable();
                // Lights first, then switches; unavailable entities only when already chosen.
                for (String domain : new String[]{"light.", "switch."}) {
                    for (HomeAssistantClient.Entity entity : entities) {
                        if (!entity.id.startsWith(domain)) continue;
                        if (!entity.available() && !savedEntities.contains(entity.id)) continue;
                        list.put(new JSONObject().put("id", entity.id).put("name", entity.name)
                                .put("state", entity.state));
                    }
                }
                JSONObject answer = new JSONObject().put("entities", list)
                        .put("selected", new JSONArray(savedEntities));
                respond(output, 200, "application/json; charset=utf-8", answer.toString());
            } catch (HomeAssistantClient.AuthException e) {
                respond(output, 401, "text/plain; charset=utf-8",
                        tr("Home Assistant odrzucił token. Utwórz nowy token długoterminowy w profilu.",
                                "Home Assistant rejected the token. Create a new long-lived token in your profile."));
            } catch (IOException | JSONException e) {
                respond(output, 502, "text/plain; charset=utf-8",
                        tr("Telewizor nie połączył się z Home Assistantem pod adresem ",
                                "The TV could not reach Home Assistant at ") + url);
            }
            return;
        }
        JSONArray chosen = body.optJSONArray("entities");
        List<String> entities = new ArrayList<>();
        if (chosen != null) for (int i = 0; i < chosen.length() && entities.size() < MAX_LIGHTS; i++) {
            String id = chosen.optString(i, "");
            if (HomeAssistantClient.supported(id) && !entities.contains(id)) entities.add(id);
        }
        listener.onSaved(url, token, entities);
        respond(output, 200, "text/plain; charset=utf-8",
                tr("Zapisano. Światła pojawią się na ekranie głównym.", "Saved. Lights will appear on the home screen."));
    }

    private static String readBody(BufferedInputStream input, Map<String, String> headers) throws IOException {
        int length;
        try { length = Integer.parseInt(headers.getOrDefault("content-length", "-1")); }
        catch (NumberFormatException e) { length = -1; }
        if (length <= 0 || length > MAX_BODY) throw new IOException("Bad length");
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) throw new IOException("Incomplete body");
            offset += read;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String readLine(BufferedInputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') return line.toString();
            if (value != '\r') line.append((char) value);
            if (line.length() > 8192) throw new IOException("Header too long");
        }
        return line.length() == 0 ? null : line.toString();
    }

    private static void respond(OutputStream output, int code, String type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : code == 401 ? "Unauthorized"
                : code == 403 ? "Forbidden" : code == 429 ? "Too Many Requests"
                : code == 502 ? "Bad Gateway" : "Not Found";
        String headers = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + payload.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(payload);
        output.flush();
    }

    private static String js(String value) {
        return JSONObject.quote(value);
    }

    private String page() {
        String hasToken = savedToken.isEmpty() ? "false" : "true";
        return "<!doctype html><html lang=" + tr("pl", "en") + "><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>LinarTV · Home Assistant</title><style>"
                + "body{margin:0;min-height:100vh;background:#111d38;color:#f6f8ff;font:18px system-ui;display:grid;place-items:center}"
                + "main{width:min(92vw,560px);background:#213454;border:1px solid #6d87ac;border-radius:24px;padding:26px;box-sizing:border-box;margin:18px 0}"
                + "h1{font-size:28px;margin:0 0 10px}p{color:#c8d4e8;line-height:1.45;margin:8px 0}label{display:block;margin:16px 0 7px}"
                + "input[type=text],input[type=password]{box-sizing:border-box;width:100%;font:inherit;padding:12px;border-radius:12px;border:1px solid #9cb2d0}"
                + "button{width:100%;margin-top:20px;padding:15px;border:0;border-radius:13px;background:#90cfff;color:#10213c;font:700 19px system-ui}"
                + "#list{margin-top:10px}.item{display:flex;align-items:center;gap:12px;padding:12px;border-radius:12px;background:#2b4268;margin:8px 0}"
                + ".item.on{outline:2px solid #ffd27a}.num{min-width:28px;height:28px;border-radius:14px;background:#ffd27a;color:#10213c;font-weight:700;display:grid;place-items:center}"
                + ".num.off{background:transparent;border:1px solid #9cb2d0}.name{flex:1}.id{display:block;font-size:13px;color:#9fb3d3}"
                + "#status{min-height:1.6em;white-space:pre-line}#step2{display:none}</style><main>"
                + "<h1>Home Assistant</h1>"
                + "<p>" + tr("Telefon i telewizor muszą być w tej samej sieci co Home Assistant.",
                        "Phone, TV and Home Assistant must be on the same network.") + "</p>"
                + "<div id=step1><label>" + tr("Kod widoczny na TV", "Code shown on TV") + "</label>"
                + "<input id=pin type=text inputmode=numeric maxlength=6 placeholder='" + tr("6 cyfr", "6 digits") + "'>"
                + "<label>" + tr("Adres Home Assistanta", "Home Assistant address") + "</label>"
                + "<input id=url type=text autocapitalize=off placeholder='http://homeassistant.local:8123'>"
                + "<label>" + tr("Token długoterminowy", "Long-lived access token") + "</label>"
                + "<input id=token type=password autocomplete=off>"
                + "<p id=tokenHint></p>"
                + "<button onclick='connect()'>" + tr("Połącz", "Connect") + "</button></div>"
                + "<div id=step2><p>" + tr("Wybierz do " + MAX_LIGHTS + " świateł. Kolejność kliknięć = kolejność na ekranie.",
                        "Choose up to " + MAX_LIGHTS + " lights. Tap order = order on screen.") + "</p>"
                + "<div id=list></div><button onclick='save()'>" + tr("Zapisz", "Save") + "</button></div>"
                + "<p id=status></p></main><script>"
                + "const T={need:" + js(tr("Wpisz kod z TV i adres.", "Enter the TV code and address."))
                + ",wait:" + js(tr("Łączenie…", "Connecting…"))
                + ",fail:" + js(tr("Nie udało się połączyć z telewizorem. Sprawdź, czy okno z kodem jest otwarte.",
                        "Could not reach the TV. Check that the code window is still open."))
                + ",max:" + js(tr("Maksymalnie " + MAX_LIGHTS + " świateł.", "At most " + MAX_LIGHTS + " lights."))
                + ",keep:" + js(tr("Token jest zapisany na TV – zostaw puste, aby go zachować.",
                        "A token is saved on the TV – leave empty to keep it."))
                + ",how:" + js(tr("Token utworzysz w HA: Profil → Bezpieczeństwo → Tokeny długoterminowe.",
                        "Create one in HA: Profile → Security → Long-lived access tokens."))
                + ",on:" + js(tr("włączone", "on")) + ",off:" + js(tr("wyłączone", "off")) + "};"
                + "const $=i=>document.getElementById(i);$('url').value=" + js(savedUrl) + ";"
                + "$('tokenHint').textContent=" + hasToken + "?T.keep:T.how;"
                + "let chosen=[],all=[];"
                + "function body(extra){return JSON.stringify(Object.assign({url:$('url').value,token:$('token').value},extra||{}))}"
                + "async function post(path,extra){return fetch(path,{method:'POST',headers:{'X-Pin':$('pin').value,'Content-Type':'application/json'},body:body(extra)})}"
                + "async function connect(){let s=$('status');if(!/^[0-9]{6}$/.test($('pin').value)||!$('url').value){s.textContent=T.need;return}"
                + "s.textContent=T.wait;try{let r=await post('/connect');if(!r.ok){s.textContent=await r.text();return}"
                + "let d=await r.json();all=d.entities;chosen=d.selected.filter(id=>all.some(e=>e.id==id));render();"
                + "$('step1').style.display='none';$('step2').style.display='block';s.textContent=''}catch(e){s.textContent=T.fail}}"
                + "function render(){let l=$('list');l.innerHTML='';all.forEach(e=>{let i=chosen.indexOf(e.id),d=document.createElement('div');"
                + "d.className='item'+(i>=0?' on':'');let n=document.createElement('span');n.className='num'+(i>=0?'':' off');n.textContent=i>=0?i+1:'';"
                + "let t=document.createElement('span');t.className='name';t.textContent=e.name+' ('+(e.state=='on'?T.on:e.state=='off'?T.off:e.state)+')';"
                + "let id=document.createElement('span');id.className='id';id.textContent=e.id;t.appendChild(id);d.append(n,t);"
                + "d.onclick=()=>{if(i>=0)chosen.splice(i,1);else if(chosen.length<" + MAX_LIGHTS + ")chosen.push(e.id);else $('status').textContent=T.max;render()};l.appendChild(d)})}"
                + "async function save(){let s=$('status');s.textContent=T.wait;try{let r=await post('/save',{entities:chosen});s.textContent=await r.text()}catch(e){s.textContent=T.fail}}"
                + "</script></html>";
    }

    @Override public void close() {
        running = false;
        try { server.close(); } catch (IOException ignored) { }
    }
}
