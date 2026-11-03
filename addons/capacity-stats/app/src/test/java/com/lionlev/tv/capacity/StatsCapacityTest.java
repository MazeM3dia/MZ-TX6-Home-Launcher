package com.lionlev.tv.capacity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StatsCapacityTest {
    private static final long GB = 1024L * 1024 * 1024;

    @Test public void usageIsInUseOfTotal() {
        assertEquals("0.7GB / 2.0GB", StatsCapacity.usage(GB * 7 / 10, GB * 2));
        assertEquals("0.0GB / 5.8GB", StatsCapacity.usage(0, GB * 58 / 10));
    }

    @Test public void negativeIsShownAsZero() {
        assertEquals("0.0GB", StatsCapacity.gb(-1));
    }

    @Test public void percentIsBoundedAndSafe() {
        assertEquals(35, StatsCapacity.percent(GB * 7 / 10, GB * 2));
        assertEquals(100, StatsCapacity.percent(GB * 3, GB * 2));
        assertEquals(0, StatsCapacity.percent(-5, GB));
        assertEquals(0, StatsCapacity.percent(GB, 0));
    }
}
