package com.lionlev.tv.capacity;

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Memory and storage in use, in the Device Report look: Inter Display, a light title, two cards with a bar each.
 * Same figures as the launcher's line on Home (ActivityManager, StatFs on the data partition). Reads every
 * 10 seconds on the main thread (two quick system calls) and only while on screen: no thread, no service.
 * Back closes it.
 */
public final class StatsActivity extends Activity {
    private static final long REFRESH_MS = 10_000;
    /** Memory above this share in use turns the bar orange, as the line on Home does. */
    private static final int HIGH_PERCENT = 85;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update();
            main.postDelayed(this, REFRESH_MS);
        }
    };
    private Typeface light, regular, bold;
    private Card ram, storage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        light = getResources().getFont(R.font.inter_display_light);
        regular = getResources().getFont(R.font.inter_display_regular);
        bold = getResources().getFont(R.font.inter_display_bold);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(64), dp(40), dp(64), dp(32));
        page.setBackgroundColor(0xFF0B0F14);

        page.addView(text(getString(R.string.app_name), light, 44, Color.WHITE));
        TextView about = text(getString(R.string.about), regular, 18, 0xB3FFFFFF);
        about.setPadding(0, dp(4), 0, dp(28));
        page.addView(about);

        LinearLayout cards = new LinearLayout(this);
        ram = new Card(R.drawable.ic_ram, R.string.memory);
        storage = new Card(R.drawable.ic_storage, R.string.storage);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        half.setMarginEnd(dp(24));
        cards.addView(ram.view, half);
        cards.addView(storage.view, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        page.addView(cards);

        TextView note = text(getString(R.string.note), regular, 15, 0x99FFFFFF);
        note.setPadding(0, dp(28), 0, 0);
        note.setLineSpacing(0, 1.15f);
        page.addView(note);

        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.post(refresh);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    private void update() {
        getSystemService(ActivityManager.class).getMemoryInfo(memory);
        ram.show(memory.totalMem - memory.availMem, memory.totalMem, true);
        StatFs data = new StatFs(Environment.getDataDirectory().getPath());
        storage.show(data.getTotalBytes() - data.getAvailableBytes(), data.getTotalBytes(), false);
    }

    /** One figure: icon and name, "0.7GB / 2.0GB", a bar, "1.3GB free · 35% used". */
    private final class Card {
        final LinearLayout view = new LinearLayout(StatsActivity.this);
        private final TextView figure, detail;
        private final View used, free;

        Card(int icon, int name) {
            view.setOrientation(LinearLayout.VERTICAL);
            view.setPadding(dp(32), dp(28), dp(32), dp(28));
            view.setBackground(round(0x14FFFFFF, dp(24)));

            LinearLayout head = new LinearLayout(StatsActivity.this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView image = new ImageView(StatsActivity.this);
            image.setImageResource(icon);
            head.addView(image, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView label = text(getString(name), bold, 20, 0xE6FFFFFF);
            label.setPadding(dp(12), 0, 0, 0);
            head.addView(label);
            view.addView(head);

            figure = text("", light, 40, Color.WHITE);
            figure.setPadding(0, dp(14), 0, dp(16));
            view.addView(figure);

            LinearLayout bar = new LinearLayout(StatsActivity.this);
            bar.setBackground(round(0x26FFFFFF, dp(4)));
            used = new View(StatsActivity.this);
            free = new View(StatsActivity.this);
            bar.addView(used, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0));
            bar.addView(free, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            view.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));

            detail = text("", regular, 16, 0xB3FFFFFF);
            detail.setPadding(0, dp(14), 0, 0);
            view.addView(detail);
        }

        void show(long inUse, long total, boolean warnWhenHigh) {
            int percent = StatsCapacity.percent(inUse, total);
            figure.setText(StatsCapacity.usage(inUse, total));
            detail.setText(getString(R.string.detail, StatsCapacity.gb(total - inUse), percent));
            boolean high = warnWhenHigh && percent > HIGH_PERCENT;
            used.setBackground(round(getColor(high ? R.color.high : R.color.accent), dp(4)));
            ((LinearLayout.LayoutParams) used.getLayoutParams()).weight = percent;
            ((LinearLayout.LayoutParams) free.getLayoutParams()).weight = 100 - percent;
            used.requestLayout();
        }
    }

    private TextView text(String s, Typeface face, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private static GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
