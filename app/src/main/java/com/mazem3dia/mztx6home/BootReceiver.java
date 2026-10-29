package com.mazem3dia.mztx6home;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

/**
 * Opens Home once the box has booted. The TX6 firmware shows AliceUX after a power cut even when this launcher is
 * the default Home ("Always"), so Home is brought to the front itself. Only when the owner picked this launcher as
 * Home: with another default, nothing happens. Runs after other boot receivers (low priority in the manifest), then
 * once more a little later in case AliceUX comes up late; that second time is skipped when an app opens on start,
 * so Home doesn't cover it.
 */
public final class BootReceiver extends BroadcastReceiver {
    private static final long FIRST_MS = 1_000;
    private static final long AGAIN_MS = 5_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        if (!isDefaultHome(app)) return;
        boolean again = new HomePrefs(app).openOnStart().isEmpty();
        PendingResult pending = goAsync();
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            openHome(app);
            if (!again) pending.finish();
        }, FIRST_MS);
        if (again) main.postDelayed(() -> {
            openHome(app);
            pending.finish();
        }, AGAIN_MS);
    }

    private static boolean isDefaultHome(Context context) {
        ResolveInfo home = context.getPackageManager().resolveActivity(homeIntent(),
                PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && home.activityInfo != null
                && context.getPackageName().equals(home.activityInfo.packageName);
    }

    private static void openHome(Context context) {
        try {
            context.startActivity(homeIntent().setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Home stays whatever the box opened; the Home button still works.
        }
    }

    private static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }
}
