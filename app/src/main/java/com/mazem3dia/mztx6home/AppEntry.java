package com.mazem3dia.mztx6home;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** One app tile: what it shows and which activity it opens. */
final class AppEntry {
    final ComponentName component;
    final String label;
    /** The app's TV banner, or null when it has none and the tile shows its icon instead. */
    final Drawable banner;
    /** Loaded only when there is no banner. */
    final Drawable icon;

    AppEntry(ComponentName component, String label, Drawable banner, Drawable icon) {
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
    }

    /** The key for the owner's choices (order, favourites, hidden): one tile per package. */
    String packageName() {
        return component.getPackageName();
    }
}
