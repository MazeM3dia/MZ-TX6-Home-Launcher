package com.mazem3dia.mztx6home;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.Calendar;

/**
 * The Waktu Solat add-on (com.mazem3dia.waktusolat) as the launcher sees it: JAKIM's times through the add-on's
 * provider, so the launcher itself needs no internet. Contract in addons/waktu-solat/README.md. The formatting
 * helpers are plain Java, tested on the JVM.
 */
final class Prayers {
    static final String PACKAGE = "com.mazem3dia.waktusolat";
    private static final String AUTHORITY = "com.mazem3dia.waktusolat";
    static final String AUTO = "AUTO";
    /** JAKIM's eight times of day; the provider's {@code times} follow this order. */
    static final String[] NAMES = {"Imsak", "Subuh", "Syuruk", "Dhuha", "Zohor", "Asar", "Maghrib", "Isyak"};
    /** The five prayers, as indexes into NAMES: the next of these is shown. */
    private static final int[] PRAYERS = {1, 4, 5, 6, 7};
    private static final int SUBUH = 1;
    private static final String[] MONTHS = {"Januari", "Februari", "Mac", "April", "Mei", "Jun", "Julai", "Ogos",
            "September", "Oktober", "November", "Disember"};
    private static final String[] DAYS = {"Ahad", "Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu"};

    private Prayers() { }

    /** A method, not a constant: Uri is an Android class, and the helpers below are tested without Android. */
    static Uri uri() {
        return Uri.parse("content://" + AUTHORITY);
    }

    static boolean installed(Context context) {
        return context.getPackageManager().resolveContentProvider(AUTHORITY, 0) != null;
    }

    /** The provider's answer, or null when the add-on is missing, disabled or fails. Off the main thread. */
    static Bundle call(Context context, String method, String arg) {
        try {
            return context.getContentResolver().call(uri(), method, arg, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The next prayer at {@code now} (minutes after midnight): {index in NAMES, minutes until it}. After Isyak,
     * tomorrow's Subuh, with 24 hours added; null if tomorrow is not known.
     */
    static int[] next(int[] today, int[] tomorrow, int now) {
        for (int p : PRAYERS)
            if (today[p] >= now) return new int[] {p, today[p] - now};
        if (tomorrow == null) return null;
        return new int[] {SUBUH, tomorrow[SUBUH] + 24 * 60 - now};
    }

    /** "16:19" with the 24-hour clock, "4:19 ptg" with the 12-hour one (pg before noon). */
    static String time(int minutes, boolean twentyFourHour) {
        int h = minutes / 60 % 24, m = minutes % 60;
        String mm = (m < 10 ? "0" : "") + m;
        if (twentyFourHour) return (h < 10 ? "0" : "") + h + ":" + mm;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + mm + (h < 12 ? " pg" : " ptg");
    }

    /** "2 jam 40 min", "40 min", or "sekarang" when it has come. */
    static String countdown(int minutesLeft) {
        if (minutesLeft <= 0) return "sekarang";
        int h = minutesLeft / 60, m = minutesLeft % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " jam" : h + " jam " + m + " min";
    }

    /** "Selasa 27 Oktober", the header's date when the prayer lines are shown. */
    static String date(Calendar day) {
        return DAYS[day.get(Calendar.DAY_OF_WEEK) - 1] + " " + day.get(Calendar.DAY_OF_MONTH) + " "
                + MONTHS[day.get(Calendar.MONTH)];
    }

    /** yyyymmdd, as the provider's {@code day}. */
    static int day(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }
}
