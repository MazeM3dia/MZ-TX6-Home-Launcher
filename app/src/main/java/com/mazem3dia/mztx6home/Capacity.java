package com.mazem3dia.mztx6home;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * Free RAM and storage under the header buttons, as the Capacity Stats add-on shows them, read by the launcher
 * itself: no add-on, no permission. Updated on start and on the clock's minute tick; orange when RAM is low.
 */
final class Capacity {
    /** Below this share of RAM free, the line turns orange. */
    private static final double LOW_RAM = 0.15;

    private final Context context;
    private final TextView view;
    private final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    private boolean enabled;

    Capacity(Context context, TextView view) {
        this.context = context;
        this.view = view;
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
        view.setText(context.getString(R.string.capacity, size(memory.availMem), size(memory.totalMem),
                size(data.getAvailableBytes()), size(data.getTotalBytes())));
        boolean low = memory.totalMem > 0 && memory.availMem < memory.totalMem * LOW_RAM;
        view.setTextColor(context.getColor(low ? R.color.no_internet : R.color.text_secondary));
    }

    /** "1.1 GB", or "850 MB" under one gigabyte. */
    static String size(long bytes) {
        double gb = bytes / (1024.0 * 1024 * 1024);
        if (gb >= 1) return String.format(Locale.ROOT, "%.1f GB", gb);
        return Math.round(bytes / (1024.0 * 1024)) + " MB";
    }
}
