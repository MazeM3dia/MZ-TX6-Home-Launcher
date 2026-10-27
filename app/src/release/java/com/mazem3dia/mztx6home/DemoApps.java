package com.mazem3dia.mztx6home;

import android.content.Context;

import java.util.Collections;
import java.util.List;

/** Release build: no demo apps. The debug build's DemoApps (src/debug) adds fake tiles to try the layout. */
final class DemoApps {
    static final boolean AVAILABLE = false;

    private DemoApps() { }

    static String menuLabel(boolean on) {
        return "";
    }

    static List<AppEntry> make(Context context) {
        return Collections.emptyList();
    }
}
