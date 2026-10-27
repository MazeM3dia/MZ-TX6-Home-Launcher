package com.mazem3dia.mztx6home;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import java.util.Calendar;
import java.util.concurrent.Executor;

/**
 * The header's date block: the Hijri date above the date, and the next prayer under it, from the Waktu Solat
 * add-on. Without the add-on, or with "Papar di Home" off, it is the plain date as before. The add-on is asked
 * only when needed (no answer yet, a new day, the add-on announcing new times, or REQUERY_MS gone by), off the
 * main thread; between answers the countdown is worked out here on the clock's minute tick.
 */
final class PrayerHeader {
    /** Ask again after this long even without news: a quiet way to pick up a refresh made elsewhere. */
    private static final long REQUERY_MS = 30 * 60_000L;

    private final Context context;
    private final Executor worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView hijri;
    private final TextView date;
    private final TextView next;
    private boolean enabled;
    private boolean started;
    private boolean querying;
    private Bundle data;
    private long queriedAt;

    private final ContentObserver changes = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            query();
        }
    };

    PrayerHeader(Context context, Executor worker, TextView hijri, TextView date, TextView next) {
        this.context = context.getApplicationContext();
        this.worker = worker;
        this.hijri = hijri;
        this.date = date;
        this.next = next;
    }

    /** Shown when the add-on is installed and "Papar di Home" is on. */
    void setEnabled(boolean on) {
        if (on == enabled) {
            render();
            return;
        }
        enabled = on;
        data = null;
        if (enabled && started) query();
        render();
    }

    /** The last answer (zone and state included), or null. */
    Bundle data() {
        return data;
    }

    /** Home on screen: listen for new times, and ask if what we have is old. */
    void start() {
        started = true;
        context.getContentResolver().registerContentObserver(Prayers.uri(), false, changes);
        if (enabled && (data == null || System.currentTimeMillis() - queriedAt > REQUERY_MS)) query();
        render();
    }

    void stop() {
        started = false;
        context.getContentResolver().unregisterContentObserver(changes);
    }

    /** Every minute, from the clock. A new day asks the add-on for its times. */
    void tick() {
        if (enabled && data != null && data.getInt("day") != Prayers.day(Calendar.getInstance())) query();
        render();
    }

    void query() {
        if (!enabled || querying) return;
        querying = true;
        worker.execute(() -> {
            Bundle answer = Prayers.call(context, "today", null);
            main.post(() -> {
                querying = false;
                data = answer;
                queriedAt = System.currentTimeMillis();
                render();
            });
        });
    }

    private void render() {
        Calendar now = Calendar.getInstance();
        if (!enabled || data == null) {
            hijri.setVisibility(View.GONE);
            next.setVisibility(View.GONE);
            date.setText(DateFormat.format(context.getString(R.string.date_header), now));
            return;
        }
        date.setText(Prayers.date(now));
        int[] times = data.getIntArray("times");
        if (times != null && data.getInt("day") == Prayers.day(now)) {
            hijri.setText(data.getString("hijri"));
            hijri.setVisibility(View.VISIBLE);
            int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int[] upcoming = Prayers.next(times, data.getIntArray("tomorrow"), minute);
            if (upcoming == null) {
                next.setVisibility(View.GONE);
                return;
            }
            int at = upcoming[0] == 1 && upcoming[1] > 24 * 60 - minute
                    ? data.getIntArray("tomorrow")[1] : times[upcoming[0]];
            next.setText(context.getString(R.string.next_prayer, Prayers.NAMES[upcoming[0]],
                    Prayers.time(at, DateFormat.is24HourFormat(context)), Prayers.countdown(upcoming[1])));
            next.setVisibility(View.VISIBLE);
            return;
        }
        // No times for today: say why, briefly; the zone is set in Customize.
        hijri.setVisibility(View.GONE);
        String state = data.getString("state", "");
        int line = "locating".equals(state) ? R.string.prayer_locating
                : "loading".equals(state) ? R.string.prayer_loading
                : "error".equals(state) ? R.string.prayer_offline
                : R.string.prayer_choose_zone;
        next.setText(line);
        next.setVisibility(View.VISIBLE);
    }
}
