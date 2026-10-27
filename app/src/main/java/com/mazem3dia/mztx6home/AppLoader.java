package com.mazem3dia.mztx6home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds the apps to show as tiles. */
final class AppLoader {
    /** The glow colour is picked from the artwork drawn this small: 576 pixels, enough for its colours. */
    private static final int SAMPLE_WIDTH = 32;
    private static final int SAMPLE_HEIGHT = 18;

    private AppLoader() { }

    /**
     * Every app the box can open, one tile per package, sorted by name. The TX6 has both phone apps (LAUNCHER)
     * and TV apps (LEANBACK_LAUNCHER), so both are listed. Disabled apps are left out by the package manager.
     * Slow on the TX6's CPU: call it off the main thread.
     */
    static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, ResolveInfo> byPackage = new HashMap<>();
        // TV activities first: an app that has both opens its TV screen, which is made for the remote.
        addAll(byPackage, pm, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addAll(byPackage, pm, Intent.CATEGORY_LAUNCHER);
        byPackage.remove(context.getPackageName());

        List<AppEntry> apps = new ArrayList<>(byPackage.size());
        for (ResolveInfo info : byPackage.values()) {
            ActivityInfo activity = info.activityInfo;
            Drawable banner = activity.loadBanner(pm);
            Drawable icon = banner == null ? info.loadIcon(pm) : null;
            apps.add(new AppEntry(new ComponentName(activity.packageName, activity.name),
                    String.valueOf(info.loadLabel(pm)), banner, icon, glowOf(banner != null ? banner : icon)));
        }
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        apps.sort((a, b) -> collator.compare(a.label, b.label));
        return apps;
    }

    private static int glowOf(Drawable artwork) {
        Bitmap sample = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888);
        Rect bounds = artwork.copyBounds();
        artwork.setBounds(0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT);
        artwork.draw(new Canvas(sample));
        artwork.setBounds(bounds);   // the tile's ImageView sets its own bounds later anyway
        int[] pixels = new int[SAMPLE_WIDTH * SAMPLE_HEIGHT];
        sample.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT);
        sample.recycle();
        return GlowColor.pick(pixels);
    }

    private static void addAll(Map<String, ResolveInfo> byPackage, PackageManager pm, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0))
            byPackage.putIfAbsent(info.activityInfo.packageName, info);
    }
}
