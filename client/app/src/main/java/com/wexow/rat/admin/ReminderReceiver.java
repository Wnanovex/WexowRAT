package com.wexow.rat.admin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String channelId = "reminder_channel";
        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                channelId, "Reminders", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }

        Intent i = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i,
            PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 Reminder: Re-enable Play Protect")
            .setContentText("Protecting your device is important.")
            .setContentIntent(pi)
            .setAutoCancel(true);

        nm.notify(888, b.build());
    }
}