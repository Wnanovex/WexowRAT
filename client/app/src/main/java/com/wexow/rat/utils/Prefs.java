package com.wexow.rat.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String FILE = "wexow_prefs";
    public static final String KEY_SETUP_DONE = "setup_completed";
    public static final String KEY_SERVER_HOST = "server_host";

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isSetupDone(Context ctx) {
        return get(ctx).getBoolean(KEY_SETUP_DONE, false);
    }

    public static void setSetupDone(Context ctx, boolean done) {
        get(ctx).edit().putBoolean(KEY_SETUP_DONE, done).apply();
    }

    public static String getServerHost(Context ctx) {
        return get(ctx).getString(KEY_SERVER_HOST, "example.com");
    }

    public static void setServerHost(Context ctx, String host) {
        get(ctx).edit().putString(KEY_SERVER_HOST, host).apply();
    }
}