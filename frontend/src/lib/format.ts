export function formatBytes(bytes: number): string {
  if (!bytes || bytes < 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1,
  );
  const v = bytes / Math.pow(1024, i);
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export function formatRate(bytesPerSec: number): string {
  return `${formatBytes(bytesPerSec)}/s`;
}

export function classNames(...xs: (string | false | null | undefined)[]) {
  return xs.filter(Boolean).join(" ");
}

export function flagFromCountry(country?: string): string {
  if (!country) return "";
  const map: Record<string, string> = {
    France: "🇫🇷",
    Germany: "🇩🇪",
    "United States": "🇺🇸",
    USA: "🇺🇸",
    UK: "🇬🇧",
    "United Kingdom": "🇬🇧",
    Spain: "🇪🇸",
    Italy: "🇮🇹",
    Morocco: "🇲🇦",
    Algeria: "🇩🇿",
    Egypt: "🇪🇬",
    Türkiye: "TR",
    Unknown: "",
  };
  return map[country] ?? "";
}

export function deviceIcon(man?: string): string {
  const m = (man || "").toLowerCase();
  if (m.includes("samsung")) return "📱";
  if (m.includes("xiaomi") || m.includes("redmi")) return "📱";
  if (m.includes("huawei")) return "📱";
  if (m.includes("google")) return "📱";
  return "📱";
}
