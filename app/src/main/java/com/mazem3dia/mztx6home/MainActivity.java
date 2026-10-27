package com.mazem3dia.mztx6home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home screen: clock, date, a Settings button and a grid of every app on the box. */
public final class MainActivity extends Activity {
    private static final float FOCUS_SCALE = 1.08f;
    private static final int FOCUS_MS = 120;
    /** Disabling a batch of apps over ADB sends one broadcast each: wait for the last before reloading. */
    private static final int RELOAD_DELAY_MS = 500;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable reload = this::loadApps;
    private ScrollView scroll;
    private GridLayout grid;
    private View settings;
    private TextView empty;
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
        setContentView(R.layout.activity_main);
        scroll = findViewById(R.id.scroll);
        grid = findViewById(R.id.grid);
        settings = findViewById(R.id.settings);
        empty = findViewById(R.id.empty);
        grid.setColumnCount(getResources().getInteger(R.integer.columns));
        settings.setOnClickListener(v -> openSettings());

        // Installs, removals, and apps disabled or enabled over ADB all change the grid.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packagesChanged, filter);
        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (tilesDropped) {
            tilesDropped = false;
            loadApps();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Home button: back to the top of the grid and the first app.
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
        // give back the icons and banners, and build them again when Home comes back.
        if (level >= TRIM_MEMORY_MODERATE && grid.getChildCount() > 0) {
            generation++;
            grid.removeAllViews();
            tilesDropped = true;
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        main.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        super.onDestroy();
    }

    private void loadApps() {
        int load = ++generation;
        Context app = getApplicationContext();
        loader.execute(() -> {
            List<AppEntry> apps = AppLoader.load(app);
            main.post(() -> {
                if (load == generation && !isDestroyed()) showApps(apps);
            });
        });
    }

    private void showApps(List<AppEntry> apps) {
        // Keep focus on the same app across a reload; leave it alone if it was on the Settings button.
        // With no tiles yet (first start, or after onTrimMemory) Android has put focus on Settings itself,
        // so the first app takes it.
        View focused = getCurrentFocus();
        ComponentName keep = focused != null && focused.getTag() instanceof AppEntry
                ? ((AppEntry) focused.getTag()).component : null;
        boolean focusTiles = grid.getChildCount() == 0 || focused == null || keep != null;

        grid.removeAllViews();
        int columns = grid.getColumnCount();
        int gap = px(R.dimen.tile_gap);
        int screen = getResources().getDisplayMetrics().widthPixels;
        int width = (screen - 2 * px(R.dimen.safe_horizontal) - (columns - 1) * gap) / columns;
        LayoutInflater inflater = getLayoutInflater();
        View restore = null;
        for (int i = 0; i < apps.size(); i++) {
            AppEntry app = apps.get(i);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width;
            params.setMarginEnd(i % columns == columns - 1 ? 0 : gap);
            params.bottomMargin = gap;
            View tile = makeTile(inflater, app, width);
            grid.addView(tile, params);
            if (app.component.equals(keep)) restore = tile;
        }
        empty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        if (!focusTiles) return;
        if (restore != null) restore.requestFocus();
        else focusFirstTile();
    }

    private View makeTile(LayoutInflater inflater, AppEntry app, int width) {
        View tile = inflater.inflate(R.layout.tile, grid, false);
        View card = tile.findViewById(R.id.card);
        card.getLayoutParams().height = width * 9 / 16;
        card.setClipToOutline(true);   // rounds a banner's corners to the card's
        ImageView image = tile.findViewById(app.banner != null ? R.id.banner : R.id.icon);
        image.setImageDrawable(app.banner != null ? app.banner : app.icon);
        image.setVisibility(View.VISIBLE);
        ((TextView) tile.findViewById(R.id.label)).setText(app.label);
        tile.setTag(app);
        tile.setContentDescription(app.label);
        tile.setOnClickListener(v -> open(app));
        // translationZ draws the grown tile above its neighbours; no outline means no shadow to render.
        tile.setOutlineProvider(null);
        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            v.setTranslationZ(hasFocus ? 1f : 0f);
            v.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_MS);
        });
        return tile;
    }

    private void focusFirstTile() {
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
        else settings.requestFocus();
    }

    private void open(AppEntry app) {
        Intent intent = Intent.makeMainActivity(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show();
        }
    }

    private int px(int dimen) {
        return getResources().getDimensionPixelSize(dimen);
    }
}
