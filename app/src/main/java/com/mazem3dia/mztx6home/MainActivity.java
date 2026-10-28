package com.mazem3dia.mztx6home;

import android.animation.ArgbEvaluator;
import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen: clock, date (with the Waktu Solat add-on, the Hijri date and the next prayer), network, the
 * Customize and Settings buttons, a Favourites row and a grid of every other app. Hold OK (or press Menu) on a tile
 * for its menu: Move, favourite, Hide, App info. Customize is a side panel (CustomizePanel) whose changes apply
 * at once: look, apps per row, open on start, hidden apps, default home, and the prayer zone.
 */
public final class MainActivity extends Activity {
    private static final float FOCUS_SCALE = 1.08f;
    private static final int FOCUS_MS = 120;
    private static final int GLOW_MS = 400;
    /** Glow opacity at its centre and halfway out, of 255. */
    private static final int GLOW_STRONG = 0x73;
    private static final int GLOW_SOFT = 0x26;
    /** Disabling a batch of apps over ADB sends one broadcast each: wait for the last before reloading. */
    private static final int RELOAD_DELAY_MS = 500;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable reload = this::loadApps;
    private HomePrefs prefs;
    private Palette palette;
    private NetworkStatus network;
    private ScrollView scroll;
    private GridLayout dock;
    private GridLayout grid;
    private View dockBand;
    private View spacer;
    private TextView hero;
    private TextView greeting;
    private View amPm;
    /** The background glow in the focused app's colour; null with Colour glow off. */
    private GradientDrawable glow;
    private ValueAnimator glowFade;
    private int glowColor = GlowColor.NONE;
    private TextView settings;
    private TextView customizeButton;
    private TextView networkPill;
    /** The top buttons' words and padding, to put back when Button labels is turned on again. */
    private CharSequence customizeText;
    private CharSequence settingsText;
    private final int[] buttonPadding = new int[4];
    private int buttonDrawablePadding;
    private TextView empty;
    private CustomizePanel panel;
    private PrayerHeader prayerHeader;
    private Capacity capacity;
    private Wallpaper wallpaper;
    /** The add-on's last answer while Customize is open: the zone setting. */
    private Bundle prayerInfo;
    /** JAKIM's zones from the add-on, asked for once. */
    private String[] zoneCodes;
    private String[] zoneStates;
    private String[] zoneAreas;
    /** The state whose zones the panel lists. */
    private String zoneState;
    private int columns;
    private int gap;
    private int tileWidth;
    /** Favourites sit inside the band's padding, so their tiles are a little narrower. */
    private int dockTileWidth;
    private boolean animations;
    /** The app last opened from a tile, or null after Settings or App info: Home comes back to it. */
    private String lastLaunched;
    /** Home went out of sight (onStop) and hasn't been resumed since: the next Home press is a return. */
    private boolean returning;
    /** Every app on the box, A to Z, from the last load; null after onTrimMemory. */
    private List<AppEntry> installed;
    /** What the Favourites row and the Apps grid show, tile for tile. */
    private final List<AppEntry> dockApps = new ArrayList<>();
    private final List<AppEntry> gridApps = new ArrayList<>();
    /** The tile being moved with the D-pad, or null. */
    private View moving;
    /** OK has been held on a tile: its menu opens when OK is let go. */
    private boolean menuOnRelease;
    private Dialog dialog;
    /** Bumped on every load, so an older load that finishes late never replaces a newer one. */
    private int generation;
    /** onTrimMemory dropped the tiles; the next onStart builds them again. */
    private boolean tilesDropped;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            main.removeCallbacks(reload);
            main.postDelayed(reload, RELOAD_DELAY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new HomePrefs(this);
        palette = new Palette(getResources(), prefs.palette());
        getWindow().setBackgroundDrawable(palette.window());
        setContentView(R.layout.activity_main);
        scroll = findViewById(R.id.scroll);
        dock = findViewById(R.id.dock);
        grid = findViewById(R.id.grid);
        dockBand = findViewById(R.id.dock_band);
        spacer = findViewById(R.id.spacer);
        hero = findViewById(R.id.hero);
        greeting = findViewById(R.id.greeting);
        amPm = findViewById(R.id.am_pm);
        settings = findViewById(R.id.settings);
        empty = findViewById(R.id.empty);
        customizeButton = findViewById(R.id.customize);
        networkPill = findViewById(R.id.network);
        network = new NetworkStatus(this, networkPill, !prefs.buttonLabels());
        // Tiles fill the row at any density: the width comes from the screen, not a fixed size.
        gap = px(R.dimen.tile_gap);
        computeSizes();

        customizeText = customizeButton.getText();
        settingsText = settings.getText();
        buttonPadding[0] = customizeButton.getPaddingStart();
        buttonPadding[1] = customizeButton.getPaddingTop();
        buttonPadding[2] = customizeButton.getPaddingEnd();
        buttonPadding[3] = customizeButton.getPaddingBottom();
        buttonDrawablePadding = customizeButton.getCompoundDrawablePadding();
        customizeButton.setBackground(palette.button());
        settings.setBackground(palette.button());
        networkPill.setBackground(palette.pill());
        dockBand.setBackground(palette.band());
        customizeButton.setOnClickListener(v -> openCustomize());
        settings.setOnClickListener(v -> openSettings());
        if (!prefs.buttonLabels()) iconOnly(customizeButton, settings, networkPill);
        setAnimations(prefs.animations());
        if (prefs.background() == Wallpaper.APP_COLOUR) setUpGlow();
        panel = new CustomizePanel(this, findViewById(android.R.id.content), findViewById(R.id.home));
        panel.setPalette(palette, animations);
        prayerHeader = new PrayerHeader(this, loader, findViewById(R.id.hijri), findViewById(R.id.date),
                findViewById(R.id.next_prayer));
        prayerHeader.setEnabled(prefs.prayerOnHome() && Prayers.installed(this));
        wallpaper = new Wallpaper(findViewById(R.id.wallpaper), loader, main);
        wallpaper.show(prefs.background(), prefs.wallpaperDim());
        capacity = new Capacity(this, findViewById(R.id.capacity));
        capacity.setEnabled(prefs.capacityOnHome());
        findViewById(R.id.content).getViewTreeObserver().addOnGlobalLayoutListener(this::fitSpacer);
        // The greeting, the prayer countdown and the capacity line follow the clock's own minute tick: no
        // timer of their own.
        ((TextView) findViewById(R.id.clock)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                updateGreeting();
                prayerHeader.tick();
                capacity.update();
            }
        });

        // Installs, removals, and apps disabled or enabled over ADB all change the grid.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packagesChanged, filter);
        loadApps();
        openOnStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        network.start();
        prayerHeader.start();
        capacity.update();
        amPm.setVisibility(DateFormat.is24HourFormat(this) ? View.GONE : View.VISIBLE);
        updateGreeting();
        wallpaper.show(prefs.background(), prefs.wallpaperDim());   // again after onTrimMemory, else nothing
        if (tilesDropped) {
            tilesDropped = false;
            loadApps();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from an app lands here without onNewIntent: focus is still on its tile, nothing to do.
        returning = false;
    }

    @Override
    protected void onStop() {
        endMove();
        network.stop();
        prayerHeader.stop();
        returning = true;
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Home button. Coming back from an app (onNewIntent comes before onResume), focus its tile; pressed
        // on Home itself, back to the top and the first app. Either way any menu closes and a move finishes.
        endMove();
        dismissDialog();
        panel.close();
        if (returning) {
            View tile = lastLaunched != null ? findTile(lastLaunched) : null;
            if (tile != null) tile.requestFocus();
            return;
        }
        scroll.scrollTo(0, 0);
        focusFirstTile();
    }

    /** Home is the bottom of the stack: Back has nowhere to go. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // MODERATE and above arrive only while Home is out of sight and the box is short of memory:
        // give back the wallpaper, icons and banners, and build them again when Home comes back.
        if (level >= TRIM_MEMORY_MODERATE) wallpaper.release();
        if (level >= TRIM_MEMORY_MODERATE && installed != null) {
            generation++;
            installed = null;
            dockApps.clear();
            gridApps.clear();
            dock.removeAllViews();
            grid.removeAllViews();
            tilesDropped = true;
        }
    }

    @Override
    protected void onDestroy() {
        dismissDialog();
        panel.close();
        unregisterReceiver(packagesChanged);
        main.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        // The Customize panel takes Back; its rows take the rest. Tiles behind it get nothing.
        if (panel.isOpen()) return panel.dispatch(event) || super.dispatchKeyEvent(event);
        if (moving != null && !isVolumeKey(code)) {
            // Move mode takes every key: arrows move the tile, OK or Back puts it down.
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) step(-1, 0);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) step(1, 0);
                else if (code == KeyEvent.KEYCODE_DPAD_UP) step(0, -1);
                else if (code == KeyEvent.KEYCODE_DPAD_DOWN) step(0, 1);
            } else if (event.getAction() == KeyEvent.ACTION_UP && (isOkKey(code)
                    || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
                endMove();
            }
            return true;
        }
        // The tile menu: Menu, or OK held for half a second (when it starts repeating) and let go. The menu
        // opens on the release: a dialog shown while a key is still down never gets key focus on Android 9.
        View focused = getCurrentFocus();
        boolean onTile = focused != null && focused.getTag() instanceof AppEntry;
        if (isOkKey(code) && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) {
            if (event.getRepeatCount() == 1 && onTile) menuOnRelease = true;
            if (menuOnRelease) return true;
        }
        if (isOkKey(code) && event.getAction() == KeyEvent.ACTION_UP && menuOnRelease) {
            menuOnRelease = false;
            if (onTile) {
                focused.setPressed(false);   // the tile never sees this release, so it would not open the app
                showTileMenu(focused);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP && onTile) {
            showTileMenu(focused);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ----- Loading and laying out the tiles -----

    private void loadApps() {
        int load = ++generation;
        Context app = getApplicationContext();
        loader.execute(() -> {
            List<AppEntry> apps = AppLoader.load(app);
            if (DemoApps.AVAILABLE && prefs.demoApps()) {
                apps.addAll(DemoApps.make(app));
                AppLoader.sort(apps);
            }
            main.post(() -> {
                if (load != generation || isDestroyed()) return;
                installed = apps;
                arrange();
                // The Waktu Solat add-on may have come or gone with this change.
                prayerHeader.setEnabled(prefs.prayerOnHome() && Prayers.installed(this));
            });
        });
    }

    /** Splits the installed apps into Favourites and Apps by the owner's choices, and shows them. */
    private void arrange() {
        // Keep focus on the same app, or failing that the same place; leave it alone on the top buttons.
        // With no tiles yet (first start, or after onTrimMemory) Android has put focus on a button itself,
        // so the first app takes it.
        View focused = getCurrentFocus();
        boolean onTile = focused != null && focused.getTag() instanceof AppEntry;
        String keep = onTile ? ((AppEntry) focused.getTag()).packageName() : null;
        GridLayout keepGrid = onTile ? (GridLayout) focused.getParent() : null;
        int keepIndex = onTile ? keepGrid.indexOfChild(focused) : 0;
        boolean focusTiles = dock.getChildCount() + grid.getChildCount() == 0 || focused == null || onTile;

        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        for (AppEntry app : installed) byPackage.put(app.packageName(), app);
        Set<String> hidden = prefs.hidden();
        dockApps.clear();
        List<String> docked = new ArrayList<>();
        for (String pkg : prefs.dock()) {
            AppEntry app = byPackage.get(pkg);
            if (app == null || hidden.contains(pkg) || dockApps.size() == columns) continue;
            dockApps.add(app);
            docked.add(pkg);
        }
        List<String> rest = new ArrayList<>();
        for (String pkg : byPackage.keySet())
            if (!hidden.contains(pkg) && !docked.contains(pkg)) rest.add(pkg);
        gridApps.clear();
        for (String pkg : Arrangement.order(rest, prefs.order())) gridApps.add(byPackage.get(pkg));

        fill(dock, dockApps);
        fill(grid, gridApps);
        int dockVisibility = dockApps.isEmpty() ? View.GONE : View.VISIBLE;
        dockBand.setVisibility(dockVisibility);
        empty.setVisibility(dockApps.isEmpty() && gridApps.isEmpty() ? View.VISIBLE : View.GONE);

        if (!focusTiles) return;
        // After onTrimMemory nothing was focused: the app last opened, as if the tiles had never gone.
        View restore = findTile(keep != null ? keep : lastLaunched);
        if (restore == null && keepGrid != null && keepGrid.getChildCount() > 0)
            restore = keepGrid.getChildAt(Math.min(keepIndex, keepGrid.getChildCount() - 1));
        if (restore != null) restore.requestFocus();
        else focusFirstTile();
    }

    private void fill(GridLayout target, List<AppEntry> apps) {
        target.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        boolean inDock = target == dock;
        int width = inDock ? dockTileWidth : tileWidth;
        for (int i = 0; i < apps.size(); i++) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width;
            params.setMarginEnd(i % columns == columns - 1 ? 0 : gap);
            params.bottomMargin = inDock ? 0 : gap;   // favourites are one row, the band pads it
            View tile = newTile(inflater, target, width);
            bind(tile, apps.get(i));
            target.addView(tile, params);
        }
    }

    private View newTile(LayoutInflater inflater, GridLayout parent, int width) {
        View tile = inflater.inflate(R.layout.tile, parent, false);
        View card = tile.findViewById(R.id.card);
        card.getLayoutParams().height = width * 9 / 16;
        // Names under the focused tile only; in the favourites band never, the hero line names it.
        View label = tile.findViewById(R.id.label);
        label.setVisibility(parent == dock ? View.GONE : View.INVISIBLE);
        card.setBackground(palette.card());
        card.setForeground(palette.ring());
        card.setClipToOutline(true);   // rounds a banner's corners to the card's
        // The tag is the app the tile shows now: moving a tile changes it.
        tile.setOnClickListener(v -> open((AppEntry) v.getTag()));
        // No long-click listener: holding OK is caught in dispatchKeyEvent, so the menu can open on release.
        tile.setLongClickable(false);
        // translationZ draws the grown tile above its neighbours; no outline means no shadow to render.
        tile.setOutlineProvider(null);
        tile.setOnFocusChangeListener((v, hasFocus) -> {
            if (label.getVisibility() != View.GONE) label.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            if (hasFocus) {
                AppEntry app = (AppEntry) v.getTag();
                hero.setText(app.label);
                showGlow(app.glow);
            }
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            v.setTranslationZ(hasFocus ? 1f : 0f);
            if (animations) {
                v.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_MS);
            } else {
                v.animate().cancel();
                v.setScaleX(scale);
                v.setScaleY(scale);
            }
        });
        return tile;
    }

    private static void bind(View tile, AppEntry app) {
        boolean hasBanner = app.banner != null;
        ImageView banner = tile.findViewById(R.id.banner);
        ImageView icon = tile.findViewById(R.id.icon);
        banner.setImageDrawable(hasBanner ? app.banner : null);
        banner.setVisibility(hasBanner ? View.VISIBLE : View.GONE);
        icon.setImageDrawable(hasBanner ? null : app.icon);
        icon.setVisibility(hasBanner ? View.GONE : View.VISIBLE);
        ((TextView) tile.findViewById(R.id.label)).setText(app.label);
        tile.setTag(app);
        tile.setContentDescription(app.label);
    }

    /**
     * Sizes the breathing room above the hero line so the favourites band (or, with no favourites, the first row
     * of apps) ends focus_margin_bottom above the screen's bottom edge on the first screen. Runs after every
     * layout; it changes the spacer only when the answer changes, so the second pass stops there.
     */
    private void fitSpacer() {
        boolean band = dockBand.getVisibility() == View.VISIBLE;
        if (scroll.getHeight() == 0 || (!band && grid.getChildCount() == 0)) return;
        int anchorBottom = band ? dockBand.getBottom() : grid.getTop() + grid.getChildAt(0).getBottom();
        int belowSpacer = anchorBottom - spacer.getHeight();
        int room = scroll.getHeight() - scroll.getPaddingTop() - px(R.dimen.focus_margin_bottom) - belowSpacer;
        int height = Math.max(0, room);
        if (Math.abs(height - spacer.getLayoutParams().height) <= 1) return;
        spacer.getLayoutParams().height = height;
        spacer.requestLayout();
    }

    private View findTile(String pkg) {
        for (GridLayout target : new GridLayout[] {dock, grid})
            for (int i = 0; i < target.getChildCount(); i++)
                if (((AppEntry) target.getChildAt(i).getTag()).packageName().equals(pkg))
                    return target.getChildAt(i);
        return null;
    }

    private void focusFirstTile() {
        if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
        else if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
        else settings.requestFocus();
    }

    // ----- Tile menu: move, favourite, hide, app info -----

    @SuppressLint("InflateParams") // a dialog's content has no parent to inflate into
    private void showTileMenu(View tile) {
        AppEntry app = (AppEntry) tile.getTag();
        boolean inDock = tile.getParent() == dock;
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout card = (LinearLayout) inflater.inflate(R.layout.tile_menu, null);
        card.setBackground(palette.menu());
        ((TextView) card.findViewById(R.id.menu_title)).setText(app.label);
        // Still a platform Dialog underneath: its window takes key focus reliably on Android 9.
        Dialog menu = new Dialog(this, R.style.Dialog);
        menuRow(menu, card, inflater, getString(R.string.move), () -> startMove(tile));
        menuRow(menu, card, inflater, getString(inDock ? R.string.remove_favourite : R.string.add_favourite),
                () -> setFavourite(app, !inDock));
        menuRow(menu, card, inflater, getString(R.string.hide), () -> hide(app));
        menuRow(menu, card, inflater, getString(R.string.app_info), () -> openAppInfo(app));
        // Inflated without a parent, the card loses its XML width: give it back here.
        menu.setContentView(card, new ViewGroup.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.menu_width), ViewGroup.LayoutParams.WRAP_CONTENT));
        dismissDialog();
        dialog = menu;
        menu.show();
        card.getChildAt(1).requestFocus();
    }

    /** One tile-menu row, built like a Customize row: OK closes the menu and runs the action. */
    private void menuRow(Dialog menu, LinearLayout card, LayoutInflater inflater, String label, Runnable action) {
        View row = inflater.inflate(R.layout.panel_row, card, false);
        ((TextView) row.findViewById(R.id.row_label)).setText(label);
        row.findViewById(R.id.row_value).setVisibility(View.GONE);
        row.setBackground(palette.row());
        row.setOnClickListener(v -> {
            menu.dismiss();
            action.run();
        });
        card.addView(row);
    }

    private void startMove(View tile) {
        moving = tile;
        moving.setActivated(true);   // amber ring
        moving.requestFocus();
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_SHORT).show();
    }

    /** One D-pad step in move mode: the tile swaps places along its row or column, within its own section. */
    private void step(int dx, int dy) {
        GridLayout target = (GridLayout) moving.getParent();
        List<AppEntry> apps = target == dock ? dockApps : gridApps;
        int from = target.indexOfChild(moving);
        int to = Arrangement.moveTarget(from, apps.size(), columns, dx, dy);
        if (to == from) return;
        // The views stay where they are; the apps they show shift by one, and focus follows the moved app.
        Arrangement.move(apps, from, to);
        for (int i = Math.min(from, to); i <= Math.max(from, to); i++) bind(target.getChildAt(i), apps.get(i));
        moving.setActivated(false);
        moving = target.getChildAt(to);
        moving.setActivated(true);
        moving.requestFocus();
    }

    private void endMove() {
        if (moving == null) return;
        boolean inDock = moving.getParent() == dock;
        moving.setActivated(false);
        moving = null;
        if (inDock) prefs.setDock(packages(dockApps));
        else prefs.setOrder(packages(gridApps));
    }

    private void setFavourite(AppEntry app, boolean favourite) {
        List<String> docked = packages(dockApps);
        if (favourite) {
            if (docked.size() >= columns) {
                Toast.makeText(this, getString(R.string.favourites_full, columns), Toast.LENGTH_SHORT).show();
                return;
            }
            docked.add(app.packageName());
        } else {
            docked.remove(app.packageName());
        }
        prefs.setDock(docked);
        arrange();
    }

    private void unhide(AppEntry app) {
        Set<String> hidden = prefs.hidden();
        hidden.remove(app.packageName());
        prefs.setHidden(hidden);
        arrange();
    }

    private void hide(AppEntry app) {
        Set<String> hidden = prefs.hidden();
        hidden.add(app.packageName());
        prefs.setHidden(hidden);
        List<String> docked = packages(dockApps);
        if (docked.remove(app.packageName())) prefs.setDock(docked);
        arrange();
    }

    // ----- Customize panel -----

    /** Opens the panel; with the Waktu Solat add-on, after asking it for the zone (and the zone list, once). */
    private void openCustomize() {
        if (installed == null || panel.isOpen()) return;   // still loading
        if (!Prayers.installed(this)) {
            prayerInfo = null;
            panel.open(mainPage);
            return;
        }
        Context app = getApplicationContext();
        boolean needZones = zoneCodes == null;
        loader.execute(() -> {
            Bundle today = Prayers.call(app, "today", null);
            Bundle zones = needZones ? Prayers.call(app, "zones", null) : null;
            main.post(() -> {
                if (isDestroyed() || panel.isOpen()) return;
                prayerInfo = today;
                if (zones != null) {
                    zoneCodes = zones.getStringArray("codes");
                    zoneStates = zones.getStringArray("states");
                    zoneAreas = zones.getStringArray("areas");
                }
                panel.open(mainPage);
            });
        });
    }

    private final CustomizePanel.Page mainPage = new CustomizePanel.Page() {
        @Override
        public String title() {
            return getString(R.string.customize);
        }

        @Override
        public void build(CustomizePanel p) {
            String[] themes = getResources().getStringArray(R.array.palette_names);
            p.section(getString(R.string.section_look));
            p.choice(getString(R.string.theme), () -> themes[prefs.palette()], step -> {
                prefs.setPalette(Math.floorMod(prefs.palette() + step, Palette.count()));
                applyPalette();
            });
            p.link(getString(R.string.wallpaper), backgroundName(prefs.background()),
                    () -> p.push(wallpaperPage));
            p.choice(getString(R.string.apps_per_row), () -> String.valueOf(columns), step -> {
                prefs.setColumns(4 + Math.floorMod(columns - 4 + step, 4));   // 4 to 7
                applyColumns();
            });
            p.choice(getString(R.string.button_labels), () -> onOff(prefs.buttonLabels()), step -> {
                prefs.setButtonLabels(!prefs.buttonLabels());
                applyButtonLabels(prefs.buttonLabels());
            });
            p.choice(getString(R.string.animations), () -> onOff(animations), step -> {
                prefs.setAnimations(!animations);
                setAnimations(!animations);
            });

            p.section(getString(R.string.section_home));
            p.choice(getString(R.string.capacity_on_home), () -> onOff(prefs.capacityOnHome()), step -> {
                prefs.setCapacityOnHome(!prefs.capacityOnHome());
                capacity.setEnabled(prefs.capacityOnHome());
            });
            AppEntry onStart = findInstalled(prefs.openOnStart());
            p.link(getString(R.string.open_on_start), onStart != null ? onStart.label : getString(R.string.off),
                    () -> p.push(openOnStartPage));
            int hiddenCount = hiddenApps().size();
            p.link(getString(R.string.hidden_apps), hiddenCount > 0 ? String.valueOf(hiddenCount) : "",
                    () -> p.push(hiddenPage));
            p.action(getString(R.string.reset_order), MainActivity.this::resetOrder);
            p.action(getString(R.string.default_home), () -> {
                p.close();
                chooseDefaultHome();
            });

            List<String[]> addOns = AddOns.installed(MainActivity.this);
            if (!addOns.isEmpty()) {
                p.section(getString(R.string.section_addons));
                for (String[] addOn : addOns) {
                    p.action(addOn[1], () -> {
                        p.close();
                        openAddOn(addOn[0], addOn[1]);
                    });
                }
            }

            if (Prayers.installed(MainActivity.this)) {
                p.section(getString(R.string.section_prayer));
                p.choice(getString(R.string.prayer_on_home),
                        () -> getString(prefs.prayerOnHome() ? R.string.yes : R.string.no), step -> {
                            prefs.setPrayerOnHome(!prefs.prayerOnHome());
                            prayerHeader.setEnabled(prefs.prayerOnHome());
                        });
                if (zoneCodes != null) p.link(getString(R.string.zone), zoneValue(), () -> p.push(zonePage));
                p.action(getString(R.string.open_prayer), () -> {
                    p.close();
                    openPrayerTimes();
                });
            }
            if (DemoApps.AVAILABLE) {
                p.section(getString(R.string.section_debug));
                p.choice(DemoApps.label(), () -> onOff(prefs.demoApps()), step -> {
                    prefs.setDemoApps(!prefs.demoApps());
                    loadApps();
                });
            }
        }
    };

    /**
     * Customize > Wallpaper: one background, ✓ on the chosen one. None and App colour first, then the pictures
     * and, for a picture, how dim it is drawn. Room for the owner's own pictures later, after Pictures.
     */
    private final CustomizePanel.Page wallpaperPage = new CustomizePanel.Page() {
        @Override
        public String title() {
            return getString(R.string.wallpaper);
        }

        @Override
        public void build(CustomizePanel p) {
            int current = prefs.background();
            p.option(getString(R.string.bg_none), getString(R.string.bg_none_detail), current == Wallpaper.NONE,
                    () -> pickBackground(p, Wallpaper.NONE));
            p.option(getString(R.string.bg_app_colour), getString(R.string.bg_app_colour_detail),
                    current == Wallpaper.APP_COLOUR, () -> pickBackground(p, Wallpaper.APP_COLOUR));
            p.section(getString(R.string.section_pictures));
            String[] pictures = getResources().getStringArray(R.array.wallpaper_pictures);
            for (int i = 0; i < pictures.length; i++) {
                int index = i;
                p.option(pictures[i], null, current == i, () -> pickBackground(p, index));
            }
            if (current >= 0) {
                p.section(getString(R.string.section_picture));
                p.choice(getString(R.string.wallpaper_dim), () -> prefs.wallpaperDim() + "%", step -> {
                    prefs.setWallpaperDim(Math.floorMod(prefs.wallpaperDim() + 20 * step, 100));   // 0 to 80
                    wallpaper.setDim(prefs.wallpaperDim());
                });
            }
        }
    };

    /** Applies a background at once; the page stays open with its ✓ moved (and Dim shown for a picture). */
    private void pickBackground(CustomizePanel p, int background) {
        prefs.setBackground(background);
        applyGlow(background == Wallpaper.APP_COLOUR);
        wallpaper.show(background, prefs.wallpaperDim());
        p.refresh();
    }

    private String backgroundName(int background) {
        if (background == Wallpaper.NONE) return getString(R.string.bg_none);
        if (background == Wallpaper.APP_COLOUR) return getString(R.string.bg_app_colour);
        return getResources().getStringArray(R.array.wallpaper_pictures)[background];
    }

    private final CustomizePanel.Page openOnStartPage = new CustomizePanel.Page() {
        @Override
        public String title() {
            return getString(R.string.open_on_start);
        }

        @Override
        public void build(CustomizePanel p) {
            String current = prefs.openOnStart();
            p.option(getString(R.string.off), null, current.isEmpty(), () -> pickOpenOnStart(""));
            for (AppEntry app : installed)
                p.option(app.label, null, app.packageName().equals(current),
                        () -> pickOpenOnStart(app.packageName()));
        }
    };

    private void pickOpenOnStart(String pkg) {
        prefs.setOpenOnStart(pkg);
        prefs.setOpenedOnBoot(bootId());   // this boot counts as done: the app opens from the next start on
        panel.back();
    }

    /** Every app, ✓ for hidden: OK hides or shows it at once. */
    private final CustomizePanel.Page hiddenPage = new CustomizePanel.Page() {
        @Override
        public String title() {
            return getString(R.string.hidden_apps);
        }

        @Override
        public void build(CustomizePanel p) {
            Set<String> hidden = prefs.hidden();
            for (AppEntry app : installed)
                p.option(app.label, null, hidden.contains(app.packageName()), () -> {
                    if (prefs.hidden().contains(app.packageName())) unhide(app);
                    else hide(app);
                    p.refresh();
                });
        }
    };

    /** Auto, then the states; a state opens its zones. */
    private final CustomizePanel.Page zonePage = new CustomizePanel.Page() {
        @Override
        public String title() {
            return getString(R.string.zone);
        }

        @Override
        public void build(CustomizePanel p) {
            String setting = prayerSetting();
            boolean manual = !setting.isEmpty() && !Prayers.AUTO.equals(setting);
            String currentState = manual ? stateOf(setting) : "";
            p.option(getString(R.string.zone_auto), getString(R.string.zone_auto_detail),
                    Prayers.AUTO.equals(setting), () -> setZone(Prayers.AUTO));
            p.section(getString(R.string.choose_state));
            String previous = null;
            for (int i = 0; i < zoneCodes.length; i++) {
                String state = zoneStates[i];
                if (state.equals(previous)) continue;
                previous = state;
                p.link(state, state.equals(currentState) ? Prayers.zoneName(setting) : "", () -> {
                    zoneState = state;
                    p.push(statePage);
                });
            }
        }
    };

    private final CustomizePanel.Page statePage = new CustomizePanel.Page() {
        @Override
        public String title() {
            return zoneState;
        }

        @Override
        public void build(CustomizePanel p) {
            String setting = prayerSetting();
            for (int i = 0; i < zoneCodes.length; i++) {
                if (!zoneStates[i].equals(zoneState)) continue;
                String code = zoneCodes[i];
                p.option(Prayers.zoneName(code), zoneAreas[i], code.equals(setting), () -> setZone(code));
            }
        }
    };

    /** Tells the add-on; Auto then opens its page, which asks for the location permission and finds the zone. */
    private void setZone(String zone) {
        Context app = getApplicationContext();
        loader.execute(() -> {
            Bundle answer = Prayers.call(app, "setZone", zone);
            main.post(() -> {
                if (isDestroyed()) return;
                prayerInfo = answer;
                prayerHeader.query();
                if (Prayers.AUTO.equals(zone)) {
                    panel.close();
                    openPrayerTimes();
                } else {
                    panel.back();
                    panel.back();   // from the state's zones, past the states, to the main page
                }
            });
        });
    }

    private String prayerSetting() {
        return prayerInfo != null ? prayerInfo.getString("setting", "") : "";
    }

    /** "Johor · Zon 1", "Auto · Johor · Zon 1", or not chosen yet. */
    private String zoneValue() {
        String setting = prayerSetting();
        String zone = prayerInfo != null ? prayerInfo.getString("zone") : null;
        String named = zone == null ? null : getString(R.string.zone_value, stateOf(zone), Prayers.zoneName(zone));
        if (Prayers.AUTO.equals(setting))
            return named != null ? getString(R.string.zone_auto_value, named) : getString(R.string.zone_auto);
        return named != null ? named : getString(R.string.zone_none);
    }

    private String stateOf(String zone) {
        for (int i = 0; i < zoneCodes.length; i++)
            if (zoneCodes[i].equals(zone)) return zoneStates[i];
        return "";
    }

    private String onOff(boolean on) {
        return getString(on ? R.string.on : R.string.off);
    }

    // ----- Changes from the panel, applied at once -----

    /** Tile sizes from the screen width and apps per row; the grids are emptied, arrange() fills them again. */
    private void computeSizes() {
        columns = prefs.columns(getResources().getInteger(R.integer.columns));
        int row = getResources().getDisplayMetrics().widthPixels - 2 * px(R.dimen.safe_horizontal);
        tileWidth = (row - (columns - 1) * gap) / columns;
        dockTileWidth = (row - 2 * px(R.dimen.band_padding) - (columns - 1) * gap) / columns;
        dock.removeAllViews();
        grid.removeAllViews();
        dock.setColumnCount(columns);
        grid.setColumnCount(columns);
    }

    private void applyColumns() {
        computeSizes();
        if (installed != null) arrange();
    }

    private void applyPalette() {
        palette = new Palette(getResources(), prefs.palette());
        getWindow().setBackgroundDrawable(palette.window());
        customizeButton.setBackground(palette.button());
        settings.setBackground(palette.button());
        networkPill.setBackground(palette.pill());
        dockBand.setBackground(palette.band());
        if (installed != null) arrange();   // tiles are drawn from the palette
        panel.setPalette(palette, animations);
    }

    private void applyGlow(boolean on) {
        if (glowFade != null) glowFade.cancel();
        if (on) {
            setUpGlow();
            return;
        }
        glow = null;
        glowColor = GlowColor.NONE;
        findViewById(R.id.glow).setBackground(null);
    }

    private void applyButtonLabels(boolean on) {
        network.setIconOnly(!on);
        if (!on) {
            iconOnly(customizeButton, settings, networkPill);
            return;
        }
        customizeButton.setText(customizeText);
        settings.setText(settingsText);
        for (TextView button : new TextView[] {customizeButton, settings, networkPill}) {
            button.setPaddingRelative(buttonPadding[0], buttonPadding[1], buttonPadding[2], buttonPadding[3]);
            if (button != networkPill) button.setCompoundDrawablePadding(buttonDrawablePadding);
        }
    }

    // ----- Look: glow, greeting, icon-only buttons -----

    /** A wide radial glow behind everything, centred over the hero line, in the focused app's colour. */
    private void setUpGlow() {
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.3f, 0.35f);
        glow.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.6f);
        glow.setColors(glowStops(GlowColor.NONE, 0));
        findViewById(R.id.glow).setBackground(glow);
    }

    /** Fades the glow to {@code color} (GlowColor.NONE fades it out); at once with animations off. */
    private void showGlow(int color) {
        if (glow == null || color == glowColor) return;
        int[] from = glowStops(glowColor, color);
        int[] to = glowStops(color, glowColor);
        glowColor = color;
        if (glowFade != null) glowFade.cancel();
        if (!animations) {
            glow.setColors(to);
            return;
        }
        ArgbEvaluator blend = new ArgbEvaluator();
        int[] stops = new int[to.length];
        glowFade = ValueAnimator.ofFloat(0f, 1f).setDuration(GLOW_MS);
        glowFade.addUpdateListener(animation -> {
            for (int i = 0; i < stops.length; i++)
                stops[i] = (int) blend.evaluate(animation.getAnimatedFraction(), from[i], to[i]);
            glow.setColors(stops);
        });
        glowFade.start();
    }

    /**
     * The gradient's colours from the centre out. No glow is drawn as {@code other}'s hue at zero opacity, so a
     * fade in or out changes only the strength, never passes through grey.
     */
    private static int[] glowStops(int color, int other) {
        boolean none = color == GlowColor.NONE;
        int rgb = (none ? other : color) & 0xFFFFFF;
        return new int[] {(none ? 0 : GLOW_STRONG) << 24 | rgb, (none ? 0 : GLOW_SOFT) << 24 | rgb, rgb};
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(hour < 5 ? R.string.greeting_night : hour < 12 ? R.string.greeting_morning
                : hour < 17 ? R.string.greeting_afternoon : hour < 22 ? R.string.greeting_evening
                : R.string.greeting_night);
    }

    /** Button labels off: round icon buttons; the words stay as what a screen reader says. */
    private void iconOnly(TextView... buttons) {
        int pad = px(R.dimen.icon_button_padding);
        for (TextView button : buttons) {
            if (button.getText().length() > 0) button.setContentDescription(button.getText());
            button.setText(null);
            button.setCompoundDrawablePadding(0);
            button.setPadding(pad, pad, pad, pad);
        }
    }

    /** Off: the focused tile grows at once and the grid jumps instead of scrolling, for a GPU that can't keep up. */
    private void setAnimations(boolean on) {
        animations = on;
        scroll.setSmoothScrollingEnabled(on);
    }

    /**
     * Android's Home settings page where the box has one. Android TV often has none: then this launcher stops
     * being the default and Android asks which Home to use, offering every enabled Home app, the original one
     * included. Backing out of the question leaves no default, and the next Home press asks again.
     */
    @SuppressWarnings("deprecation")
    private void chooseDefaultHome() {
        Intent page = new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (page.resolveActivity(getPackageManager()) != null) {
            start(page, getString(R.string.cannot_open, getString(R.string.default_home)), null);
            return;
        }
        getPackageManager().clearPackagePreferredActivities(getPackageName());
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        start(home, getString(R.string.cannot_open, getString(R.string.default_home)), null);
    }

    private List<AppEntry> hiddenApps() {
        Set<String> hidden = prefs.hidden();
        List<AppEntry> apps = new ArrayList<>();
        for (AppEntry app : installed)
            if (hidden.contains(app.packageName())) apps.add(app);
        return apps;
    }

    private void resetOrder() {
        prefs.setOrder(new ArrayList<>());
        arrange();
        Toast.makeText(this, R.string.order_reset, Toast.LENGTH_SHORT).show();
    }

    // ----- Opening things -----

    private void open(AppEntry app) {
        Intent intent = Intent.makeMainActivity(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(intent, getString(R.string.cannot_open, app.label), app.packageName());
    }

    private void openAppInfo(AppEntry app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.packageName(), null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        start(intent, getString(R.string.cannot_open, getString(R.string.app_info)), null);
    }

    /** An add-on from Customize > Add-ons. Home returns to the top, as there is no tile to focus. */
    private void openAddOn(String pkg, String label) {
        Intent intent = AddOns.launchIntent(this, pkg);
        if (intent == null) return;
        start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), getString(R.string.cannot_open, label), null);
    }

    /** The Waktu Solat add-on's own page: all eight times, and where Auto asks for the location. */
    private void openPrayerTimes() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(Prayers.PACKAGE);
        if (intent == null) intent = pm.getLaunchIntentForPackage(Prayers.PACKAGE);
        if (intent == null) return;
        start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), getString(R.string.cannot_open,
                getString(R.string.open_prayer)), Prayers.PACKAGE);
    }

    private void openSettings() {
        start(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                getString(R.string.cannot_open_settings), null);
    }

    /**
     * Customize > Open on start: the chosen app opens once each time the box starts. Home and Back still reach
     * the launcher afterwards. An app that is gone or disabled leaves Home on screen.
     */
    private void openOnStart() {
        String pkg = prefs.openOnStart();
        long boot = bootId();
        if (pkg.isEmpty() || prefs.openedOnBoot() == boot) return;
        prefs.setOpenedOnBoot(boot);
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) return;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(intent, getString(R.string.cannot_open, pkg), pkg);
    }

    /** Different on every boot: Android's boot counter, or where the firmware has none, the boot time in minutes. */
    private long bootId() {
        int count = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (count >= 0) return count;
        return (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000;
    }

    /** {@code pkg} is the app being opened, so Home comes back to its tile; null for Settings and the like. */
    private void start(Intent intent, String failure, String pkg) {
        try {
            startActivity(intent);
            lastLaunched = pkg;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, failure, Toast.LENGTH_SHORT).show();
        }
    }

    private AppEntry findInstalled(String pkg) {
        for (AppEntry app : installed)
            if (app.packageName().equals(pkg)) return app;
        return null;
    }

    // ----- Helpers -----

    private void dismissDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    private static List<String> packages(List<AppEntry> apps) {
        List<String> packages = new ArrayList<>(apps.size());
        for (AppEntry app : apps) packages.add(app.packageName());
        return packages;
    }

    /** OK on the remote, Enter on a keyboard (and the emulator). */
    private static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static boolean isVolumeKey(int code) {
        return code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    private int px(int dimen) {
        return getResources().getDimensionPixelSize(dimen);
    }
}
