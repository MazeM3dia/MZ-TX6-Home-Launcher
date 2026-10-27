package com.mazem3dia.mztx6home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Calendar;

import org.junit.Test;

public class PrayersTest {
    /** JAKIM's times for WLY01, 27 October 2026: Imsak, Subuh, Syuruk, Dhuha, Zohor, Asar, Maghrib, Isyak. */
    private static final int[] TODAY = {338, 348, 416, 441, 780, 979, 1139, 1209};
    private static final int[] TOMORROW = {339, 349, 417, 442, 780, 979, 1139, 1209};

    @Test
    public void nextPrayerSkipsImsakSyurukAndDhuha() {
        assertArrayEquals(new int[] {1, 48}, Prayers.next(TODAY, TOMORROW, 300));    // 05:00: Subuh
        assertArrayEquals(new int[] {4, 420}, Prayers.next(TODAY, TOMORROW, 360));   // 06:00: Zohor
        assertArrayEquals(new int[] {5, 160}, Prayers.next(TODAY, TOMORROW, 819));   // 13:39: Asar
    }

    @Test
    public void afterIsyakComesTomorrowsSubuh() {
        assertArrayEquals(new int[] {1, 529}, Prayers.next(TODAY, TOMORROW, 1260));
        assertNull(Prayers.next(TODAY, null, 1260));
    }

    @Test
    public void formatsTimesAndCountdown() {
        assertEquals("4:19 ptg", Prayers.time(979, false));
        assertEquals("5:48 pg", Prayers.time(348, false));
        assertEquals("16:19", Prayers.time(979, true));
        assertEquals("2 jam 40 min", Prayers.countdown(160));
        assertEquals("40 min", Prayers.countdown(40));
        assertEquals("sekarang", Prayers.countdown(0));
    }

    @Test
    public void namesZonesByNumber() {
        assertEquals("Zon 1", Prayers.zoneName("JHR01"));
        assertEquals("Zon 2", Prayers.zoneName("WLY02"));
    }

    @Test
    public void writesTheDateInMalay() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.OCTOBER, 27);
        assertEquals("Selasa 27 Oktober", Prayers.date(c));
        assertEquals(20261027, Prayers.day(c));
    }
}
