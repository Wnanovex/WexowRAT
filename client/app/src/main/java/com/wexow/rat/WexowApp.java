package com.wexow.rat;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class WexowApp extends Application {
    private static WexowApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.e("WexowApp", "🚀 App started");

        try {
            Intent svc = new Intent(this, com.wexow.rat.core.MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception ignored) {}
    }

    public static WexowApp getInstance() {
        return instance;
    }
}
