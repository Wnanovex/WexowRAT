package com.wexow.rat.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.wexow.rat.MainActivity;

public class SecretCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Exception ignored) {}
    }
}