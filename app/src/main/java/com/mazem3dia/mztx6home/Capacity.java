package com.mazem3dia.mztx6home;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.StatFs;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * RAM and storage in use under the header buttons, "[RAM] 0.7GB/2.0GB · [storage] 0.2GB/5.8GB", read by the
 * launcher itself: no add-on, no permission. Updated on start and on the clock's minute tick; orange when RAM
 * is nearly full.
 */
final class Capacity {
    /** Above this share of RAM in use, the line turns orange. */
    private static final double HIGH_RAM = 0.85;
    private static final String GAP = "  ·  ";

    private final Context context;
    private final TextView view;
    private final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    private final Drawable ram;
    private final Drawable storage;
    private boolean enabled;

    Capacity(Context context, TextView view) {
        this.context = context;
        this.view = view;
        // The Caps style's all-caps would drop the icon spans; the numbers need no capitals anyway.
        view.setAllCaps(false);
        ram = context.getDrawable(R.drawable.ic_ram).mutate();
        storage = context.getDrawable(R.drawable.ic_storage).mutate();
    }

    void setEnabled(boolean on) {
        enabled = on;
        view.setVisibility(on ? View.VISIBLE : View.GONE);
        update();
    }

    void update() {
        if (!enabled) return;
        context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
        StatFs data = new StatFs(Environment.getDataDirectory().getPath());
        long ramUsed = memory.totalMem - memory.availMem;
        long diskUsed = data.getTotalBytes() - data.getAvailableBytes();
        boolean high = memory.totalMem > 0 && ramUsed > memory.totalMem * HIGH_RAM;
        int color = context.getColor(high ? R.color.no_internet : R.color.text_secondary);
        view.setTextColor(color);
        ram.setTint(color);
        storage.setTint(color);

        SpannableStringBuilder text = new SpannableStringBuilder();
        icon(text, ram);
        text.append(usage(ramUsed, memory.totalMem)).append(GAP);
        icon(text, storage);
        text.append(usage(diskUsed, data.getTotalBytes()));
        view.setText(text);
    }

    private void icon(SpannableStringBuilder text, Drawable drawable) {
        int start = text.length();
        text.append(" ");
        text.setSpan(new IconSpan(drawable, Math.round(view.getTextSize() * 1.25f)), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(" ");
    }

    /** "0.7GB/2.0GB": in use of total. */
    static String usage(long used, long total) {
        return String.format(Locale.ROOT, "%.1fGB/%.1fGB", gb(Math.max(0, used)), gb(total));
    }

    private static double gb(long bytes) {
        return bytes / (1024.0 * 1024 * 1024);
    }

    /** An icon centred on the text's capital height; ImageSpan's centre alignment needs Android 10. */
    private static final class IconSpan extends ReplacementSpan {
        private final Drawable drawable;
        private final int size;

        IconSpan(Drawable drawable, int size) {
            this.drawable = drawable;
            this.size = size;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return size;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                Paint paint) {
            Paint.FontMetrics m = paint.getFontMetrics();
            float centre = y + (m.ascent + m.descent) / 2f;
            int left = Math.round(x);
            int t = Math.round(centre - size / 2f);
            drawable.setBounds(left, t, left + size, t + size);
            drawable.draw(canvas);
        }
    }
}
