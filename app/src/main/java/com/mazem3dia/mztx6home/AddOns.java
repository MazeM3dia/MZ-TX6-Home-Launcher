package com.mazem3dia.mztx6home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Add-ons that open from Customize > Add-ons instead of taking a tile in the Apps grid. They stay ordinary apps,
 * so another launcher still lists them. Waktu Solat is not here: it keeps its tile and its own Customize section.
 */
final class AddOns {
    static final String DEVICE_REPORT = "com.mazem3dia.devicereport";
    static final String CAPACITY_STATS = "com.lionlev.tv.capacity";
    private static final String[] PACKAGES = {DEVICE_REPORT, CAPACITY_STATS};

    private AddOns() { }

    static boolean isAddOn(String pkg) {
        for (String p : PACKAGES) if (p.equals(pkg)) return true;
        return false;
    }

    /** The installed, enabled add-ons as package and label, in PACKAGES order. */
    static List<String[]> installed(Context context) {
        PackageManager pm = context.getPackageManager();
        List<String[]> out = new ArrayList<>();
        for (String pkg : PACKAGES) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                if (info.enabled) out.add(new String[]{pkg, String.valueOf(info.loadLabel(pm))});
            } catch (PackageManager.NameNotFoundException ignored) {
                // Not installed.
            }
        }
        return out;
    }

    /** The add-on's TV screen, else its phone one; null if it has neither. */
    static Intent launchIntent(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        return intent;
    }
}
