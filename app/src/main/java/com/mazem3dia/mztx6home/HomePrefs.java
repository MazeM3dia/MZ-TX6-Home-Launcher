package com.mazem3dia.mztx6home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The owner's choices: app order, favourites, hidden apps, theme, apps per row, animations, glow, button
 * labels and the app to open on start. Packages are stored one per line.
 */
final class HomePrefs {
    private static final String ORDER = "order";
    private static final String DOCK = "dock";
    private static final String HIDDEN = "hidden";
    private static final String PALETTE = "palette";
    private static final String COLUMNS = "columns";
    private static final String ANIMATIONS = "animations";
    private static final String OPEN_ON_START = "open_on_start";
    private static final String OPENED_ON_BOOT = "opened_on_boot";
    private static final String GLOW = "glow";
    private static final String BUTTON_LABELS = "button_labels";
    private static final String DEMO_APPS = "demo_apps";

    private final SharedPreferences prefs;

    HomePrefs(Context context) {
        prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    /** The Apps grid order the owner set with Move; empty means A to Z. */
    List<String> order() { return list(ORDER); }

    void setOrder(List<String> order) { putList(ORDER, order); }

    /** Favourites, in their order on the dock. */
    List<String> dock() { return list(DOCK); }

    void setDock(List<String> dock) { putList(DOCK, dock); }

    Set<String> hidden() { return new HashSet<>(list(HIDDEN)); }

    void setHidden(Set<String> hidden) { putList(HIDDEN, new ArrayList<>(hidden)); }

    int palette() { return prefs.getInt(PALETTE, 0); }

    void setPalette(int index) { prefs.edit().putInt(PALETTE, index).apply(); }

    /** Tiles per row, 4 to 7; {@code fallback} (res/values/integers.xml) until the owner picks one. */
    int columns(int fallback) { return prefs.getInt(COLUMNS, fallback); }

    void setColumns(int columns) { prefs.edit().putInt(COLUMNS, columns).apply(); }

    /** Focus zoom and smooth scrolling; off for a box whose GPU can't keep up. */
    boolean animations() { return prefs.getBoolean(ANIMATIONS, true); }

    void setAnimations(boolean on) { prefs.edit().putBoolean(ANIMATIONS, on).apply(); }

    /** The background glow in the focused app's colour. */
    boolean glow() { return prefs.getBoolean(GLOW, true); }

    void setGlow(boolean on) { prefs.edit().putBoolean(GLOW, on).apply(); }

    /** Text on the top buttons; off, they are round icons. */
    boolean buttonLabels() { return prefs.getBoolean(BUTTON_LABELS, true); }

    void setButtonLabels(boolean on) { prefs.edit().putBoolean(BUTTON_LABELS, on).apply(); }

    /** Debug build only: DemoApps' fake tiles added to the real ones. */
    boolean demoApps() { return prefs.getBoolean(DEMO_APPS, false); }

    void setDemoApps(boolean on) { prefs.edit().putBoolean(DEMO_APPS, on).apply(); }

    /** The app opened once after the box starts; empty for none. */
    String openOnStart() { return prefs.getString(OPEN_ON_START, ""); }

    void setOpenOnStart(String pkg) { prefs.edit().putString(OPEN_ON_START, pkg).apply(); }

    /** The boot that already opened it (MainActivity.bootId), so it opens once per boot. */
    long openedOnBoot() { return prefs.getLong(OPENED_ON_BOOT, Long.MIN_VALUE); }

    void setOpenedOnBoot(long boot) { prefs.edit().putLong(OPENED_ON_BOOT, boot).apply(); }

    private List<String> list(String key) {
        String text = prefs.getString(key, "");
        if (text.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(text.split("\n")));
    }

    private void putList(String key, List<String> packages) {
        prefs.edit().putString(key, String.join("\n", packages)).apply();
    }
}
