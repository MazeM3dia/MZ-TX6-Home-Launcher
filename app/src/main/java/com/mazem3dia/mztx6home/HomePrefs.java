package com.mazem3dia.mztx6home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The owner's choices: app order, favourites, hidden apps and theme. Packages are stored one per line. */
final class HomePrefs {
    private static final String ORDER = "order";
    private static final String DOCK = "dock";
    private static final String HIDDEN = "hidden";
    private static final String PALETTE = "palette";

    private final SharedPreferences prefs;

    HomePrefs(Context context) {
        prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    /** The Apps grid order the owner set with Move; empty means A to Z. */
    List<String> order() { return list(ORDER); }

    void setOrder(List<String> order) { putList(ORDER, order); }

    /** Favourites, in their order on the dock. */
    List<String> dock() { return list(DOCK); }

    void setDock(List<String> dock) { putList(DOCK, dock); }

    Set<String> hidden() { return new HashSet<>(list(HIDDEN)); }

    void setHidden(Set<String> hidden) { putList(HIDDEN, new ArrayList<>(hidden)); }

    int palette() { return prefs.getInt(PALETTE, 0); }

    void setPalette(int index) { prefs.edit().putInt(PALETTE, index).apply(); }

    private List<String> list(String key) {
        String text = prefs.getString(key, "");
        if (text.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(text.split("\n")));
    }

    private void putList(String key, List<String> packages) {
        prefs.edit().putString(key, String.join("\n", packages)).apply();
    }
}
