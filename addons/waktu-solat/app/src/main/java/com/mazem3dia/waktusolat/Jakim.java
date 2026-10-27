package com.mazem3dia.waktusolat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * JAKIM e-Solat (www.e-solat.gov.my): the request, the days in its answer, and the Malay names and formats JAKIM
 * uses. The API is JAKIM's own, unofficial and undocumented (guide: github.com/acfatah/jakim-esolat-api), so the
 * parsing accepts both the Malay and the English month abbreviations it has used.
 */
final class Jakim {
    /** One POST with datestart and dateend returns every day between them. */
    static final String URL = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=duration&zone=";

    /** JAKIM's eight times of day, in its order and with its names. */
    static final String[] NAMES = {"Imsak", "Subuh", "Syuruk", "Dhuha", "Zohor", "Asar", "Maghrib", "Isyak"};
    /** The answer's key for each of NAMES. */
    private static final String[] KEYS = {"imsak", "fajr", "syuruk", "dhuha", "dhuhr", "asr", "maghrib", "isha"};
    /** The five prayers, as indexes into NAMES; the others are shown but not counted down to. */
    static final int[] PRAYERS = {1, 4, 5, 6, 7};
    static final int SUBUH = 1;

    static final String[] HIJRI_MONTHS = {"Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal",
            "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah"};
    private static final String[] MONTHS = {"Januari", "Februari", "Mac", "April", "Mei", "Jun", "Julai", "Ogos",
            "September", "Oktober", "November", "Disember"};
    private static final String[] DAYS = {"Ahad", "Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu"};
    /** Month abbreviations in the answer's dates, Malay first ("27-Okt-2026"), English as JAKIM once sent. */
    private static final String[][] ABBREVIATIONS = {{"jan"}, {"feb"}, {"mac", "mar"}, {"apr"}, {"mei", "may"},
            {"jun"}, {"jul"}, {"ogo", "aug"}, {"sep"}, {"okt", "oct"}, {"nov"}, {"dis", "dec"}};

    private Jakim() { }

    /** One day of the answer. */
    static final class Day {
        final int year, month, day;   // Gregorian; month 1 to 12
        final String hijri;           // as JAKIM sends it: "1448-05-16"
        final int[] minutes;          // after midnight, in NAMES order

        Day(int year, int month, int day, String hijri, int[] minutes) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.hijri = hijri;
            this.minutes = minutes;
        }

        boolean is(int y, int m, int d) {
            return year == y && month == m && day == d;
        }
    }

    /** The form body asking for {@code start} and the {@code days} - 1 days after it. */
    static String body(Calendar start, int days) {
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, days - 1);
        return "datestart=" + iso(start) + "&dateend=" + iso(end);
    }

    static List<Day> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!root.optString("status").startsWith("OK")) throw new JSONException("status " + root.optString("status"));
        JSONArray list = root.getJSONArray("prayerTime");
        List<Day> days = new ArrayList<>(list.length());
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            String[] date = item.getString("date").split("-");   // 27-Okt-2026
            int month = date.length == 3 ? month(date[1]) : -1;
            if (month < 0) throw new JSONException("date " + item.getString("date"));
            int[] minutes = new int[KEYS.length];
            for (int k = 0; k < KEYS.length; k++) minutes[k] = minutes(item.getString(KEYS[k]));
            days.add(new Day(Integer.parseInt(date[2]), month + 1, Integer.parseInt(date[0]),
                    item.optString("hijri"), minutes));
        }
        return days;
    }

    /** 0 to 11 for a month abbreviation in the answer, or -1. */
    static int month(String abbreviation) {
        String a = abbreviation.trim().toLowerCase(java.util.Locale.ROOT);
        for (int m = 0; m < ABBREVIATIONS.length; m++)
            for (String option : ABBREVIATIONS[m])
                if (a.startsWith(option)) return m;
        return -1;
    }

    /** "05:48:00" or "05:48" to minutes after midnight. */
    static int minutes(String time) {
        String[] parts = time.trim().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    /** "1448-05-16" to "16 Jamadilawal 1448H"; as sent if it doesn't read as a date. */
    static String hijri(String date) {
        String[] p = date.split("-");
        try {
            int month = Integer.parseInt(p[1]);
            return Integer.parseInt(p[2]) + " " + HIJRI_MONTHS[month - 1] + " " + Integer.parseInt(p[0]) + "H";
        } catch (RuntimeException e) {
            return date;
        }
    }

    /** "Selasa, 27 Oktober 2026". */
    static String gregorian(Calendar day) {
        return DAYS[day.get(Calendar.DAY_OF_WEEK) - 1] + ", " + day.get(Calendar.DAY_OF_MONTH) + " "
                + MONTHS[day.get(Calendar.MONTH)] + " " + day.get(Calendar.YEAR);
    }

    /** "16:19" with the 24-hour clock; "4:19 ptg" with the 12-hour one (pg before noon, ptg from noon). */
    static String time(int minutes, boolean twentyFourHour) {
        int h = minutes / 60 % 24, m = minutes % 60;
        String mm = (m < 10 ? "0" : "") + m;
        if (twentyFourHour) return (h < 10 ? "0" : "") + h + ":" + mm;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + mm + (h < 12 ? " pg" : " ptg");
    }

    /** "2 jam 40 minit", "40 minit", or "sekarang" when the time has come. */
    static String countdown(int minutesLeft) {
        if (minutesLeft <= 0) return "sekarang";
        int h = minutesLeft / 60, m = minutesLeft % 60;
        if (h == 0) return m + " minit";
        return m == 0 ? h + " jam" : h + " jam " + m + " minit";
    }

    /**
     * The next prayer at {@code now} (minutes after midnight): its index in NAMES and the minutes until it, from
     * {@code today}; after Isyak, Subuh from {@code tomorrow} (null if not known) with 24 hours added.
     * Returns {index, minutesLeft}, or null when nothing is known.
     */
    static int[] next(int[] today, int[] tomorrow, int now) {
        for (int p : PRAYERS)
            if (today[p] >= now) return new int[] {p, today[p] - now};
        if (tomorrow == null) return null;
        return new int[] {SUBUH, tomorrow[SUBUH] + 24 * 60 - now};
    }

    private static String iso(Calendar c) {
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
