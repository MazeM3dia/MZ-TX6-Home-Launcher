package com.mazem3dia.mztx6home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The owner's choices: app order, favourites, hidden apps, theme, apps per row, animations, background, button
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
    private static final String PRAYER_ON_HOME = "prayer_on_home";
    private static final String CAPACITY_ON_HOME = "capacity_on_home";
    private static final String WALLPAPER = "wallpaper";
    private static final String BACKGROUND = "background";
    private static final String CUSTOM_NAME = "custom_wallpaper_name";
    private static final String WALLPAPER_DIM = "wallpaper_dim";

    private static final String SETTINGS_APP = "settings_app";
    /** Customize > Settings app: the TV's own Settings (default) or Android's full phone-style one. */
    static final String SETTINGS_TV = "tv";
    static final String SETTINGS_ANDROID = "android";

    private static final String FILE = "home";
    /** migrate() has run in this process with the storage unlocked. */
    private static boolean migrated;

    private final SharedPreferences prefs;

    /**
     * In device-protected storage: Home starts before the box unlocks its storage on a cold boot
     * (directBootAware), and only this storage can be read that early.
     */
    HomePrefs(Context context) {
        migrate(context);
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * Moves what 1.3-test2 and older saved in normal storage (choices, custom wallpaper) to device-protected
     * storage, once, when the storage is unlocked. True when something moved: a HomePrefs made before that holds
     * the old values, so Home starts again.
     */
    static synchronized boolean migrate(Context context) {
        if (migrated) return false;
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return false;
        migrated = true;
        Context device = context.createDeviceProtectedStorageContext();
        boolean moved = false;
        if (new File(context.getDataDir(), "shared_prefs/" + FILE + ".xml").isFile()) {
            moved = device.moveSharedPreferencesFrom(context, FILE);
        }
        return Wallpaper.migrate(context) || moved;
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


    /** SETTINGS_TV or SETTINGS_ANDROID: what the Settings button opens. */
    String settingsApp() { return prefs.getString(SETTINGS_APP, SETTINGS_TV); }

    void setSettingsApp(String app) { prefs.edit().putString(SETTINGS_APP, app).apply(); }

    /** Text on the top buttons; off, they are round icons. */
    boolean buttonLabels() { return prefs.getBoolean(BUTTON_LABELS, true); }

    void setButtonLabels(boolean on) { prefs.edit().putBoolean(BUTTON_LABELS, on).apply(); }

    /** The Waktu Solat add-on's Hijri date and next prayer in the header, when the add-on is installed. */
    boolean prayerOnHome() { return prefs.getBoolean(PRAYER_ON_HOME, true); }

    void setPrayerOnHome(boolean on) { prefs.edit().putBoolean(PRAYER_ON_HOME, on).apply(); }

    /** Free RAM and storage under the header buttons. */
    boolean capacityOnHome() { return prefs.getBoolean(CAPACITY_ON_HOME, true); }

    void setCapacityOnHome(boolean on) { prefs.edit().putBoolean(CAPACITY_ON_HOME, on).apply(); }

    /**
     * Customize > Wallpaper: Wallpaper.NONE (the theme's colours), Wallpaper.APP_COLOUR (the glow in the focused
     * app's colour) or an index into Wallpaper.IMAGES. Before it existed, Colour glow and Wallpaper were two
     * settings: their values decide the first answer.
     */
    int background() {
        if (prefs.contains(BACKGROUND)) return prefs.getInt(BACKGROUND, Wallpaper.APP_COLOUR);
        int picture = prefs.getInt(WALLPAPER, -1);
        if (picture >= 0) return picture;
        return prefs.getBoolean(GLOW, true) ? Wallpaper.APP_COLOUR : Wallpaper.NONE;
    }

    void setBackground(int background) { prefs.edit().putInt(BACKGROUND, background).apply(); }

    /** The file name the custom wallpaper was copied from, shown under "My picture". */
    String customWallpaperName() { return prefs.getString(CUSTOM_NAME, ""); }

    void setCustomWallpaperName(String name) { prefs.edit().putString(CUSTOM_NAME, name).apply(); }

    /** How much darker a wallpaper picture is drawn, in percent: 0 to 80 in steps of 20. */
    int wallpaperDim() { return prefs.getInt(WALLPAPER_DIM, 40); }

    void setWallpaperDim(int percent) { prefs.edit().putInt(WALLPAPER_DIM, percent).apply(); }

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
