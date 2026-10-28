package com.mazem3dia.mztx6home;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

/**
 * A picture behind Home, centre-cropped, darkened by the dim setting so the clock and names stay readable: one
 * of the built-in pictures, or the owner's own, copied into the app's storage by {@link #importPicture}.
 * Decoded on the loader thread in RGB_565 and no larger than the screen: about 4 MB at 1080p, given back in
 * onTrimMemory and decoded again when Home returns.
 */
final class Wallpaper {
    /** Backgrounds that are not pictures: the theme's colours alone, or the glow in the focused app's colour. */
    static final int NONE = -2;
    static final int APP_COLOUR = -1;
    /** The owner's own picture, in customFile. */
    static final int CUSTOM = 100;
    /** Pictures in Customize order, named by R.array.wallpaper_pictures. Credits: CREDITS.md. */
    static final int[] IMAGES = {R.drawable.wall_milky_way, R.drawable.wall_night_sea,
            R.drawable.wall_sunset_tree, R.drawable.wall_flower, R.drawable.wall_sunflowers};

    private final ImageView view;
    private final Executor loader;
    private final Handler main;
    private final File customFile;
    /** The picture on screen (-1: none), and a counter so a slow decode can't land after a newer choice. */
    private int shown = -1;
    private int generation;

    Wallpaper(ImageView view, Executor loader, Handler main) {
        this.view = view;
        this.loader = loader;
        this.main = main;
        this.customFile = customFile(view.getContext().getFilesDir());
    }

    static File customFile(File filesDir) {
        return new File(filesDir, "custom_wallpaper.jpg");
    }

    boolean hasCustom() {
        return customFile.isFile();
    }

    /** Shows a background (a built-in index, CUSTOM, or NONE / APP_COLOUR for no picture) {@code dim} % darker. */
    void show(int background, int dim) {
        setDim(dim);
        boolean builtIn = background >= 0 && background < IMAGES.length;
        int picture = builtIn || (background == CUSTOM && hasCustom()) ? background : -1;
        if (picture == shown) return;
        int ticket = ++generation;
        shown = picture;
        if (picture < 0) {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            return;
        }
        Resources res = view.getResources();
        int width = res.getDisplayMetrics().widthPixels;
        int height = res.getDisplayMetrics().heightPixels;
        loader.execute(() -> {
            Bitmap bitmap = builtIn ? decode(res, IMAGES[picture], width, height) : decode(customFile, width, height);
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

    /** Low memory with Home out of sight, or a new custom picture: drop the bitmap; show() decodes it again. */
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
        opts = sampled(opts, width, height);
        opts.inScaled = false;
        try {
            return BitmapFactory.decodeResource(res, id, opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap decode(File file, int width, int height) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), opts);
        try {
            return BitmapFactory.decodeFile(file.getPath(), sampled(opts, width, height));
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static BitmapFactory.Options sampled(BitmapFactory.Options bounds, int width, int height) {
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return opts;
    }

    /**
     * Copies a picture from the box (a MediaStore image: internal storage or a USB stick) into {@code out}:
     * turned upright as the camera recorded it, and scaled down to just cover a {@code width} x {@code height}
     * screen, so later starts need neither the storage permission nor the original. Slow: call it off the main
     * thread. False if the picture can't be read.
     */
    static boolean importPicture(ContentResolver cr, Uri uri, File out, int width, int height) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            // Sides compared short to short and long to long: a portrait photo is turned before it is shown.
            int shortSide = Math.min(width, height);
            int longSide = Math.max(width, height);
            int sample = 1;
            while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= shortSide
                    && Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longSide) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = cr.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bitmap == null) return false;
            int degrees = 0;
            try (InputStream in = cr.openInputStream(uri)) {
                if (in != null) degrees = degrees(new ExifInterface(in));
            } catch (IOException ignored) {
                // No EXIF (a PNG, or a screenshot): taken as upright.
            }
            boolean turned = degrees % 180 != 0;
            int w = turned ? bitmap.getHeight() : bitmap.getWidth();
            int h = turned ? bitmap.getWidth() : bitmap.getHeight();
            float scale = Math.max((float) width / w, (float) height / h);
            Matrix m = new Matrix();
            if (scale < 1) m.postScale(scale, scale);
            m.postRotate(degrees);
            if (!m.isIdentity()) {
                Bitmap upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                if (upright != bitmap) bitmap.recycle();
                bitmap = upright;
            }
            File tmp = new File(out.getPath() + ".tmp");
            try (OutputStream os = new FileOutputStream(tmp)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os)) return false;
            }
            return tmp.renameTo(out);
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            return false;
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private static int degrees(ExifInterface exif) {
        switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }
}
