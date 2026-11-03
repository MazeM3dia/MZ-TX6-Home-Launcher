package com.lionlev.tv.capacity;

import java.util.Locale;

/** The figures as shown: "0.7GB / 2.0GB" in use of total, like the launcher's line on Home. Plain Java, tested on the JVM. */
final class StatsCapacity {
    private static final double GB = 1024.0 * 1024 * 1024;

    private StatsCapacity() { }

    /** "0.7GB / 2.0GB": in use of total. */
    static String usage(long used, long total) {
        return gb(used) + " / " + gb(total);
    }

    /** "1.3GB", never negative. */
    static String gb(long bytes) {
        return String.format(Locale.ROOT, "%.1fGB", Math.max(0, bytes) / GB);
    }

    /** Share in use, 0 to 100; 0 when the total is unknown. */
    static int percent(long used, long total) {
        if (total <= 0) return 0;
        return (int) Math.round(100.0 * Math.min(Math.max(0, used), total) / total);
    }
}
