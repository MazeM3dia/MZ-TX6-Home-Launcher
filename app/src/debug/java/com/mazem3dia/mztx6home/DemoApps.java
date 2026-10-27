package com.mazem3dia.mztx6home;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Debug build only: 40 fake app tiles, to try the layout with many apps (Customize > Demo apps). They open
 * nothing. The release build has an empty twin in src/release, so none of this reaches the TX6.
 */
final class DemoApps {
    static final boolean AVAILABLE = true;
    private static final int COUNT = 40;
    /** Brand-like colours, white and near-black included, so the glow has something to pick and to skip. */
    private static final int[] COLOURS = {
            0xFFE50914, 0xFF1A98FF, 0xFFFFFFFF, 0xFF1DB954, 0xFFFF9900,
            0xFF8E44AD, 0xFF222222, 0xFFFF0050, 0xFF00C2A8, 0xFF3D5AFE,
    };

    private DemoApps() { }

    static String menuLabel(boolean on) {
        return on ? "Demo apps: on" : "Demo apps: off";
    }

    static List<AppEntry> make(Context context) {
        Resources res = context.getResources();
        List<AppEntry> apps = new ArrayList<>(COUNT);
        for (int i = 1; i <= COUNT; i++) {
            String label = String.format(Locale.ROOT, "Demo %02d", i);
            int colour = COLOURS[i % COLOURS.length];
            // Every third one has no banner, like the phone apps on the TX6: its tile shows an icon.
            boolean hasBanner = i % 3 != 0;
            Drawable art = hasBanner ? banner(res, label, colour) : icon(res, colour);
            String pkg = "demo.app" + i;
            apps.add(new AppEntry(new ComponentName(pkg, pkg + ".Main"), label,
                    hasBanner ? art : null, hasBanner ? null : art, AppLoader.glowOf(art)));
        }
        return apps;
    }

    private static Drawable banner(Resources res, String label, int colour) {
        Bitmap bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(colour);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(isLight(colour) ? 0xFF000000 : 0xFFFFFFFF);
        text.setTextSize(44);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        canvas.drawText(label, 160, 105, text);
        return new BitmapDrawable(res, bitmap);
    }

    private static Drawable icon(Resources res, int colour) {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        new Canvas(bitmap).drawCircle(48, 48, 46, paint);
        return new BitmapDrawable(res, bitmap);
    }

    private static boolean isLight(int colour) {
        int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return r * 299 + g * 587 + b * 114 > 160_000;
    }
}
