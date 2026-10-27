package com.linart.tvpreview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal Home Assistant REST client for lights and switches.
 * Every method does network I/O: call it from a background thread.
 */
final class HomeAssistantClient {
    /** Thrown when Home Assistant rejects the long-lived access token (HTTP 401/403). */
    static final class AuthException extends IOException {
        AuthException() { super("Home Assistant rejected the token"); }
    }

    static final class Entity {
        final String id;
        final String name;
        final String state;

        Entity(String id, String name, String state) {
            this.id = id;
            this.name = name;
            this.state = state;
        }

        boolean on() { return "on".equals(state); }
        boolean available() { return !"unavailable".equals(state) && !"unknown".equals(state); }
        Entity withState(String newState) { return new Entity(id, name, newState); }
    }

    private final String base;
    private final String token;

    HomeAssistantClient(String base, String token) {
        this.base = normalize(base);
        this.token = token == null ? "" : token.trim();
    }

    /** "homeassistant.local:8123/" -> "http://homeassistant.local:8123" */
    static String normalize(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://"))
            value = "http://" + value;
        return value;
    }

    static boolean supported(String entityId) {
        return entityId != null && (entityId.startsWith("light.") || entityId.startsWith("switch."));
    }

    /** All lights and switches known to Home Assistant, sorted by name. */
    List<Entity> controllable() throws IOException {
        List<Entity> result = new ArrayList<>(allStates().values());
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    /** Current state of the given entities; missing ones are left out. */
    Map<String, Entity> states(Collection<String> ids) throws IOException {
        Map<String, Entity> all = allStates();
        Map<String, Entity> result = new HashMap<>();
        for (String id : ids) if (all.containsKey(id)) result.put(id, all.get(id));
        return result;
    }

    void toggle(String entityId) throws IOException {
        try {
            request("POST", "/api/services/homeassistant/toggle",
                    new JSONObject().put("entity_id", entityId).toString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** One upcoming launch from the Rocket Launch Live integration (sensor.rocket_launch_N). */
    static final class Launch {
        final String name, provider, vehicle, pad, location, missions, description, dateLabel;
        /** Planned lift-off in epoch milliseconds, 0 when the date is not fixed yet. */
        final long startsAt;
        final boolean warning20m, warning24h;

        Launch(JSONObject attributes) {
            name = text(attributes, "name");
            provider = text(attributes, "provider");
            vehicle = text(attributes, "vehicle");
            pad = text(attributes, "launch_pad");
            location = text(attributes, "launch_location");
            String missionList = text(attributes, "launch_missions").trim();
            while (missionList.endsWith("|")) missionList = missionList.substring(0, missionList.length() - 1).trim();
            missions = missionList.replace(" |", ",");
            description = text(attributes, "launch_description");
            dateLabel = text(attributes, "launch_date_target");
            startsAt = Math.max(0, attributes.optLong("launch_target_timestamp", 0)) * 1000L;
            warning20m = "true".equalsIgnoreCase(text(attributes, "launch_20m_warning"));
            warning24h = "true".equalsIgnoreCase(text(attributes, "launch_24h_warning"));
        }

        private static String text(JSONObject attributes, String key) {
            Object value = attributes.opt(key);
            return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("name", name).put("provider", provider).put("vehicle", vehicle)
                    .put("launch_pad", pad).put("launch_location", location).put("launch_missions", missions)
                    .put("launch_description", description).put("launch_date_target", dateLabel)
                    .put("launch_target_timestamp", startsAt / 1000L)
                    .put("launch_20m_warning", String.valueOf(warning20m))
                    .put("launch_24h_warning", String.valueOf(warning24h));
        }
    }

    static final String ROCKET_SENSOR_PREFIX = "sensor.rocket_launch_";

    /** Upcoming launches in the order of sensor.rocket_launch_1..5, the same sensors the HA dashboard uses. */
    List<Launch> launches() throws IOException {
        try {
            JSONArray all = new JSONArray(request("GET", "/api/states", null));
            java.util.TreeMap<Integer, Launch> found = new java.util.TreeMap<>();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.getJSONObject(i);
                String id = item.optString("entity_id");
                if (!id.startsWith(ROCKET_SENSOR_PREFIX)) continue;
                int index;
                try { index = Integer.parseInt(id.substring(ROCKET_SENSOR_PREFIX.length())); }
                catch (NumberFormatException e) { continue; }
                String state = item.optString("state", "unknown");
                JSONObject attributes = item.optJSONObject("attributes");
                if (attributes == null || "unavailable".equals(state) || "unknown".equals(state)) continue;
                Launch launch = new Launch(attributes);
                if (!launch.name.isEmpty()) found.put(index, launch);
            }
            return new ArrayList<>(found.values());
        } catch (JSONException e) {
            throw new IOException("Unexpected answer from Home Assistant", e);
        }
    }

    /** Raw bytes of a file Home Assistant serves, e.g. "/local/rocket_off.jpg" from the www folder. */
    byte[] download(String path) throws IOException {
        if (base.isEmpty()) throw new IOException("Home Assistant is not configured");
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(15_000);
            if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300)
                throw new IOException(String.format(Locale.ROOT, "Home Assistant HTTP %d", code));
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[16_384];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > 6_000_000) throw new IOException("File too large");
                }
                return buffer.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private Map<String, Entity> allStates() throws IOException {
        try {
            JSONArray all = new JSONArray(request("GET", "/api/states", null));
            Map<String, Entity> result = new HashMap<>();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.getJSONObject(i);
                String id = item.optString("entity_id");
                if (!supported(id)) continue;
                JSONObject attributes = item.optJSONObject("attributes");
                String name = attributes == null ? "" : attributes.optString("friendly_name", "");
                if (name.isEmpty()) name = id.substring(id.indexOf('.') + 1).replace('_', ' ');
                result.put(id, new Entity(id, name, item.optString("state", "unknown")));
            }
            return result;
        } catch (JSONException e) {
            throw new IOException("Unexpected answer from Home Assistant", e);
        }
    }

    private String request(String method, String path, String body) throws IOException {
        if (base.isEmpty() || token.isEmpty()) throw new IOException("Home Assistant is not configured");
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            if (code == 401 || code == 403) throw new AuthException();
            if (code < 200 || code >= 300)
                throw new IOException(String.format(Locale.ROOT, "Home Assistant HTTP %d", code));
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[16_384];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > 8_000_000) throw new IOException("Answer too large");
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
