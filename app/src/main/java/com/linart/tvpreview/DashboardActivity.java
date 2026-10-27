package com.linart.tvpreview;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.MetricAffectingSpan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.HashMap;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.net.URL;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** A safe, native TV preview. It intentionally does not claim the Android HOME intent. */
public final class DashboardActivity extends Activity {
    private static final int W = 1672;
    private static final int H = 941;
    private static final Locale PL = new Locale("pl", "PL");
    private static final Locale EN = Locale.ENGLISH;
    private static final int FOCUS_RING = 0xFF9FD5FF;
    /** Preferred dock order. Only apps that are actually installed ever reach the dock. */
    private static final String[] POPULAR_DOCK_PACKAGES = {"com.google.android.youtube.tv", "com.netflix.ninja",
            "com.canal.android.canal", "com.spotify.tv.android", "com.disney.disneyplus", "com.wbd.stream",
            "com.amazon.amazonvideo.livingroom", "com.tvp.vodtv.tv", "com.google.android.youtube.tvkids",
            "pl.cda.tv"};
    private static final int DOCK_SIZE = 8;
    /** The "more apps" drawer keeps rectangular banners, eight per row like the dock. */
    private static final int DRAWER_COLUMNS = 8, DRAWER_TILE_W = 160, DRAWER_TILE_H = 90;
    /** Launchable system entries that are not apps a person would pin to the dock. */
    private static final java.util.Set<String> HIDDEN_APP_PACKAGES = new HashSet<>(java.util.Arrays.asList(
            "org.droidtv.playtv", "org.droidtv.eum", "org.droidtv.channels", "org.droidtv.contentexplorer",
            "com.android.traceur", "com.android.tv.settings"));
    private static final String[] CLEANUP_PACKAGES = {"com.google.android.youtube.tv", "com.netflix.ninja",
            "com.canal.android.canal", "com.spotify.tv.android", "com.tvp.vodtv.tv",
            "com.google.android.youtube.tvkids", "pl.cda.tv"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float scale;
    private FrameLayout root;
    private TextView clock;
    private SpotifyNowPlaying spotify;
    private FrameLayout spotifyBar;
    private NowPlayingTicker spotifyTitle;
    private ImageView spotifyCover;
    private String spotifyTrack, spotifyPerformer;
    private Bitmap spotifyArtwork;
    private boolean spotifyPlaying;
    private TextView date;
    private boolean night;
    private boolean dashboardVisible;
    private int ink;
    private int muted;
    private int panel;
    private int outline;
    private int detailText;
    private int detailMuted;
    private int detailCard;
    private int detailAstronomy;
    private final HashMap<String, Bitmap> logoCache = new HashMap<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> dockGlows = new ArrayList<>();
    private final List<String> dockPackages = new ArrayList<>();
    private int dockMoveIndex = -1;
    private List<String> dockMoveBackup;
    private boolean suppressDockKeyUp;
    private TextView dockMoveHint;
    private boolean firstRunSetup;
    private final List<List<View>> extraAppRows = new ArrayList<>();
    private int drawerFocusRow;
    private int drawerFocusColumn;
    private final List<View> suspendedFocusables = new ArrayList<>();
    private FrameLayout drawerBackdrop;
    private ImageView drawerBlurImage;
    private Bitmap drawerBlurBitmap;
    private TextView drawerTitle;
    private ScrollView appDrawer;
    private LinearLayout drawerRows;
    private boolean drawerRowsLoaded;
    private static final int APP_ROW_HEIGHT = 142;
    private ImageView dockExpand;
    private View lastDockFocus;
    private View firstExtraApp;
    private boolean appDrawerOpen;
    private CrestRepository crests;
    private final HashMap<String, List<CrestSlot>> crestSlots = new HashMap<>();
    private final WeakHashMap<Bitmap, Bitmap> scaledCrests = new WeakHashMap<>();
    private static final class CrestSlot {
        final ImageView image;
        final View fallback;
        final String name, league;
        final int teamId;
        /** A large faded crest behind a match card: shown whole, not as a 128 px badge. */
        final boolean watermark;
        CrestSlot(ImageView image, View fallback, String name, String league, int teamId) {
            this(image, fallback, name, league, teamId, false);
        }
        CrestSlot(ImageView image, View fallback, String name, String league, int teamId, boolean watermark) {
            this.image = image; this.fallback = fallback; this.name = name; this.league = league;
            this.teamId = teamId; this.watermark = watermark;
        }
    }
    private Typeface weatherTypeface;
    private MeteoconIcons weatherIcons;
    private JSONObject forecast;
    private long forecastUpdatedAt;
    private long forecastAttemptedAt;
    private boolean forecastLoading;
    private boolean weatherSettingsRefreshPending;
    private long weatherLocationVersion;
    private static final long WEATHER_REFRESH_MS = 30L * 60 * 1000;
    private String cityName;
    private double cityLatitude;
    private double cityLongitude;
    private static final long CALENDAR_REFRESH_MS = 24L * 60 * 60 * 1000;
    private static final long CALENDAR_PARSE_LOOKBACK_MS = 26L * 60 * 60 * 1000;
    private static final long MATCH_VISIBLE_AFTER_KICKOFF_MS = 105L * 60 * 1000;
    private static final class CalendarFeed {
        final String key, url, club, league, leagueSlug, requiredDescription;
        List<IcsMatchParser.Match> matches = new ArrayList<>();
        long updatedAt;
        long attemptedAt;
        boolean loading;
        boolean settingsRefreshPending;
        int selected;
        long renderedFirstMatchStart;
        boolean renderedLive;
        View tileButton;
        final List<View> cardViews = new ArrayList<>();
        CalendarFeed(String key, String url, String club, String league, String leagueSlug) {
            this.key = key; this.url = url; this.club = club; this.league = league;
            this.leagueSlug = leagueSlug; this.requiredDescription = null;
        }
        boolean configured() { return CalendarScope.accepts(url) && !club.isEmpty(); }
    }
    private CalendarFeed clubOneCalendar;
    private CalendarFeed clubTwoCalendar;
    private boolean setupShown;
    private PhotoUploadServer photoUploadServer;
    // Home Assistant lights (row above the app dock).
    private static final long LIGHT_POLL_MS = 5_000;
    private HomeAssistantSetupServer homeAssistantSetupServer;
    private String haUrl = "";
    private String haToken = "";
    private final List<String> haEntities = new ArrayList<>();
    private final HashMap<String, HomeAssistantClient.Entity> haStates = new HashMap<>();
    private final java.util.LinkedHashMap<String, LightTile> lightTiles = new java.util.LinkedHashMap<>();
    private String haError;
    private boolean haPolling;
    private int haGeneration;
    private static final class LightTile {
        final String id;
        final FrameLayout card;
        final ImageView bulb;
        final TextView name;
        final TextView state;
        final View iconCircle;
        boolean focused;
        LightTile(String id, FrameLayout card, ImageView bulb, TextView name, TextView state, View iconCircle) {
            this.id = id;
            this.iconCircle = iconCircle;
            this.card = card;
            this.bulb = bulb;
            this.name = name;
            this.state = state;
        }
    }
    private final Runnable pollLights = new Runnable() {
        @Override public void run() {
            handler.removeCallbacks(this);
            if (!dashboardVisible || !haConfigured()) return;
            if (!haPolling && !screensaverActive) {
                haPolling = true;
                final int generation = haGeneration;
                final HomeAssistantClient client = new HomeAssistantClient(haUrl, haToken);
                final List<String> ids = new ArrayList<>(haEntities);
                new Thread(() -> {
                    java.util.Map<String, HomeAssistantClient.Entity> result = null;
                    String error = null;
                    try { result = client.states(ids); }
                    catch (HomeAssistantClient.AuthException e) { error = tr("token odrzucony", "token rejected"); }
                    catch (IOException e) { error = tr("brak połączenia z HA", "no connection to HA"); }
                    final java.util.Map<String, HomeAssistantClient.Entity> states = result;
                    final String failure = error;
                    handler.post(() -> {
                        haPolling = false;
                        if (generation != haGeneration) return;
                        if (states != null) { haStates.putAll(states); haError = null; }
                        else haError = failure;
                        paintAllLights();
                    });
                }, "home-assistant-poll").start();
            }
            handler.postDelayed(this, LIGHT_POLL_MS);
        }
    };
    private static final long SCREENSAVER_SLIDE_MS = 30_000L;
    private boolean popupOpen;
    private boolean rebuildAfterPopup;
    private int popupDepth;
    private final List<Dialog> visibleDialogs = new ArrayList<>();
    private ImageView popupBlurImage;
    private Bitmap popupBlurBitmap;
    private boolean english;
    private boolean fahrenheit;
    private Locale displayLocale() { return english ? EN : PL; }
    private String tr(String polish, String englishText) { return english ? englishText : polish; }
    private boolean screensaverActive;
    private boolean suppressSaverKeyUp;
    private boolean suppressSaverTouch;
    private FrameLayout screensaver;
    private View screensaverPreviousFocus;
    private ImageView screensaverImage;
    private LinearLayout screensaverInfo;
    private TextView screensaverClock;
    private TextView screensaverDate;
    private Bitmap screensaverBitmap;
    private final List<String> screensaverChoices = new ArrayList<>();
    private int screensaverIndex;
    private final Runnable startScreensaver = () -> {
        if (!popupOpen && !screensaverActive) showScreensaver();
    };
    private final Runnable advanceScreensaver = new Runnable() {
        @Override public void run() {
            if (!screensaverActive) return;
            screensaverIndex = (screensaverIndex + 1) % screensaverChoices.size();
            showScreensaverSlide();
            if (screensaverActive) handler.postDelayed(this, SCREENSAVER_SLIDE_MS);
        }
    };
    private final Runnable updateTime = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", PL).format(now));
            String formatted = new SimpleDateFormat("EEEE, d MMMM", displayLocale()).format(now);
            date.setText(Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1));
            if (!popupOpen && night != isNight()) { buildScreen(); return; }
            if (!popupOpen && (matchCardNeedsUpdate(clubOneCalendar, now.getTime())
                    || matchCardNeedsUpdate(clubTwoCalendar, now.getTime()))) {
                buildScreen();
                return;
            }
            refreshRocketCard();
            refreshPortfolioIfDue(false);
            refreshWeather();
            refreshCalendar(clubOneCalendar);
            refreshCalendar(clubTwoCalendar);
            crests.refreshIfDue(clubOneCalendar.matches, clubTwoCalendar.matches, false);
            handler.postDelayed(this, 60_000);
        }
    };

    private final Runnable updateMatchCards = new Runnable() {
        @Override public void run() {
            if (!dashboardVisible) return;
            if (popupOpen) {
                handler.postDelayed(this, 1000);
                return;
            }
            long now = System.currentTimeMillis();
            if (matchCardNeedsUpdate(clubOneCalendar, now)
                    || matchCardNeedsUpdate(clubTwoCalendar, now)) buildScreen();
            else scheduleNextMatchBoundary();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // As the default home screen, keep the wake monitor running even without a reboot.
        if (BootReceiver.isDefaultHome(this)) BootReceiver.startWakeMonitor(this);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        scale = Math.min(metrics.widthPixels / (float) W, metrics.heightPixels / (float) H);
        SharedPreferences saved = getPreferences(MODE_PRIVATE);
        english = "en".equals(saved.getString("language", "pl"));
        fahrenheit = "f".equals(saved.getString("temperature_unit", "c"));
        clubOneCalendar = configuredCalendar(saved, 1);
        clubTwoCalendar = configuredCalendar(saved, 2);
        forecastUpdatedAt = saved.getLong("weather_updated", 0);
        try { forecast = new JSONObject(saved.getString("weather_json", "")); }
        catch (Exception ignored) { forecast = null; }
        // Weather comes from Open-Meteo for any city; IMGW data saved by 1.0.0 is dropped.
        if (forecast != null && !"Open-Meteo".equals(forecast.optString("provider"))) {
            forecast = null;
            forecastUpdatedAt = 0;
        }
        if (forecast != null && (forecast.optJSONArray("hourly") == null
                || forecast.optInt("icon_format") != 4)) forecastUpdatedAt = 0;
        cityName = saved.getString("city_name", "Kraków");
        cityLatitude = Double.longBitsToDouble(saved.getLong("city_lat", Double.doubleToLongBits(50.0614)));
        cityLongitude = Double.longBitsToDouble(saved.getLong("city_lon", Double.doubleToLongBits(19.9366)));
        weatherIcons = new MeteoconIcons(this);
        if (forecast != null && (!cityName.equals(forecast.optString("city"))
                || forecast.optJSONObject("current") == null)) forecastUpdatedAt = 0;
        crests = new CrestRepository(this, this::updateCrestViews);
        restoreCalendar(clubOneCalendar, saved);
        restoreCalendar(clubTwoCalendar, saved);
        loadHomeAssistant(saved);
        restoreLaunches(saved);
        restorePortfolio(saved);
        buildScreen();
        if (!clubOneCalendar.configured()) handler.post(() -> {
            if (!isFinishing() && !setupShown) { firstRunSetup = true; showTeamSetup(); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (spotify == null) spotify = new SpotifyNowPlaying(this, (title, artist, cover, playing) -> {
            spotifyTrack = title; spotifyPerformer = artist;
            spotifyArtwork = cover; spotifyPlaying = playing;
            updateSpotifyBar();
        });
        spotify.start();
        dashboardVisible = true;
        handler.removeCallbacks(updateTime);
        updateTime.run();
        scheduleNextMatchBoundary();
        resetScreensaverTimer();
        pollLights.run();
        pollRockets.run();
    }

    @Override protected void onPause() {
        if (spotify != null) spotify.stop();
        dashboardVisible = false;
        handler.removeCallbacks(updateTime);
        handler.removeCallbacks(updateMatchCards);
        handler.removeCallbacks(pollLights);
        handler.removeCallbacks(pollRockets);
        handler.removeCallbacks(startScreensaver);
        hideScreensaver();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (crests != null) crests.close();
        if (photoUploadServer != null) {
            photoUploadServer.close();
            photoUploadServer = null;
        }
        if (homeAssistantSetupServer != null) {
            homeAssistantSetupServer.close();
            homeAssistantSetupServer = null;
        }
        super.onDestroy();
    }

    private boolean isNight() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Calendar now = Calendar.getInstance();
        return LauncherTiming.isNight(prefs.getString("appearance_mode", "auto"),
                now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
                prefs.getInt("day_start_minute", 480), prefs.getInt("night_start_minute", 1200));
    }

    private void refreshWeather() {
        long now = System.currentTimeMillis();
        if (forecastLoading || isForecastCurrent() && now - forecastUpdatedAt < WEATHER_REFRESH_MS
                || now - forecastAttemptedAt < 5 * 60_000L) return;
        forecastLoading = true;
        forecastAttemptedAt = now;
        String requestedCity = cityName;
        double requestedLatitude = cityLatitude, requestedLongitude = cityLongitude;
        long requestedLocationVersion = weatherLocationVersion;
        new Thread(() -> {
            JSONObject result = null;
            try { result = PublicForecastClient.fetch(requestedLatitude, requestedLongitude).put("city", requestedCity); }
            catch (Exception e) {
                android.util.Log.w("LinarTV", "Weather refresh failed", e);
                result = null;
            }
            final JSONObject fetched = result;
            handler.post(() -> {
                forecastLoading = false;
                if (fetched != null && requestedLocationVersion == weatherLocationVersion) {
                    forecast = fetched;
                    forecastUpdatedAt = System.currentTimeMillis();
                    getPreferences(MODE_PRIVATE).edit().putString("weather_json", fetched.toString())
                            .putLong("weather_updated", forecastUpdatedAt).apply();
                    if (weatherSettingsRefreshPending) {
                        weatherSettingsRefreshPending = false;
                        applyVisualSettingsNow();
                    } else buildScreen();
                }
                if (requestedLocationVersion != weatherLocationVersion) {
                    forecastAttemptedAt = 0;
                    refreshWeather();
                }
            });
        }, "weather-fetch").start();
    }

    private JSONObject currentWeather() {
        return forecast == null ? null : forecast.optJSONObject("current");
    }

    private String currentWeatherTime() {
        JSONObject current = currentWeather();
        if (current == null || current.optLong("time") <= 0) return tr("brak danych", "no data");
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy, HH:mm", displayLocale());
        format.setTimeZone(forecastZone());
        return format.format(new Date(current.optLong("time")));
    }

    private TimeZone forecastZone() {
        return TimeZone.getTimeZone(forecast == null ? TimeZone.getDefault().getID()
                : forecast.optString("timezone", TimeZone.getDefault().getID()));
    }

    private String observed(String field, String unit) {
        JSONObject current = currentWeather();
        if (current == null || !current.has(field) || current.isNull(field)) return "—";
        double value = current.optDouble(field, Double.NaN);
        if (Double.isNaN(value)) return "—";
        String text = Math.abs(value - Math.rint(value)) < 0.05 ? String.valueOf(Math.round(value))
                : String.format(PL, "%.1f", value);
        return text + unit;
    }

    private void restoreCalendar(CalendarFeed feed, SharedPreferences saved) {
        if (!feed.configured()) return;
        // Older caches discarded competition metadata and could label cup ties as league rounds.
        if (saved.getInt(feed.key + "_calendar_format", 0) != LeagueCatalogClient.CACHE_FORMAT) return;
        feed.updatedAt = saved.getLong(feed.key + "_calendar_updated", 0);
        String apiCache = saved.getString(feed.key + "_calendar_matches", "");
        if (!apiCache.isEmpty()) {
            try {
                JSONArray rows = new JSONArray(apiCache);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    feed.matches.add(new IcsMatchParser.Match(row.getString("home"), row.getString("away"),
                            row.getLong("startsAt"), row.getBoolean("hasTime"), row.optInt("round", -1),
                            row.optString("competition", ""), row.optString("stage", ""),
                            row.optInt("homeTeamId", 0), row.optInt("awayTeamId", 0),
                            row.optLong("homeCrestVer", 0), row.optLong("awayCrestVer", 0)));
                }
                if (crests != null) crests.noteVersions(feed.matches);
                return;
            } catch (Exception e) { feed.matches.clear(); }
        }
        String ics = saved.getString(feed.key + "_calendar_ics", "");
        if (ics.startsWith("BEGIN:VCALENDAR"))
            feed.matches = IcsMatchParser.parse(ics, feed.requiredDescription,
                    System.currentTimeMillis() - CALENDAR_PARSE_LOOKBACK_MS);
    }

    private CalendarFeed configuredCalendar(SharedPreferences saved, int slot) {
        String key = "club" + slot;
        return new CalendarFeed(key, saved.getString(key + "_url", ""),
                saved.getString(key + "_name", ""), saved.getString(key + "_league", ""),
                saved.getString(key + "_league_slug", ""));
    }

    private void refreshCalendar(CalendarFeed feed) {
        if (!feed.configured()) return;
        long now = System.currentTimeMillis();
        boolean hasApiCache = !getPreferences(MODE_PRIVATE)
                .getString(feed.key + "_calendar_matches", "").isEmpty();
        if (feed.loading || (hasApiCache && now - feed.updatedAt < CALENDAR_REFRESH_MS
                && !upcomingMatches(feed, now).isEmpty())
                || now - feed.attemptedAt < 30 * 60 * 1000) return;
        feed.loading = true;
        feed.attemptedAt = now;
        new Thread(() -> {
            try {
                    long from = System.currentTimeMillis() - CALENDAR_PARSE_LOOKBACK_MS;
                    List<IcsMatchParser.Match> matches = LeagueCatalogClient.teamMatches(feed.url, from);
                    JSONArray rows = new JSONArray();
                    for (IcsMatchParser.Match match : matches) {
                        JSONObject row = new JSONObject();
                        row.put("home", match.home).put("away", match.away)
                                .put("homeTeamId", match.homeTeamId).put("awayTeamId", match.awayTeamId)
                                .put("homeCrestVer", match.homeCrestVer).put("awayCrestVer", match.awayCrestVer)
                                .put("startsAt", match.startsAt).put("hasTime", match.hasTime)
                                .put("round", match.round).put("competition", match.competition)
                                .put("stage", match.stage);
                        rows.put(row);
                    }
                    handler.post(() -> {
                        if (feed != clubOneCalendar && feed != clubTwoCalendar) return;
                        feed.loading = false;
                        feed.matches = matches;
                        crests.noteVersions(matches); // before the cards ask for crests
                        feed.updatedAt = System.currentTimeMillis();
                        feed.selected = 0;
                        getPreferences(MODE_PRIVATE).edit().putString(feed.key + "_calendar_matches", rows.toString())
                                .putInt(feed.key + "_calendar_format", LeagueCatalogClient.CACHE_FORMAT)
                                .remove(feed.key + "_calendar_ics")
                                .putLong(feed.key + "_calendar_updated", feed.updatedAt).apply();
                        finishCalendarSettingsRefresh(feed);
                        crests.refreshIfDue(clubOneCalendar.matches, clubTwoCalendar.matches, true);
                    });
            } catch (Exception e) {
                android.util.Log.w("LinarTV", "Team calendar refresh failed; keeping cached matches", e);
                handler.post(() -> {
                    if (feed != clubOneCalendar && feed != clubTwoCalendar) return;
                    feed.loading = false;
                    finishCalendarSettingsRefresh(feed);
                });
            }
        }, "calendar-" + feed.key).start();
    }

    private boolean isForecastCurrent() {
        if (forecast == null) return false;
        try {
            String firstDay = forecast.getJSONObject("daily").getJSONArray("time").getString(0);
            return firstDay.equals(new SimpleDateFormat("yyyy-MM-dd", PL).format(new Date()));
        } catch (Exception ignored) { return false; }
    }

    private int px(float value) { return Math.round(value * scale); }

    private void place(View view, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        root.addView(view, lp);
    }

    private GradientDrawable shape(int fill, int border, int radius) {
        return shape(fill, border, radius, 1);
    }

    private GradientDrawable shape(int fill, int border, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(px(radius));
        d.setStroke(Math.max(1, px(stroke)), border);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold, int x, int y, int w, int h) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        place(view, x, y, w, h);
        return view;
    }

    private void panel(int x, int y, int w, int h) {
        View view = new View(this);
        view.setBackground(shape(panel, outline, 29));
        place(view, x, y, w, h);
    }

    private void line(int x, int y, int width) {
        View view = new View(this);
        view.setBackgroundColor(outline);
        place(view, x, y, width, 1);
    }

    private void verticalLine(int x, int y, int height) {
        View view = new View(this);
        view.setBackgroundColor(outline);
        place(view, x, y, 1, height);
    }

    private TextView action(String label, int x, int y, int w, int h, Runnable click) {
        TextView view = text(label, 19, ink, false, x, y, w, h);
        view.setGravity(Gravity.CENTER);
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(shape(panel, outline, 18));
        view.setOnFocusChangeListener((v, hasFocus) -> {
            view.setBackground(shape(hasFocus ? (night ? 0xCC416B9E : 0xFFFFFFFF) : panel,
                    hasFocus ? 0xFF4C9CFF : outline, 18));
            view.setScaleX(hasFocus ? 1.04f : 1f);
            view.setScaleY(hasFocus ? 1.04f : 1f);
        });
        view.setOnClickListener(v -> click.run());
        return view;
    }

    private ImageView logo(int drawable, int x, int y, int w, int h) {
        ImageView image = new ImageView(this);
        int targetWidth = px(w);
        int targetHeight = px(h);
        String cacheKey = drawable + ":" + targetWidth + ":" + targetHeight;
        Bitmap scaled = logoCache.get(cacheKey);
        if (scaled == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap current = BitmapFactory.decodeResource(getResources(), drawable, options);
            if (current != null) {
                float ratio = Math.min(targetWidth / (float) current.getWidth(), targetHeight / (float) current.getHeight());
                int finalWidth = Math.max(1, Math.round(current.getWidth() * ratio));
                int finalHeight = Math.max(1, Math.round(current.getHeight() * ratio));
                while (current.getWidth() > finalWidth * 2 && current.getHeight() > finalHeight * 2) {
                    Bitmap next = Bitmap.createScaledBitmap(current, Math.max(finalWidth, current.getWidth() / 2),
                            Math.max(finalHeight, current.getHeight() / 2), true);
                    if (next != current) current.recycle();
                    current = next;
                }
                Bitmap result = Bitmap.createScaledBitmap(current, finalWidth, finalHeight, true);
                if (result != current) current.recycle();
                scaled = result;
                logoCache.put(cacheKey, scaled);
            }
        }
        if (scaled != null) image.setImageBitmap(scaled);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        place(image, x, y, w, h);
        return image;
    }

    private void buildScreen() {
        if (isFinishing() || isDestroyed()) return;
        // Keep nested dialogs and their captured backdrop attached to the same dashboard.
        if (popupOpen) { rebuildAfterPopup = true; return; }
        boolean restoreScreensaver = screensaverActive;
        if (restoreScreensaver) hideScreensaver();
        boolean restoreDrawer = appDrawerOpen;
        int restoreRow = 0, restoreColumn = 0;
        if (restoreDrawer) {
            View focused = getCurrentFocus();
            for (int row = 0; row < extraAppRows.size(); row++) {
                int column = extraAppRows.get(row).indexOf(focused);
                if (column >= 0) { restoreRow = row; restoreColumn = column; break; }
            }
        }
        final int focusRow = restoreRow, focusColumn = restoreColumn;
        if (drawerBlurBitmap != null) {
            if (drawerBlurImage != null) drawerBlurImage.setImageDrawable(null);
            drawerBlurBitmap.recycle();
            drawerBlurBitmap = null;
        }
        suspendedFocusables.clear();
        crestSlots.clear();
        clubOneCalendar.cardViews.clear();
        clubTwoCalendar.cardViews.clear();
        dockCards.clear();
        dockGlows.clear();
        dockLabels.clear();
        rocketCard = null;
        appDrawerOpen = false;
        lastDockFocus = null;
        night = isNight();
        ink = night ? Color.WHITE : 0xFF15223E;
        muted = night ? 0xFFC6D1E4 : 0xFF34435B;
        panel = night ? 0x80102443 : 0x80FAF7EF;
        outline = night ? 0x888292AF : 0xAAFFFFFF;

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1F3B);
        setContentView(root);
        ImageView wallpaper = new ImageView(this);
        showSavedWallpaper(wallpaper);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        // The design's own background is already darkened; photos keep the readability veil.
        glassHosts.clear();
        View veil = null;
        if (!"cosmos".equals(selectedWallpaper())) {
            veil = new View(this);
            veil.setBackgroundColor(0x660A1036);
            root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        }

        renderDesignHeader();
        renderSpotifyBar();
        designCircleAction(R.drawable.ic_ds_tv, tr("Oglądaj telewizję", "Watch TV"), 1556,
                () -> launch("org.droidtv.playtv"));
        designCircleAction(R.drawable.ic_ds_input, tr("Źródła", "Inputs"), 1652, this::showSources);
        designCircleAction(R.drawable.ic_ds_settings, tr("Ustawienia", "Settings"), 1748,
                this::showPublicSettings);
        renderPhotosButton();
        renderDesignWeather(712, 64, 788, 208);
        renderDesignEvents();
        renderLightRow();
        buildAppDock();
        prepareBackdropBlur(wallpaper, veil, 0);
        handler.removeCallbacks(updateTime);
        updateTime.run();
        scheduleNextMatchBoundary();
        if (restoreScreensaver) showScreensaver();
        if (restoreDrawer) handler.post(() -> openAppDrawer(focusRow, focusColumn));
    }

    // ======================= Design 1.2: "Launcher TV — ciemny" =======================
    // The home screen follows a 1920×1080 design. Design pixels are converted with DS
    // into the launcher's own 1672×941 grid, so every number below matches the design file.
    private static final float DS = W / 1920f;
    private static final int D_INK = 0xFFF4F6FB;
    private static final int D_ACCENT = 0xFF7FA8FF;
    private static final int D_GLASS = 0x660E1630;
    private static final int D_GLASS_BORDER = 0x1FFFFFFF;
    private static final float D_LEFT = 96, D_CONTENT_W = 1728;
    private static final float D_EVENTS_Y = 324, D_EVENTS_H = 320, D_EVENTS_GAP = 32;
    private static final float D_LIGHTS_Y = 680, D_LIGHT_H = 96, D_LIGHT_GAP = 20;
    private static final float D_DOCK_Y = 856, D_DOCK_ICON = 140, D_DOCK_GAP = 24;
    private static final float D_AURA_PAD = 70;
    private static final int MAX_LIGHTS = 6;
    private final android.util.SparseArray<Typeface> manropeFaces = new android.util.SparseArray<>();
    private final List<View> dockLabels = new ArrayList<>();

    private static int inkAlpha(float alpha) {
        return (Math.round(alpha * 255) << 24) | (D_INK & 0xFFFFFF);
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255) << 24) | (color & 0xFFFFFF);
    }

    /** Design pixels (1920 wide) to screen pixels. */
    private int pxD(float value) { return Math.round(value * DS * scale); }

    private FrameLayout.LayoutParams paramsD(float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pxD(w), pxD(h));
        lp.leftMargin = pxD(x);
        lp.topMargin = pxD(y);
        return lp;
    }

    private <T extends View> T placeD(T view, float x, float y, float w, float h) {
        root.addView(view, paramsD(x, y, w, h));
        return view;
    }

    private <T extends View> T addD(FrameLayout parent, T view, float x, float y, float w, float h) {
        if (parent == null) return placeD(view, x, y, w, h);
        parent.addView(view, paramsD(x, y, w, h));
        return view;
    }

    /** Manrope (OFL, bundled) at a given weight; falls back to the system font. */
    private Typeface manrope(int weight) {
        Typeface face = manropeFaces.get(weight);
        if (face != null) return face;
        try {
            face = new Typeface.Builder(getAssets(), "fonts/Manrope.ttf")
                    .setFontVariationSettings("'wght' " + weight).setWeight(weight).build();
        } catch (Exception e) { face = null; }
        if (face == null) face = weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        manropeFaces.put(weight, face);
        return face;
    }

    private TextView dText(FrameLayout parent, CharSequence value, float size, int weight, int color,
                           float x, float y, float w, float h) {
        // Keep room for the font's full line (ascenders, descenders) around the same centre.
        float minH = size * 1.38f;
        if (h < minH) { y -= (minH - h) / 2f; h = minH; }
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxD(size));
        view.setTypeface(manrope(weight));
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return addD(parent, view, x, y, w, h);
    }

    private ImageView dIcon(FrameLayout parent, int drawable, int tint, float x, float y, float size) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawable);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tint != 0) icon.setColorFilter(tint);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return addD(parent, icon, x, y, size, size);
    }

    /** Width of a text in design pixels. */
    private float measureD(TextView view, CharSequence value) {
        return android.text.Layout.getDesiredWidth(value, view.getPaint()) / (DS * scale);
    }

    /** Glass fill; radius in design px, negative = circle. */
    private GradientDrawable glass(float radius) {
        GradientDrawable d = new GradientDrawable();
        if (radius < 0) d.setShape(GradientDrawable.OVAL);
        else d.setCornerRadius(pxD(radius));
        d.setColor(D_GLASS);
        d.setStroke(Math.max(1, pxD(1)), D_GLASS_BORDER);
        return d;
    }

    /** Clips content to the card's shape without casting a shadow. */
    private android.view.ViewOutlineProvider clipOutline(float radius) {
        return new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                if (radius < 0) outline.setOval(0, 0, view.getWidth(), view.getHeight());
                else outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), pxD(radius));
                outline.setAlpha(0f);
            }
        };
    }

    private FrameLayout dCard(float x, float y, float w, float h, float radius) {
        RoundClipFrame card = new RoundClipFrame(radius);
        card.setBackground(new BackdropGlass(card, radius, D_GLASS, D_GLASS_BORDER));
        // No clipToOutline: GPU outline clipping is not anti-aliased and showed steps on curves.
        card.setOutlineProvider(clipOutline(radius));
        glassHosts.add(card);
        return placeD(card, x, y, w, h);
    }

    // ---------- Backdrop blur (Android 9 has no RenderEffect) ----------
    // The wallpaper is static, so one heavily blurred, slightly saturated copy of it is made per
    // build; every glass card paints its own piece of that copy under the tint ("backdrop-filter").

    private static final int BACKDROP_SCALE = 6;
    private Bitmap backdropBlur;
    private final List<View> glassHosts = new ArrayList<>();
    private static final android.graphics.ColorMatrixColorFilter BACKDROP_SATURATE;
    static {
        android.graphics.ColorMatrix saturate = new android.graphics.ColorMatrix();
        saturate.setSaturation(1.5f);
        BACKDROP_SATURATE = new android.graphics.ColorMatrixColorFilter(saturate);
    }

    private void prepareBackdropBlur(View wallpaper, View veil, int attempt) {
        handler.postDelayed(() -> {
            if (wallpaper.getParent() != root) return;
            int w = root.getWidth(), h = root.getHeight();
            if (w <= 0 || h <= 0 || wallpaper.getWidth() <= 0) {
                if (attempt < 20) prepareBackdropBlur(wallpaper, veil, attempt + 1);
                return;
            }
            Bitmap small = Bitmap.createBitmap(Math.max(1, w / BACKDROP_SCALE), Math.max(1, h / BACKDROP_SCALE),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(small);
            canvas.scale(small.getWidth() / (float) w, small.getHeight() / (float) h);
            wallpaper.draw(canvas);
            if (veil != null) veil.draw(canvas);
            blurBackdrop(small, 6, 3);
            backdropBlur = small;
            for (View host : glassHosts) {
                host.invalidate();
                if (host.getBackground() != null) host.getBackground().invalidateSelf();
            }
        }, attempt == 0 ? 0 : 100);
    }

    /** Glass: the blurred wallpaper under the card, a translucent tint and a hairline border. */
    private final class BackdropGlass extends Drawable {
        private final View host;
        private final float radius;
        private final int tint, border;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();

        BackdropGlass(View host, float radius, int tint, int border) {
            this.host = host; this.radius = radius; this.tint = tint; this.border = border;
        }

        @Override public void draw(Canvas canvas) {
            RectF r = new RectF(getBounds());
            float corner = radius < 0 ? Math.min(r.width(), r.height()) / 2f : pxD(radius);
            Bitmap blur = backdropBlur;
            paint.setStyle(Paint.Style.FILL);
            if (blur != null && !blur.isRecycled() && root != null && root.getWidth() > 0) {
                android.graphics.BitmapShader shader = new android.graphics.BitmapShader(blur,
                        android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                matrix.setScale(root.getWidth() / (float) blur.getWidth(), root.getHeight() / (float) blur.getHeight());
                matrix.postTranslate(-host.getLeft(), -host.getTop());
                shader.setLocalMatrix(matrix);
                paint.setShader(shader);
                paint.setColorFilter(BACKDROP_SATURATE);
                canvas.drawRoundRect(r, corner, corner, paint);
                paint.setShader(null);
                paint.setColorFilter(null);
                paint.setColor(tint);
            } else paint.setColor(withAlpha(tint, Math.min(1f, (tint >>> 24) / 255f + 0.2f)));
            canvas.drawRoundRect(r, corner, corner, paint);
            if (border != 0) {
                float stroke = Math.max(1, pxD(1));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(border);
                r.inset(stroke / 2f, stroke / 2f);
                canvas.drawRoundRect(r, Math.max(0, corner - stroke / 2f), Math.max(0, corner - stroke / 2f), paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * A card that can clip its content (photos, the faded crest) to its rounded shape with an
     * anti-aliased mask. Clipping is off by default because it costs an offscreen layer.
     */
    private final class RoundClipFrame extends FrameLayout {
        final float radius;
        private boolean clipContent;
        private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path outside = new android.graphics.Path();

        RoundClipFrame(float radius) {
            super(DashboardActivity.this);
            this.radius = radius;
            mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        }

        void clipContent() {
            clipContent = true;
            // Rendered once into a texture; redrawn only when the card changes.
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            if (!clipContent || getChildCount() == 0) { super.dispatchDraw(canvas); return; }
            int w = getWidth(), h = getHeight();
            int save = canvas.saveLayer(0, 0, w, h, null);
            super.dispatchDraw(canvas);
            float corner = radius < 0 ? Math.min(w, h) / 2f : pxD(radius);
            outside.reset();
            outside.addRoundRect(new RectF(0, 0, w, h), corner, corner, android.graphics.Path.Direction.CW);
            outside.setFillType(android.graphics.Path.FillType.INVERSE_WINDING);
            canvas.drawPath(outside, mask);
            canvas.restoreToCount(save);
        }
    }

    /** Accent aura behind a card; invisible until the card has focus. */
    private View dFocusAura(float x, float y, float w, float h, float radius) {
        View aura = new View(this);
        aura.setBackground(new FocusAura(radius));
        aura.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        aura.setOutlineProvider(null);
        aura.setAlpha(0f);
        aura.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return placeD(aura, x - D_AURA_PAD, y - D_AURA_PAD, w + 2 * D_AURA_PAD, h + 2 * D_AURA_PAD);
    }

    /** Makes a card focusable with the design's focus: scale 1.05, white ring, accent aura, sheen. */
    private Sheen prepareDesignFocus(View card) {
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        float radius = card instanceof RoundClipFrame ? ((RoundClipFrame) card).radius : -1;
        Sheen sheen = new Sheen(radius < 0 ? -1 : pxD(radius));
        card.setForeground(sheen);
        return sheen;
    }

    private void dFocusable(View card, View aura, java.util.function.Consumer<Boolean> extra) {
        Sheen sheen = prepareDesignFocus(card);
        card.setOnFocusChangeListener((v, focused) -> {
            showDesignFocus(card, aura, sheen, focused);
            if (extra != null) extra.accept(focused);
        });
    }

    private void showDesignFocus(View card, View aura, Sheen sheen, boolean focused) {
        float s = focused ? 1.05f : 1f;
        android.view.animation.Interpolator ease =
                new android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
        card.animate().scaleX(s).scaleY(s).translationZ(focused ? pxD(8) : 0)
                .setDuration(280).setInterpolator(ease).start();
        if (aura != null) {
            aura.setTranslationZ(focused ? pxD(7) : 0);
            aura.animate().alpha(focused ? 1f : 0f).scaleX(s).scaleY(s)
                    .setDuration(focused ? 240 : 160).setInterpolator(ease).start();
        }
        if (sheen != null) { if (focused) sheen.start(); else sheen.stop(); }
    }

    /** Outer focus decoration: accent glow, a 9 px accent halo and a 3 px white ring. */
    private final class FocusAura extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;
        FocusAura(float radius) { this.radius = radius; }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float pad = pxD(D_AURA_PAD);
            RectF card = new RectF(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad);
            float r = radius < 0 ? card.height() / 2f : pxD(radius);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(D_ACCENT, 0.55f));
            paint.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1, pxD(34)),
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            RectF lowered = new RectF(card);
            lowered.offset(0, pxD(18));
            canvas.drawRoundRect(lowered, r, r, paint);
            paint.setMaskFilter(null);
            // Translucent cards must not pick the glow up through their glass.
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            canvas.drawRoundRect(card, r, r, paint);
            paint.setXfermode(null);
            paint.setStyle(Paint.Style.STROKE);
            float ring = pxD(3), halo = pxD(6);
            paint.setStrokeWidth(halo);
            paint.setColor(withAlpha(D_ACCENT, 0.30f));
            RectF haloRect = new RectF(card);
            haloRect.inset(-(ring + halo / 2f), -(ring + halo / 2f));
            canvas.drawRoundRect(haloRect, r + ring + halo / 2f, r + ring + halo / 2f, paint);
            paint.setStrokeWidth(ring);
            paint.setColor(0xD9FFFFFF);
            RectF ringRect = new RectF(card);
            ringRect.inset(-ring / 2f, -ring / 2f);
            canvas.drawRoundRect(ringRect, r + ring / 2f, r + ring / 2f, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** A light band sweeping across the focused card every 2.6 s (design "sheen"). */
    private static final class Sheen extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.view.animation.Interpolator ease =
                new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f);
        private static final int[] COLORS = {0x00FFFFFF, 0x4DFFFFFF, 0x14FFFFFF, 0x00FFFFFF};
        private static final float[] STOPS = {0f, 0.47f, 0.6f, 1f};
        private android.animation.ValueAnimator animator;
        private float phase;
        /** Corner radius in px; negative = circle. The band is drawn inside the card's own shape. */
        private final float cornerPx;
        Sheen(float cornerPx) { this.cornerPx = cornerPx; }

        void start() {
            if (animator != null) return;
            animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2600);
            animator.setStartDelay(200);
            animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            animator.setInterpolator(new android.view.animation.LinearInterpolator());
            animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidateSelf(); });
            phase = 0f;
            animator.start();
        }

        void stop() {
            if (animator != null) animator.cancel();
            animator = null;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            if (animator == null) return;
            float t = phase / 0.55f;
            if (t <= 0f || t >= 1f) return;
            t = ease.getInterpolation(t);
            android.graphics.Rect b = getBounds();
            float w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;
            float cx = b.left + (-0.5f + 2f * t) * w, cy = b.exactCenterY();
            float half = Math.max(w, h) * 0.45f;
            float dx = half * 0.906f, dy = half * 0.423f; // CSS 115deg
            paint.setShader(new android.graphics.LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy,
                    COLORS, STOPS, android.graphics.Shader.TileMode.CLAMP));
            float corner = cornerPx < 0 ? Math.min(w, h) / 2f : cornerPx;
            canvas.drawRoundRect(new RectF(b), corner, corner, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** "odświeżono 21:44" today, "odświeżono 26.10 21:44" for older data. */
    private String stampLabel(long updatedAt) {
        if (updatedAt <= 0) return tr("oczekiwanie na dane", "waiting for data");
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(updatedAt);
        Calendar now = Calendar.getInstance();
        boolean today = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
        return tr("odświeżono ", "updated ") + new SimpleDateFormat(today ? "HH:mm" : "d.MM HH:mm",
                displayLocale()).format(new Date(updatedAt));
    }

    /** Card header: icon, title and a right-aligned refresh stamp sharing one line. */
    private void cardHeader(FrameLayout card, float w, int icon, String title, String stamp, float alpha) {
        dIcon(card, icon, inkAlpha(alpha), 34, 33, 24);
        TextView stampView = dText(card, stamp, 16, 500, inkAlpha(0.58f), 0, 30, 10, 30);
        float stampW = Math.min(240, measureD(stampView, stamp) + 6);
        stampView.setLayoutParams(paramsD(w - 34 - stampW, 30, stampW, 30));
        stampView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView label = dText(card, title, 22, 600, inkAlpha(alpha), 70, 30,
                Math.max(60, w - 70 - 34 - stampW - 14), 30);
        label.setAutoSizeTextTypeUniformWithConfiguration(pxD(16), pxD(22), 1, TypedValue.COMPLEX_UNIT_PX);
    }

    /** Small rounded label at the card's bottom right ("WYJAZD", "U SIEBIE", "NA ŻYWO"). */
    private void cardPill(FrameLayout card, float w, float h, String value, int fill, int border, int color) {
        String upper = value.toUpperCase(displayLocale());
        TextView pill = dText(card, upper, 16, 700, color, 0, 0, 10, 36);
        pill.setLetterSpacing(0.09f);
        pill.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(pxD(18));
        background.setColor(fill);
        background.setStroke(Math.max(1, pxD(1)), border);
        pill.setBackground(background);
        float pillW = measureD(pill, upper) * 1.09f + 30;
        pill.setLayoutParams(paramsD(w - 34 - pillW, h - 30 - 36, pillW, 36));
    }

    /** "Pt. 30.10 · za 4 dni" */
    private String relativeDayLabel(long millis) {
        String day = new SimpleDateFormat("EEE d.MM", displayLocale()).format(new Date(millis));
        day = Character.toUpperCase(day.charAt(0)) + day.substring(1);
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance(); then.setTimeInMillis(millis);
        now.set(Calendar.HOUR_OF_DAY, 12); now.set(Calendar.MINUTE, 0); now.set(Calendar.SECOND, 0);
        then.set(Calendar.HOUR_OF_DAY, 12); then.set(Calendar.MINUTE, 0); then.set(Calendar.SECOND, 0);
        int days = (int) Math.round((then.getTimeInMillis() - now.getTimeInMillis()) / 86_400_000.0);
        return day + (days < 0 ? "" : days == 0 ? tr(" · dziś", " · today") : days == 1
                ? tr(" · jutro", " · tomorrow") : tr(" · za ", " · in ") + days + tr(" dni", " days"));
    }

    // ---------- Header: clock, weather, system buttons ----------

    private void renderDesignHeader() {
        clock = dText(null, "", 168, 300, D_INK, 90, 60, 580, 160);
        clock.setLetterSpacing(-6f / 168f);
        date = dText(null, "", 34, 500, inkAlpha(0.74f), 102, 222, 570, 48);
        date.setAutoSizeTextTypeUniformWithConfiguration(pxD(24), pxD(34), 1, TypedValue.COMPLEX_UNIT_PX);
    }

    private void designCircleAction(int icon, String description, float x, Runnable click) {
        View aura = dFocusAura(x, 64, 76, 76, -1);
        FrameLayout button = dCard(x, 64, 76, 76, -1);
        dIcon(button, icon, D_INK, 23, 23, 30);
        button.setContentDescription(description);
        dFocusable(button, aura, null);
        button.setOnClickListener(v -> click.run());
    }

    private void renderDesignWeather(float x, float y, float w, float h) {
        View aura = dFocusAura(x, y, w, h, 32);
        FrameLayout card = dCard(x, y, w, h, 32);
        dIcon(card, R.drawable.ic_ds_pin, D_INK, 32, 27, 24);
        String stamp = stampLabel(forecastUpdatedAt);
        TextView stampView = dText(card, stamp, 16, 500, inkAlpha(0.58f), 0, 24, 10, 30);
        float stampW = Math.min(260, measureD(stampView, stamp) + 6);
        stampView.setLayoutParams(paramsD(w - 32 - stampW, 24, stampW, 30));
        stampView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        dText(card, cityName, 24, 700, D_INK, 66, 22, w - 66 - 32 - stampW - 16, 34);

        String currentIcon = forecast == null ? "" : forecast.optString("current_icon", "");
        ImageView now = new ImageView(this);
        now.setScaleType(ImageView.ScaleType.FIT_CENTER);
        now.setImageBitmap(weatherIcons.bitmap(currentIcon, pxD(96)));
        addD(card, now, 24, 80, 96, 96);
        JSONObject current = currentWeather();
        CharSequence tempLabel = current == null || !current.has("temperature") ? "—"
                : temperatureLabel(current.optDouble("temperature", 0));
        TextView temp = dText(card, tempLabel, 92, 600, D_INK, 128, 72, 176, 112);
        temp.setLetterSpacing(-3f / 92f);
        temp.setAutoSizeTextTypeUniformWithConfiguration(pxD(56), pxD(92), 2, TypedValue.COMPLEX_UNIT_PX);
        View divider = new View(this);
        divider.setBackgroundColor(0x1FFFFFFF);
        addD(card, divider, 320, 80, 1, 104);

        List<JSONObject> hours = hourlySamples(6);
        float areaX = 340, areaW = w - 32 - areaX, colW = areaW / 6f;
        SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", displayLocale());
        hourFormat.setTimeZone(forecastZone());
        for (int i = 0; i < hours.size(); i++) {
            JSONObject item = hours.get(i);
            float hx = areaX + i * colW;
            dText(card, hourFormat.format(new Date(item.optLong("time"))), 17, 600, inkAlpha(0.64f),
                    hx, 76, colW, 24).setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageBitmap(weatherIcons.bitmap(item.optString("icon", ""), pxD(48)));
            addD(card, icon, hx + (colW - 48) / 2f, 102, 48, 48);
            dText(card, temperatureLabel(item.optDouble("temperature")), 21, 700, D_INK,
                    hx, 152, colW, 30).setGravity(Gravity.CENTER);
        }
        if (hours.isEmpty()) dText(card, tr("Pobieranie prognozy godzinowej…", "Loading hourly forecast…"),
                18, 600, inkAlpha(0.68f), areaX, 112, areaW, 40).setGravity(Gravity.CENTER);
        card.setContentDescription(tr("Pogoda ", "Weather ") + cityName
                + tr(". Otwórz szczegóły", ". Open details"));
        dFocusable(card, aura, null);
        card.setOnClickListener(v -> showWeatherDetails());
    }

    // ---------- Events row: one or two clubs and the rocket launch ----------

    private void renderDesignEvents() {
        List<CalendarFeed> clubs = new ArrayList<>();
        if (clubOneCalendar.configured()) {
            clubs.add(clubOneCalendar);
            if (clubTwoCalendar.configured()) clubs.add(clubTwoCalendar);
        }
        int count = Math.max(1, clubs.size()) + 1;
        float w = (D_CONTENT_W - (count - 1) * D_EVENTS_GAP) / count;
        float x = D_LEFT;
        if (clubs.isEmpty()) {
            renderDesignSetupCard(x, w);
            x += w + D_EVENTS_GAP;
        }
        for (CalendarFeed feed : clubs) {
            renderMatchCard(x, w, feed);
            x += w + D_EVENTS_GAP;
        }
        renderRocketCard(x, w);
    }

    private void renderDesignSetupCard(float x, float w) {
        float y = D_EVENTS_Y, h = D_EVENTS_H;
        View aura = dFocusAura(x, y, w, h, 32);
        FrameLayout card = dCard(x, y, w, h, 32);
        cardHeader(card, w, R.drawable.ic_ds_shield, tr("Kalendarz meczów", "Match calendar"), "", 0.8f);
        dText(card, tr("Wybierz swój klub", "Choose your club"), 36, 800, D_INK, 34, 110, w - 68, 46);
        TextView hint = dText(card, tr("Możesz dodać jeden lub dwa kluby z aktualnych lig.",
                "Add one or two clubs from available leagues."), 20, 600, inkAlpha(0.68f), 34, 168, w - 68, 60);
        hint.setSingleLine(false);
        hint.setMaxLines(2);
        hint.setGravity(Gravity.TOP);
        card.setContentDescription(tr("Wybierz klub do kalendarza meczów", "Choose a club for the match calendar"));
        dFocusable(card, aura, null);
        card.setOnClickListener(v -> showTeamSetup());
    }

    // ---------- Rocket launch (Home Assistant: Rocket Launch Live, sensor.rocket_launch_1..5) ----------

    private static final long ROCKET_POLL_MS = 2L * 60 * 1000;
    private static final long ROCKET_IMAGE_RETRY_MS = 30L * 60 * 1000;
    /** A launch stays on the card for an hour after its planned lift-off. */
    private static final long ROCKET_VISIBLE_AFTER_MS = 60L * 60 * 1000;
    private final List<HomeAssistantClient.Launch> launches = new ArrayList<>();
    private long launchesUpdatedAt;
    private boolean launchesLoading;
    private String launchesError;
    private FrameLayout rocketCard;
    private float rocketCardW;
    private Bitmap rocketImageOff, rocketImageOn;
    private boolean rocketImagesLoading;
    private long rocketImagesAttemptedAt;

    private boolean haConnected() {
        return !haUrl.isEmpty() && !haToken.isEmpty();
    }

    private final Runnable pollRockets = new Runnable() {
        @Override public void run() {
            handler.removeCallbacks(this);
            if (!dashboardVisible || !haConnected()) return;
            if (!launchesLoading && !screensaverActive) {
                launchesLoading = true;
                final String address = haUrl;
                final HomeAssistantClient client = new HomeAssistantClient(haUrl, haToken);
                new Thread(() -> {
                    List<HomeAssistantClient.Launch> result = null;
                    String error = null;
                    try { result = client.launches(); }
                    catch (HomeAssistantClient.AuthException e) { error = tr("token odrzucony", "token rejected"); }
                    catch (IOException e) { error = tr("brak połączenia z HA", "no connection to HA"); }
                    final List<HomeAssistantClient.Launch> found = result;
                    final String failure = error;
                    handler.post(() -> {
                        launchesLoading = false;
                        if (!address.equals(haUrl)) return;
                        if (found != null) {
                            launches.clear();
                            launches.addAll(found);
                            launchesUpdatedAt = System.currentTimeMillis();
                            launchesError = null;
                            saveLaunches();
                        } else launchesError = failure;
                        refreshRocketCard();
                    });
                }, "rocket-launches").start();
            }
            loadRocketImages();
            handler.postDelayed(this, ROCKET_POLL_MS);
        }
    };

    private void restoreLaunches(SharedPreferences saved) {
        launches.clear();
        launchesUpdatedAt = saved.getLong("rocket_updated", 0);
        try {
            JSONArray items = new JSONArray(saved.getString("rocket_json", "[]"));
            for (int i = 0; i < items.length(); i++)
                launches.add(new HomeAssistantClient.Launch(items.getJSONObject(i)));
        } catch (Exception ignored) { launches.clear(); }
    }

    private void saveLaunches() {
        JSONArray items = new JSONArray();
        try { for (HomeAssistantClient.Launch launch : launches) items.put(launch.toJson()); }
        catch (Exception ignored) { return; }
        getPreferences(MODE_PRIVATE).edit().putString("rocket_json", items.toString())
                .putLong("rocket_updated", launchesUpdatedAt).apply();
    }

    /** The next launch that has not lifted off more than an hour ago. */
    private HomeAssistantClient.Launch currentLaunch() {
        long now = System.currentTimeMillis();
        for (HomeAssistantClient.Launch launch : launches)
            if (launch.startsAt == 0 || launch.startsAt + ROCKET_VISIBLE_AFTER_MS > now) return launch;
        return null;
    }

    /** The same pictures as the HA dashboard card: /local/rocket_off.jpg and rocket_on.jpg (20 min before lift-off). */
    private void loadRocketImages() {
        if (!haConnected() || rocketImagesLoading || rocketImageOff != null && rocketImageOn != null) return;
        long now = System.currentTimeMillis();
        if (now - rocketImagesAttemptedAt < ROCKET_IMAGE_RETRY_MS) return;
        rocketImagesAttemptedAt = now;
        rocketImagesLoading = true;
        final HomeAssistantClient client = new HomeAssistantClient(haUrl, haToken);
        final int target = Math.max(320, pxD(720));
        new Thread(() -> {
            Bitmap off = rocketImageOff != null ? rocketImageOff : decodeRocketImage(client, "/local/rocket_off.jpg", target);
            Bitmap on = rocketImageOn != null ? rocketImageOn : decodeRocketImage(client, "/local/rocket_on.jpg", target);
            handler.post(() -> {
                rocketImagesLoading = false;
                rocketImageOff = off;
                rocketImageOn = on;
                refreshRocketCard();
            });
        }, "rocket-images").start();
    }

    private static Bitmap decodeRocketImage(HomeAssistantClient client, String path, int targetWidth) {
        try {
            byte[] data = client.download(path);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / (options.inSampleSize * 2) >= targetWidth) options.inSampleSize *= 2;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception e) { return null; }
    }

    private void renderRocketCard(float x, float w) {
        View aura = dFocusAura(x, D_EVENTS_Y, w, D_EVENTS_H, 32);
        rocketCard = dCard(x, D_EVENTS_Y, w, D_EVENTS_H, 32);
        ((RoundClipFrame) rocketCard).clipContent();
        rocketCardW = w;
        dFocusable(rocketCard, aura, null);
        rocketCard.setOnClickListener(v -> {
            if (!haConnected()) showHomeAssistantSetup();
            else showRocketDetails();
        });
        fillRocketCard();
        if (haConnected() && launchesUpdatedAt == 0 && !launchesLoading) handler.post(pollRockets);
    }

    private void refreshRocketCard() {
        if (rocketCard != null && rocketCard.getParent() == root) fillRocketCard();
    }

    private void fillRocketCard() {
        FrameLayout card = rocketCard;
        float w = rocketCardW, h = D_EVENTS_H;
        card.removeAllViews();
        long now = System.currentTimeMillis();
        HomeAssistantClient.Launch launch = haConnected() ? currentLaunch() : null;
        boolean imminent = launch != null && launch.warning20m;
        Bitmap picture = imminent && rocketImageOn != null ? rocketImageOn : rocketImageOff;
        if (picture != null) {
            ImageView photo = new ImageView(this);
            photo.setImageBitmap(picture);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            // Slightly see-through, so the blurred wallpaper glows through the photo.
            photo.setAlpha(0.78f);
            addD(card, photo, 0, 0, w, h);
            View side = new View(this);
            side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xC7050A18, 0x99050A18, 0x26050A18}));
            addD(card, side, 0, 0, w, h);
            View bottom = new View(this);
            bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0x73050A18, 0x00050A18}));
            addD(card, bottom, 0, h * 0.55f, w, h * 0.45f);
        }
        cardHeader(card, w, R.drawable.ic_ds_rocket, tr("Start rakiety", "Rocket launch"),
                haConnected() ? stampLabel(launchesUpdatedAt) : "", 0.82f);
        if (!haConnected()) {
            dText(card, tr("Połącz Home Assistanta", "Connect Home Assistant"), 32, 800, D_INK, 34, 104, w - 68, 44);
            TextView hint = dText(card, tr("Najbliższy start z czujnika Rocket Launch Live, tak jak na pulpicie HA.",
                    "Next launch from the Rocket Launch Live sensor, as on the HA dashboard."),
                    20, 600, inkAlpha(0.72f), 34, 160, w - 68, 60);
            hint.setSingleLine(false);
            hint.setMaxLines(2);
            hint.setGravity(Gravity.TOP);
            dText(card, tr("OK — konfiguracja z telefonu", "OK — set up from your phone"), 18, 600,
                    inkAlpha(0.58f), 34, h - 30 - 26, w - 68, 26);
            card.setContentDescription(tr("Start rakiety. Połącz Home Assistanta", "Rocket launch. Connect Home Assistant"));
            return;
        }
        if (launch == null) {
            String title = launchesUpdatedAt == 0 && launchesError == null
                    ? tr("Pobieranie startów…", "Loading launches…")
                    : tr("Brak zaplanowanych startów", "No launches scheduled");
            TextView titleView = dText(card, title, 32, 800, D_INK, 34, 104, w - 68, 84);
            titleView.setSingleLine(false);
            titleView.setMaxLines(2);
            titleView.setGravity(Gravity.TOP);
            if (launchesError != null) dText(card, launchesError, 20, 600, inkAlpha(0.68f), 34, h - 30 - 28, w - 68, 28);
            card.setContentDescription(tr("Start rakiety. ", "Rocket launch. ") + title);
            return;
        }
        dText(card, launch.name, 36, 800, D_INK, 34, 84, w - 68, 46);
        String vehicle = launch.vehicle + (launch.vehicle.isEmpty() || launch.provider.isEmpty() ? "" : " · ")
                + launch.provider;
        if (!vehicle.isEmpty()) {
            dIcon(card, R.drawable.ic_ds_building, inkAlpha(0.8f), 34, 142, 20);
            dText(card, vehicle, 20, 600, inkAlpha(0.8f), 64, 138, w - 64 - 34, 28);
        }
        String place = launch.pad + (launch.pad.isEmpty() || launch.location.isEmpty() ? "" : ", ") + launch.location;
        if (!place.isEmpty()) {
            dIcon(card, R.drawable.ic_ds_pin, inkAlpha(0.8f), 34, 176, 20);
            dText(card, place, 20, 600, inkAlpha(0.8f), 64, 172, w - 64 - 34, 28);
        }
        String time = launch.startsAt > 0
                ? new SimpleDateFormat("HH:mm", displayLocale()).format(new Date(launch.startsAt)) : "—:—";
        TextView timeView = dText(card, time, 52, 700, D_INK, 34, 214, 220, 56);
        timeView.setLetterSpacing(-1.5f / 52f);
        String day = launch.startsAt > 0 ? relativeDayLabel(launch.startsAt)
                : launch.dateLabel.isEmpty() ? tr("termin nieustalony", "date not fixed")
                : tr("planowo ", "planned ") + launch.dateLabel;
        dText(card, day, 20, 600, inkAlpha(0.68f), 34, 272, w - 68 - 150, 26);
        long left = launch.startsAt - now;
        if (imminent || launch.startsAt > 0 && left > -ROCKET_VISIBLE_AFTER_MS && left < 24L * 3600_000) {
            String countdown = left <= 0 ? tr("start", "lift-off")
                    : left < 3600_000 ? tr("za ", "in ") + Math.max(1, left / 60_000) + " min"
                    : tr("za ", "in ") + Math.round(left / 3600_000.0) + tr(" godz.", " h");
            if (imminent) cardPill(card, w, h, countdown, 0x40FFC861, 0x99FFC861, 0xFFFFD38A);
            else cardPill(card, w, h, countdown, 0x14FFFFFF, 0x24FFFFFF, D_INK);
        }
        card.setContentDescription(tr("Start rakiety: ", "Rocket launch: ") + launch.name + ", " + vehicle
                + ", " + time + ", " + day);
    }

    private void showRocketDetails() {
        HomeAssistantClient.Launch launch = currentLaunch();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        popupTitle(content, launch == null ? tr("Start rakiety", "Rocket launch") : launch.name);
        if (launch == null) {
            popupDescription(content, launchesError != null ? launchesError
                    : tr("Home Assistant nie podał najbliższego startu (sensor.rocket_launch_1).",
                    "Home Assistant reported no upcoming launch (sensor.rocket_launch_1)."));
        } else {
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rowsParams = new LinearLayout.LayoutParams(-1, -2);
            rowsParams.setMargins(0, px(14), 0, px(6));
            content.addView(rows, rowsParams);
            rocketDetailRow(rows, tr("Dostawca", "Provider"), launch.provider);
            rocketDetailRow(rows, tr("Pojazd", "Vehicle"), launch.vehicle);
            rocketDetailRow(rows, tr("Wyrzutnia", "Launch pad"), launch.pad);
            rocketDetailRow(rows, tr("Lokalizacja", "Location"), launch.location);
            rocketDetailRow(rows, tr("Misje", "Missions"), launch.missions);
            rocketDetailRow(rows, tr("Start", "Lift-off"), launch.startsAt > 0
                    ? new SimpleDateFormat(english ? "EEEE, d MMMM, HH:mm" : "EEEE, d MMMM, HH:mm",
                            displayLocale()).format(new Date(launch.startsAt))
                    : launch.dateLabel);
            if (!launch.description.isEmpty()) popupDescription(content, launch.description);
            List<HomeAssistantClient.Launch> later = new ArrayList<>();
            boolean afterCurrent = false;
            for (HomeAssistantClient.Launch item : launches) {
                if (afterCurrent) later.add(item);
                if (item == launch) afterCurrent = true;
            }
            if (!later.isEmpty()) {
                TextView heading = new TextView(this);
                heading.setText(tr("Kolejne starty", "Next launches"));
                heading.setTextColor(ink);
                heading.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(21));
                heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                content.addView(heading);
                for (HomeAssistantClient.Launch item : later) {
                    String when = item.startsAt > 0
                            ? new SimpleDateFormat("d.MM HH:mm", displayLocale()).format(new Date(item.startsAt))
                            : item.dateLabel;
                    detailLine(content, when + "  ·  " + item.name
                            + (item.provider.isEmpty() ? "" : " (" + item.provider + ")"), 18, false, muted);
                }
            }
        }
        TextView close = setupButton(tr("Zamknij", "Close"));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(px(150), px(54));
        closeParams.gravity = Gravity.END;
        closeParams.topMargin = px(16);
        content.addView(close, closeParams);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(860), -2);
        close.requestFocus();
    }

    private void rocketDetailRow(LinearLayout rows, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(muted);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        row.addView(name, new LinearLayout.LayoutParams(px(170), -2));
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(ink);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = px(8);
        rows.addView(row, params);
    }


    // ---------- Photo viewer: JUR / URBANOWICZ (friends' portfolios) ----------

    private static final long PORTFOLIO_REFRESH_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long PORTFOLIO_RETRY_MS = 60L * 60 * 1000;
    private static final long PHOTO_SLIDE_MS = 10_000L;
    private final List<PortfolioPhotos.Photo> portfolioPhotos = new ArrayList<>();
    private long portfolioUpdatedAt, portfolioAttemptedAt;
    private boolean portfolioLoading;
    private final java.util.Random photoRandom = new java.util.Random();
    /** The open viewer, or null. */
    private PhotoViewer photoViewer;

    private void restorePortfolio(SharedPreferences saved) {
        portfolioPhotos.clear();
        portfolioUpdatedAt = saved.getLong("portfolio_updated", 0);
        try {
            JSONArray items = new JSONArray(saved.getString("portfolio_json", "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                portfolioPhotos.add(new PortfolioPhotos.Photo(item.getString("u"), item.optString("a"), item.optString("g")));
            }
        } catch (Exception ignored) { portfolioPhotos.clear(); }
    }

    private void savePortfolio() {
        JSONArray items = new JSONArray();
        try {
            for (PortfolioPhotos.Photo photo : portfolioPhotos)
                items.put(new JSONObject().put("u", photo.url).put("a", photo.author).put("g", photo.gallery));
        } catch (Exception ignored) { return; }
        getPreferences(MODE_PRIVATE).edit().putString("portfolio_json", items.toString())
                .putLong("portfolio_updated", portfolioUpdatedAt).apply();
    }

    /** The photo list changes rarely: refresh it once a week (retry hourly after a failure). */
    private void refreshPortfolioIfDue(boolean force) {
        long now = System.currentTimeMillis();
        if (portfolioLoading) return;
        if (!force && now - portfolioUpdatedAt < PORTFOLIO_REFRESH_MS) return;
        if (!force && now - portfolioAttemptedAt < PORTFOLIO_RETRY_MS) return;
        portfolioAttemptedAt = now;
        portfolioLoading = true;
        new Thread(() -> {
            List<PortfolioPhotos.Photo> found = null;
            try { found = PortfolioPhotos.fetchAll(); } catch (IOException ignored) { }
            final List<PortfolioPhotos.Photo> result = found;
            handler.post(() -> {
                portfolioLoading = false;
                if (result != null && !result.isEmpty()) {
                    portfolioPhotos.clear();
                    portfolioPhotos.addAll(result);
                    portfolioUpdatedAt = System.currentTimeMillis();
                    savePortfolio();
                }
                if (photoViewer != null) photoViewer.listChanged();
            });
        }, "portfolio-list").start();
    }

    /** Header button under the system icons, as wide as their three columns. */
    private void renderPhotosButton() {
        float x = 1556, y = 168, w = 268, h = 76;
        View aura = dFocusAura(x, y, w, h, 38);
        FrameLayout button = dCard(x, y, w, h, 38);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_jurbanowicz);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setColorFilter(D_INK);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addD(button, logo, 34, 18, w - 68, 40);
        button.setContentDescription(tr("Zdjęcia: Janusz Jurek i Marcin Urbanowicz",
                "Photos: Janusz Jurek and Marcin Urbanowicz"));
        dFocusable(button, aura, null);
        button.setOnClickListener(v -> showPhotoViewer());
    }

    private void showPhotoViewer() {
        if (photoViewer != null) return;
        photoViewer = new PhotoViewer();
        photoViewer.show();
        if (portfolioPhotos.isEmpty()) refreshPortfolioIfDue(true);
    }

    /** Full-screen random slideshow: ◀ ▶ browse (with history), OK = next, Back closes. */
    private final class PhotoViewer {
        private final Dialog dialog = new Dialog(DashboardActivity.this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        private final FrameLayout stage = new FrameLayout(DashboardActivity.this);
        private final ImageView image = new ImageView(DashboardActivity.this);
        private final TextView author, gallery, status;
        private final View leftArrow, rightArrow;
        private final List<PortfolioPhotos.Photo> history = new ArrayList<>();
        private final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
        private int position = -1;
        private int generation;
        private PortfolioPhotos.Photo prefetchPhoto;
        private Bitmap prefetchBitmap;
        private boolean prefetching;
        private boolean closed;
        private final Runnable advance = () -> step(1);

        PhotoViewer() {
            stage.setBackgroundColor(0xFF020409);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            stage.addView(image, new FrameLayout.LayoutParams(-1, -1));
            View shade = new View(DashboardActivity.this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xB3020409, 0x00020409}));
            stage.addView(shade, paramsD(0, 820, 1920, 260));
            author = addText(26, 700, D_INK, 96, 958, 1100, 40);
            gallery = addText(20, 600, inkAlpha(0.72f), 96, 1000, 1100, 32);
            status = addText(24, 600, inkAlpha(0.8f), 0, 520, 1920, 40);
            status.setGravity(Gravity.CENTER);
            ImageView mark = new ImageView(DashboardActivity.this);
            mark.setImageResource(R.drawable.logo_jurbanowicz);
            mark.setScaleType(ImageView.ScaleType.FIT_END);
            mark.setColorFilter(inkAlpha(0.55f));
            stage.addView(mark, paramsD(1920 - 96 - 220, 976, 220, 44));
            leftArrow = arrow(48, 90f, () -> step(-1));
            rightArrow = arrow(1920 - 48 - 88, -90f, () -> step(1));
            leftArrow.setContentDescription(tr("Poprzednie zdjęcie", "Previous photo"));
            rightArrow.setContentDescription(tr("Następne zdjęcie", "Next photo"));
            dialog.setContentView(stage);
            dialog.setOnKeyListener((d, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) { pulse(leftArrow); step(-1); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_CENTER
                        || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    pulse(rightArrow); step(1); return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) return true;
                return false; // Back closes the dialog
            });
        }

        private TextView addText(float size, int weight, int color, float x, float y, float w, float h) {
            TextView view = new TextView(DashboardActivity.this);
            view.setTextColor(color);
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxD(size));
            view.setTypeface(manrope(weight));
            view.setIncludeFontPadding(false);
            view.setSingleLine(true);
            view.setEllipsize(android.text.TextUtils.TruncateAt.END);
            view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            stage.addView(view, paramsD(x, y, w, h));
            return view;
        }

        private View arrow(float x, float rotation, Runnable click) {
            FrameLayout button = new FrameLayout(DashboardActivity.this);
            GradientDrawable circle = glass(-1);
            circle.setColor(0x990E1630);
            button.setBackground(circle);
            ImageView chevron = new ImageView(DashboardActivity.this);
            chevron.setImageResource(R.drawable.ic_ds_chevron);
            chevron.setColorFilter(D_INK);
            chevron.setRotation(rotation);
            button.addView(chevron, paramsD(24, 24, 40, 40));
            button.setClickable(true);
            button.setOnClickListener(v -> { pulse(v); click.run(); });
            stage.addView(button, paramsD(x, 540 - 44, 88, 88));
            return button;
        }

        private void pulse(View view) {
            view.animate().cancel();
            view.setScaleX(1.15f);
            view.setScaleY(1.15f);
            view.setAlpha(1f);
            view.animate().scaleX(1f).scaleY(1f).setDuration(260).start();
        }

        void show() {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
            showDialogWithBlur(dialog, this::close);
            dialog.getWindow().setLayout(-1, -1);
            listChanged();
        }

        private void close() {
            closed = true;
            handler.removeCallbacks(advance);
            image.setImageDrawable(null);
            prefetchBitmap = null;
            history.clear();
            if (photoViewer == this) photoViewer = null;
        }

        /** Called when the photo list arrives or changes. */
        void listChanged() {
            if (closed) return;
            if (position < 0) {
                if (portfolioPhotos.isEmpty()) {
                    status.setText(portfolioLoading ? tr("Pobieranie listy zdjęć…", "Loading the photo list…")
                            : tr("Nie udało się pobrać zdjęć. Sprawdź internet.", "Could not load photos. Check the internet."));
                    status.setVisibility(View.VISIBLE);
                    return;
                }
                step(1);
            }
        }

        /** Half of the picks come from each author, so the smaller portfolio is not drowned out. */
        private PortfolioPhotos.Photo pick() {
            if (portfolioPhotos.isEmpty()) return null;
            List<String> authors = new ArrayList<>();
            for (PortfolioPhotos.Photo photo : portfolioPhotos)
                if (!authors.contains(photo.author)) authors.add(photo.author);
            String author = authors.get(photoRandom.nextInt(authors.size()));
            List<PortfolioPhotos.Photo> pool = new ArrayList<>();
            for (PortfolioPhotos.Photo photo : portfolioPhotos)
                if (photo.author.equals(author) && !recent.contains(photo.url)) pool.add(photo);
            if (pool.isEmpty()) for (PortfolioPhotos.Photo photo : portfolioPhotos)
                if (photo.author.equals(author)) pool.add(photo);
            PortfolioPhotos.Photo chosen = pool.get(photoRandom.nextInt(pool.size()));
            recent.addLast(chosen.url);
            while (recent.size() > Math.min(60, portfolioPhotos.size() / 2)) recent.removeFirst();
            return chosen;
        }

        private void step(int delta) {
            if (closed) return;
            handler.removeCallbacks(advance);
            if (delta < 0) {
                if (position <= 0) { handler.postDelayed(advance, PHOTO_SLIDE_MS); return; }
                position--;
                display(history.get(position), null);
                return;
            }
            if (position + 1 < history.size()) {
                position++;
                display(history.get(position), null);
                return;
            }
            PortfolioPhotos.Photo next;
            Bitmap ready = null;
            if (prefetchPhoto != null && !prefetching) {
                next = prefetchPhoto;
                ready = prefetchBitmap;
                prefetchPhoto = null;
                prefetchBitmap = null;
            } else next = pick();
            if (next == null) { listChanged(); return; }
            history.add(next);
            while (history.size() > 50) history.remove(0);
            position = history.size() - 1;
            display(next, ready);
        }

        private void display(PortfolioPhotos.Photo photo, Bitmap ready) {
            final int current = ++generation;
            author.setText(photo.author);
            gallery.setText(photo.gallery);
            leftArrow.setAlpha(position > 0 ? 1f : 0.35f);
            if (ready != null) { showBitmap(ready); return; }
            status.setText(tr("Wczytywanie zdjęcia…", "Loading photo…"));
            status.setVisibility(View.VISIBLE);
            loadPhoto(photo, bitmap -> {
                if (closed || current != generation) return;
                if (bitmap == null) {
                    status.setText(tr("Nie udało się wczytać zdjęcia", "Could not load the photo"));
                    status.setVisibility(View.VISIBLE);
                    handler.postDelayed(advance, 2500);
                    return;
                }
                showBitmap(bitmap);
            });
        }

        private void showBitmap(Bitmap bitmap) {
            status.setVisibility(View.GONE);
            image.setAlpha(0f);
            image.setImageBitmap(bitmap);
            image.animate().alpha(1f).setDuration(450).start();
            handler.postDelayed(advance, PHOTO_SLIDE_MS);
            prefetch();
        }

        private void prefetch() {
            if (prefetching || prefetchPhoto != null || position + 1 < history.size()) return;
            PortfolioPhotos.Photo next = pick();
            if (next == null) return;
            prefetchPhoto = next;
            prefetching = true;
            loadPhoto(next, bitmap -> {
                prefetching = false;
                if (closed) return;
                if (bitmap == null) prefetchPhoto = null;
                else prefetchBitmap = bitmap;
            });
        }

        private void loadPhoto(PortfolioPhotos.Photo photo, java.util.function.Consumer<Bitmap> done) {
            final int maxW = Math.max(640, getResources().getDisplayMetrics().widthPixels);
            final int maxH = Math.max(360, getResources().getDisplayMetrics().heightPixels);
            new Thread(() -> {
                Bitmap bitmap = null;
                try {
                    byte[] data = PortfolioPhotos.download(photo.url, 25_000_000);
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    options.inSampleSize = 1;
                    while (bounds.outWidth / (options.inSampleSize * 2) >= maxW
                            || bounds.outHeight / (options.inSampleSize * 2) >= maxH) options.inSampleSize *= 2;
                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                } catch (Exception | OutOfMemoryError ignored) { bitmap = null; }
                final Bitmap result = bitmap;
                handler.post(() -> done.accept(result));
            }, "portfolio-photo").start();
        }
    }

    private void showPublicSettings() {
        showPublicSettings(false);
    }

    private Dialog publicSettingsDialog;

    private void showPublicSettings(boolean focusLanguage) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        publicSettingsDialog = dialog;
        renderPublicSettings(dialog, focusLanguage);
    }

    private void renderPublicSettings(Dialog dialog, boolean focusLanguage) {
        LinearLayout content = popupContent();
        content.setClipChildren(false);
        content.setClipToPadding(false);
        GradientDrawable sideSurface = new GradientDrawable();
        sideSurface.setColor(night ? 0xFF142548 : 0xFFF5F2EA);
        sideSurface.setCornerRadii(new float[]{px(26), px(26), 0, 0, 0, 0, px(26), px(26)});
        content.setBackground(sideSurface);
        popupTitle(content, tr("Ustawienia", "Settings"));
        View cleanup = settingsTile(tr("Zamknij aplikacje", "Close apps"),
                R.drawable.ic_toolbar_cleanup, this::closeBackgroundApps);
        settingsFocusable(cleanup).setContentDescription(
                tr("Zamknij aplikacje działające w tle", "Close background apps"));
        View wallpapers = settingsTile(tr("Tapety", "Wallpapers"), R.drawable.ic_toolbar_gallery,
                this::showWallpaperGallery);
        View screensaver = settingsTile(tr("Wygaszacz", "Screensaver"),
                R.drawable.ic_settings_screensaver, this::showScreensaverSettings);
        View appearance = settingsTile(tr("Wygląd launchera", "Launcher appearance"),
                R.drawable.ic_settings_appearance, this::showAppearanceSettings);
        View weather = settingsTile(tr("Pogoda", "Weather"), R.drawable.ic_weather_thermometer,
                this::showWeatherSettings);
        View calendars = settingsTile(tr("Kalendarze", "Calendars"),
                R.drawable.ic_settings_calendar, this::showCalendarSettings);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        addSettingsRow(grid, cleanup, wallpapers);
        addSettingsRow(grid, screensaver, appearance);
        addSettingsRow(grid, weather, calendars);
        linkSettingsGrid(cleanup, wallpapers, screensaver, appearance, weather, calendars);


        // System values belong only to this overview, never to the submenus.
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        addSystemMeter(content, "RAM", memory.availMem, memory.totalMem);
        android.os.StatFs storage = new android.os.StatFs(getFilesDir().getAbsolutePath());
        addSystemMeter(content, tr("Pamięć", "Storage"), storage.getAvailableBytes(), storage.getTotalBytes());
        TextView system = setupButton(tr("Ustawienia Android TV", "Android TV settings"));
        system.setGravity(Gravity.CENTER);
        system.setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));
        addSettingsControl(content, system, 50);
        TextView homeAssistant = setupButton(tr("Światła · Home Assistant", "Lights · Home Assistant"));
        homeAssistant.setGravity(Gravity.CENTER);
        homeAssistant.setOnClickListener(v -> showHomeAssistantSetup());
        addSettingsControl(content, homeAssistant, 50);
        LinearLayout footer = new LinearLayout(this);
        TextView language = setupButton(tr("Język", "Language"));
        language.setGravity(Gravity.CENTER);
        language.setOnClickListener(v -> showLanguageSetup());
        TextView about = setupButton(tr("O aplikacji", "About"));
        about.setGravity(Gravity.CENTER);
        about.setOnClickListener(v -> showMessageDialog("LinarTV " + appVersion(),
                tr("Launcher dla Android TV", "Launcher for Android TV")));
        footer.addView(language, new LinearLayout.LayoutParams(0, px(46), 1));
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(0, px(46), 1);
        aboutParams.leftMargin = px(12);
        footer.addView(about, aboutParams);
        TextView spotifyAccess = setupButton("Spotify");
        spotifyAccess.setGravity(Gravity.CENTER);
        spotifyAccess.setOnClickListener(v -> enableSpotifyPanel());
        LinearLayout.LayoutParams spotifyParams = new LinearLayout.LayoutParams(0, px(46), 1);
        spotifyParams.leftMargin = px(12);
        footer.addView(spotifyAccess, spotifyParams);
        addSettingsControl(content, footer, 46);
        dialog.setContentView(content, new ViewGroup.LayoutParams(-1, -1));
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        dialog.getWindow().getDecorView().setSystemUiVisibility(root.getSystemUiVisibility());
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (!dialog.isShowing()) showDialogWithBlur(dialog);
        alignSidePanel(dialog);
        View first = content.findViewById(settingsFirstTileId);
        if (focusLanguage) language.requestFocus();
        else if (first != null) first.requestFocus();
    }

    private void alignSidePanel(Dialog dialog) {
        Window window = dialog.getWindow();
        // This is a persistent side panel, not a floating popup: it is flush with
        // the right, top and bottom edges of the launcher.
        window.setLayout(px(520), -1);
        window.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        android.view.WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.x = 0;
        attrs.y = 0;
        attrs.horizontalMargin = 0;
        attrs.verticalMargin = 0;
        window.setAttributes(attrs);
    }

    private int settingsFirstTileId;

    private View settingsTile(String label, int icon, Runnable action) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);
        View glow = new View(this);
        glow.setBackground(new AppGlowDrawable(20, false));
        glow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glow.setAlpha(0f);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(-1, -1);
        glowParams.setMargins(-px(24), -px(24), -px(24), -px(24));
        wrap.addView(glow, glowParams);
        LinearLayout tile = new LinearLayout(this);
        tile.setId(View.generateViewId());
        if (icon == R.drawable.ic_toolbar_cleanup) settingsFirstTileId = tile.getId();
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(px(16), px(12), px(16), px(12));
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setClickable(true);
        tile.setContentDescription(label);
        ImageView glyph = new ImageView(this);
        glyph.setImageResource(icon);
        glyph.setColorFilter(ink);
        tile.addView(glyph, new LinearLayout.LayoutParams(px(54), px(54)));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(ink);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = px(12);
        tile.addView(name, nameParams);
        int normal = night ? 0xFF344B72 : Color.WHITE;
        tile.setBackground(shape(normal, Color.TRANSPARENT, 20));
        tile.setOnFocusChangeListener((v, focused) -> {
            showTileGlow(glow, focused);
            int color = focused ? (night ? 0xFF152849 : Color.WHITE) : ink;
            tile.setBackground(shape(focused ? (night ? 0xFFF0F6FF : 0xFF244574) : normal,
                    Color.TRANSPARENT, 20));
            name.setTextColor(color);
            glyph.setColorFilter(color);
        });
        tile.setOnClickListener(v -> action.run());
        wrap.addView(tile, new FrameLayout.LayoutParams(-1, -1));
        return wrap;
    }

    private void addSettingsRow(LinearLayout content, View first, View second) {
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.addView(first, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams next = new LinearLayout.LayoutParams(0, -1, 1);
        next.leftMargin = px(14);
        row.addView(second, next);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, 0, 1);
        rowParams.topMargin = px(14);
        content.addView(row, rowParams);
    }

    private void linkSettingsGrid(View first, View second, View third, View fourth, View fifth, View sixth) {
        View a = settingsFocusable(first), b = settingsFocusable(second), c = settingsFocusable(third);
        View d = settingsFocusable(fourth), e = settingsFocusable(fifth), f = settingsFocusable(sixth);
        a.setNextFocusRightId(b.getId()); a.setNextFocusDownId(c.getId());
        b.setNextFocusLeftId(a.getId()); b.setNextFocusDownId(d.getId());
        c.setNextFocusUpId(a.getId()); c.setNextFocusRightId(d.getId()); c.setNextFocusDownId(e.getId());
        d.setNextFocusUpId(b.getId()); d.setNextFocusLeftId(c.getId()); d.setNextFocusDownId(f.getId());
        e.setNextFocusUpId(c.getId()); e.setNextFocusRightId(f.getId());
        f.setNextFocusUpId(d.getId()); f.setNextFocusLeftId(e.getId());
    }

    private View settingsFocusable(View wrapper) {
        return ((ViewGroup) wrapper).getChildAt(1);
    }

    private void addSettingsControl(LinearLayout content, View view, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, px(height));
        params.topMargin = px(12);
        content.addView(view, params);
    }

    private void addSystemMeter(LinearLayout content, String label, long free, long total) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(muted);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16));
        row.addView(name, new LinearLayout.LayoutParams(px(70), -2));
        android.widget.ProgressBar meter = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        meter.setProgress(total > 0 ? (int) ((total - free) * 100 / total) : 0);
        row.addView(meter, new LinearLayout.LayoutParams(0, px(10), 1));
        TextView value = new TextView(this);
        value.setText(android.text.format.Formatter.formatShortFileSize(this, free)
                + tr(" wolne", " free"));
        value.setTextColor(ink);
        value.setGravity(Gravity.RIGHT);
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
        row.addView(value, new LinearLayout.LayoutParams(px(145), -2));
        addSettingsControl(content, row, 28);
    }

    private void showWeatherSettings() {
        showChoiceDialog(tr("Pogoda", "Weather"), new String[]{
                tr("Miasto: ", "City: ") + cityName,
                tr("Temperatura: ", "Temperature: ") + (fahrenheit ? "°F" : "°C")},
                which -> { if (which == 0) showCitySetup(); else showTemperatureUnitSetup(); });
    }

    private void showCalendarSettings() {
        showChoiceDialog(tr("Kalendarze", "Calendars"), new String[]{
                tr("Wybierz kluby", "Choose clubs"), tr("Odśwież teraz", "Refresh now")}, which -> {
            if (which == 0) { showTeamSetup(); return; }
            for (CalendarFeed feed : new CalendarFeed[]{clubOneCalendar, clubTwoCalendar}) {
                feed.settingsRefreshPending = true;
                if (feed.loading) continue;
                feed.updatedAt = 0;
                feed.attemptedAt = 0;
                refreshCalendar(feed);
            }
            Toast.makeText(this, tr("Rozpoczęto odświeżanie terminarzy", "Refreshing fixtures"), Toast.LENGTH_SHORT).show();
        });
    }

    private void showScreensaverSettings() {
        showChoiceDialog(tr("Wygaszacz", "Screensaver"), new String[]{
                tr("Wyłączony", "Off"), tr("Po 2 minutach", "After 2 minutes"),
                tr("Po 5 minutach", "After 5 minutes"), tr("Po 10 minutach", "After 10 minutes"),
                tr("Po 15 minutach", "After 15 minutes"), tr("Własny czas…", "Custom time…")}, which -> {
            if (which == 5) {
                showNumberSetting(tr("Czas bezczynności (minuty)", "Idle time (minutes)"),
                        getPreferences(MODE_PRIVATE).getInt("screensaver_minutes", 5), 1, 1440,
                        value -> saveScreensaverMinutes(value));
            } else saveScreensaverMinutes(new int[]{0, 2, 5, 10, 15}[which]);
        });
    }

    private void finishCalendarSettingsRefresh(CalendarFeed feed) {
        if (feed.settingsRefreshPending) {
            feed.settingsRefreshPending = false;
            applyVisualSettingsNow();
        } else buildScreen();
    }

    private void saveScreensaverMinutes(int minutes) {
        getPreferences(MODE_PRIVATE).edit().putInt("screensaver_minutes", minutes).apply();
        resetScreensaverTimer();
        applyVisualSettingsNow();
    }

    private void showAppearanceSettings() {
        showChoiceDialog(tr("Wygląd launchera", "Launcher appearance"), new String[]{
                tr("Automatycznie", "Automatic"), tr("Zawsze jasny", "Always light"),
                tr("Zawsze ciemny", "Always dark"), tr("Własne godziny", "Custom schedule")}, which -> {
            if (which == 3) { showAppearanceSchedule(); return; }
            getPreferences(MODE_PRIVATE).edit().putString("appearance_mode", new String[]{"auto", "light", "dark"}[which]).apply();
            applyVisualSettingsNow();
        });
    }

    private void showAppearanceSchedule() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        int day = prefs.getInt("day_start_minute", 480), dark = prefs.getInt("night_start_minute", 1200);
        String[] labels = {tr("Dzień od ", "Day from ") + clockMinutes(day),
                tr("Noc od ", "Night from ") + clockMinutes(dark)};
        showChoiceDialog(tr("Własne godziny", "Custom schedule"), labels, which -> {
            int current = which == 0 ? day : dark;
            showClockSetting(labels[which], current, selected -> {
                int other = prefs.getInt(which == 0 ? "night_start_minute" : "day_start_minute", which == 0 ? 1200 : 480);
                if (selected == other) {
                    showMessageDialog(tr("Godziny muszą być różne", "Times must differ"),
                            tr("Ustaw różne godziny początku dnia i nocy.", "Choose different day and night start times."));
                    return;
                }
                prefs.edit().putInt(which == 0 ? "day_start_minute" : "night_start_minute", selected)
                        .putString("appearance_mode", "auto").apply();
                applyVisualSettingsNow();
            });
        });
    }

    private void showClockSetting(String title, int current, java.util.function.IntConsumer onSave) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        popupTitle(content, title);
        popupDescription(content, tr("Wpisz godzinę w formacie HH:mm", "Enter a time in HH:mm format"));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
        input.setTextColor(ink);
        input.setText(clockMinutes(current));
        addSettingsControl(content, input, 60);
        TextView save = setupButton(tr("Zapisz", "Save"));
        save.setGravity(Gravity.CENTER);
        save.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.matches("([01][0-9]|2[0-3]):[0-5][0-9]")) {
                input.setError(tr("Wpisz poprawną godzinę HH:mm", "Enter a valid HH:mm time"));
                return;
            }
            int minute = Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3));
            dialog.dismiss();
            onSave.accept(minute);
        });
        addSettingsControl(content, save, 54);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(560), -2);
        input.requestFocus();
        input.selectAll();
    }

    private String clockMinutes(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private void showNumberSetting(String title, int current, int min, int max,
                                   java.util.function.IntConsumer onSave) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        popupTitle(content, title);
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setTextColor(ink);
        input.setText(String.valueOf(Math.max(min, current)));
        addSettingsControl(content, input, 60);
        TextView save = setupButton(tr("Zapisz", "Save"));
        save.setGravity(Gravity.CENTER);
        save.setOnClickListener(v -> {
            int value;
            try { value = Integer.parseInt(input.getText().toString().trim()); }
            catch (NumberFormatException e) { value = -1; }
            if (value < min || value > max) {
                input.setError(tr("Wpisz liczbę od ", "Enter a number from ") + min + "–" + max);
                return;
            }
            dialog.dismiss();
            onSave.accept(value);
        });
        addSettingsControl(content, save, 54);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(560), -2);
        input.requestFocus();
        input.selectAll();
    }

    private void showTemperatureUnitSetup() {
        showChoiceDialog(tr("Jednostka temperatury", "Temperature unit"),
                new String[]{"°C", "°F"}, which -> {
            boolean selectedFahrenheit = which == 1;
            if (fahrenheit == selectedFahrenheit) return;
            fahrenheit = selectedFahrenheit;
            getPreferences(MODE_PRIVATE).edit().putString("temperature_unit", fahrenheit ? "f" : "c").apply();
            applyVisualSettingsNow();
        });
    }

    private int shownTemperature(double celsius) {
        return (int) Math.round(fahrenheit ? celsius * 9.0 / 5.0 + 32.0 : celsius);
    }

    private CharSequence temperatureLabel(double celsius) {
        return temperatureLabelParts(shownTemperature(celsius) + "°");
    }

    private CharSequence temperatureLabelParts(String value) {
        String text = value.replace('°', (char) (fahrenheit ? 0xf045 : 0xf03c));
        SpannableString label = new SpannableString(text);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == (char) (fahrenheit ? 0xf045 : 0xf03c))
                label.setSpan(new TemperatureUnitSpan(), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return label;
    }

    private final class TemperatureUnitSpan extends MetricAffectingSpan {
        private void apply(TextPaint paint) {
            if (weatherTypeface == null) weatherTypeface = Typeface.createFromAsset(getAssets(),
                    "weathericons-regular-webfont.ttf");
            paint.setTypeface(weatherTypeface);
            paint.setTextSize(paint.getTextSize() * 0.68f);
        }
        @Override public void updateDrawState(TextPaint paint) { apply(paint); }
        @Override public void updateMeasureState(TextPaint paint) { apply(paint); }
    }

    private void showLanguageSetup() {
        showChoiceDialog(tr("Język", "Language"), new String[]{"Polski", "English"}, which -> {
            boolean selectedEnglish = which == 1;
            english = selectedEnglish;
            getPreferences(MODE_PRIVATE).edit().putString("language", english ? "en" : "pl").apply();
            // Update the still-open overview in place. Dialog dismiss listeners
            // can run after this callback; do not depend on their ordering.
            applyVisualSettingsNow();
            if (publicSettingsDialog != null && publicSettingsDialog.isShowing())
                renderPublicSettings(publicSettingsDialog, true);
        });
    }

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return ""; }
    }

    private void showCitySetup() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        popupTitle(content, tr("Miasto do prognozy pogody", "Weather forecast city"));
        popupDescription(content, tr("Wpisz nazwę miasta i wybierz wynik.",
                "Type a city name and choose a result."));
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setTextColor(ink);
        query.setHintTextColor(muted);
        query.setHint(tr("Np. Gdańsk", "E.g. London"));
        query.setText(cityName);
        query.selectAll();
        query.setPadding(px(20), 0, px(20), 0);
        query.setBackground(shape(night ? 0xFF2B4268 : 0xFFE3E9F0,
                night ? 0xFF7899C3 : 0xFFB7C4D5, 15));
        content.addView(query, new LinearLayout.LayoutParams(-1, px(66)));
        TextView status = popupDescription(content, "");
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView resultScroll = new ScrollView(this);
        resultScroll.addView(results);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, px(300));
        resultParams.topMargin = px(8);
        content.addView(resultScroll, resultParams);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView close = setupButton(tr("Zamknij", "Close"));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        actions.addView(close, new LinearLayout.LayoutParams(px(150), px(58)));
        TextView search = setupButton(tr("Szukaj", "Search"));
        search.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(px(150), px(58));
        searchParams.leftMargin = px(14);
        actions.addView(search, searchParams);
        content.addView(actions);
        search.setOnClickListener(v -> searchCity(query.getText().toString().trim(), dialog, results, status));
        query.setOnEditorActionListener((v, actionId, event) -> {
            search.performClick();
            return true;
        });
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(780), -2);
        query.requestFocus();
    }

    private void searchCity(String name, Dialog dialog, LinearLayout results, TextView status) {
        if (name.length() < 2) {
            status.setText(tr("Wpisz co najmniej dwie litery nazwy miasta.",
                    "Enter at least two letters of the city name."));
            return;
        }
        status.setText(tr("Szukanie miasta…", "Searching for city…"));
        results.removeAllViews();
        new Thread(() -> {
            List<PublicForecastClient.Place> places;
            try { places = PublicForecastClient.searchPlaces(name, english ? "en" : "pl"); }
            catch (Exception e) {
                android.util.Log.w("LinarTV", "City search failed", e);
                places = null;
            }
            final List<PublicForecastClient.Place> found = places;
            handler.post(() -> {
                if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                if (found == null) {
                    status.setText(tr("Nie udało się wyszukać miasta. Sprawdź połączenie z internetem.",
                            "Could not search for a city. Check your internet connection."));
                    return;
                }
                if (found.isEmpty()) {
                    status.setText(tr("Nie znaleziono miasta: ", "City not found: ") + name);
                    return;
                }
                status.setText(tr("Wybierz właściwe miasto:", "Choose the correct city:"));
                for (PublicForecastClient.Place place : found) {
                    TextView item = setupButton(place.label());
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, px(62));
                    params.bottomMargin = px(8);
                    results.addView(item, params);
                    item.setOnClickListener(v -> { saveCity(place); dialog.dismiss(); });
                }
                if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
            });
        }, "city-search").start();
    }

    private LinearLayout popupContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(36), px(28), px(36), px(30));
        content.setBackground(shape(night ? 0xFF142548 : 0xFFF5F2EA,
                night ? 0xFF899BC6 : 0xFFB7C4D5, 26));
        return content;
    }

    private void popupTitle(LinearLayout content, String value) {
        TextView title = new TextView(this);
        title.setText(value);
        title.setTextColor(ink);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(32));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
    }

    private TextView popupDescription(LinearLayout content, String value) {
        TextView description = new TextView(this);
        description.setText(value);
        description.setTextColor(muted);
        description.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, px(8), 0, px(20));
        content.addView(description, params);
        return description;
    }

    private void showChoiceDialog(String title, String[] labels, java.util.function.IntConsumer onChoose) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        popupTitle(content, title);
        EditText search = null;
        if (labels.length > 30) {
            search = new EditText(this);
            search.setSingleLine(true);
            search.setTextColor(ink);
            search.setHintTextColor(muted);
            search.setHint(tr("Szukaj ligi", "Search leagues"));
            search.setPadding(px(18), 0, px(18), 0);
            search.setBackground(shape(night ? 0xFF2B4268 : 0xFFE3E9F0,
                    night ? 0xFF7899C3 : 0xFFB7C4D5, 15));
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, px(58));
            searchParams.topMargin = px(16);
            content.addView(search, searchParams);
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, px(Math.min(520,
                Math.max(90, labels.length * 72))));
        scrollParams.topMargin = px(22);
        content.addView(scroll, scrollParams);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView button = setupButton(labels[i]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, px(62));
            params.bottomMargin = px(10);
            choices.addView(button, params);
            button.setOnClickListener(v -> {
                dialog.dismiss();
                onChoose.accept(index);
            });
        }
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < labels.length; i++)
                    choices.getChildAt(i).setVisibility(labels[i].toLowerCase(Locale.ROOT).contains(query)
                            ? View.VISIBLE : View.GONE);
                scroll.scrollTo(0, 0);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        TextView close = setupButton(tr("Zamknij", "Close"));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(px(150), px(54));
        closeParams.gravity = Gravity.END;
        content.addView(close, closeParams);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(780), -2);
        if (choices.getChildCount() > 0) choices.getChildAt(0).requestFocus();
    }

    private void showMessageDialog(String title, String message) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        popupTitle(content, title);
        popupDescription(content, message);
        TextView close = setupButton(tr("Zamknij", "Close"));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(px(150), px(54));
        closeParams.gravity = Gravity.END;
        content.addView(close, closeParams);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(780), -2);
        close.requestFocus();
    }

    private void saveCity(PublicForecastClient.Place place) {
        weatherLocationVersion++;
        cityName = place.name;
        cityLatitude = place.latitude;
        cityLongitude = place.longitude;
        getPreferences(MODE_PRIVATE).edit().putString("city_name", cityName)
                .putLong("city_lat", Double.doubleToLongBits(cityLatitude))
                .putLong("city_lon", Double.doubleToLongBits(cityLongitude))
                .remove("weather_json").remove("weather_updated").apply();
        forecast = null;
        forecastUpdatedAt = 0;
        forecastAttemptedAt = 0;
        weatherSettingsRefreshPending = true;
        applyVisualSettingsNow();
        refreshWeather();
    }

    private TextView setupButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(night ? Color.WHITE : 0xFF15223E);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(21));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(px(22), 0, px(18), 0);
        button.setBackground(shape(night ? 0xFF2B4268 : 0xFFE3E9F0,
                night ? 0xFF7899C3 : 0xFFB7C4D5, 15));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setOnFocusChangeListener((v, focused) -> button.setBackground(shape(
                focused ? (night ? 0xFF4B75A5 : 0xFFCEDCEC)
                        : (night ? 0xFF2B4268 : 0xFFE3E9F0),
                focused ? FOCUS_RING : night ? 0xFF7899C3 : 0xFFB7C4D5, 15,
                focused ? 2 : 1)));
        return button;
    }

    private void showTeamSetup() {
        setupShown = true;
        CalendarFeed[] draft = {clubOneCalendar, clubTwoCalendar};
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(36), px(28), px(36), px(30));
        content.setBackground(shape(night ? 0xFF142548 : 0xFFF5F2EA,
                night ? 0xFF899BC6 : 0xFFB7C4D5, 26));
        TextView title = new TextView(this);
        title.setText(tr("Twoje kluby", "Your clubs"));
        title.setTextColor(ink);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(32));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        TextView description = new TextView(this);
        description.setText(tr("Wybierz jeden lub dwa zespoły. Terminarze będą aktualizowane automatycznie.",
                "Choose one or two teams. Fixtures update automatically."));
        description.setTextColor(muted);
        description.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.setMargins(0, px(8), 0, px(24));
        content.addView(description, descriptionParams);
        TextView first = setupButton(draft[0].configured() ? "1. " + draft[0].club + " · " + draft[0].league
                : tr("1. Wybierz klub", "1. Choose a club"));
        content.addView(first, new LinearLayout.LayoutParams(-1, px(66)));
        TextView second = setupButton(draft[1].configured() ? "2. " + draft[1].club + " · " + draft[1].league
                : tr("2. Dodaj drugi klub (opcjonalnie)", "2. Add a second club (optional)"));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1, px(66));
        secondParams.setMargins(0, px(14), 0, 0);
        content.addView(second, secondParams);
        TextView status = new TextView(this);
        status.setTextColor(muted);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(17));
        status.setMinHeight(px(46));
        status.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(status);
first.setOnClickListener(v -> chooseLeague(0, draft, first, dialog, status));
second.setOnClickListener(v -> chooseLeague(1, draft, second, dialog, status));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT);
        TextView remove = setupButton(tr("Usuń drugi klub", "Remove second club"));
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(0, 0, 0, 0);
        remove.setOnClickListener(v -> {
            draft[1] = new CalendarFeed("club2", "", "", "", "");
            second.setText(tr("2. Dodaj drugi klub (opcjonalnie)", "2. Add a second club (optional)"));
        });
        actions.addView(remove, new LinearLayout.LayoutParams(px(260), px(54)));
        TextView save = setupButton(tr("Zapisz", "Save"));
        save.setGravity(Gravity.CENTER);
        save.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(px(145), px(54));
        saveParams.leftMargin = px(14);
        actions.addView(save, saveParams);
        save.setOnClickListener(v -> {
            if (!draft[0].configured()) { status.setText(tr("Najpierw wybierz pierwszy klub.",
                    "Choose the first club before saving.")); return; }
            saveConfiguredClubs(draft);
            dialog.dismiss();
            if (firstRunSetup) {
                // Last first-run step: pick the dock apps among what is actually installed.
                firstRunSetup = false;
                handler.postDelayed(() -> { if (!isFinishing()) showDockEditor(true); }, 350);
            }
        });
        content.addView(actions);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(960), -2);
        first.requestFocusFromTouch();
    }


    private void chooseLeague(int slot, CalendarFeed[] draft, TextView button,
                              Dialog setup, TextView status) {
        status.setText(tr("Pobieranie listy lig…", "Loading leagues…"));
        new Thread(() -> {
            try {
                List<LeagueCatalogClient.League> leagues = LeagueCatalogClient.leagues();
                handler.post(() -> {
                    if (!setup.isShowing()) return;
                    status.setText("");
                    // National leagues first, then one "IV liga" entry that opens the voivodeships.
                    List<LeagueCatalogClient.League> national = new ArrayList<>(), regional = new ArrayList<>();
                    for (LeagueCatalogClient.League league : leagues)
                        (league.fourthLeague() ? regional : national).add(league);
                    String[] names = new String[national.size() + (regional.isEmpty() ? 0 : 1)];
                    for (int i = 0; i < national.size(); i++) names[i] = national.get(i).name;
                    if (!regional.isEmpty()) names[names.length - 1] = "IV liga";
                    showChoiceDialog(tr("Wybierz ligę", "Choose a league"), names, index -> {
                        if (index < national.size()) {
                            chooseClub(slot, national.get(index), draft, button, setup, status);
                            return;
                        }
                        String[] regions = new String[regional.size()];
                        for (int i = 0; i < regions.length; i++) {
                            String r = regional.get(i).region;
                            regions[i] = r.substring(0, 1).toUpperCase(PL) + r.substring(1);
                        }
                        showChoiceDialog(tr("IV liga — województwo", "IV liga — region"), regions,
                                region -> chooseClub(slot, regional.get(region), draft, button, setup, status));
                    });
                });
            } catch (Exception e) {
                handler.post(() -> { if (setup.isShowing()) status.setText(tr("Nie udało się pobrać lig. Spróbuj ponownie.",
                        "Could not load leagues. Try again.")); });
            }
        }, "league-catalog").start();
    }

    private void chooseClub(int slot, LeagueCatalogClient.League league, CalendarFeed[] draft,
                            TextView button, Dialog setup, TextView status) {
        status.setText(tr("Pobieranie drużyn…", "Loading teams…"));
        new Thread(() -> {
            try {
                List<LeagueCatalogClient.Team> teams = LeagueCatalogClient.teams(league);
                handler.post(() -> {
                    if (!setup.isShowing()) return;
                    if (teams.isEmpty()) { status.setText(tr("Brak drużyn w tej lidze.",
                            "No teams in this league.")); return; }
                    status.setText("");
                    String[] names = new String[teams.size()];
                    for (int i = 0; i < names.length; i++) names[i] = teams.get(i).name;
                    showChoiceDialog(league.name, names, index -> {
                        LeagueCatalogClient.Team team = teams.get(index);
                        int other = 1 - slot;
                        if (draft[other].configured() && draft[other].url.equals(team.calendarUrl(league))) {
                            status.setText(tr("Ten klub jest już wybrany.", "This club is already selected."));
                            return;
                        }
                        draft[slot] = new CalendarFeed("club" + (slot + 1), team.calendarUrl(league),
                                team.name, league.name, league.slug);
                        button.setText((slot + 1) + ". " + team.name + " · " + league.name);
                        button.requestFocusFromTouch();
                    });
                });
            } catch (Exception e) {
                handler.post(() -> { if (setup.isShowing()) status.setText(tr("Nie udało się pobrać drużyn. Spróbuj ponownie.",
                        "Could not load teams. Try again.")); });
            }
        }, "team-catalog").start();
    }

    private void saveConfiguredClubs(CalendarFeed[] chosen) {
        SharedPreferences saved = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor edit = saved.edit();
        for (int i = 0; i < 2; i++) {
            String key = "club" + (i + 1);
            edit.putString(key + "_url", chosen[i].url)
                    .putString(key + "_name", chosen[i].club)
                    .putString(key + "_league", chosen[i].league)
                    .putString(key + "_league_slug", chosen[i].leagueSlug);
            if (!chosen[i].url.equals(i == 0 ? clubOneCalendar.url : clubTwoCalendar.url))
                edit.remove(key + "_calendar_ics").remove(key + "_calendar_matches")
                        .remove(key + "_calendar_updated");
        }
        edit.apply();
        clubOneCalendar = configuredCalendar(saved, 1);
        clubTwoCalendar = configuredCalendar(saved, 2);
        restoreCalendar(clubOneCalendar, saved);
        restoreCalendar(clubTwoCalendar, saved);
        clubOneCalendar.settingsRefreshPending = true;
        clubTwoCalendar.settingsRefreshPending = true;
        applyVisualSettingsNow();
    }

    private View tileGlow(int x, int y, int w, int h, int radius) {
        View glow = new View(this);
        glow.setBackground(new AppGlowDrawable(radius, true));
        glow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glow.setAlpha(0f);
        place(glow, x - 24, y - 22, w + 48, h + 44);
        return glow;
    }

    private void showTileGlow(View glow, boolean focused) {
        glow.animate().alpha(focused ? 1f : 0f).setDuration(170).start();
    }

    private void renderSpotifyBar() {
        // Now playing takes the date's place under the clock while music plays.
        float x = 96, y = 212, w = 560, h = 64;
        View aura = dFocusAura(x, y, w, h, 32);
        spotifyBar = dCard(x, y, w, h, 32);
        spotifyBar.setOnClickListener(v -> launch("com.spotify.tv.android"));
        dFocusable(spotifyBar, aura, null);
        spotifyCover = new ImageView(this);
        spotifyCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable coverShape = new GradientDrawable();
        coverShape.setShape(GradientDrawable.OVAL);
        coverShape.setColor(0x331DB954);
        RoundClipFrame coverFrame = new RoundClipFrame(-1);
        coverFrame.setBackground(coverShape);
        coverFrame.clipContent();
        addD(spotifyBar, coverFrame, 8, 8, 48, 48);
        coverFrame.addView(spotifyCover, new FrameLayout.LayoutParams(-1, -1));
        spotifyTitle = new NowPlayingTicker(this, pxD(22), D_INK);
        addD(spotifyBar, spotifyTitle, 70, 0, w - 70 - 22, h);
        updateSpotifyBar();
    }

    private void updateSpotifyBar() {
        if (spotifyBar == null) return;
        boolean visible = spotifyTrack != null;
        if (!visible && spotifyBar.hasFocus()) {
            if (dockExpand != null) dockExpand.requestFocus();
            else root.requestFocus();
        }
        spotifyBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (date != null) date.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        if (!visible) return;
        spotifyTitle.setSong(spotifyPerformer, spotifyTrack);
        spotifyCover.setImageBitmap(spotifyArtwork);
        spotifyBar.setContentDescription("Spotify: " + (spotifyPerformer == null ? "" : spotifyPerformer + ", ") + spotifyTrack);
    }

    private void enableSpotifyPanel() {
        showChoiceDialog("Spotify", new String[]{
                tr("Włącz dostęp do odtwarzanej muzyki", "Enable now playing access")}, which -> {
            showMessageDialog("Spotify", tr(
                    "W ustawieniach systemowych zezwól LinarTV na dostęp do powiadomień. Launcher odczytuje tylko sesję Spotify; nie zapisuje powiadomień.",
                    "Allow LinarTV notification access in system settings. Only the Spotify session is read; notifications are not stored."));
            openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        });
    }

    private long matchVisibleUntil(IcsMatchParser.Match match) {
        if (match.hasTime) return match.startsAt + MATCH_VISIBLE_AFTER_KICKOFF_MS;
        Calendar nextDay = Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
        nextDay.setTimeInMillis(match.startsAt);
        nextDay.add(Calendar.DAY_OF_MONTH, 1);
        return nextDay.getTimeInMillis();
    }

    private List<IcsMatchParser.Match> upcomingMatches(CalendarFeed feed, long now) {
        List<IcsMatchParser.Match> upcoming = new ArrayList<>();
        for (IcsMatchParser.Match candidate : feed.matches)
            if (now < matchVisibleUntil(candidate)) upcoming.add(candidate);
        return upcoming;
    }

    private boolean matchIsLive(IcsMatchParser.Match match, long now) {
        return match != null && match.hasTime && now >= match.startsAt
                && now < matchVisibleUntil(match);
    }

    private boolean matchCardNeedsUpdate(CalendarFeed feed, long now) {
        if (!feed.configured()) return false;
        List<IcsMatchParser.Match> upcoming = upcomingMatches(feed, now);
        long first = upcoming.isEmpty() ? 0 : upcoming.get(0).startsAt;
        if (first != feed.renderedFirstMatchStart) return true;
        IcsMatchParser.Match shown = upcoming.isEmpty() ? null
                : upcoming.get(Math.min(feed.selected, upcoming.size() - 1));
        return matchIsLive(shown, now) != feed.renderedLive;
    }

    private void scheduleNextMatchBoundary() {
        handler.removeCallbacks(updateMatchCards);
        if (!dashboardVisible) return;
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (CalendarFeed feed : new CalendarFeed[]{clubOneCalendar, clubTwoCalendar}) {
            if (!feed.configured()) continue;
            for (IcsMatchParser.Match match : feed.matches) {
                if (match.hasTime && match.startsAt > now)
                    next = Math.min(next, match.startsAt);
                long end = matchVisibleUntil(match);
                if (end > now) next = Math.min(next, end);
            }
        }
        if (next != Long.MAX_VALUE)
            handler.postDelayed(updateMatchCards, Math.max(1, next - now + 100));
    }

    private void renderMatchCard(float x, float w, CalendarFeed feed) {
        int firstChild = root.getChildCount();
        renderMatchCardContent(x, w, feed);
        feed.cardViews.clear();
        for (int i = firstChild; i < root.getChildCount(); i++)
            feed.cardViews.add(root.getChildAt(i));
    }

    private void replaceMatchCard(float x, float w, CalendarFeed feed) {
        int position = feed.cardViews.isEmpty() ? root.getChildCount()
                : root.indexOfChild(feed.cardViews.get(0));
        for (List<CrestSlot> slots : crestSlots.values())
            slots.removeIf(slot -> feed.cardViews.contains(slot.image)
                    || slot.image.getParent() instanceof View && feed.cardViews.contains((View) slot.image.getParent()));
        for (View view : feed.cardViews) root.removeView(view);
        renderMatchCard(x, w, feed);
        for (View view : feed.cardViews) {
            root.removeView(view);
            root.addView(view, Math.min(position++, root.getChildCount()));
        }
        if (feed.tileButton != null) feed.tileButton.requestFocus();
    }

    /** Design match card: competition, both teams (the chosen club in bold), kick-off and home/away. */
    private void renderMatchCardContent(float x, float w, CalendarFeed feed) {
        final float y = D_EVENTS_Y, h = D_EVENTS_H;
        final String club = feed.club;
        long now = System.currentTimeMillis();
        List<IcsMatchParser.Match> upcoming = upcomingMatches(feed, now);
        long first = upcoming.isEmpty() ? 0 : upcoming.get(0).startsAt;
        if (feed.renderedFirstMatchStart != 0 && first != feed.renderedFirstMatchStart)
            feed.selected = 0;
        feed.renderedFirstMatchStart = first;
        if (feed.selected >= upcoming.size()) feed.selected = 0;
        IcsMatchParser.Match match = upcoming.isEmpty() ? null : upcoming.get(feed.selected);
        boolean live = matchIsLive(match, now);
        feed.renderedLive = live;
        boolean home = match != null && match.home.startsWith(club);
        View aura = dFocusAura(x, y, w, h, 32);
        FrameLayout card = dCard(x, y, w, h, 32);

        if (match != null) {
            ((RoundClipFrame) card).clipContent();
            // The club's crest, large and faded, in the bottom-right corner (as in the design).
            String crestName = home ? match.home : match.away;
            int teamId = home ? match.homeTeamId : match.awayTeamId;
            ImageView mark = new ImageView(this);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mark.setAlpha(0.17f);
            mark.setRotation(-8f);
            android.graphics.ColorMatrix desaturate = new android.graphics.ColorMatrix();
            desaturate.setSaturation(0.55f);
            mark.setColorFilter(new android.graphics.ColorMatrixColorFilter(desaturate));
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addD(card, mark, w + 54 - 330, h + 64 - 330, 330, 330);
            Bitmap crest = crests.get(crestName, teamId);
            if (crest != null) mark.setImageBitmap(crest);
            crestSlots.computeIfAbsent(feed.leagueSlug + "|" + crestName, k -> new ArrayList<>())
                    .add(new CrestSlot(mark, new View(this), crestName, feed.leagueSlug, teamId, true));
        }

        String competition = match != null && !match.competition.isEmpty() ? match.competition
                : match == null ? feed.league : tr("Rozgrywki", "Competition");
        String stage = match != null && !match.stage.isEmpty() ? " · " + match.stage
                : match != null && match.round > 0 ? tr(" · Kolejka ", " · Round ") + match.round : "";
        String lower = competition.toLowerCase(Locale.ROOT);
        boolean cup = match != null && (!match.stage.isEmpty() || lower.contains("puchar") || lower.contains("cup"));
        cardHeader(card, w, cup ? R.drawable.ic_ds_trophy : R.drawable.ic_ds_shield, competition + stage,
                stampLabel(feed.updatedAt), 0.8f);

        if (match == null) {
            TextView title = dText(card, tr("Brak nadchodzących meczów", "No upcoming matches"), 32, 800,
                    D_INK, 34, 96, w - 68, 84);
            title.setSingleLine(false);
            title.setMaxLines(2);
            title.setGravity(Gravity.TOP);
            dText(card, feed.loading ? tr("Pobieranie terminarza…", "Loading fixtures…")
                    : tr("Terminarz chwilowo niedostępny", "Fixtures temporarily unavailable"), 20, 600,
                    inkAlpha(0.68f), 34, h - 30 - 28, w - 68, 28);
            card.setContentDescription(club + ". " + tr("Brak nadchodzących meczów", "No upcoming matches"));
        } else {
            boolean clubAtHome = home;
            dText(card, match.home, clubAtHome ? 36 : 30, clubAtHome ? 800 : 500,
                    clubAtHome ? D_INK : inkAlpha(0.72f), 34, 88, w - 68, 42);
            dText(card, match.away, clubAtHome ? 30 : 36, clubAtHome ? 500 : 800,
                    clubAtHome ? inkAlpha(0.72f) : D_INK, 34, 134, w - 68, 44);
            String time = match.hasTime ? new SimpleDateFormat("HH:mm", PL).format(new Date(match.startsAt)) : "—:—";
            TextView timeView = dText(card, time, 64, 700, D_INK, 34, 196, 240, 66);
            timeView.setLetterSpacing(-2f / 64f);
            dText(card, matchDateLabel(match), 20, 600, inkAlpha(0.68f), 34, 266, w - 68 - 150, 26);
            if (live) cardPill(card, w, h, tr("Na żywo", "Live"), 0xFFD8283F, 0xFFFF9AA6, 0xFFFFFFFF);
            else cardPill(card, w, h, home ? tr("U siebie", "Home") : tr("Wyjazd", "Away"),
                    0x14FFFFFF, 0x24FFFFFF, D_INK);
            card.setContentDescription(match.home + " – " + match.away + ", " + time + ", "
                    + matchDateLabel(match) + (upcoming.size() > 1
                    ? tr(". OK — następny mecz", ". OK — next match") : ""));
        }
        dFocusable(card, aura, null);
        feed.tileButton = card;
        card.setOnClickListener(v -> {
            if (upcoming.size() < 2) return;
            feed.selected = feed.selected == 0 ? 1 : 0;
            replaceMatchCard(x, w, feed);
        });
    }

    private String shortName(String name) {
        String shortened = name.trim();
        while (shortened.length() > 24 && shortened.lastIndexOf(' ') > 0)
            shortened = shortened.substring(0, shortened.lastIndexOf(' '));
        return shortened;
    }

    private String matchDateLabel(IcsMatchParser.Match match) {
        return relativeDayLabel(match.startsAt);
    }

    private void updateCrestViews(String club) {
        for (List<CrestSlot> slots : crestSlots.values()) for (CrestSlot slot : slots) {
            if (!club.equals(slot.name)) continue;
            Bitmap bitmap = crests.get(slot.name, slot.teamId);
            if (bitmap == null) continue;
            if (slot.watermark) slot.image.setImageBitmap(bitmap);
            else setCrestBitmap(slot.image, bitmap);
            slot.image.setVisibility(View.VISIBLE);
            slot.fallback.setVisibility(View.INVISIBLE);
        }
    }

    private void setCrestBitmap(ImageView image, Bitmap bitmap) {
        Bitmap result = scaledCrests.get(bitmap);
        if (result == null || result.isRecycled()) {
            int target = px(128);
            float ratio = Math.min(target / (float) bitmap.getWidth(), target / (float) bitmap.getHeight());
            int finalWidth = Math.max(1, Math.round(bitmap.getWidth() * ratio));
            int finalHeight = Math.max(1, Math.round(bitmap.getHeight() * ratio));
            Bitmap current = bitmap;
            while (current.getWidth() > finalWidth * 4 && current.getHeight() > finalHeight * 4) {
                Bitmap next = Bitmap.createScaledBitmap(current,
                        Math.max(finalWidth, current.getWidth() / 2),
                        Math.max(finalHeight, current.getHeight() / 2), true);
                if (current != bitmap && next != current) current.recycle();
                current = next;
            }
            result = Bitmap.createScaledBitmap(current, finalWidth, finalHeight, true);
            if (current != bitmap && current != result) current.recycle();
            result.setDensity(getResources().getDisplayMetrics().densityDpi);
            scaledCrests.put(bitmap, result);
        }
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setImageBitmap(result);
    }

    private void placeForecastIcon(String code, int x, int y, int width, int height) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(weatherIcons.bitmap(code, px(Math.max(width, height))));
        place(image, x, y, width, height);
    }

    private static void setLeftPx(View view, int leftPx, int widthPx) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.leftMargin = leftPx;
        lp.width = Math.max(1, widthPx);
        view.setLayoutParams(lp);
    }

    /**
     * City name sized to its length: one line as large as fits (up to 40, close to the
     * temperature digits); if one line would drop below 24, two lines up to 28. Lines break only
     * after spaces and hyphens, never inside a word ("Hellschen-Heringsand-Unterschaar").
     */
    private void fitCityName(TextView view, String name, int widthPx) {
        String[] tokens = name.trim().split("(?<=[ \\-])");
        android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
        List<String> lines = null;
        int size = 40;
        for (; size >= 24 && lines == null; size--) lines = wrapCity(tokens, paint, size, widthPx, 1);
        if (lines == null) for (size = 28; size >= 14 && lines == null; size--)
            lines = wrapCity(tokens, paint, size, widthPx, 2);
        if (lines == null) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(14));
            view.setEllipsize(android.text.TextUtils.TruncateAt.END);
            view.setText(name);
            return;
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size + 1)); // loop stepped once past the fit
        view.setText(android.text.TextUtils.join("\n", lines));
    }

    /** Greedy wrap at the given size; null when a word is too wide or more than maxLines are needed. */
    private List<String> wrapCity(String[] tokens, android.text.TextPaint paint, int size, int widthPx, int maxLines) {
        paint.setTextSize(px(size));
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String token : tokens) {
            if (paint.measureText(token.trim()) > widthPx) return null;
            String candidate = line + token;
            if (line.isEmpty() || paint.measureText(candidate.trim()) <= widthPx) line = candidate;
            else { lines.add(line.trim()); line = token; }
        }
        if (!line.isEmpty()) lines.add(line.trim());
        return lines.size() <= maxLines ? lines : null;
    }

    private List<JSONObject> hourlySamples(int limit) {
        List<JSONObject> samples = new ArrayList<>();
        JSONArray hours = forecast == null ? null : forecast.optJSONArray("hourly");
        if (hours == null) return samples;
        long last = 0;
        for (int i = 0; i < hours.length() && samples.size() < limit; i++) {
            JSONObject item = hours.optJSONObject(i);
            if (item == null) continue;
            long time = item.optLong("time");
            if (time <= 0 || !samples.isEmpty() && time - last < 90 * 60_000L) continue;
            samples.add(item);
            last = time;
        }
        return samples;
    }

    private void showWeatherDetails() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        detailText = night ? Color.WHITE : 0xFF15223E;
        detailMuted = night ? 0xFFC5D3E9 : 0xFF4A5C75;
        detailCard = night ? 0xFF2B4268 : 0xFFE3E9F0;
        detailAstronomy = night ? 0xFF101E38 : 0xFFE5EBF1;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(36), px(24), px(36), px(28));
        content.setBackground(shape(night ? 0xFF142548 : 0xFFF5F2EA,
                night ? 0xFF899BC6 : 0xFFB7C4D5, 26, 1));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(tr("Szczegóły pogody - ", "Weather details - ") + cityName);
        title.setTextColor(detailText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(31));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView updated = new TextView(this);
        updated.setText(currentWeatherTime());
        updated.setTextColor(detailMuted);
        updated.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(18));
        updated.setGravity(Gravity.RIGHT);
        updated.setSingleLine(true);
        LinearLayout.LayoutParams updatedParams = new LinearLayout.LayoutParams(-2, -2);
        updatedParams.leftMargin = px(24);
        header.addView(updated, updatedParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.bottomMargin = px(16);
        content.addView(header, headerParams);
        detailLine(content, tr("Prognoza na 7 dni", "7-day forecast"), 24, true, detailText);
        addDailyForecast(content);
        detailLine(content, tr("Warunki teraz", "Conditions now"), 24, true, detailText);
        addWeatherMetrics(content);
        detailLine(content, tr("Słońce i Księżyc", "Sun and Moon"), 24, true, detailText);
        addAstronomyCards(content);
        detailLine(content, tr("Dane pogodowe: Open-Meteo.com (CC BY 4.0). Ikony: Meteocons (MIT).",
                "Weather data: Open-Meteo.com (CC BY 4.0). Icons: Meteocons (MIT)."), 14, false, detailMuted);
        TextView close = new TextView(this);
        close.setText(tr("Zamknij", "Close"));
        close.setTextColor(detailText);
        close.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        close.setGravity(Gravity.CENTER);
        close.setFocusable(true);
        close.setClickable(true);
        close.setBackground(shape(night ? 0xFF2D466F : 0xFFE3E9F0,
                night ? 0xFF7899C3 : 0xFFB7C4D5, 13));
        close.setOnFocusChangeListener((view, focused) -> close.setBackground(shape(
                focused ? (night ? 0xFF4B75A5 : 0xFFCEDCEC)
                        : (night ? 0xFF2D466F : 0xFFE3E9F0),
                focused ? FOCUS_RING : night ? 0xFF7899C3 : 0xFFB7C4D5, 13,
                focused ? 2 : 1)));
        close.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(px(160), px(48));
        closeParams.gravity = Gravity.END;
        closeParams.topMargin = px(8);
        content.addView(close, closeParams);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(1080), -2);
        close.requestFocus();
    }

    private void showDialogWithBlur(Dialog dialog) {
        showDialogWithBlur(dialog, null);
    }

    private void showDialogWithBlur(Dialog dialog, Runnable afterDismiss) {
        long preparationStarted = android.os.SystemClock.elapsedRealtimeNanos();
        popupDepth++;
        popupOpen = true;
        handler.removeCallbacks(startScreensaver);
        Dialog parentDialog = visibleDialogs.isEmpty() ? null : visibleDialogs.get(visibleDialogs.size() - 1);
        View parentContent = parentDialog != null && parentDialog.isShowing()
                ? parentDialog.findViewById(android.R.id.content) : null;
        final float parentAlpha = parentContent == null ? 1f : parentContent.getAlpha();
        if (parentContent != null) parentContent.setAlpha(0f);
        boolean reusedBackdrop = popupBlurImage != null;
        ensurePopupBackdrop();
        dialog.setOnDismissListener(d -> {
            visibleDialogs.remove(dialog);
            if (parentContent != null) parentContent.setAlpha(parentAlpha);
            popupOpen = --popupDepth > 0;
            if (!popupOpen) releasePopupBackdrop();
            if (afterDismiss != null) afterDismiss.run();
            rebuildWhenPopupsClose();
            resetScreensaverTimer();
        });
        dialog.show();
        visibleDialogs.add(dialog);
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            android.util.Log.d("LinarTVPerf", "popup_prepare_ms="
                    + (android.os.SystemClock.elapsedRealtimeNanos() - preparationStarted) / 1_000_000.0
                    + " depth=" + popupDepth + " reused=" + reusedBackdrop);
    }

    private void ensurePopupBackdrop() {
        if (popupBlurImage != null || root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        int width = Math.max(1, root.getWidth() / 4);
        int height = Math.max(1, root.getHeight() / 4);
        popupBlurBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas capture = new Canvas(popupBlurBitmap);
        capture.scale(width / (float) root.getWidth(), height / (float) root.getHeight());
        root.draw(capture);
        blurBackdrop(popupBlurBitmap);
        popupBlurImage = new ImageView(this);
        popupBlurImage.setImageBitmap(popupBlurBitmap);
        popupBlurImage.setScaleType(ImageView.ScaleType.FIT_XY);
        popupBlurImage.setFocusable(false);
        // Above raised (focused) dock tiles, which would otherwise draw over the blur.
        popupBlurImage.setElevation(px(60));
        root.addView(popupBlurImage, new FrameLayout.LayoutParams(-1, -1));
    }

    private void applyVisualSettingsNow() {
        if (!popupOpen) { buildScreen(); return; }
        // User changes are applied immediately; background network updates still wait.
        releasePopupBackdrop();
        popupOpen = false;
        try { buildScreen(); }
        finally { popupOpen = popupDepth > 0; }
        rebuildAfterPopup = false;
        handler.removeCallbacks(startScreensaver);
        if (publicSettingsDialog != null && publicSettingsDialog.isShowing())
            renderPublicSettings(publicSettingsDialog, false);
        final FrameLayout updatedRoot = root;
        updatedRoot.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (updatedRoot.getViewTreeObserver().isAlive())
                    updatedRoot.getViewTreeObserver().removeOnPreDrawListener(this);
                if (root == updatedRoot && popupOpen) ensurePopupBackdrop();
                return true;
            }
        });
    }

    private void releasePopupBackdrop() {
        if (popupBlurImage != null) {
            ViewGroup parent = (ViewGroup) popupBlurImage.getParent();
            if (parent != null) parent.removeView(popupBlurImage);
            popupBlurImage.setImageDrawable(null);
            popupBlurImage = null;
        }
        if (popupBlurBitmap != null) {
            popupBlurBitmap.recycle();
            popupBlurBitmap = null;
        }
    }

    private void rebuildWhenPopupsClose() {
        handler.post(() -> {
            if (!popupOpen && rebuildAfterPopup && !isFinishing() && !isDestroyed()) {
                rebuildAfterPopup = false;
                buildScreen();
            }
        });
    }

    private void blurBackdrop(Bitmap bitmap) {
        blurBackdrop(bitmap, 4, 2);
    }

    private void blurBackdrop(Bitmap bitmap, int radius, int passes) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        int[] temp = new int[pixels.length];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        BlurPixels.apply(pixels, temp, width, height, radius, passes);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private void addDailyForecast(LinearLayout content) {
        JSONObject daily = forecast == null ? null : forecast.optJSONObject("daily");
        if (daily == null) {
            detailLine(content, tr("Prognoza chwilowo niedostępna", "Forecast temporarily unavailable"),
                    18, false, detailMuted);
            return;
        }
        JSONArray dates = daily.optJSONArray("time");
        JSONArray highs = daily.optJSONArray("temperature_2m_max");
        JSONArray lows = daily.optJSONArray("temperature_2m_min");
        JSONArray icons = daily.optJSONArray("icon");
        if (dates == null || highs == null || lows == null || icons == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, px(125));
        rowParams.bottomMargin = px(16);
        content.addView(row, rowParams);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", PL);
        SimpleDateFormat labelFormat = new SimpleDateFormat("EEE d", displayLocale());
        for (int i = 0; i < Math.min(7, dates.length()); i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackground(shape(detailCard, Color.TRANSPARENT, 15));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -1, 1);
            cardParams.setMargins(px(4), 0, px(4), 0);
            row.addView(card, cardParams);
            TextView day = new TextView(this);
            try {
                String label = labelFormat.format(iso.parse(dates.getString(i))).replace(".", "");
                day.setText(Character.toUpperCase(label.charAt(0)) + label.substring(1));
            } catch (Exception ignored) { day.setText("—"); }
            day.setTextColor(detailMuted);
            day.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(16));
            day.setGravity(Gravity.CENTER);
            card.addView(day, new LinearLayout.LayoutParams(-1, px(30)));
            LinearLayout.LayoutParams symbolParams = new LinearLayout.LayoutParams(px(51), px(46));
            symbolParams.gravity = Gravity.CENTER_HORIZONTAL;
            card.addView(detailWeatherIcon(icons.optString(i, "")), symbolParams);
            TextView temperature = new TextView(this);
            temperature.setText(temperatureLabelParts(shownTemperature(highs.optDouble(i)) + "° / "
                    + shownTemperature(lows.optDouble(i)) + "°"));
            temperature.setTextColor(detailText);
            temperature.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(18));
            temperature.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            temperature.setGravity(Gravity.CENTER);
            card.addView(temperature, new LinearLayout.LayoutParams(-1, px(34)));
        }
    }

    private View detailWeatherIcon(String code) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(weatherIcons.bitmap(code, px(56)));
        return image;
    }

    private void addWeatherMetrics(LinearLayout content) {
        String[] labels = {tr("Wiatr", "Wind"), tr("Wilgotność", "Humidity"),
                tr("Ciśnienie", "Pressure"), tr("Opad", "Precipitation")};
        String[] values = {observed("wind", " m/s"),
                observed("humidity", "%"), observed("pressure", " hPa"),
                observed("precipitation", " mm")};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, px(80));
        rowParams.bottomMargin = px(16);
        content.addView(row, rowParams);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(px(14), 0, px(8), 0);
            card.setBackground(shape(detailCard, Color.TRANSPARENT, 14));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -1, 1);
            cardParams.setMargins(px(4), 0, px(4), 0);
            row.addView(card, cardParams);
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextColor(detailMuted);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
            card.addView(label);
            TextView value = new TextView(this);
            value.setText(values[i]);
            value.setTextColor(detailText);
            value.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(21));
            value.setSingleLine(true);
            card.addView(value);
        }
    }

    private void addAstronomyCards(LinearLayout content) {
        JSONObject sun = forecast == null ? null : forecast.optJSONObject("sun");
        TimeZone weatherZone = TimeZone.getTimeZone(
                forecast == null ? "Europe/Warsaw" : forecast.optString("timezone", "Europe/Warsaw"));
        Date sunrise = sun == null ? null : PublicForecastClient.parseTime(sun.optString("Sunrise"), forecastZone());
        Date sunset = sun == null ? null : PublicForecastClient.parseTime(sun.optString("Sunset"), forecastZone());
        SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", PL);
        clockFormat.setTimeZone(weatherZone);
        String rise = sunrise == null ? "—" : clockFormat.format(sunrise);
        String set = sunset == null ? "—" : clockFormat.format(sunset);
        int phase = moonPhaseIndex();
        String[] phases = {tr("Nów", "New moon"), tr("Sierp przybywający", "Waxing crescent"),
                tr("Pierwsza kwadra", "First quarter"), tr("Księżyc garbaty przybywający", "Waxing gibbous"),
                tr("Pełnia", "Full moon"), tr("Księżyc garbaty ubywający", "Waning gibbous"),
                tr("Ostatnia kwadra", "Last quarter"), tr("Sierp ubywający", "Waning crescent")};

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, px(169));
        rowParams.bottomMargin = px(12);
        content.addView(row, rowParams);

        LinearLayout sunCard = astronomyCard(row, tr("Pozycja Słońca", "Sun position"));
        SunTrack track = new SunTrack(sunrise, sunset);
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(px(190), px(78));
        trackParams.gravity = Gravity.CENTER_HORIZONTAL;
        sunCard.addView(track, trackParams);
        astronomyLabel(sunCard, tr("Wschód  ", "Sunrise  ") + rise
                + tr("      Zachód  ", "      Sunset  ") + set);

        LinearLayout moonCard = astronomyCard(row, tr("Faza Księżyca", "Moon phase"));
        MoonPhaseGraphic moon = new MoonPhaseGraphic(phase);
        LinearLayout.LayoutParams moonParams = new LinearLayout.LayoutParams(px(72), px(72));
        moonParams.gravity = Gravity.CENTER_HORIZONTAL;
        moonCard.addView(moon, moonParams);
        astronomyLabel(moonCard, phases[phase]);
    }

    private LinearLayout astronomyCard(LinearLayout row, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(px(10), px(10), px(10), px(8));
        card.setBackground(shape(detailAstronomy, night ? 0xFF48638A : 0xFFC9D4E0, 17));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(px(3), 0, px(3), 0);
        row.addView(card, params);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(detailMuted);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(18));
        heading.setGravity(Gravity.CENTER);
        card.addView(heading, new LinearLayout.LayoutParams(-1, px(32)));
        return card;
    }

    private void astronomyLabel(LinearLayout card, String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(detailText);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(17));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        card.addView(label, new LinearLayout.LayoutParams(-1, px(38)));
    }

    // Moon phase is computed locally (eight-phase calendar approximation), not downloaded.
    private int moonPhaseIndex() {
        Calendar date = Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
        int year = date.get(Calendar.YEAR), month = date.get(Calendar.MONTH) + 1;
        if (month < 3) { year--; month += 12; }
        double age = (365.25 * year + 30.6 * (month + 1) + date.get(Calendar.DAY_OF_MONTH) - 694039.09) / 29.5305882;
        return Math.round((float) (8 * (age - (int) age))) % 8;
    }

    private final class SunTrack extends View {
        private final Date rise, set;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SunTrack(Date rise, Date set) { super(DashboardActivity.this); this.rise = rise; this.set = set; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save(); canvas.scale(getWidth() / 190f, getHeight() / 78f);
            RectF arc = new RectF(40, 13, 150, 123);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(9);
            paint.setColor(night ? 0xFF374963 : 0xFFA7B4C6);
            canvas.drawArc(arc, 180, 180, false, paint);
            if (rise != null && set != null && set.after(rise)) {
                double progress = Math.max(0, Math.min(1,
                        (System.currentTimeMillis() - rise.getTime()) / (double) (set.getTime() - rise.getTime())));
                float angle = (float) Math.toRadians(180 + progress * 180);
                paint.setStyle(Paint.Style.FILL); paint.setColor(0xFFFFB42D);
                canvas.drawCircle(95 + 55 * (float) Math.cos(angle), 68 + 55 * (float) Math.sin(angle), 9, paint);
            }
            canvas.restore();
        }
    }

    private final class MoonPhaseGraphic extends View {
        private final int phase;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        MoonPhaseGraphic(int phase) { super(DashboardActivity.this); this.phase = phase; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy) - 2;
            paint.setColor(0xFF293344);
            canvas.drawCircle(cx, cy, radius, paint);
            Path disk = new Path(); disk.addCircle(cx, cy, radius, Path.Direction.CW);
            Path lit = new Path(disk);
            Path shifted = new Path();
            if (phase == 0) lit.reset();
            else if (phase == 1 || phase == 7) {
                shifted.addCircle(cx + (phase == 1 ? -radius * 0.72f : radius * 0.72f), cy, radius, Path.Direction.CW);
                lit.op(shifted, Path.Op.DIFFERENCE);
            } else if (phase == 2 || phase == 6) {
                Path half = new Path();
                half.addRect(phase == 2 ? cx : cx - radius, cy - radius,
                        phase == 2 ? cx + radius : cx, cy + radius, Path.Direction.CW);
                lit.op(half, Path.Op.INTERSECT);
            } else if (phase == 3 || phase == 5) {
                shifted.addCircle(cx + (phase == 3 ? radius * 0.22f : -radius * 0.22f), cy, radius, Path.Direction.CW);
                lit.op(shifted, Path.Op.INTERSECT);
            }
            canvas.save(); canvas.clipPath(lit);
            paint.setColor(0xFFE5E5DC);
            canvas.drawCircle(cx, cy, radius, paint);
            canvas.restore();
        }
    }

    private void detailLine(LinearLayout parent, String value, int size, boolean bold, int color) {
        TextView line = new TextView(this);
        line.setText(value);
        line.setTextColor(color);
        line.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        if (bold) line.setTypeface(null, Typeface.BOLD);
        line.setPadding(0, 0, 0, px(14));
        parent.addView(line);
    }

    private void buildAppDock() {
        // While a tile is being moved the unsaved order wins over the saved one.
        if (dockMoveIndex < 0) {
            dockPackages.clear();
            dockPackages.addAll(resolveDock());
        }
        // Eight round app logos in design columns; fewer apps keep their size and sit centred.
        dockLabels.clear();
        int tiles = Math.max(1, dockPackages.size());
        float columnW = (D_CONTENT_W - (DOCK_SIZE - 1) * D_DOCK_GAP) / DOCK_SIZE;
        float span = tiles * columnW + (tiles - 1) * D_DOCK_GAP;
        float firstX = D_LEFT + (D_CONTENT_W - span) / 2;
        for (int i = 0; i < dockPackages.size(); i++)
            dockTile(appLabel(dockPackages.get(i)), dockPackages.get(i), firstX + i * (columnW + D_DOCK_GAP), columnW);
        // With no installed apps at all, a single "+" tile leads to the editor instead of an empty dock.
        if (dockPackages.isEmpty()) dockTile(tr("Dodaj aplikacje", "Add apps"), null, firstX, columnW);
        dockMoveHint = null;
        if (dockMoveIndex >= 0) handler.post(this::paintDockMove);

        drawerBackdrop = new FrameLayout(this);
        drawerBackdrop.setClickable(true);
        drawerBackdrop.setOnClickListener(v -> closeAppDrawer());
        drawerBlurImage = new ImageView(this);
        drawerBlurImage.setScaleType(ImageView.ScaleType.FIT_XY);
        drawerBackdrop.addView(drawerBlurImage, new FrameLayout.LayoutParams(-1, -1));
        View drawerTint = new View(this);
        drawerTint.setBackgroundColor(night ? 0x800B1B37 : 0x8CECF1F8);
        drawerBackdrop.addView(drawerTint, new FrameLayout.LayoutParams(-1, -1));
        root.addView(drawerBackdrop, new FrameLayout.LayoutParams(-1, -1));
        drawerBackdrop.setVisibility(View.GONE);

        drawerTitle = text(tr("Aplikacje", "Apps"), 31, night ? Color.WHITE : 0xFF15223E,
                true, 56, 378, 500, 52);
        drawerTitle.setVisibility(View.GONE);

        appDrawer = new ScrollView(this);
        appDrawer.setVerticalScrollBarEnabled(false);
        appDrawer.setFillViewport(false);
        appDrawer.setFocusable(false);
        appDrawer.setClipChildren(false);
        appDrawer.setClipToPadding(false);
        place(appDrawer, 36, 450, 1600, APP_ROW_HEIGHT * 3);
        appDrawer.setVisibility(View.GONE);
        drawerRows = new LinearLayout(this);
        drawerRows.setOrientation(LinearLayout.VERTICAL);
        drawerRows.setClipChildren(false);
        appDrawer.addView(drawerRows, new ScrollView.LayoutParams(-1, -2));
        extraAppRows.clear();
        drawerRowsLoaded = false;

        float expandX = 960 - 18, expandY = 1040;
        View expandAura = dFocusAura(expandX, expandY, 36, 36, -1);
        dockExpand = new ImageView(this);
        dockExpand.setImageResource(R.drawable.ic_ds_chevron);
        dockExpand.setColorFilter(inkAlpha(0.8f));
        dockExpand.setContentDescription(tr("Więcej aplikacji — strzałka w dół", "More apps — press down"));
        dockExpand.setPadding(pxD(8), pxD(8), pxD(8), pxD(8));
        dockExpand.setBackground(new BackdropGlass(dockExpand, -1, D_GLASS, D_GLASS_BORDER));
        dockExpand.setOutlineProvider(clipOutline(-1));
        glassHosts.add(dockExpand);
        placeD(dockExpand, expandX, expandY, 36, 36);
        dFocusable(dockExpand, expandAura, null);
        dockExpand.setOnClickListener(v -> openAppDrawer());
    }

    /** Round dock tile showing the app's own logo (its launcher icon), with the name underneath. */
    private void dockTile(String brand, String pkg, float columnX, float columnW) {
        float x = columnX + (columnW - D_DOCK_ICON) / 2;
        View aura = dFocusAura(x, D_DOCK_Y, D_DOCK_ICON, D_DOCK_ICON, -1);
        dockGlows.add(aura);
        RoundClipFrame card = new RoundClipFrame(-1);
        Bitmap logo = pkg == null ? null : appLogo(pkg, pxD(D_DOCK_ICON));
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.OVAL);
        base.setColor(logo == null ? 0xFF161A2A : 0x00000000);
        if (logo == null) base.setStroke(Math.max(1, pxD(1)), 0x29FFFFFF);
        card.setBackground(base);
        card.setOutlineProvider(clipOutline(-1));
        placeD(card, x, D_DOCK_Y, D_DOCK_ICON, D_DOCK_ICON);
        dockCards.add(card);
        card.setContentDescription(brand);
        if (logo != null) {
            ImageView icon = new ImageView(this);
            icon.setImageBitmap(logo);
            icon.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView plus = new TextView(this);
            plus.setText(pkg == null ? "+" : brand.substring(0, Math.min(2, brand.length())));
            plus.setTextColor(D_INK);
            plus.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxD(pkg == null ? 56 : 40));
            plus.setTypeface(manrope(700));
            plus.setGravity(Gravity.CENTER);
            card.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        }
        TextView label = dText(null, brand, 19, 600, inkAlpha(0.74f), columnX - 8, D_DOCK_Y + D_DOCK_ICON + 14,
                columnW + 16, 28);
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dockLabels.add(label);
        Sheen sheen = prepareDesignFocus(card);
        card.setOnFocusChangeListener((view, focused) -> {
            if (focused) lastDockFocus = card;
            if (dockMoveIndex < 0) showDesignFocus(card, aura, sheen, focused);
            label.setTextColor(focused ? D_INK : inkAlpha(0.74f));
        });
        card.setOnClickListener(view -> {
            if (pkg == null) showDockEditor(false); else launch(pkg);
        });
        if (pkg != null) {
            card.setLongClickable(true);
            card.setOnLongClickListener(view -> {
                showDockMenu(dockCards.indexOf(card));
                return true;
            });
        }
    }

    /**
     * The app's own logo as a round bitmap. Adaptive icons are drawn full-bleed so their safe zone
     * fills the circle; legacy icons are cropped to their visible shape, logos without a backdrop
     * are kept whole on the dark disc.
     */
    private Bitmap appLogo(String pkg, int size) {
        String key = "app:" + pkg + ":" + size;
        Bitmap cached = logoCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        PackageManager manager = getPackageManager();
        Drawable icon = null;
        try {
            android.content.pm.ApplicationInfo info = manager.getApplicationInfo(pkg, 0);
            if (info.icon != 0) {
                try {
                    icon = manager.getResourcesForApplication(info)
                            .getDrawableForDensity(info.icon, DisplayMetrics.DENSITY_XXXHIGH, null);
                } catch (Exception ignored) { icon = null; }
            }
            if (icon == null) icon = manager.getApplicationIcon(info);
        } catch (PackageManager.NameNotFoundException e) { return null; }
        if (icon == null || size <= 0) return null;
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        if (icon instanceof android.graphics.drawable.AdaptiveIconDrawable) {
            android.graphics.drawable.AdaptiveIconDrawable adaptive = (android.graphics.drawable.AdaptiveIconDrawable) icon;
            int bleed = Math.round(size * 0.25f); // 108 dp layers, 72 dp visible
            Drawable back = adaptive.getBackground(), front = adaptive.getForeground();
            if (back != null) { back.setBounds(-bleed, -bleed, size + bleed, size + bleed); back.draw(canvas); }
            if (front != null) { front.setBounds(-bleed, -bleed, size + bleed, size + bleed); front.draw(canvas); }
        } else {
            int source = Math.max(size, 192);
            Bitmap raw = Bitmap.createBitmap(source, source, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, source, source);
            icon.draw(new Canvas(raw));
            int[] pixels = new int[source * source];
            raw.getPixels(pixels, 0, source, 0, 0, source, source);
            int left = source, top = source, right = -1, bottom = -1, opaque = 0;
            for (int yy = 0; yy < source; yy++) for (int xx = 0; xx < source; xx++) {
                if ((pixels[yy * source + xx] >>> 24) < 24) continue;
                opaque++;
                if (xx < left) left = xx;
                if (xx > right) right = xx;
                if (yy < top) top = yy;
                if (yy > bottom) bottom = yy;
            }
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            if (right < left) { raw.recycle(); return null; }
            int boxW = right - left + 1, boxH = bottom - top + 1;
            float fill = opaque / (float) (boxW * boxH);
            if (fill >= 0.72f) {
                // A shape icon (square, squircle, circle): its visible shape fills the disc.
                int side = Math.max(boxW, boxH);
                int cx = (left + right) / 2, cy = (top + bottom) / 2;
                android.graphics.Rect from = new android.graphics.Rect(cx - side / 2, cy - side / 2,
                        cx - side / 2 + side, cy - side / 2 + side);
                canvas.drawBitmap(raw, from, new RectF(0, 0, size, size), paint);
            } else {
                // A bare logo mark: keep it whole inside the circle.
                float inner = size * 0.64f;
                float ratio = inner / Math.max(boxW, boxH);
                float dw = boxW * ratio, dh = boxH * ratio;
                canvas.drawBitmap(raw, new android.graphics.Rect(left, top, right + 1, bottom + 1),
                        new RectF((size - dw) / 2f, (size - dh) / 2f, (size + dw) / 2f, (size + dh) / 2f), paint);
            }
            raw.recycle();
        }
        // Round, anti-aliased edge baked into the bitmap (no runtime clipping).
        Bitmap round = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        roundPaint.setShader(new android.graphics.BitmapShader(result,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        new Canvas(round).drawOval(new RectF(0.5f, 0.5f, size - 0.5f, size - 0.5f), roundPaint);
        result.recycle();
        logoCache.put(key, round);
        return round;
    }

    /** TV apps plus apps the person installed themselves, preferred apps first. */
    private List<String> dockCandidates() {
        java.util.LinkedHashSet<String> found = new java.util.LinkedHashSet<>();
        PackageManager manager = getPackageManager();
        for (ResolveInfo info : manager.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
            found.add(info.activityInfo.packageName);
        for (ResolveInfo info : manager.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            android.content.pm.ApplicationInfo app = info.activityInfo.applicationInfo;
            if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0)
                found.add(info.activityInfo.packageName);
        }
        found.remove(getPackageName());
        found.removeAll(HIDDEN_APP_PACKAGES);
        List<String> ordered = new ArrayList<>();
        for (String pkg : POPULAR_DOCK_PACKAGES) if (found.contains(pkg)) ordered.add(pkg);
        for (String pkg : found) if (!ordered.contains(pkg)) ordered.add(pkg);
        return ordered;
    }

    /** Saved choice first. An uninstalled app's slot is refilled in place, so the others stay put. */
    private List<String> resolveDock() {
        List<String> candidates = dockCandidates();
        List<String> slots = new ArrayList<>();
        String saved = getPreferences(MODE_PRIVATE).getString("dock_apps", null);
        if (saved != null) for (String pkg : saved.split(",")) {
            if (pkg.isEmpty() || slots.size() >= DOCK_SIZE) continue;
            slots.add(candidates.contains(pkg) && !slots.contains(pkg) ? pkg : null);
        }
        java.util.Iterator<String> spare = candidates.iterator();
        for (int i = 0; i < slots.size(); i++)
            if (slots.get(i) == null) slots.set(i, nextSpareApp(spare, slots));
        // A saved dock keeps the number of apps the person chose; only the automatic one fills to six.
        if (saved == null) while (slots.size() < DOCK_SIZE) slots.add(nextSpareApp(spare, slots));
        slots.removeIf(java.util.Objects::isNull);
        return slots;
    }

    private static String nextSpareApp(java.util.Iterator<String> spare, List<String> taken) {
        while (spare.hasNext()) {
            String pkg = spare.next();
            if (!taken.contains(pkg)) return pkg;
        }
        return null;
    }

    private void saveDock(List<String> packages) {
        getPreferences(MODE_PRIVATE).edit()
                .putString("dock_apps", android.text.TextUtils.join(",", packages)).apply();
    }

    private String appLabel(String pkg) {
        try {
            PackageManager manager = getPackageManager();
            return manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) { return pkg; }
    }

    private void refreshDock(int focusIndex) {
        applyVisualSettingsNow();
        handler.postDelayed(() -> {
            if (!dockCards.isEmpty()) dockCards.get(Math.max(0, Math.min(focusIndex, dockCards.size() - 1))).requestFocus();
        }, 120);
    }

    // ---------- Long-press menu: replace, move, edit ----------

    private void showDockMenu(int index) {
        if (index < 0 || index >= dockPackages.size()) return;
        List<String> labels = new ArrayList<>(java.util.Arrays.asList(
                tr("Zamień aplikację", "Replace app"), tr("Przesuń", "Move"),
                tr("Edytuj cały dock", "Edit the whole dock")));
        if (dockPackages.size() > 1) labels.add(tr("Usuń z docku", "Remove from dock"));
        showChoiceDialog(appLabel(dockPackages.get(index)), labels.toArray(new String[0]), choice -> {
            if (choice == 0) showDockReplace(index);
            else if (choice == 1) startDockMove(index);
            else if (choice == 2) showDockEditor(false);
            else {
                List<String> next = new ArrayList<>(dockPackages);
                next.remove(index);
                saveDock(next);
                refreshDock(index);
            }
        });
    }

    private void showDockReplace(int index) {
        List<String> options = new ArrayList<>();
        for (String pkg : dockCandidates()) if (!dockPackages.contains(pkg)) options.add(pkg);
        String current = appLabel(dockPackages.get(index));
        if (options.isEmpty()) {
            showMessageDialog(tr("Zamień aplikację", "Replace app"),
                    tr("Nie ma innych zainstalowanych aplikacji.", "There are no other installed apps."));
            return;
        }
        String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = appLabel(options.get(i));
        showChoiceDialog(tr("Zamień „", "Replace “") + current + "”", labels, choice -> {
            List<String> next = new ArrayList<>(dockPackages);
            next.set(index, options.get(choice));
            saveDock(next);
            refreshDock(index);
        });
    }

    private void startDockMove(int index) {
        dockMoveBackup = new ArrayList<>(dockPackages);
        dockMoveIndex = index;
        handler.postDelayed(this::paintDockMove, 80);
    }

    private void paintDockMove() {
        if (dockMoveIndex < 0 || dockMoveIndex >= dockCards.size()) return;
        dockCards.get(dockMoveIndex).requestFocus();
        for (int i = 0; i < dockCards.size(); i++) {
            boolean lifted = i == dockMoveIndex;
            View card = dockCards.get(i);
            card.animate().translationY(lifted ? -px(18) : 0).scaleX(lifted ? 1.05f : 1f)
                    .scaleY(lifted ? 1.05f : 1f).alpha(lifted ? 1f : 0.55f).setDuration(160).start();
            card.setElevation(lifted ? px(18) : 0);
            dockGlows.get(i).animate().alpha(lifted ? 1f : 0f).translationY(lifted ? -px(18) : 0)
                    .setDuration(160).start();
        }
        if (dockMoveHint == null || dockMoveHint.getParent() != root) {
            dockMoveHint = text(tr("◀ ▶  przesuń   ·   OK  zapisz   ·   Wstecz  anuluj",
                    "◀ ▶  move   ·   OK  save   ·   Back  cancel"), 21, Color.WHITE, false, 520, 690, 560, 50);
            dockMoveHint.setGravity(Gravity.CENTER);
            dockMoveHint.setBackground(shape(0xE0152146, 0x809FD5FF, 18));
        }
        dockMoveHint.setVisibility(View.VISIBLE);
    }

    private void moveDockTile(int delta) {
        int from = dockMoveIndex, to = from + delta;
        if (to < 0 || to >= dockPackages.size()) return;
        java.util.Collections.swap(dockPackages, from, to);
        swapDockViews(dockCards, from, to);
        swapDockViews(dockGlows, from, to);
        if (dockLabels.size() == dockCards.size()) swapDockViews(dockLabels, from, to);
        dockMoveIndex = to;
        paintDockMove();
    }

    /** Swaps two dock views' positions with a short slide, keeping list order equal to screen order. */
    private void swapDockViews(List<View> views, int from, int to) {
        View a = views.get(from), b = views.get(to);
        FrameLayout.LayoutParams pa = (FrameLayout.LayoutParams) a.getLayoutParams();
        FrameLayout.LayoutParams pb = (FrameLayout.LayoutParams) b.getLayoutParams();
        int ax = pa.leftMargin, bx = pb.leftMargin;
        pa.leftMargin = bx;
        pb.leftMargin = ax;
        a.setLayoutParams(pa);
        b.setLayoutParams(pb);
        a.setTranslationX(ax - bx);
        b.setTranslationX(bx - ax);
        a.animate().translationX(0).setDuration(180).start();
        b.animate().translationX(0).setDuration(180).start();
        java.util.Collections.swap(views, from, to);
    }

    private void finishDockMove(boolean save) {
        if (dockMoveIndex < 0) return;
        String moved = dockPackages.get(dockMoveIndex);
        if (save) saveDock(dockPackages);
        else { dockPackages.clear(); dockPackages.addAll(dockMoveBackup); }
        dockMoveIndex = -1;
        dockMoveBackup = null;
        if (dockMoveHint != null) dockMoveHint.setVisibility(View.GONE);
        refreshDock(dockPackages.indexOf(moved));
        Toast.makeText(this, save ? tr("Zapisano kolejność", "Order saved")
                : tr("Kolejność bez zmian", "Order unchanged"), Toast.LENGTH_SHORT).show();
    }

    // ---------- "Your apps": first-run step and full dock editor ----------

    private void showDockEditor(boolean firstRun) {
        List<String> candidates = dockCandidates();
        List<String> picked = new ArrayList<>(dockPackages);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        popupTitle(content, tr("Twoje aplikacje", "Your apps"));
        popupDescription(content, (firstRun ? tr("Ostatni krok. ", "Last step. ") : "")
                + tr("Wybierz do " + DOCK_SIZE + " aplikacji na dole pulpitu. Numer pokazuje miejsce w docku. "
                        + "Kolejność zmienisz później, przytrzymując OK na kafelku.",
                "Choose up to " + DOCK_SIZE + " apps for the bottom of the home screen. The number shows the dock position. "
                        + "Change the order later by holding OK on a tile."));
        TextView status = new TextView(this);
        status.setTextColor(night ? 0xFFFFB8BE : 0xFFA12636);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(17));
        HashMap<String, TextView> badges = new HashMap<>();
        HashMap<String, FrameLayout> tiles = new HashMap<>();
        Runnable paint = () -> {
            for (String pkg : candidates) {
                int position = picked.indexOf(pkg);
                TextView badge = badges.get(pkg);
                badge.setText(String.valueOf(position + 1));
                badge.setVisibility(position >= 0 ? View.VISIBLE : View.INVISIBLE);
                FrameLayout tile = tiles.get(pkg);
                paintEditorTile(tile, tile.hasFocus(), position >= 0);
            }
        };
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setPadding(px(10), px(14), px(10), px(10));
        scroll.addView(rows);
        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < candidates.size(); i++) {
            String pkg = candidates.get(i);
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, px(132));
                rows.addView(row, rowParams);
            }
            FrameLayout cell = new FrameLayout(this);
            cell.setClipChildren(false);
            FrameLayout tile = new FrameLayout(this);
            tile.setFocusable(true);
            tile.setFocusableInTouchMode(true);
            tile.setClickable(true);
            tile.setClipToOutline(true);
            tile.setContentDescription(appLabel(pkg));
            tile.setPadding(px(8), px(8), px(8), px(8));
            Drawable art = null;
            boolean banner = false;
            try {
                art = getPackageManager().getApplicationBanner(pkg);
                banner = art != null;
                if (art == null) art = getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) { }
            if (art != null) {
                ImageView image = new ImageView(this);
                image.setImageDrawable(art);
                image.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
                image.setClipToOutline(true);
                image.setBackground(shape(Color.TRANSPARENT, Color.TRANSPARENT, 14));
                tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
            }
            if (!banner) {
                TextView name = new TextView(this);
                name.setText(appLabel(pkg));
                name.setTextColor(ink);
                name.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(15));
                name.setGravity(Gravity.CENTER);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
                tile.addView(name, nameParams);
            }
            tiles.put(pkg, tile);
            // The tile leaves room in its cell so the number badge never gets clipped.
            FrameLayout.LayoutParams tileParams = new FrameLayout.LayoutParams(-1, px(112));
            tileParams.topMargin = px(14);
            tileParams.rightMargin = px(14);
            cell.addView(tile, tileParams);
            TextView badge = new TextView(this);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(20));
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(shape(0xFF244574, night ? 0xFF142548 : 0xFFF5F2EA, 20, 3));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(px(40), px(40), Gravity.END | Gravity.TOP);
            // Above the focused (raised, scaled) tile, so the position number stays readable.
            badge.setElevation(px(24));
            badge.setTranslationZ(px(24));
            cell.addView(badge, badgeParams);
            badges.put(pkg, badge);
            tile.setOnFocusChangeListener((v, focused) -> paintEditorTile(tile, focused, picked.contains(pkg)));
            tile.setOnClickListener(v -> {
                status.setText("");
                if (picked.remove(pkg)) { paint.run(); return; }
                if (picked.size() >= DOCK_SIZE) {
                    status.setText(tr("Dock mieści " + DOCK_SIZE + " aplikacji. Odznacz jedną, aby wybrać inną.",
                            "The dock holds " + DOCK_SIZE + " apps. Deselect one to choose another."));
                    return;
                }
                picked.add(pkg);
                paint.run();
            });
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, -1, 1);
            if (i % 4 != 0) cellParams.leftMargin = px(16);
            row.addView(cell, cellParams);
            if (first == null) first = tile;
        }
        if (row != null) for (int i = candidates.size() % 4; i > 0 && i < 4; i++)
            row.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1));
        if (candidates.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(tr("Nie znaleziono zainstalowanych aplikacji.", "No installed apps were found."));
            empty.setTextColor(muted);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
            rows.addView(empty);
        }
        int rowsCount = Math.max(1, (candidates.size() + 3) / 4);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, px(Math.min(420, rowsCount * 132 + 24)));
        scrollParams.topMargin = px(8);
        content.addView(scroll, scrollParams);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, px(40));
        content.addView(status, statusParams);
        status.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT);
        TextView cancel = setupButton(firstRun ? tr("Pomiń", "Skip") : tr("Anuluj", "Cancel"));
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(px(160), px(54)));
        TextView save = setupButton(firstRun ? tr("Gotowe", "Done") : tr("Zapisz", "Save"));
        save.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(px(160), px(54));
        saveParams.leftMargin = px(14);
        actions.addView(save, saveParams);
        save.setOnClickListener(v -> {
            if (picked.isEmpty() && !candidates.isEmpty()) {
                status.setText(tr("Wybierz przynajmniej jedną aplikację.", "Choose at least one app."));
                return;
            }
            saveDock(picked);
            dialog.dismiss();
            refreshDock(0);
        });
        content.addView(actions);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(1180), -2);
        paint.run();
        if (first != null) first.requestFocus(); else save.requestFocus();
    }

    private void paintEditorTile(FrameLayout tile, boolean focused, boolean picked) {
        int fill = night ? 0xFF344B72 : Color.WHITE;
        // Night uses a light ring for picked tiles; dark navy would vanish on the dark surface.
        int border = focused ? FOCUS_RING : picked ? (night ? 0xFFDCE8F8 : 0xFF385E8F)
                : (night ? 0x668DA8CF : 0x99C1CCE0);
        tile.setBackground(shape(fill, border, 18, focused || picked ? 3 : 1));
        tile.setScaleX(focused ? 1.04f : 1f);
        tile.setScaleY(focused ? 1.04f : 1f);
        tile.setElevation(focused ? px(10) : 0);
    }

    private void addMainDrawerRow(LinearLayout rows, String[] brands, String[] packages) {
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        rows.addView(row, new LinearLayout.LayoutParams(-1, px(APP_ROW_HEIGHT)));
        List<View> base = new ArrayList<>();
        extraAppRows.add(base);
        for (int i = 0; i < brands.length; i++) {
            String brand = brands[i], pkg = packages[i];
            int column = i;
            FrameLayout wrapper = new FrameLayout(this);
            wrapper.setClipChildren(false);
            View glow = new View(this);
            glow.setBackground(new AppGlowDrawable());
            glow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            glow.setAlpha(0f);
            FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(px(DRAWER_TILE_W + 48), px(DRAWER_TILE_H + 44));
            glowParams.leftMargin = -px(4);
            glowParams.topMargin = -px(11);
            glow.setBackground(new AppGlowDrawable(22, false));
            wrapper.addView(glow, glowParams);
            FrameLayout tile = new FrameLayout(this);
            tile.setBackground(shape(night ? 0xFF263451 : 0xFFE3EAF2,
                    Color.TRANSPARENT, 22));
            tile.setClipToOutline(true);
            tile.setContentDescription(brand);
            tile.setFocusable(true);
            tile.setFocusableInTouchMode(true);
            tile.setClickable(true);
            tile.setOnFocusChangeListener((v, focused) -> {
                setAppTileFocus(tile, glow, focused);
                if (focused) { drawerFocusRow = 0; drawerFocusColumn = column; }
            });
            Drawable artwork = null;
            try {
                artwork = getPackageManager().getApplicationBanner(pkg);
                if (artwork == null) artwork = getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) { }
            if (artwork != null) {
                ImageView image = new ImageView(this);
                image.setImageDrawable(artwork);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setAlpha(0.93f);
                tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
            } else {
                TextView label = new TextView(this);
                label.setText(brand);
                label.setTextColor(Color.WHITE);
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24));
                label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                label.setGravity(Gravity.CENTER);
                tile.addView(label, new FrameLayout.LayoutParams(-1, -1));
            }
            tile.setOnClickListener(v -> launch(pkg));
            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(px(DRAWER_TILE_W), px(DRAWER_TILE_H));
            cardParams.leftMargin = px(20);
            cardParams.topMargin = px(11);
            wrapper.addView(tile, cardParams);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(px(DRAWER_TILE_W + 40), px(APP_ROW_HEIGHT));
            if (i < brands.length - 1) itemParams.rightMargin = 0;
            row.addView(wrapper, itemParams);
            base.add(tile);
        }
    }

    private void populateExtraApps(LinearLayout rows) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(getPackageManager().queryIntentActivities(query, 0));
        HashSet<String> excluded = new HashSet<>();
        excluded.add(getPackageName());
        excluded.addAll(dockPackages);
        excluded.addAll(HIDDEN_APP_PACKAGES);
        List<ResolveInfo> extras = new ArrayList<>();
        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            if (excluded.add(pkg)) extras.add(info);
        }
        String[] priorities = {"tvp vod", "youtube kids", "cda", "google play"};
        List<ResolveInfo> ordered = new ArrayList<>();
        for (String priority : priorities) {
            for (ResolveInfo info : extras) {
                String label = info.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT);
                String pkg = info.activityInfo.packageName.toLowerCase(Locale.ROOT);
                boolean match = priority.equals("tvp vod") ? (label.contains("tvp") && label.contains("vod")) || pkg.contains("tvp.vod")
                        : priority.equals("youtube kids") ? (label.contains("youtube") && label.contains("kids")) || pkg.contains("youtubekids")
                        : priority.equals("cda") ? label.contains("cda") || pkg.contains("cda")
                        : label.contains("sklep play") || label.contains("play store") || pkg.equals("com.android.vending");
                if (match && !ordered.contains(info)) { ordered.add(info); break; }
            }
        }
        for (ResolveInfo info : extras) if (!ordered.contains(info)) ordered.add(info);
        firstExtraApp = null;
        LinearLayout row = null;
        for (int i = 0; i < ordered.size(); i++) {
            ResolveInfo info = ordered.get(i);
            String pkg = info.activityInfo.packageName;
            int drawerRow = 1 + i / DRAWER_COLUMNS, drawerColumn = i % DRAWER_COLUMNS;
            if (i % DRAWER_COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, px(APP_ROW_HEIGHT));
                rows.addView(row, rowParams);
                extraAppRows.add(new ArrayList<>());
            }
            FrameLayout wrapper = new FrameLayout(this);
            wrapper.setClipChildren(false);
            View glow = new View(this);
            glow.setBackground(new AppGlowDrawable());
            glow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            glow.setAlpha(0f);
            FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(px(DRAWER_TILE_W + 48), px(DRAWER_TILE_H + 44));
            glowParams.leftMargin = -px(4);
            glowParams.topMargin = -px(11);
            glow.setBackground(new AppGlowDrawable(22, false));
            wrapper.addView(glow, glowParams);
            FrameLayout tile = new FrameLayout(this);
            tile.setFocusable(true);
            tile.setFocusableInTouchMode(true);
            tile.setClickable(true);
            tile.setClipToOutline(true);
            tile.setContentDescription(info.loadLabel(getPackageManager()));
            tile.setBackground(shape(night ? 0xFF263451 : 0xFFE3EAF2,
                    Color.TRANSPARENT, 22));
            tile.setOnFocusChangeListener((v, focused) -> {
                setAppTileFocus(tile, glow, focused);
                if (focused) { drawerFocusRow = drawerRow; drawerFocusColumn = drawerColumn; }
            });
            ImageView icon = new ImageView(this);
            Drawable banner = null;
            try { banner = getPackageManager().getApplicationBanner(pkg); }
            catch (PackageManager.NameNotFoundException ignored) { }
            icon.setImageDrawable(banner != null ? banner : info.loadIcon(getPackageManager()));
            icon.setScaleType(banner != null ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(-1, -1);
            if (banner == null) icon.setPadding(px(20), px(10), px(20), px(10));
            icon.setAlpha(0.93f);
            tile.addView(icon, iconParams);
            tile.setOnClickListener(v -> {
                closeAppDrawer();
                Intent start = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                start.setClassName(pkg, info.activityInfo.name);
                start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(start);
            });
            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(px(DRAWER_TILE_W), px(DRAWER_TILE_H));
            cardParams.leftMargin = px(20);
            cardParams.topMargin = px(11);
            wrapper.addView(tile, cardParams);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(DRAWER_TILE_W + 40), px(APP_ROW_HEIGHT));
            if (i % DRAWER_COLUMNS != DRAWER_COLUMNS - 1) params.rightMargin = 0;
            row.addView(wrapper, params);
            extraAppRows.get(1 + i / DRAWER_COLUMNS).add(tile);
            if (firstExtraApp == null) firstExtraApp = tile;
        }
        if (firstExtraApp == null) {
            TextView empty = new TextView(this);
            empty.setText(tr("Nie znaleziono dodatkowych aplikacji", "No additional apps found"));
            empty.setTextColor(Color.WHITE);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
            empty.setGravity(Gravity.CENTER_VERTICAL);
            rows.addView(empty, new LinearLayout.LayoutParams(px(500), px(100)));
        }
    }

    private void openAppDrawer() {
        openAppDrawer(1, 0);
    }

    private void openAppDrawer(int focusRow, int focusColumn) {
        if (appDrawerOpen) return;
        if (!drawerRowsLoaded) {
            String[] dockLabels = new String[dockPackages.size()];
            for (int i = 0; i < dockLabels.length; i++) dockLabels[i] = appLabel(dockPackages.get(i));
            addMainDrawerRow(drawerRows, dockLabels, dockPackages.toArray(new String[0]));
            populateExtraApps(drawerRows);
            drawerRowsLoaded = true;
        }
        appDrawerOpen = true;
        int width = Math.max(1, root.getWidth() / 4);
        int height = Math.max(1, root.getHeight() / 4);
        drawerBlurBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas capture = new Canvas(drawerBlurBitmap);
        capture.scale(width / (float) root.getWidth(), height / (float) root.getHeight());
        for (View card : dockCards) card.setVisibility(View.INVISIBLE);
        for (View glow : dockGlows) glow.setVisibility(View.INVISIBLE);
        for (View label : dockLabels) label.setVisibility(View.INVISIBLE);
        dockExpand.setVisibility(View.INVISIBLE);
        root.draw(capture);
        for (View card : dockCards) card.setVisibility(View.VISIBLE);
        for (View glow : dockGlows) glow.setVisibility(View.VISIBLE);
        for (View label : dockLabels) label.setVisibility(View.VISIBLE);
        dockExpand.setVisibility(View.VISIBLE);
        blurBackdrop(drawerBlurBitmap, 2, 1);
        drawerBlurImage.setImageBitmap(drawerBlurBitmap);
        drawerBackdrop.setAlpha(0f);
        drawerBackdrop.setVisibility(View.VISIBLE);
        drawerBackdrop.animate().alpha(1f).setDuration(220).start();
        drawerTitle.setAlpha(0f);
        drawerTitle.setVisibility(View.VISIBLE);
        drawerTitle.animate().alpha(1f).setDuration(260).start();
        int targetRow = Math.min(focusRow, Math.max(0, extraAppRows.size() - 1));
        List<View> row = extraAppRows.isEmpty() ? null : extraAppRows.get(targetRow);
        View target = row == null || row.isEmpty() ? null
                : row.get(Math.min(focusColumn, row.size() - 1));
        drawerFocusRow = targetRow;
        drawerFocusColumn = target == null ? 0 : Math.min(focusColumn, row.size() - 1);
        appDrawer.scrollTo(0, px(Math.max(0, targetRow - 1) * APP_ROW_HEIGHT));
        appDrawer.setTranslationY(px(110));
        appDrawer.setAlpha(0f);
        appDrawer.setVisibility(View.VISIBLE);
        appDrawer.animate().translationY(0).alpha(1f).setDuration(320).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        dockExpand.setVisibility(View.GONE);
        suspendBackgroundFocus(root);
        if (target != null) target.requestFocus();
        if (target != null) handler.postDelayed(() -> {
            if (appDrawerOpen) target.requestFocus();
        }, 340);
    }

    private void suspendBackgroundFocus(View view) {
        if (view == appDrawer) return;
        if (view != root && view.isFocusable()) {
            suspendedFocusables.add(view);
            view.setFocusable(false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                suspendBackgroundFocus(group.getChildAt(i));
        }
    }

    private void closeAppDrawer() {
        if (!appDrawerOpen) return;
        appDrawerOpen = false;
        for (View view : suspendedFocusables) view.setFocusable(true);
        suspendedFocusables.clear();
        drawerBackdrop.animate().alpha(0).setDuration(220).withEndAction(() -> {
            if (!appDrawerOpen) {
                drawerBackdrop.setVisibility(View.GONE);
                drawerBlurImage.setImageDrawable(null);
                if (drawerBlurBitmap != null) {
                    drawerBlurBitmap.recycle();
                    drawerBlurBitmap = null;
                }
            }
        }).start();
        drawerTitle.animate().alpha(0).setDuration(220).withEndAction(() -> {
            if (!appDrawerOpen) drawerTitle.setVisibility(View.GONE);
        }).start();
        appDrawer.animate().translationY(px(110)).alpha(0).setDuration(260).withEndAction(() -> {
            if (!appDrawerOpen) appDrawer.setVisibility(View.GONE);
        }).start();
        dockExpand.setVisibility(View.VISIBLE);
        View restore = lastDockFocus != null ? lastDockFocus : (dockCards.isEmpty() ? null : dockCards.get(0));
        if (restore != null) handler.postDelayed(restore::requestFocus, 40);
    }

    @Override public void onBackPressed() {
        if (appDrawerOpen) closeAppDrawer();
        // The dashboard is Home: Back has nowhere to navigate when no overlay is open.
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && suppressSaverKeyUp) {
            suppressSaverKeyUp = false;
            return true;
        }
        if (dockMoveIndex >= 0) {
            // Move mode owns the remote: arrows swap, OK saves, Back cancels.
            int key = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                resetScreensaverTimer();
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) moveDockTile(-1);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) moveDockTile(1);
                else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                        || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (event.getRepeatCount() == 0) { finishDockMove(true); suppressDockKeyUp = true; }
                } else if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
                    finishDockMove(false);
                    suppressDockKeyUp = true;
                }
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && suppressDockKeyUp) {
            suppressDockKeyUp = false;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (screensaverActive) {
                hideScreensaver();
                suppressSaverKeyUp = true;
                resetScreensaverTimer();
                return true;
            }
            resetScreensaverTimer();
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if (appDrawerOpen && key == KeyEvent.KEYCODE_BACK) {
                closeAppDrawer();
                return true;
            }
            if (appDrawerOpen) {
                if (!extraAppRows.isEmpty() && !extraAppRows.get(
                        Math.min(drawerFocusRow, extraAppRows.size() - 1)).isEmpty()) {
                    int row = Math.min(drawerFocusRow, extraAppRows.size() - 1);
                    int column = Math.min(drawerFocusColumn, extraAppRows.get(row).size() - 1);
                    View focused = extraAppRows.get(row).get(column);
                    if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                        focused.performClick();
                        return true;
                    }
                    if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        int nextColumn = column + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                        if (nextColumn >= 0 && nextColumn < extraAppRows.get(row).size())
                            extraAppRows.get(row).get(nextColumn).requestFocus();
                        return true;
                    }
                    if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int nextRow = row + (key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                        if (nextRow < 0 || nextRow < extraAppRows.size() && extraAppRows.get(nextRow).isEmpty()) {
                            closeAppDrawer();
                        } else if (nextRow < extraAppRows.size()) {
                            List<View> next = extraAppRows.get(nextRow);
                            next.get(Math.min(column, next.size() - 1)).requestFocus();
                            appDrawer.smoothScrollTo(0,
                                    px(Math.max(0, nextRow - 1) * APP_ROW_HEIGHT));
                        }
                        return true;
                    }
                }
            }
            if (!appDrawerOpen && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                boolean dockFocused = dockExpand != null && dockExpand.hasFocus();
                for (View card : dockCards) dockFocused |= card.hasFocus();
                if (dockFocused) { openAppDrawer(); return true; }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (suppressSaverTouch) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) suppressSaverTouch = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (screensaverActive) {
                hideScreensaver();
                suppressSaverTouch = true;
                resetScreensaverTimer();
                return true;
            }
            resetScreensaverTimer();
        }
        return super.dispatchTouchEvent(event);
    }

    private void setAppTileFocus(FrameLayout tile, View glow, boolean focused) {
        glow.animate().alpha(focused ? 1f : 0f).setDuration(170).start();
        tile.setElevation(focused ? px(14) : 0);
        tile.setScaleX(focused ? 1.015f : 1f);
        tile.setScaleY(focused ? 1.015f : 1f);
    }

    private final class AppGlowDrawable extends Drawable {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int radius;
        private final boolean outerOnly;
        private final int outerColor, innerColor;
        AppGlowDrawable() { this(32, false); }
        AppGlowDrawable(int radius, boolean outerOnly) {
            this(radius, outerOnly, 0xBA55BAFF, 0xA57FDBFF);
        }
        AppGlowDrawable(int radius, boolean outerOnly, int outerColor, int innerColor) {
            this.radius = radius;
            this.outerOnly = outerOnly;
            this.outerColor = outerColor;
            this.innerColor = innerColor;
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            RectF card = new RectF(bounds.left + px(24), bounds.top + px(22),
                    bounds.right - px(24), bounds.bottom - px(22));
            if (outerOnly) {
                // Use the app tile's full-strength aura, then mask its centre because
                // these dashboard cards are translucent and would reveal the fill.
                glow.setStyle(Paint.Style.FILL);
                glow.setColor(outerColor);
                glow.setMaskFilter(new android.graphics.BlurMaskFilter(px(26),
                        android.graphics.BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(card, px(radius), px(radius), glow);
                glow.setColor(innerColor);
                glow.setMaskFilter(new android.graphics.BlurMaskFilter(px(11),
                        android.graphics.BlurMaskFilter.Blur.NORMAL));
                canvas.drawRoundRect(card, px(radius), px(radius), glow);
                glow.setMaskFilter(null);
                glow.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                canvas.drawRoundRect(card, px(radius), px(radius), glow);
                glow.setXfermode(null);
                return;
            }
            glow.setStyle(Paint.Style.FILL);
            glow.setColor(outerColor);
            glow.setMaskFilter(new android.graphics.BlurMaskFilter(px(26),
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            canvas.drawRoundRect(card, px(radius), px(radius), glow);
            glow.setColor(innerColor);
            glow.setMaskFilter(new android.graphics.BlurMaskFilter(px(11),
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            canvas.drawRoundRect(card, px(radius), px(radius), glow);
            glow.setMaskFilter(null);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void launch(String pkg) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) startActivity(intent);
        else Toast.makeText(this, tr("Nie znaleziono aplikacji: ", "App not found: ") + pkg,
                Toast.LENGTH_SHORT).show();
    }

    private void closeBackgroundApps() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return;
        new Thread(() -> {
            for (String pkg : CLEANUP_PACKAGES) {
                // Cast discovery and reception must remain available independently of the launcher.
                if ("com.google.android.apps.mediashell".equals(pkg)
                        || "com.google.android.gms".equals(pkg) || getPackageName().equals(pkg)) continue;
                try { manager.killBackgroundProcesses(pkg); }
                catch (SecurityException ignored) { }
            }
            handler.post(() -> {
                if (publicSettingsDialog != null && publicSettingsDialog.isShowing())
                    renderPublicSettings(publicSettingsDialog, false);
                Toast.makeText(this,
                    tr("Poproszono system o zamknięcie aplikacji w tle",
                            "Asked the system to close background apps"), Toast.LENGTH_SHORT).show();
            });
        }, "background-cleanup").start();
    }

    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception e) { Toast.makeText(this, tr("Ustawienia niedostępne", "Settings unavailable"),
                Toast.LENGTH_SHORT).show(); }
    }

    private void showSavedWallpaper(ImageView image) {
        String choice = selectedWallpaper();
        if ("cosmos".equals(choice)) image.setImageResource(R.drawable.cosmos_wallpaper);
        else if ("aurora".equals(choice)) image.setImageResource(R.drawable.aurora_wallpaper);
        else if (choice.startsWith("folder:")) {
            File file = new File(wallpaperFolder(), choice.substring("folder:".length()));
            if (file.getParentFile().equals(wallpaperFolder()) && showWallpaperFile(image, file)) return;
            image.setImageResource(R.drawable.cosmos_wallpaper);
        }
        else if ("custom".equals(choice)) {
            File file = new File(getFilesDir(), "custom-wallpaper.img");
            if (showWallpaperFile(image, file)) return;
            image.setImageResource(R.drawable.cosmos_wallpaper);
        } else if ("sunset".equals(choice)) image.setImageResource(R.drawable.sunset_wallpaper);
        else image.setImageResource(R.drawable.cosmos_wallpaper);
    }

    private String selectedWallpaper() {
        SharedPreferences saved = getPreferences(MODE_PRIVATE);
        // 1.2.0: the dark "cosmos" background of the new design replaces the old default once.
        if (!saved.getBoolean("design2_wallpaper", false)) {
            String old = saved.getString("wallpaper", "sunset");
            SharedPreferences.Editor editor = saved.edit().putBoolean("design2_wallpaper", true);
            if ("sunset".equals(old) || "dussehra".equals(old)) editor.putString("wallpaper", "cosmos");
            editor.apply();
        }
        String choice = saved.getString("wallpaper", "cosmos");
        if ("dussehra".equals(choice)) {
            saved.edit().putString("wallpaper", "cosmos").apply();
            return "cosmos";
        }
        return choice;
    }

    private void resetScreensaverTimer() {
        handler.removeCallbacks(startScreensaver);
        long idle = LauncherTiming.idleMillis(getPreferences(MODE_PRIVATE).getInt("screensaver_minutes", 5));
        if (dashboardVisible && !popupOpen && !screensaverActive && root != null && idle > 0)
            handler.postDelayed(startScreensaver, idle);
    }

    private void showScreensaver() {
        if (screensaverActive || popupOpen || root == null) return;
        handler.removeCallbacks(startScreensaver);
        screensaverChoices.clear();
        String selected = selectedWallpaper();
        if ("custom".equals(selected) && new File(getFilesDir(), "custom-wallpaper.img").isFile())
            screensaverChoices.add(selected);
        else if (selected.startsWith("folder:")) {
            File selectedFile = new File(wallpaperFolder(), selected.substring("folder:".length()));
            if (selectedFile.getParentFile().equals(wallpaperFolder()) && selectedFile.isFile())
                screensaverChoices.add(selected);
        }
        File[] photos = wallpaperFolder().listFiles(file -> file.isFile()
                && file.length() <= 25_000_000
                && file.getName().matches("(?i).+\\.(jpg|jpeg|png|webp)"));
        if (photos != null) {
            Arrays.sort(photos, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File file : photos) {
                String choice = "folder:" + file.getName();
                if (!screensaverChoices.contains(choice)) screensaverChoices.add(choice);
            }
        }
        if (new File(getFilesDir(), "custom-wallpaper.img").isFile()
                && !screensaverChoices.contains("custom")) screensaverChoices.add("custom");
        if (screensaverChoices.isEmpty()) return;
        screensaverIndex = 0;
        screensaverPreviousFocus = getCurrentFocus();
        screensaver = new FrameLayout(this);
        screensaver.setElevation(px(100));
        screensaver.setFocusable(true);
        screensaver.setFocusableInTouchMode(true);
        screensaver.setBackgroundColor(0xFF081126);
        screensaverImage = new ImageView(this);
        screensaverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        screensaver.addView(screensaverImage, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0x33000000);
        screensaver.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        screensaverInfo = new LinearLayout(this);
        screensaverInfo.setOrientation(LinearLayout.VERTICAL);
        screensaverClock = new TextView(this);
        screensaverClock.setTextColor(Color.WHITE);
        screensaverClock.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(104));
        screensaverClock.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        screensaverClock.setLetterSpacing(0.045f);
        screensaverClock.setShadowLayer(px(12), 0, px(3), 0x99000000);
        screensaverClock.setIncludeFontPadding(false);
        screensaverInfo.addView(screensaverClock);
        screensaverDate = new TextView(this);
        screensaverDate.setTextColor(Color.WHITE);
        screensaverDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(22));
        screensaverDate.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        screensaverDate.setLetterSpacing(0.13f);
        screensaverDate.setShadowLayer(px(7), 0, px(2), 0xBB000000);
        screensaverDate.setPadding(px(3), px(8), 0, 0);
        screensaverInfo.addView(screensaverDate);
        screensaver.addView(screensaverInfo);
        root.addView(screensaver, new FrameLayout.LayoutParams(-1, -1));
        screensaverActive = true;
        screensaver.requestFocus();
        showScreensaverSlide();
        handler.removeCallbacks(advanceScreensaver);
        if (screensaverActive) handler.postDelayed(advanceScreensaver, SCREENSAVER_SLIDE_MS);
    }

    private void showScreensaverSlide() {
        if (!screensaverActive || screensaverChoices.isEmpty()) return;
        Bitmap next = null;
        while (!screensaverChoices.isEmpty()) {
            String choice = screensaverChoices.get(screensaverIndex);
            if (choice.startsWith("folder:"))
                next = decodeWallpaperFile(new File(wallpaperFolder(), choice.substring("folder:".length())));
            else if ("custom".equals(choice))
                next = decodeWallpaperFile(new File(getFilesDir(), "custom-wallpaper.img"));
            if (next != null) break;
            screensaverChoices.remove(screensaverIndex);
            if (!screensaverChoices.isEmpty()) screensaverIndex %= screensaverChoices.size();
        }
        if (next == null) {
            hideScreensaver();
            return;
        }
        screensaverImage.animate().cancel();
        screensaverImage.setImageBitmap(next);
        if (screensaverBitmap != null) screensaverBitmap.recycle();
        screensaverBitmap = next;
        screensaverImage.setScaleX(1.02f);
        screensaverImage.setScaleY(1.02f);
        screensaverImage.animate().scaleX(1.09f).scaleY(1.09f)
                .setDuration(SCREENSAVER_SLIDE_MS).start();
        Date now = new Date();
        screensaverClock.setText(new SimpleDateFormat("HH:mm", PL).format(now));
        String formatted = new SimpleDateFormat("EEEE, d MMMM", displayLocale()).format(now);
        screensaverDate.setText(formatted.toUpperCase(PL));
        FrameLayout.LayoutParams info = new FrameLayout.LayoutParams(-2, -2);
        int corner = screensaverIndex % 4;
        info.gravity = (corner % 2 == 0 ? Gravity.LEFT : Gravity.RIGHT)
                | (corner < 2 ? Gravity.TOP : Gravity.BOTTOM);
        info.setMargins(px(70), px(55), px(70), px(65));
        screensaverInfo.setLayoutParams(info);
    }

    private void hideScreensaver() {
        if (!screensaverActive) return;
        screensaverActive = false;
        handler.removeCallbacks(advanceScreensaver);
        if (screensaverImage != null) {
            screensaverImage.animate().cancel();
            screensaverImage.setImageDrawable(null);
        }
        if (screensaverBitmap != null) {
            screensaverBitmap.recycle();
            screensaverBitmap = null;
        }
        if (screensaver != null && screensaver.getParent() instanceof ViewGroup)
            ((ViewGroup) screensaver.getParent()).removeView(screensaver);
        screensaver = null;
        screensaverImage = null;
        screensaverInfo = null;
        if (screensaverPreviousFocus != null && screensaverPreviousFocus.isAttachedToWindow())
            screensaverPreviousFocus.requestFocus();
        screensaverPreviousFocus = null;
    }

    private File wallpaperFolder() {
        File folder = getExternalFilesDir("wallpapers");
        if (folder == null) folder = new File(getFilesDir(), "wallpapers");
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    private boolean showWallpaperFile(ImageView image, File file) {
        Bitmap bitmap = decodeWallpaperFile(file);
        if (bitmap == null) return false;
        image.setImageBitmap(bitmap);
        return true;
    }

    private Bitmap decodeWallpaperFile(File file) {
        try (InputStream boundsStream = new FileInputStream(file);
                 InputStream imageStream = new FileInputStream(file)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(boundsStream, null, options);
                options.inJustDecodeBounds = false;
                options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > 2560
                        || options.outHeight / options.inSampleSize > 1440) options.inSampleSize *= 2;
                 return BitmapFactory.decodeStream(imageStream, null, options);
            } catch (Exception ignored) { }
        return null;
    }

    // ---- Home Assistant lights ----

    private boolean haConfigured() {
        return !haUrl.isEmpty() && !haToken.isEmpty() && !haEntities.isEmpty();
    }

    private void loadHomeAssistant(SharedPreferences saved) {
        haUrl = saved.getString("ha_url", "");
        haToken = saved.getString("ha_token", "");
        haEntities.clear();
        for (String id : saved.getString("ha_entities", "").split(",")) {
            String trimmed = id.trim();
            if (HomeAssistantClient.supported(trimmed) && !haEntities.contains(trimmed)) haEntities.add(trimmed);
        }
    }

    private void renderLightRow() {
        lightTiles.clear();
        float tileW = (D_CONTENT_W - (MAX_LIGHTS - 1) * D_LIGHT_GAP) / MAX_LIGHTS;
        if (!haConfigured()) {
            float w = tileW * 2 + D_LIGHT_GAP;
            lightSetupTile(D_LEFT + (D_CONTENT_W - w) / 2, w);
            return;
        }
        int count = Math.min(MAX_LIGHTS, haEntities.size());
        float total = count * tileW + (count - 1) * D_LIGHT_GAP;
        float firstX = D_LEFT + (D_CONTENT_W - total) / 2;
        for (int i = 0; i < count; i++) lightTile(haEntities.get(i), firstX + i * (tileW + D_LIGHT_GAP), tileW);
    }

    private void lightSetupTile(float x, float w) {
        View aura = dFocusAura(x, D_LIGHTS_Y, w, D_LIGHT_H, 48);
        FrameLayout card = dCard(x, D_LIGHTS_Y, w, D_LIGHT_H, 48);
        View circle = new View(this);
        GradientDrawable circleShape = new GradientDrawable();
        circleShape.setShape(GradientDrawable.OVAL);
        circleShape.setColor(0x12FFFFFF);
        circle.setBackground(circleShape);
        addD(card, circle, 18, 19, 58, 58);
        dIcon(card, R.drawable.ic_ds_bulb_off, inkAlpha(0.8f), 33, 34, 28);
        dText(card, "+  " + tr("Światła z Home Assistanta", "Home Assistant lights"), 22, 700, D_INK,
                92, 18, w - 92 - 26, 32);
        dText(card, tr("Połącz i wybierz do 6 świateł z telefonu", "Connect and pick up to 6 lights on your phone"),
                17, 600, inkAlpha(0.62f), 92, 52, w - 92 - 26, 24);
        card.setContentDescription(tr("Światła z Home Assistanta. Skonfiguruj", "Home Assistant lights. Set up"));
        dFocusable(card, aura, null);
        card.setOnClickListener(v -> showHomeAssistantSetup());
    }

    /** Pill-shaped light switch: bulb in a circle, name and state; amber when on. */
    private void lightTile(String id, float x, float w) {
        View aura = dFocusAura(x, D_LIGHTS_Y, w, D_LIGHT_H, 48);
        FrameLayout card = dCard(x, D_LIGHTS_Y, w, D_LIGHT_H, 48);
        View circle = new View(this);
        addD(card, circle, 18, 19, 58, 58);
        ImageView bulb = dIcon(card, R.drawable.ic_ds_bulb_off, 0, 33, 34, 28);
        TextView name = dText(card, "", 22, 700, D_INK, 92, 18, w - 92 - 22, 32);
        TextView state = dText(card, "", 17, 600, inkAlpha(0.62f), 92, 52, w - 92 - 22, 24);
        LightTile tile = new LightTile(id, card, bulb, name, state, circle);
        lightTiles.put(id, tile);
        dFocusable(card, aura, focused -> tile.focused = focused);
        card.setOnClickListener(v -> toggleLight(tile));
        paintLightTile(tile);
    }

    private void paintAllLights() {
        for (LightTile tile : lightTiles.values()) paintLightTile(tile);
    }

    private void paintLightTile(LightTile tile) {
        HomeAssistantClient.Entity entity = haStates.get(tile.id);
        boolean known = entity != null && haError == null;
        boolean available = known && entity.available();
        boolean on = available && entity.on();
        if (on) {
            GradientDrawable amber = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0x47FFBE64, 0x1FFF9646});
            amber.setCornerRadius(pxD(48));
            amber.setStroke(Math.max(1, pxD(1)), 0x73FFCD82);
            tile.card.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{
                    new BackdropGlass(tile.card, 48, D_GLASS, 0), amber}));
        } else tile.card.setBackground(new BackdropGlass(tile.card, 48, D_GLASS, D_GLASS_BORDER));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(on ? 0x38FFC86E : 0x12FFFFFF);
        tile.iconCircle.setBackground(circle);
        tile.bulb.setImageResource(on ? R.drawable.ic_ds_bulb_on : R.drawable.ic_ds_bulb_off);
        if (on) tile.bulb.clearColorFilter();
        else tile.bulb.setColorFilter(inkAlpha(0.8f));
        tile.state.setTextColor(on ? 0xFFFFD38A : inkAlpha(0.62f));
        String label = entity != null ? entity.name
                : tile.id.substring(tile.id.indexOf('.') + 1).replace('_', ' ');
        String status = haError != null ? haError
                : entity == null ? tr("ładowanie…", "loading…")
                : !available ? tr("niedostępne", "unavailable")
                : on ? tr("włączone", "on") : tr("wyłączone", "off");
        tile.name.setText(label);
        tile.state.setText(status);
        tile.card.setAlpha(known && !available ? 0.55f : 1f);
        tile.card.setContentDescription(label + ", " + status);
    }

    private void toggleLight(LightTile tile) {
        if (!haConfigured()) return;
        HomeAssistantClient.Entity entity = haStates.get(tile.id);
        haGeneration++;
        if (entity != null && entity.available() && haError == null) {
            haStates.put(tile.id, entity.withState(entity.on() ? "off" : "on"));
            paintLightTile(tile);
        }
        final HomeAssistantClient client = new HomeAssistantClient(haUrl, haToken);
        final String id = tile.id;
        new Thread(() -> {
            String error = null;
            try { client.toggle(id); }
            catch (HomeAssistantClient.AuthException e) { error = tr("token odrzucony", "token rejected"); }
            catch (IOException e) { error = tr("brak połączenia z HA", "no connection to HA"); }
            final String failure = error;
            handler.post(() -> {
                if (failure != null) { haError = failure; paintAllLights(); }
                handler.removeCallbacks(pollLights);
                handler.postDelayed(pollLights, 700);
            });
        }, "home-assistant-toggle").start();
    }

    private void showHomeAssistantSetup() {
        String address = PhotoUploadServer.localIpv4();
        if (address == null) {
            showMessageDialog("Home Assistant",
                    tr("Telewizor nie ma adresu w sieci lokalnej. Połącz go i telefon z tą samą siecią.",
                            "The TV has no local network address. Connect it and your phone to the same network."));
            return;
        }
        if (homeAssistantSetupServer != null) homeAssistantSetupServer.close();
        TextView status = new TextView(this);
        final boolean[] changed = {false};
        HomeAssistantSetupServer server;
        try {
            server = new HomeAssistantSetupServer(english, haUrl, haToken, haEntities,
                    (url, token, entities) -> handler.post(() -> {
                        haUrl = url;
                        haToken = token;
                        haEntities.clear();
                        haEntities.addAll(entities);
                        haStates.clear();
                        haError = null;
                        haGeneration++;
                        launches.clear();
                        launchesUpdatedAt = 0;
                        launchesError = null;
                        rocketImageOff = null;
                        rocketImageOn = null;
                        rocketImagesAttemptedAt = 0;
                        getPreferences(MODE_PRIVATE).edit()
                                .putString("ha_url", url)
                                .putString("ha_token", token)
                                .putString("ha_entities", android.text.TextUtils.join(",", entities))
                                .apply();
                        changed[0] = true;
                        status.setText(tr("Zapisano " + entities.size() + " świateł. Zamknij to okno.",
                                "Saved " + entities.size() + " lights. Close this window."));
                    }));
        } catch (IOException e) {
            showMessageDialog("Home Assistant",
                    tr("Nie udało się uruchomić konfiguracji. Spróbuj ponownie za chwilę.",
                            "Could not start the setup page. Try again shortly."));
            return;
        }
        homeAssistantSetupServer = server;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(36), px(28), px(36), px(30));
        content.setBackground(shape(night ? 0xFF142548 : 0xFFF5F2EA,
                night ? 0xFF899BC6 : 0xFFB7C4D5, 26, 1));
        detailLine(content, tr("Światła z Home Assistanta", "Home Assistant lights"), 31, true, ink);
        detailLine(content, tr("Zeskanuj kod telefonem, podaj adres HA i token, a potem wybierz światła.",
                "Scan the code with your phone, enter the HA address and token, then pick the lights."),
                18, false, muted);
        String setupUrl = "http://" + address + ":" + HomeAssistantSetupServer.PORT + "/";
        try {
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(qrBitmap(setupUrl, 350));
            qr.setBackground(shape(Color.WHITE, Color.TRANSPARENT, 16));
            qr.setPadding(px(12), px(12), px(12), px(12));
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(px(238), px(238));
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            qrParams.setMargins(0, px(12), 0, px(12));
            content.addView(qr, qrParams);
        } catch (WriterException e) {
            detailLine(content, tr("Nie udało się przygotować kodu QR.",
                    "Could not generate the QR code."), 17, false, muted);
        }
        detailLine(content, tr("Jeśli skanowanie nie zadziała, wpisz adres:",
                "If scanning fails, enter this address:"), 16, false, muted);
        detailLine(content, setupUrl, 19, false, ink);
        detailLine(content, tr("Kod: ", "Code: ") + server.pin(), 24, true, ink);
        status.setText(haConfigured()
                ? tr("Teraz: " + haEntities.size() + " świateł z " + haUrl, "Now: " + haEntities.size() + " lights from " + haUrl)
                : tr("Home Assistant nie jest jeszcze skonfigurowany.", "Home Assistant is not set up yet."));
        status.setTextColor(muted);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(17));
        status.setMinHeight(px(55));
        content.addView(status);
        TextView close = setupButton(tr("Zamknij", "Close"));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(px(160), px(50));
        closeParams.gravity = Gravity.END;
        content.addView(close, closeParams);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog, () -> {
            server.close();
            if (homeAssistantSetupServer == server) homeAssistantSetupServer = null;
            if (changed[0]) {
                rebuildAfterPopup = true;
                handler.removeCallbacks(pollLights);
                handler.postDelayed(pollLights, 300);
            }
        });
        dialog.getWindow().setLayout(px(720), -2);
        close.requestFocus();
    }

    private void showPhoneUpload() {
        String address = PhotoUploadServer.localIpv4();
        if (address == null) {
            showMessageDialog(tr("Dodawanie zdjęć", "Add photos"),
                    tr("Telewizor nie ma adresu w sieci lokalnej. Połącz go i telefon z tą samą siecią Wi‑Fi.",
                            "The TV has no local network address. Connect it and your phone to the same Wi-Fi network."));
            return;
        }
        if (photoUploadServer != null) photoUploadServer.close();
        TextView status = new TextView(this);
        final boolean[] receivedPhoto = {false};
        PhotoUploadServer server;
        try {
            server = new PhotoUploadServer(wallpaperFolder(), english, filename -> handler.post(() -> {
                receivedPhoto[0] = true;
                status.setText(tr("Zdjęcie dodane. Po zamknięciu zobaczysz je w galerii.",
                        "Photo added. It will appear in the gallery when you close this window."));
            }));
        } catch (Exception e) {
            showMessageDialog(tr("Dodawanie zdjęć", "Add photos"),
                    tr("Nie udało się uruchomić przesyłania. Spróbuj ponownie za chwilę.",
                            "Could not start photo upload. Try again shortly."));
            return;
        }
        photoUploadServer = server;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(36), px(28), px(36), px(30));
        content.setBackground(shape(night ? 0xFF142548 : 0xFFF5F2EA,
                night ? 0xFF899BC6 : 0xFFB7C4D5, 26, 1));
        detailLine(content, tr("Dodaj zdjęcia z telefonu", "Add photos from phone"), 31, true, ink);
        detailLine(content, tr("Telefon i telewizor muszą być w tej samej sieci.",
                "Phone and TV must be on the same network."), 19, false, muted);
        detailLine(content, tr("Zeskanuj kod aparatem telefonu:",
                "Scan the code with your phone camera:"), 18, false, muted);
        String uploadUrl = "http://" + address + ":" + PhotoUploadServer.PORT + "/";
        try {
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(qrBitmap(uploadUrl, 350));
            qr.setBackground(shape(Color.WHITE, Color.TRANSPARENT, 16));
            qr.setPadding(px(12), px(12), px(12), px(12));
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(px(238), px(238));
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            qrParams.setMargins(0, px(12), 0, px(12));
            content.addView(qr, qrParams);
        } catch (WriterException e) {
            detailLine(content, tr("Nie udało się przygotować kodu QR.",
                    "Could not generate the QR code."), 17, false, muted);
        }
        detailLine(content, tr("Na telefonie wpisz kod widoczny poniżej. Jeśli skanowanie nie zadziała, wpisz adres:",
                "Enter the code below on your phone. If scanning fails, enter this address:"), 16, false, muted);
        TextView url = new TextView(this);
        url.setText("http://" + address + ":" + PhotoUploadServer.PORT);
        url.setTextColor(ink);
        url.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        url.setPadding(0, px(5), 0, px(5));
        content.addView(url);
        TextView pin = new TextView(this);
        pin.setText(tr("Kod: ", "Code: ") + server.pin());
        pin.setTextColor(ink);
        pin.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24));
        pin.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pin.setPadding(0, 0, 0, px(12));
        content.addView(pin);
        status.setText(tr("Wybierz zdjęcia na telefonie i naciśnij „Dodaj zdjęcia”.",
                "Select photos on your phone and tap Add photos."));
        status.setTextColor(muted);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(17));
        status.setMinHeight(px(65));
        content.addView(status);
        TextView close = new TextView(this);
        close.setText(tr("Zamknij", "Close"));
        close.setTextColor(ink);
        close.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(night ? 0xFF2D466F : 0xFFE3E9F0,
                night ? 0xFF7899C3 : 0xFFB7C4D5, 13));
        close.setFocusable(true);
        close.setClickable(true);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(px(160), px(48));
        closeParams.gravity = Gravity.END;
        content.addView(close, closeParams);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog, () -> {
            server.close();
            if (photoUploadServer == server) photoUploadServer = null;
            if (receivedPhoto[0] && !isFinishing()) handler.post(this::showWallpaperGallery);
        });
        dialog.getWindow().setLayout(px(700), -2);
        close.requestFocus();
    }

    private Bitmap qrBitmap(String value, int size) throws WriterException {
        java.util.Map<EncodeHintType, Object> hints = new java.util.EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    private void showWallpaperGallery() {
        File[] files = wallpaperFolder().listFiles(file -> file.isFile()
                && file.length() <= 25_000_000
                && file.getName().matches("(?i).+\\.(jpg|jpeg|png|webp)"));
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(34), px(26), px(34), px(29));
        content.setBackground(shape(night ? 0xFF142548 : 0xFFF5F2EA,
                night ? 0xFF899BC6 : 0xFFB7C4D5, 26));
        detailLine(content, tr("Tapety", "Wallpapers"), 31, true, ink);
        detailLine(content, tr("Wybierz zdjęcie, aby ustawić je jako tło pulpitu.",
                "Choose a photo to set it as your background."), 18, false, muted);
        android.widget.HorizontalScrollView gallery = new android.widget.HorizontalScrollView(this);
        gallery.setHorizontalScrollBarEnabled(false);
        gallery.setClipChildren(false);
        gallery.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        File[] deleteTarget = {null};
        String[] deleteKey = {null};
        FrameLayout deleteWrap = new FrameLayout(this);
        deleteWrap.setClipChildren(false);
        deleteWrap.setVisibility(View.GONE);
        View deleteGlow = new View(this);
        deleteGlow.setBackground(new AppGlowDrawable(13, false));
        deleteGlow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        deleteGlow.setAlpha(0f);
        deleteWrap.addView(deleteGlow, new FrameLayout.LayoutParams(px(251), px(92)));
        TextView delete = new TextView(this);
        delete.setId(View.generateViewId());
        delete.setText(tr("Usuń zdjęcie", "Delete photo"));
        delete.setGravity(Gravity.CENTER);
        delete.setTextColor(ink);
        delete.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        delete.setBackground(shape(night ? 0xFF2D466F : 0xFFE3E9F0,
                night ? 0xFF7899C3 : 0xFFB7C4D5, 13));
        delete.setFocusable(true);
        delete.setClickable(true);
        delete.setOnFocusChangeListener((v, focused) -> showTileGlow(deleteGlow, focused));
        delete.setOnClickListener(v -> {
            if (deleteTarget[0] != null) confirmWallpaperDeletion(dialog, deleteKey[0], deleteTarget[0]);
        });
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(px(203), px(48));
        deleteParams.leftMargin = px(24);
        deleteParams.topMargin = px(22);
        deleteWrap.addView(delete, deleteParams);
        String selected = selectedWallpaper();
        addWallpaperThumbnail(row, dialog, "cosmos", R.drawable.cosmos_wallpaper, null, selected,
                deleteWrap, delete, deleteTarget, deleteKey);
        addWallpaperThumbnail(row, dialog, "sunset", R.drawable.sunset_wallpaper, null, selected,
                deleteWrap, delete, deleteTarget, deleteKey);
        addWallpaperThumbnail(row, dialog, "aurora", R.drawable.aurora_wallpaper, null, selected,
                deleteWrap, delete, deleteTarget, deleteKey);
        File legacy = new File(getFilesDir(), "custom-wallpaper.img");
        if (legacy.isFile()) addWallpaperThumbnail(row, dialog, "custom", 0, legacy, selected,
                deleteWrap, delete, deleteTarget, deleteKey);
        for (File file : files)
            addWallpaperThumbnail(row, dialog, "folder:" + file.getName(), 0, file, selected,
                    deleteWrap, delete, deleteTarget, deleteKey);
        gallery.addView(row);
        content.addView(gallery, new LinearLayout.LayoutParams(-1, px(232)));
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, px(8), 0, 0);
        footer.setClipChildren(false);
        footer.setClipToPadding(false);
        FrameLayout addWrap = new FrameLayout(this);
        addWrap.setClipChildren(false);
        View addGlow = new View(this);
        addGlow.setBackground(new AppGlowDrawable(14, false));
        addGlow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        addGlow.setAlpha(0f);
        addWrap.addView(addGlow, new FrameLayout.LayoutParams(px(328), px(97)));
        LinearLayout add = new LinearLayout(this);
        add.setGravity(Gravity.CENTER);
        add.setPadding(px(15), 0, px(15), 0);
        add.setBackground(shape(night ? 0xFF2D466F : 0xFF385E8F,
                night ? 0xFF7899C3 : 0xFF385E8F, 14));
        ImageView phone = new ImageView(this);
        phone.setImageResource(R.drawable.ic_phone_add);
        add.addView(phone, new LinearLayout.LayoutParams(px(29), px(29)));
        TextView addLabel = new TextView(this);
        addLabel.setText(tr("Dodaj z telefonu", "Add from phone"));
        addLabel.setTextColor(Color.WHITE);
        addLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        addLabel.setPadding(px(10), 0, 0, 0);
        add.addView(addLabel);
        add.setFocusable(true);
        add.setFocusableInTouchMode(true);
        add.setClickable(true);
        add.setId(View.generateViewId());
        add.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        add.setOnFocusChangeListener((v, focused) -> {
            showTileGlow(addGlow, focused);
            if (focused) { deleteWrap.setVisibility(View.GONE); deleteTarget[0] = null; }
        });
        add.setOnClickListener(v -> { dialog.dismiss(); handler.post(this::showPhoneUpload); });
        add.setOnKeyListener((v, key, event) -> {
            if ((key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)
                    && event.getAction() == KeyEvent.ACTION_UP) {
                add.performClick();
                return true;
            }
            return false;
        });
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(px(280), px(53));
        addParams.leftMargin = px(24);
        addParams.topMargin = px(22);
        addWrap.addView(add, addParams);
        footer.addView(addWrap, new LinearLayout.LayoutParams(px(328), px(97)));
        View spacer = new View(this);
        footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        footer.addView(deleteWrap, new LinearLayout.LayoutParams(px(251), px(92)));
        FrameLayout closeWrap = new FrameLayout(this);
        closeWrap.setClipChildren(false);
        View closeGlow = new View(this);
        closeGlow.setBackground(new AppGlowDrawable(13, false));
        closeGlow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        closeGlow.setAlpha(0f);
        closeWrap.addView(closeGlow, new FrameLayout.LayoutParams(px(203), px(92)));
        TextView close = new TextView(this);
        close.setText(tr("Zamknij", "Close"));
        close.setGravity(Gravity.CENTER);
        close.setTextColor(ink);
        close.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        close.setBackground(shape(night ? 0xFF2D466F : 0xFFE3E9F0,
                night ? 0xFF7899C3 : 0xFFB7C4D5, 13));
        close.setFocusable(true);
        close.setFocusableInTouchMode(true);
        close.setClickable(true);
        close.setId(View.generateViewId());
        close.setOnFocusChangeListener((v, focused) -> {
            showTileGlow(closeGlow, focused);
            if (focused) { deleteWrap.setVisibility(View.GONE); deleteTarget[0] = null; }
        });
        add.setNextFocusRightId(close.getId());
        delete.setNextFocusLeftId(add.getId());
        delete.setNextFocusRightId(close.getId());
        close.setNextFocusLeftId(add.getId());
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(px(155), px(48));
        closeParams.leftMargin = px(24);
        closeParams.topMargin = px(22);
        closeWrap.addView(close, closeParams);
        footer.addView(closeWrap, new LinearLayout.LayoutParams(px(203), px(92)));
        content.addView(footer);
        dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        showDialogWithBlur(dialog);
        dialog.getWindow().setLayout(px(1080), -2);
        add.requestFocusFromTouch();
    }

    private void addWallpaperThumbnail(LinearLayout row, Dialog dialog, String key,
            int resource, File file, String selected, View deleteWrap, TextView delete,
            File[] deleteTarget, String[] deleteKey) {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);
        View glow = new View(this);
        glow.setBackground(new AppGlowDrawable(18, false));
        glow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glow.setAlpha(0f);
        wrapper.addView(glow, new FrameLayout.LayoutParams(px(308), px(226)));
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(shape(night ? 0xFF263A5E : 0xFFE0E7EF,
                key.equals(selected) ? 0xFF80CAFF : Color.TRANSPARENT, 18,
                key.equals(selected) ? 4 : 1));
        tile.setClipToOutline(true);
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (file == null) preview.setImageResource(resource);
        else preview.setImageBitmap(decodeWallpaperThumbnail(file));
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(-1, -1);
        imageParams.setMargins(px(4), px(4), px(4), px(4));
        tile.addView(preview, imageParams);
        if (key.equals(selected)) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(Color.WHITE);
            check.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24));
            check.setGravity(Gravity.CENTER);
            check.setBackground(shape(0xDD1E426C, Color.TRANSPARENT, 25));
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(px(38), px(38),
                    Gravity.TOP | Gravity.RIGHT);
            checkParams.setMargins(0, px(8), px(8), 0);
            tile.addView(check, checkParams);
        }
        tile.setContentDescription(file == null ? key : file.getName());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnFocusChangeListener((v, focused) -> {
            setAppTileFocus(tile, glow, focused);
            if (focused) {
                deleteTarget[0] = file;
                deleteKey[0] = file == null ? null : key;
                deleteWrap.setVisibility(file == null ? View.GONE : View.VISIBLE);
            }
        });
        if (file != null) tile.setNextFocusDownId(delete.getId());
        tile.setOnClickListener(v -> {
            getPreferences(MODE_PRIVATE).edit().putString("wallpaper", key).apply();
            dialog.dismiss();
            applyVisualSettingsNow();
        });
        FrameLayout.LayoutParams tileParams = new FrameLayout.LayoutParams(px(260), px(182));
        tileParams.leftMargin = px(24);
        tileParams.topMargin = px(22);
        wrapper.addView(tile, tileParams);
        row.addView(wrapper, new LinearLayout.LayoutParams(px(308), px(226)));
    }

    private void confirmWallpaperDeletion(Dialog gallery, String key, File file) {
        if (key == null || file == null) return;
        Dialog confirmation = new Dialog(this);
        confirmation.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(34), px(24), px(34), px(24));
        content.setBackground(shape(night ? 0xFF142548 : 0xFFF5F2EA,
                night ? 0xFF899BC6 : 0xFFB7C4D5, 26, 1));
        detailLine(content, tr("Usunąć zdjęcie?", "Delete this photo?"), 29, true, ink);
        detailLine(content, tr("Zdjęcie zniknie z galerii i wygaszacza.",
                "The photo will be removed from the gallery and screensaver."), 19, false, muted);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        TextView cancel = wallpaperConfirmationAction(actions, tr("Anuluj", "Cancel"), ink, confirmation::dismiss);
        wallpaperConfirmationAction(actions, tr("Usuń", "Delete"), night ? 0xFFFFB8BE : 0xFFA12636, () -> {
            try {
                File folder = wallpaperFolder().getCanonicalFile();
                File target = file.getCanonicalFile();
                File legacy = new File(getFilesDir(), "custom-wallpaper.img").getCanonicalFile();
                if (!(target.getParentFile().equals(folder) || target.equals(legacy))
                        || !target.isFile() || !target.delete())
                    throw new IOException("Nie udało się usunąć zdjęcia");
                if (key.equals(getPreferences(MODE_PRIVATE).getString("wallpaper", "cosmos")))
                    getPreferences(MODE_PRIVATE).edit().putString("wallpaper", "cosmos").apply();
                confirmation.dismiss();
                gallery.dismiss();
                applyVisualSettingsNow();
                handler.post(this::showWallpaperGallery);
            } catch (IOException e) {
                Toast.makeText(this, tr("Nie udało się usunąć zdjęcia", "Could not delete photo"), Toast.LENGTH_LONG).show();
            }
        });
        content.addView(actions);
        confirmation.setContentView(content);
        confirmation.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        confirmation.show();
        confirmation.getWindow().setLayout(px(620), -2);
        cancel.requestFocusFromTouch();
    }

    private TextView wallpaperConfirmationAction(LinearLayout actions, String label,
            int textColor, Runnable click) {
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setClipChildren(false);
        View glow = new View(this);
        glow.setBackground(new AppGlowDrawable(13, false));
        glow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glow.setAlpha(0f);
        wrapper.addView(glow, new FrameLayout.LayoutParams(px(188), px(92)));
        TextView button = new TextView(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(textColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19));
        button.setBackground(shape(night ? 0xFF2D466F : 0xFFE3E9F0,
                night ? 0xFF7899C3 : 0xFFB7C4D5, 13));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        button.setOnFocusChangeListener((v, focused) -> showTileGlow(glow, focused));
        button.setOnClickListener(v -> click.run());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(140), px(48));
        params.leftMargin = px(24);
        params.topMargin = px(22);
        wrapper.addView(button, params);
        actions.addView(wrapper, new LinearLayout.LayoutParams(px(188), px(92)));
        return button;
    }

    private Bitmap decodeWallpaperThumbnail(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > 520
                    || bounds.outHeight / options.inSampleSize > 360) options.inSampleSize *= 2;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception ignored) { return null; }
    }

    private void showSources() {
        TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (manager != null) for (TvInputInfo info : manager.getTvInputList()) {
            if (info.getType() == TvInputInfo.TYPE_HDMI) inputs.add(info);
        }
        inputs.sort((a, b) -> String.valueOf(a.loadLabel(this))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(this))));
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = popupContent();
        content.setClipChildren(false);
        content.setClipToPadding(false);
        GradientDrawable sideSurface = new GradientDrawable();
        sideSurface.setColor(night ? 0xFF142548 : 0xFFF5F2EA);
        sideSurface.setCornerRadii(new float[]{px(26), px(26), 0, 0, 0, 0, px(26), px(26)});
        content.setBackground(sideSurface);
        TextView title = new TextView(this);
        title.setText(tr("Źródła", "Inputs"));
        title.setTextColor(night ? Color.WHITE : 0xFF17284B);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(31));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, px(16), 0, 0);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        // Opt-in demonstration for the local test environment only. Real TV inputs
        // always take precedence, and release builds never show simulated entries.
        boolean demo = inputs.isEmpty()
                && (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && getIntent().getBooleanExtra("demo_hdmi_inputs", false);
        int hdmiCount = demo ? 2 : inputs.size();
        View first = null;
        for (int i = 0; i <= hdmiCount; i++) {
            final int item = i;
            String label;
            if (i == 0) label = tr("Telewizja", "Live TV");
            else if (demo) label = "HDMI " + i;
            else {
                CharSequence inputLabel = inputs.get(i - 1).loadLabel(this);
                label = inputLabel == null || inputLabel.length() == 0 ? "HDMI " + i : inputLabel.toString();
            }
            View tile = sourceTile(label, i == 0, () -> {
                if (demo) {
                    Toast.makeText(this, tr("Podgląd źródła: ", "Input preview: ") + label,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                if (item == 0) { launch("org.droidtv.playtv"); return; }
                try {
                    Intent view = new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(inputs.get(item - 1).getId()));
                    startActivity(view);
                } catch (Exception e) {
                    Toast.makeText(this, tr("Telewizor nie otworzył wejścia HDMI",
                            "The TV could not open the HDMI input"), Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(-1, px(112));
            tileParams.setMargins(0, px(8), 0, px(12));
            rows.addView(tile, tileParams);
            if (first == null) first = ((ViewGroup) tile).getChildAt(1);
        }
        dialog.setContentView(content, new ViewGroup.LayoutParams(-1, -1));
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        dialog.getWindow().getDecorView().setSystemUiVisibility(root.getSystemUiVisibility());
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        showDialogWithBlur(dialog);
        alignSidePanel(dialog);
        if (first != null) first.requestFocus();
    }

    private View sourceTile(String label, boolean television, Runnable action) {
        int normalText = night ? Color.WHITE : 0xFF17284B;
        int focusedText = night ? 0xFF152849 : Color.WHITE;
        int normalFill = night ? 0xFF344B72 : 0xFFFFFFFF;
        int focusedFill = night ? 0xFFF0F6FF : 0xFF244574;
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);
        View glow = new View(this);
        glow.setBackground(new AppGlowDrawable(21, false));
        glow.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        glow.setAlpha(0f);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(-1, -1);
        glowParams.setMargins(-px(24), -px(24), -px(24), -px(24));
        wrapper.addView(glow, glowParams);
        LinearLayout tile = new LinearLayout(this);
        tile.setId(View.generateViewId());
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(px(24), 0, px(24), 0);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setClickable(true);
        tile.setContentDescription(label);
        tile.setBackground(shape(normalFill, night ? 0x668DA8CF : 0x99C1CCE0, 21, 1));
        SourceGlyph glyph = new SourceGlyph(television);
        glyph.setColor(normalText);
        tile.addView(glyph, new LinearLayout.LayoutParams(px(49), px(49)));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(normalText);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(21));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.setMargins(px(22), 0, 0, 0);
        tile.addView(name, nameParams);
        tile.setOnFocusChangeListener((view, focused) -> {
            showTileGlow(glow, focused);
            tile.setBackground(shape(focused ? focusedFill : normalFill,
                    focused ? Color.TRANSPARENT : night ? 0x668DA8CF : 0x99C1CCE0,
                    21, focused ? 0 : 1));
            name.setTextColor(focused ? focusedText : normalText);
            glyph.setColor(focused ? focusedText : normalText);
            tile.setElevation(focused ? px(12) : 0);
        });
        tile.setOnClickListener(view -> action.run());
        wrapper.addView(tile, new FrameLayout.LayoutParams(-1, -1));
        return wrapper;
    }

    private final class SourceGlyph extends View {
        private final boolean television;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SourceGlyph(boolean television) { super(DashboardActivity.this); this.television = television; }
        void setColor(int color) { paint.setColor(color); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 64f, getHeight() / 64f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (television) {
                canvas.drawRoundRect(new RectF(8, 11, 56, 46), 5, 5, paint);
                canvas.drawLine(25, 46, 25, 53, paint);
                canvas.drawLine(39, 46, 39, 53, paint);
                canvas.drawLine(20, 53, 44, 53, paint);
                Path play = new Path();
                play.moveTo(28, 22); play.lineTo(28, 36); play.lineTo(40, 29); play.close();
                canvas.drawPath(play, paint);
            } else {
                Path port = new Path();
                port.moveTo(12, 20); port.lineTo(52, 20); port.lineTo(47, 44);
                port.lineTo(17, 44); port.close();
                canvas.drawPath(port, paint);
                canvas.drawLine(23, 27, 41, 27, paint);
                canvas.drawLine(24, 34, 40, 34, paint);
            }
            canvas.restore();
        }
    }

}
