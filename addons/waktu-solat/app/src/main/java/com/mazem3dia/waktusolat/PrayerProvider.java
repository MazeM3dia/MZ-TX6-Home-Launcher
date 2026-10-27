package com.mazem3dia.waktusolat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * How MZ TX6 Home reads the prayer times and sets the zone, without internet of its own: ContentResolver.call
 * on content://com.mazem3dia.waktusolat. Methods:
 * <ul>
 * <li>{@code today}: the state, zone, area, Hijri date and today's and tomorrow's times (see {@link #today});
 * <li>{@code zones}: JAKIM's zones (codes, states, areas);
 * <li>{@code setZone} with a zone code or AUTO: changes the zone, then answers as {@code today}.
 * </ul>
 * A change (new times, a new zone) is announced with notifyChange on the same URI.
 * The data is JAKIM's public timetable; any app on the box may read it.
 */
public final class PrayerProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://com.mazem3dia.waktusolat");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        switch (method) {
            case "today":
                return today();
            case "zones":
                Bundle zones = new Bundle();
                zones.putStringArray("codes", Zones.CODES);
                zones.putStringArray("states", Zones.STATES);
                zones.putStringArray("areas", Zones.AREAS);
                return zones;
            case "setZone":
                if (arg != null) Store.setSetting(getContext(), arg);
                return today();
            default:
                return null;
        }
    }

    /**
     * {@code state} (Store.OK, LOADING, ERROR, NO_ZONE, LOCATING, NO_LOCATION), {@code setting}, {@code zone}, the
     * zone's {@code negeri} ("Johor") and {@code area}; with times: {@code hijri} ("16 Jamadilawal 1448H"),
     * {@code times} (minutes after midnight in Jakim.NAMES order), {@code tomorrow} (the same for tomorrow, if
     * known), {@code day} (yyyymmdd the times are for) and {@code updated} (when they were fetched, ms).
     */
    private Bundle today() {
        Store.Snapshot s = Store.read(getContext());
        Bundle b = new Bundle();
        b.putString("state", s.state);
        b.putString("setting", s.setting);
        b.putString("zone", s.zone);
        int index = s.zone != null ? Zones.indexOf(s.zone) : -1;
        if (index >= 0) {
            b.putString("negeri", Zones.STATES[index]);
            b.putString("area", Zones.AREAS[index]);
        }
        if (s.today != null) {
            b.putString("hijri", Jakim.hijri(s.today.hijri));
            b.putIntArray("times", s.today.minutes);
            if (s.tomorrow != null) b.putIntArray("tomorrow", s.tomorrow.minutes);
            b.putInt("day", s.today.year * 10000 + s.today.month * 100 + s.today.day);
        }
        b.putLong("updated", s.updated);
        return b;
    }

    // The provider only answers call(); there is no table to query.
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
