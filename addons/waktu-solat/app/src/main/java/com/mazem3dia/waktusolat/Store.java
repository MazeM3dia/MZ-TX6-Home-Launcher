package com.mazem3dia.waktusolat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The zone setting and JAKIM's days, kept on the box. One request fetches DAYS days, so month ends, the night
 * after Isyak and days offline are covered; a new one is made only when fewer than REFRESH_LEFT days remain,
 * the zone changes or today isn't there. Nothing runs in the background: a read starts the refresh when needed.
 */
final class Store {
    static final String AUTO = "AUTO";
    static final int DAYS = 35;
    private static final int REFRESH_LEFT = 7;
    /** After a failed request, the next read waits this long before trying again. */
    private static final long RETRY_MS = 15 * 60_000L;
    private static final int TIMEOUT_MS = 10_000;
    private static final String FILE = "jakim.json";
    private static final String SETTING = "zone";
    private static final String AUTO_ZONE = "auto_zone";
    private static final String FETCHED_ZONE = "fetched_zone";
    private static final String FETCHED_AT = "fetched_at";

    // States of a Snapshot, as the launcher reads them from the provider too.
    static final String OK = "ok";
    static final String LOADING = "loading";
    static final String ERROR = "error";
    static final String NO_ZONE = "nozone";
    static final String LOCATING = "locating";
    static final String NO_LOCATION = "nolocation";

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static List<Jakim.Day> days;
    private static String daysZone;
    private static boolean fetching;
    private static long failedAt;
    private static String lastError;

    private Store() { }

    /** What is known now, for today. */
    static final class Snapshot {
        String state;
        /** "AUTO", a zone code, or "" before the owner chose. */
        String setting;
        /** The zone in use: the setting, or the one Auto found; null if none. */
        String zone;
        Jakim.Day today;
        Jakim.Day tomorrow;
        long updated;
        String error;
    }

    static synchronized Snapshot read(Context context) {
        SharedPreferences prefs = prefs(context);
        Snapshot s = new Snapshot();
        s.setting = prefs.getString(SETTING, "");
        s.zone = zone(prefs);
        s.updated = prefs.getLong(FETCHED_AT, 0);
        s.error = lastError;
        if (s.zone == null) {
            boolean auto = AUTO.equals(s.setting);
            if (auto && mayLocate(context) && !fetching && retryAllowed()) refresh(context);
            s.state = !auto ? NO_ZONE : fetching ? LOCATING : NO_LOCATION;
            return s;
        }
        load(context, prefs, s.zone);
        Calendar now = Calendar.getInstance();
        int index = indexOf(now);
        if (index >= 0) {
            s.today = days.get(index);
            s.tomorrow = index + 1 < days.size() ? days.get(index + 1) : null;
        }
        boolean stale = index < 0 || days.size() - index < REFRESH_LEFT;
        if (stale && !fetching && retryAllowed()) refresh(context);
        s.state = s.today != null ? OK : fetching ? LOADING : ERROR;
        return s;
    }

    static synchronized String setting(Context context) {
        return prefs(context).getString(SETTING, "");
    }

    /** A zone code or AUTO. Forgets the stored days and fetches the new zone's. */
    static synchronized void setSetting(Context context, String setting) {
        if (!AUTO.equals(setting) && Zones.indexOf(setting) < 0) return;
        prefs(context).edit().putString(SETTING, setting).apply();
        days = null;
        daysZone = null;
        failedAt = 0;
        refresh(context);
    }

    /** Auto found a zone from a fresh location (MainActivity asks for one when none is stored). */
    static synchronized void setAutoZone(Context context, String zone) {
        prefs(context).edit().putString(AUTO_ZONE, zone).apply();
        failedAt = 0;
        refresh(context);
    }

    static synchronized void refresh(Context context) {
        if (fetching) return;
        fetching = true;
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            String error = null;
            try {
                SharedPreferences prefs = prefs(app);
                if (AUTO.equals(prefs.getString(SETTING, ""))) {
                    String found = detect(app);
                    if (found != null) prefs.edit().putString(AUTO_ZONE, found).apply();
                }
                String zone = zone(prefs);
                if (zone != null) {
                    String json = fetch(zone);
                    List<Jakim.Day> parsed = Jakim.parse(json);   // only a readable answer replaces the stored one
                    write(app, json);
                    prefs.edit().putString(FETCHED_ZONE, zone).putLong(FETCHED_AT, System.currentTimeMillis()).apply();
                    synchronized (Store.class) {
                        days = parsed;
                        daysZone = zone;
                    }
                }
            } catch (Exception e) {
                error = e.getClass().getSimpleName();
            }
            synchronized (Store.class) {
                fetching = false;
                lastError = error;
                failedAt = error == null ? 0 : System.currentTimeMillis();
            }
            app.getContentResolver().notifyChange(PrayerProvider.URI, null);
        });
    }

    static boolean mayLocate(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The zone from the box's newest known location, or null (no permission, no location, outside Malaysia). */
    static String detect(Context context) {
        if (!mayLocate(context)) return null;
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) return null;
        Location newest = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location l = manager.getLastKnownLocation(provider);
                if (l != null && (newest == null || l.getTime() > newest.getTime())) newest = l;
            }
        } catch (SecurityException e) {
            return null;
        }
        return newest == null ? null : Zones.nearest(newest.getLatitude(), newest.getLongitude());
    }

    private static String zone(SharedPreferences prefs) {
        String setting = prefs.getString(SETTING, "");
        if (AUTO.equals(setting)) return prefs.getString(AUTO_ZONE, null);
        return setting.isEmpty() ? null : setting;
    }

    private static boolean retryAllowed() {
        return System.currentTimeMillis() - failedAt > RETRY_MS;
    }

    private static int indexOf(Calendar day) {
        if (days == null) return -1;
        int y = day.get(Calendar.YEAR), m = day.get(Calendar.MONTH) + 1, d = day.get(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < days.size(); i++)
            if (days.get(i).is(y, m, d)) return i;
        return -1;
    }

    /** The stored days for {@code zone}, read from the file once per process. */
    private static void load(Context context, SharedPreferences prefs, String zone) {
        if (zone.equals(daysZone) && days != null) return;
        days = null;
        daysZone = null;
        if (!zone.equals(prefs.getString(FETCHED_ZONE, null))) return;
        try (InputStream in = new AtomicFile(file(context)).openRead()) {
            days = Jakim.parse(new String(readAll(in), StandardCharsets.UTF_8));
            daysZone = zone;
        } catch (Exception e) {
            days = null;
        }
    }

    private static String fetch(String zone) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(Jakim.URL + zone).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(Jakim.body(Calendar.getInstance(), DAYS).getBytes(StandardCharsets.UTF_8));
            }
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static void write(Context context, String json) throws java.io.IOException {
        AtomicFile file = new AtomicFile(file(context));
        FileOutputStream out = file.startWrite();
        try {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (java.io.IOException e) {
            file.failWrite(out);
            throw e;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("waktu", Context.MODE_PRIVATE);
    }
}
