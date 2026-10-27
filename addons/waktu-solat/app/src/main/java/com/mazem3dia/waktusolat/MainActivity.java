package com.mazem3dia.waktusolat;

import android.Manifest;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * Today's eight JAKIM times for the chosen zone, in Malay, with the next prayer counted down. Based on the
 * Prayer Times add-on by @majnioui (cleanyourtv); the data now comes from JAKIM e-Solat through Store.
 */
public final class MainActivity extends Activity {
    private static final long MINUTE_MS = 60_000L;
    private static final long LOCATION_WAIT_MS = 20_000L;
    private static final int LOCATION_REQUEST = 1;
    /** One icon per Jakim.NAMES entry: Imsak and Subuh pre-dawn, Syuruk and Dhuha the rising sun. */
    private static final int[] ICONS = {R.drawable.ic_fajr, R.drawable.ic_fajr, R.drawable.ic_sunrise,
            R.drawable.ic_sunrise, R.drawable.ic_dhuhr, R.drawable.ic_asr, R.drawable.ic_maghrib, R.drawable.ic_isha};

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface light;
    private Typeface medium;
    private int accent;
    private TextView place;
    private TextView next;
    private TextView dates;
    private TextView status;
    private LinearLayout rows;
    private boolean askedForLocation;
    private LocationListener waiting;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            main.postDelayed(this, MINUTE_MS);
        }
    };

    /** Store announces new times (or a new zone) on the provider's URI. */
    private final ContentObserver changes = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            render();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        light = getResources().getFont(R.font.inter_display_light);
        medium = getResources().getFont(R.font.inter_display_medium);
        accent = getColor(R.color.accent);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {(accent & 0x00FFFFFF) | 0x55000000, 0xFF050607, 0xFF050607}));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(80), dp(48), dp(80), dp(40));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        place = text(18, medium, 0xB3FFFFFF);
        place.setLetterSpacing(0.2f);
        place.setAllCaps(true);
        column.addView(place);
        next = text(52, light, 0xFFFFFFFF);
        next.setPadding(0, dp(10), 0, dp(4));
        column.addView(next);
        dates = text(20, medium, 0xB3FFFFFF);
        column.addView(dates);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        rowParams.setMargins(0, dp(36), 0, 0);
        column.addView(rows, rowParams);

        status = text(16, medium, 0x80FFFFFF);
        status.setPadding(0, dp(18), 0, 0);
        column.addView(status);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(PrayerProvider.URI, false, changes);
        render();
        main.postDelayed(tick, MINUTE_MS);
        locateIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(changes);
        main.removeCallbacksAndMessages(null);
        stopWaiting();
    }

    private void render() {
        Store.Snapshot s = Store.read(this);
        int zone = s.zone != null ? Zones.indexOf(s.zone) : -1;
        place.setText(zone < 0 ? "" : getString(Store.AUTO.equals(s.setting) ? R.string.zone_line_auto
                : R.string.zone_line, Zones.CODES[zone], Zones.AREAS[zone]));
        rows.removeAllViews();
        if (s.today == null) {
            showProblem(s.state);
            return;
        }
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int[] upcoming = Jakim.next(s.today.minutes, s.tomorrow != null ? s.tomorrow.minutes : null, minute);
        if (upcoming == null) next.setText("");
        else if (upcoming[1] == 0) next.setText(getString(R.string.next_now, Jakim.NAMES[upcoming[0]]));
        else next.setText(getString(R.string.next_in, Jakim.NAMES[upcoming[0]], Jakim.countdown(upcoming[1])));
        // After Isyak the highlight is tomorrow's Subuh: no card today is the next one.
        int highlight = upcoming != null && upcoming[1] < 24 * 60 - minute ? upcoming[0] : -1;
        dates.setText(getString(R.string.dates, Jakim.gregorian(now), Jakim.hijri(s.today.hijri)));
        boolean clock24 = DateFormat.is24HourFormat(this);
        for (int i = 0; i < Jakim.NAMES.length; i++) rows.addView(card(i, s.today.minutes[i], i == highlight,
                clock24), cardParams(i));
        Calendar fetched = Calendar.getInstance();
        fetched.setTimeInMillis(s.updated);
        String updated = s.updated > 0 ? Jakim.time(fetched.get(Calendar.HOUR_OF_DAY) * 60
                + fetched.get(Calendar.MINUTE), clock24) : "";
        status.setText(getString(s.error == null ? R.string.status : R.string.status_offline, updated));
    }

    private void showProblem(String state) {
        dates.setText("");
        int title, hint = 0;
        switch (state) {
            case Store.NO_ZONE:
                title = R.string.choose_zone;
                hint = R.string.choose_zone_hint;
                break;
            case Store.LOCATING:
                title = R.string.locating;
                break;
            case Store.NO_LOCATION:
                title = R.string.no_location;
                hint = R.string.no_location_hint;
                break;
            case Store.LOADING:
                title = R.string.loading;
                break;
            default:
                title = R.string.error;
                hint = R.string.error_hint;
        }
        next.setText(title);
        status.setText(hint == 0 ? "" : getString(hint));
    }

    private LinearLayout card(int index, int minutes, boolean highlight, boolean clock24) {
        // Imsak, Syuruk and Dhuha are shown as JAKIM does, but dimmed: they are not among the five prayers.
        boolean prayer = false;
        for (int p : Jakim.PRAYERS) prayer |= p == index;
        int fg = highlight || prayer ? 0xFFFFFFFF : 0x80FFFFFF;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(4), dp(20), dp(4), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(22));
        background.setColor(highlight ? accent : 0x14FFFFFF);
        card.setBackground(background);

        ImageView icon = new ImageView(this);
        icon.setImageResource(ICONS[index]);
        icon.setImageTintList(ColorStateList.valueOf(highlight ? 0xFFFFFFFF : prayer ? accent : 0x80FFFFFF));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.bottomMargin = dp(20);
        card.addView(icon, iconParams);
        TextView name = centered(text(22, medium, fg));
        name.setText(Jakim.NAMES[index]);
        name.setPadding(0, 0, 0, dp(16));
        card.addView(name);
        TextView time = centered(text(36, highlight ? medium : light, fg));
        time.setText(Jakim.time(minutes, clock24));
        // Shrink rather than cut "12:45 ptg" when eight cards share the width.
        time.setMaxLines(1);
        time.setAutoSizeTextTypeUniformWithConfiguration(16, 36, 1, TypedValue.COMPLEX_UNIT_SP);
        card.addView(time, new LinearLayout.LayoutParams(-1, dp(48)));
        return card;
    }

    private LinearLayout.LayoutParams cardParams(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        params.setMargins(index == 0 ? 0 : dp(6), 0, index == Jakim.NAMES.length - 1 ? 0 : dp(6), 0);
        return params;
    }

    // ----- Auto zone: the box's location, asked for only while this page is open -----

    private void locateIfNeeded() {
        if (!Store.AUTO.equals(Store.setting(this)) || Store.read(this).zone != null) return;
        if (!Store.mayLocate(this)) {
            if (!askedForLocation) {
                askedForLocation = true;
                requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            }
            return;
        }
        if (Store.detect(this) != null) {
            Store.refresh(this);
            return;
        }
        waitForLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == LOCATION_REQUEST) locateIfNeeded();
    }

    /** No location known yet: ask the network provider for one, and give up after LOCATION_WAIT_MS. */
    @SuppressWarnings({"deprecation", "MissingPermission"})
    private void waitForLocation() {
        LocationManager manager = getSystemService(LocationManager.class);
        if (manager == null || !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            render();
            return;
        }
        stopWaiting();
        waiting = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                stopWaiting();
                String zone = Zones.nearest(location.getLatitude(), location.getLongitude());
                if (zone != null) Store.setAutoZone(MainActivity.this, zone);
                render();
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };
        try {
            manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, waiting, Looper.getMainLooper());
            main.postDelayed(this::stopWaiting, LOCATION_WAIT_MS);
        } catch (SecurityException e) {
            waiting = null;
        }
    }

    private void stopWaiting() {
        if (waiting == null) return;
        LocationManager manager = getSystemService(LocationManager.class);
        if (manager != null) manager.removeUpdates(waiting);
        waiting = null;
    }

    // ----- Helpers -----

    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        return t;
    }

    private TextView text(float sp, Typeface font, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(font);
        t.setTextLocale(new Locale("ms", "MY"));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
