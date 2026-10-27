package com.mazem3dia.waktusolat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.List;

import org.json.JSONException;
import org.junit.Test;

public class JakimTest {
    /** JAKIM's real answer for WLY01, 27 October 2026 (period=today), as fetched while writing this. */
    private static final String TODAY = "{\"prayerTime\":[{\"hijri\":\"1448-05-16\",\"date\":\"27-Okt-2026\","
            + "\"day\":\"Tuesday\",\"imsak\":\"05:38:00\",\"fajr\":\"05:48:00\",\"syuruk\":\"06:56:00\","
            + "\"dhuha\":\"07:21:00\",\"dhuhr\":\"13:00:00\",\"asr\":\"16:19:00\",\"maghrib\":\"18:59:00\","
            + "\"isha\":\"20:09:00\"}],\"status\":\"OK!\",\"serverTime\":\"2026-10-27 14:27:27\","
            + "\"periodType\":\"today\",\"lang\":\"ms_my\",\"zone\":\"WLY01\",\"bearing\":\"292&#176;\"}";

    @Test
    public void parsesJakimsAnswer() throws JSONException {
        List<Jakim.Day> days = Jakim.parse(TODAY);
        assertEquals(1, days.size());
        Jakim.Day d = days.get(0);
        assertTrue(d.is(2026, 10, 27));
        assertEquals("1448-05-16", d.hijri);
        assertArrayEquals(new int[] {338, 348, 416, 441, 780, 979, 1139, 1209}, d.minutes);
    }

    @Test(expected = JSONException.class)
    public void refusesAnAnswerThatIsNotOk() throws JSONException {
        Jakim.parse("{\"prayerTime\":[],\"status\":\"NO_RECORD!\"}");
    }

    @Test
    public void readsMalayAndEnglishMonths() {
        assertEquals(9, Jakim.month("Okt"));
        assertEquals(9, Jakim.month("Oct"));
        assertEquals(7, Jakim.month("Ogo"));
        assertEquals(2, Jakim.month("Mac"));
        assertEquals(11, Jakim.month("Dis"));
        assertEquals(-1, Jakim.month("Xyz"));
    }

    @Test
    public void namesTheHijriMonthInMalay() {
        assertEquals("16 Jamadilawal 1448H", Jakim.hijri("1448-05-16"));
        assertEquals("1 Ramadan 1449H", Jakim.hijri("1449-09-01"));
        assertEquals("garbage", Jakim.hijri("garbage"));
    }

    @Test
    public void writesTheGregorianDateInMalay() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.OCTOBER, 27);
        assertEquals("Selasa, 27 Oktober 2026", Jakim.gregorian(c));
    }

    @Test
    public void formatsTimesBothWays() {
        assertEquals("16:19", Jakim.time(979, true));
        assertEquals("05:48", Jakim.time(348, true));
        assertEquals("4:19 ptg", Jakim.time(979, false));
        assertEquals("5:48 pg", Jakim.time(348, false));
        assertEquals("12:30 ptg", Jakim.time(750, false));
        assertEquals("12:05 pg", Jakim.time(5, false));
    }

    @Test
    public void countsDownInMalay() {
        assertEquals("2 jam 40 minit", Jakim.countdown(160));
        assertEquals("40 minit", Jakim.countdown(40));
        assertEquals("1 jam", Jakim.countdown(60));
        assertEquals("sekarang", Jakim.countdown(0));
    }

    @Test
    public void findsTheNextPrayer() {
        int[] today = {338, 348, 416, 441, 780, 979, 1139, 1209};
        int[] tomorrow = {339, 349, 417, 442, 780, 979, 1139, 1209};
        assertArrayEquals(new int[] {5, 160}, Jakim.next(today, tomorrow, 819));   // 13:39: Asar at 16:19
        assertArrayEquals(new int[] {1, 0}, Jakim.next(today, tomorrow, 348));     // Subuh now
        // Syuruk and Dhuha are not prayers: at 06:00 the next one is Zohor.
        assertArrayEquals(new int[] {4, 420}, Jakim.next(today, tomorrow, 360));
        // After Isyak: tomorrow's Subuh, 21:00 to 05:49 is 8 h 49 min.
        assertArrayEquals(new int[] {1, 529}, Jakim.next(today, tomorrow, 1260));
        assertNull(Jakim.next(today, null, 1260));
    }

    @Test
    public void asksForTheRightDays() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.DECEMBER, 20);
        assertEquals("datestart=2026-12-20&dateend=2027-01-23", Jakim.body(c, 35));
    }
}
