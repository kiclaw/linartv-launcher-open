package com.linart.tvpreview;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Photos from two friends' public portfolios, shown in the "JUR / URBANOWICZ" slideshow.
 * Plain Java (no Android types) so the page parsers can be checked on a computer.
 * Network methods block: call them from a background thread.
 */
final class PortfolioPhotos {
    static final class Photo {
        final String url, author, gallery;
        Photo(String url, String author, String gallery) {
            this.url = url; this.author = author; this.gallery = gallery;
        }
    }

    static final String JUREK_BASE = "https://januszjurekphotography.myportfolio.com";
    static final String JUREK_AUTHOR = "Janusz Jurek";
    static final String URBANOWICZ_PAGE = "https://www.marcinurbanowicz.pl/portfolio-item/reportaz/";
    static final String URBANOWICZ_AUTHOR = "Marcin Urbanowicz";
    static final String URBANOWICZ_GALLERY = "Reportaż";
    /** Largest Adobe Portfolio rendition we ask for; the TV shows 1920 px at most. */
    private static final int MAX_WIDTH = 1920;

    private static final Pattern COVER = Pattern.compile(
            "<a\\s[^>]*class=\"[^\"]*project-cover[^\"]*\"[^>]*href=\"(/[^\"#?]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern COVER_HREF_FIRST = Pattern.compile(
            "<a\\s[^>]*href=\"(/[^\"#?]+)\"[^>]*class=\"[^\"]*project-cover[^\"]*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern RENDITION = Pattern.compile(
            "(https://cdn\\.myportfolio\\.com/[^\"\\s?,]+?)_rw_(\\d+)\\.(jpe?g|png|gif|webp)\\?h=([0-9a-f]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern WP_FULL = Pattern.compile(
            "href=\"(https?://[^\"]*/wp-content/uploads/[^\"]+\\.(?:jpe?g|png|webp))\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern WP_RESIZED = Pattern.compile("-\\d+x\\d+\\.[a-z]+$", Pattern.CASE_INSENSITIVE);

    private PortfolioPhotos() { }

    // ---------- Parsers (pure, unit-checked in tests/PortfolioPhotosCheck.java) ----------

    /** Gallery paths ("/street", "/hasidim") linked as project covers on the /work page. */
    static List<String> jurekGalleries(String html) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (Pattern pattern : new Pattern[]{COVER, COVER_HREF_FIRST}) {
            Matcher m = pattern.matcher(html);
            while (m.find()) paths.add(m.group(1));
        }
        return new ArrayList<>(paths);
    }

    /** "Janusz Jurek - HASIDIM - reportage" -> "Hasidim - reportage" */
    static String jurekGalleryTitle(String html) {
        Matcher m = TITLE.matcher(html);
        String title = m.find() ? decodeEntities(m.group(1)).trim() : "";
        String prefix = JUREK_AUTHOR + " - ";
        if (title.startsWith(prefix)) title = title.substring(prefix.length()).trim();
        // "HASIDIM - reportage", "DOGMA project" -> just the gallery name.
        title = title.replaceAll("(?i)\\s*(-\\s*)?(reportage|project|projekt)\\s*$", "").trim();
        if (title.isEmpty()) return "";
        // Galleries are written in capitals; "HASIDIM - reportage" reads better as "Hasidim - reportage".
        String lower = title.toLowerCase(new Locale("pl", "PL"));
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Each photo once, in its largest rendition up to 1920 px (cover crops "_rwc_" are skipped). */
    static List<String> jurekImages(String html) {
        Map<String, String> best = new LinkedHashMap<>();
        Map<String, Integer> width = new LinkedHashMap<>();
        Matcher m = RENDITION.matcher(html);
        while (m.find()) {
            String base = m.group(1);
            int w;
            try { w = Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { continue; }
            if (w > MAX_WIDTH) continue;
            Integer known = width.get(base);
            if (known == null || w > known) {
                width.put(base, w);
                best.put(base, m.group(0));
            }
        }
        return new ArrayList<>(best.values());
    }

    /** Full-size photos linked from the WordPress gallery; favicons and resized copies are skipped. */
    static List<String> urbanowiczImages(String html) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher m = WP_FULL.matcher(html);
        while (m.find()) {
            String url = m.group(1);
            String name = url.substring(url.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (name.startsWith("cropped-") || WP_RESIZED.matcher(name).find() || name.contains("logo")) continue;
            urls.add(url);
        }
        return new ArrayList<>(urls);
    }

    private static String decodeEntities(String value) {
        return value.replace("&#8211;", "–").replace("&#8212;", "—").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#039;", "'").replace("&#8217;", "’");
    }

    // ---------- Network ----------

    /** Both portfolios. One failing source does not hide the other; fails only when both do. */
    static List<Photo> fetchAll() throws IOException {
        List<Photo> photos = new ArrayList<>();
        IOException failure = null;
        try { photos.addAll(fetchJurek()); } catch (IOException e) { failure = e; }
        try { photos.addAll(fetchUrbanowicz()); } catch (IOException e) { failure = e; }
        if (photos.isEmpty() && failure != null) throw failure;
        return photos;
    }

    static List<Photo> fetchJurek() throws IOException {
        List<Photo> photos = new ArrayList<>();
        for (String path : jurekGalleries(get(JUREK_BASE + "/work"))) {
            String page;
            try { page = get(JUREK_BASE + path); } catch (IOException e) { continue; }
            String gallery = jurekGalleryTitle(page);
            for (String url : jurekImages(page)) photos.add(new Photo(url, JUREK_AUTHOR, gallery));
        }
        if (photos.isEmpty()) throw new IOException("No photos found on " + JUREK_BASE);
        return photos;
    }

    static List<Photo> fetchUrbanowicz() throws IOException {
        List<Photo> photos = new ArrayList<>();
        for (String url : urbanowiczImages(get(URBANOWICZ_PAGE)))
            photos.add(new Photo(url, URBANOWICZ_AUTHOR, URBANOWICZ_GALLERY));
        if (photos.isEmpty()) throw new IOException("No photos found on " + URBANOWICZ_PAGE);
        return photos;
    }

    static String get(String url) throws IOException {
        return new String(download(url, 4_000_000), StandardCharsets.UTF_8);
    }

    static byte[] download(String url, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LinarTV (Android TV launcher)");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300)
                throw new IOException(String.format(Locale.ROOT, "HTTP %d for %s", code, url));
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[16_384];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > limit) throw new IOException("Too large: " + url);
                }
                return buffer.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
