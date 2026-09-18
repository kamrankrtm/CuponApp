import React from 'react';
import { Download, X, Sparkles } from 'lucide-react';
import type { UpdateInfo } from '../lib/update';

interface Props {
  update: UpdateInfo | null;
  onDismiss: () => void;
}

/**
 * نوار اعلان نسخه جدید.
 *
 * لینک با target="_blank" باز می‌شود تا Capacitor آن را به مرورگر سیستم
 * بسپارد؛ دانلود APK داخل WebView خود اپ کار نمی‌کند.
 */
export const UpdateBanner: React.FC<Props> = ({ update, onDismiss }) => {
  if (!update) return null;

  return (
    <div className="rounded-2xl border border-sky-500/25 bg-sky-500/[0.08] p-3.5 flex items-center gap-3">
      <span className="shrink-0 w-9 h-9 rounded-xl bg-sky-500/15 text-sky-300 flex items-center justify-center">
        <Sparkles className="w-4 h-4" />
      </span>

      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-bold text-sky-100">نسخه {update.version} آماده است</p>
        <p className="text-[11px] text-sky-200/60 truncate">برای نصب روی دکمه بزن</p>
      </div>

      <a
        href={update.apkUrl}
        target="_blank"
        rel="noreferrer"
        className="shrink-0 inline-flex items-center gap-1.5 px-3.5 py-2 bg-sky-500 hover:bg-sky-400 text-slate-950 font-bold rounded-xl text-[12px] transition"
      >
        <Download className="w-3.5 h-3.5" />
        دریافت
      </a>

      <button
        onClick={onDismiss}
        aria-label="بستن"
        className="shrink-0 text-sky-300/50 hover:text-sky-200 p-1 transition cursor-pointer"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
};
