package com.mazem3dia.mztx6home;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tile order and moves, on package names only, so it is tested on the JVM without Android. */
final class Arrangement {
    private Arrangement() { }

    /**
     * Packages in display order: those in {@code saved} first, in that order, then the rest in the order
     * {@code available} has them (A to Z). Saved packages that are no longer available are skipped.
     */
    static List<String> order(List<String> available, List<String> saved) {
        Set<String> rest = new LinkedHashSet<>(available);
        List<String> ordered = new ArrayList<>(available.size());
        for (String pkg : saved)
            if (rest.remove(pkg)) ordered.add(pkg);
        ordered.addAll(rest);
        return ordered;
    }

    /**
     * Where a tile at {@code index} lands when moved one step by the D-pad ({@code dx}, {@code dy} each -1, 0
     * or 1) in a grid of {@code count} tiles, {@code columns} wide. It stays put at an edge. Moving down into
     * a shorter last row lands on the last tile.
     */
    static int moveTarget(int index, int count, int columns, int dx, int dy) {
        if (dx != 0) {
            int target = index + dx;
            return target >= 0 && target < count ? target : index;
        }
        if (dy < 0) return index - columns >= 0 ? index - columns : index;
        if (dy > 0) {
            if (index + columns < count) return index + columns;
            boolean rowBelow = index / columns < (count - 1) / columns;
            return rowBelow ? count - 1 : index;
        }
        return index;
    }

    /** {@code list} with the item at {@code from} moved to {@code to}; the others shift to make room. */
    static <T> void move(List<T> list, int from, int to) {
        list.add(to, list.remove(from));
    }
}
