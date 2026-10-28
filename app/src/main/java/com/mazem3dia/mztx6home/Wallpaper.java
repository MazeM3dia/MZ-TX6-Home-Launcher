package com.mazem3dia.mztx6home;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

import java.util.concurrent.Executor;

/**
 * A built-in picture behind Home, centre-cropped, darkened by the dim setting so the clock and names stay
 * readable. Decoded on the loader thread in RGB_565 and no larger than the screen: about 4 MB at 1080p, given
 * back in onTrimMemory and decoded again when Home returns.
 */
final class Wallpaper {
    /** Backgrounds that are not pictures: the theme's colours alone, or the glow in the focused app's colour. */
    static final int NONE = -2;
    static final int APP_COLOUR = -1;
    /** Pictures in Customize order, named by R.array.wallpaper_pictures. Credits: CREDITS.md. */
    static final int[] IMAGES = {R.drawable.wall_milky_way, R.drawable.wall_night_sea,
            R.drawable.wall_sunset_tree, R.drawable.wall_flower, R.drawable.wall_sunflowers};

    private final ImageView view;
    private final Executor loader;
    private final Handler main;
    /** The picture on screen (-1: none), and a counter so a slow decode can't land after a newer choice. */
    private int shown = -1;
    private int generation;

    Wallpaper(ImageView view, Executor loader, Handler main) {
        this.view = view;
        this.loader = loader;
        this.main = main;
    }

    /** Shows picture {@code index} of IMAGES (-1: none) at {@code dim} percent darker. */
    void show(int index, int dim) {
        setDim(dim);
        if (index == shown) return;
        int ticket = ++generation;
        if (index < 0 || index >= IMAGES.length) {
            shown = -1;
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            return;
        }
        shown = index;
        Resources res = view.getResources();
        int width = res.getDisplayMetrics().widthPixels;
        int height = res.getDisplayMetrics().heightPixels;
        int id = IMAGES[index];
        loader.execute(() -> {
            Bitmap bitmap = decode(res, id, width, height);
            main.post(() -> {
                if (ticket != generation) return;
                view.setImageBitmap(bitmap);
                view.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
            });
        });
    }

    void setDim(int percent) {
        view.setColorFilter(Color.argb(Math.round(percent * 2.55f), 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
    }

    /** Low memory with Home out of sight: drop the bitmap; the next show() decodes it again. */
    void release() {
        generation++;
        shown = -1;
        view.setImageDrawable(null);
    }

    /** The picture sampled down to no less than the screen's size, in 16-bit colour. */
    private static Bitmap decode(Resources res, int id, int width, int height) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, id, opts);
        int sample = 1;
        while (opts.outWidth / (sample * 2) >= width && opts.outHeight / (sample * 2) >= height) sample *= 2;
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        opts.inScaled = false;
        try {
            return BitmapFactory.decodeResource(res, id, opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
