import React from 'react';
import {
  Smartphone, ShieldCheck, Loader2, ScanLine, AlertTriangle, Lock, Settings2,
} from 'lucide-react';
import type { ScanProgress } from '../lib/scan';
import type { FilterStats } from '../lib/smsFilter';

interface Props {
  isNative: boolean;
  hasApiKey: boolean;
  permission: 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale';
  isScanning: boolean;
  progress: ScanProgress | null;
  lastStats: FilterStats | null;
  errors: string[];
  onRequestPermission: () => void;
  onScan: () => void;
  onOpenSettings: () => void;
}

export const ScanPanel: React.FC<Props> = ({
  isNative,
  hasApiKey,
  permission,
  isScanning,
  progress,
  lastStats,
  errors,
  onRequestPermission,
  onScan,
  onOpenSettings,
}) => {
  // در مرورگر خواندن پیامک ممکن نیست؛ کاربر باید نسخه اندروید را نصب کند
  if (!isNative) {
    return (
      <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-5 flex items-start gap-3.5">
        <div className="w-10 h-10 rounded-2xl bg-slate-800 text-slate-400 flex items-center justify-center shrink-0">
          <Smartphone className="w-5 h-5" />
        </div>
        <div className="space-y-1">
          <h3 className="text-sm font-bold text-slate-200">خواندن پیامک فقط در نسخه اندروید</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            مرورگرها به پیامک‌های گوشی دسترسی ندارند. برای اسکن خودکار صندوق ورودی،
            فایل APK اپ را نصب کنید. در این نسخه وب می‌توانید پیامک را دستی وارد
            کنید تا تحلیل شود.
          </p>
        </div>
      </div>
    );
  }

  const needsPermission = permission !== 'granted';

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-3xl p-5 space-y-4">
      <div className="flex items-start gap-3.5">
        <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-emerald-500 to-emerald-600 text-slate-950 flex items-center justify-center shrink-0">
          <ShieldCheck className="w-5 h-5" />
        </div>
        <div className="space-y-1 flex-1">
          <h3 className="text-sm font-bold text-white">اسکن صندوق پیامک</h3>
          <p className="text-xs text-slate-400 leading-relaxed">
            پیامک‌های ۲ ماه اخیر خوانده می‌شوند. پیام‌های شخصی، بانکی و رمزهای
            یک‌بارمصرف روی خود گوشی جدا و حذف می‌شوند و فقط پیامک‌های تبلیغاتی
            برای استخراج کد تخفیف به هوش مصنوعی ارسال می‌گردند.
          </p>
        </div>
      </div>

      {/* هشدار نبود کلید */}
      {!hasApiKey && (
        <div className="flex items-start gap-2 bg-amber-500/10 border border-amber-500/20 rounded-2xl p-3 text-[11px] text-amber-200 leading-relaxed">
          <Settings2 className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <span className="flex-1">
            هنوز کلید هوش مصنوعی وارد نشده است. بدون کلید، پیامک‌ها خوانده و دسته‌بندی
            می‌شوند ولی کد تخفیفی استخراج نمی‌شود.
          </span>
          <button
            onClick={onOpenSettings}
            className="shrink-0 underline hover:text-amber-100 cursor-pointer font-bold"
          >
            تنظیم کلید
          </button>
        </div>
      )}

      {/* وضعیت مجوز */}
      {needsPermission && (
        <div className="flex items-center gap-2 bg-slate-950 border border-slate-800 rounded-2xl p-3">
          <Lock className="w-4 h-4 text-slate-500 shrink-0" />
          <span className="text-[11px] text-slate-400 flex-1">
            برای خواندن پیامک‌ها به مجوز «خواندن پیامک» نیاز است.
          </span>
          <button
            onClick={onRequestPermission}
            className="px-3.5 py-1.5 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold rounded-xl text-[11px] transition cursor-pointer shrink-0"
          >
            اجازه می‌دهم
          </button>
        </div>
      )}

      {/* دکمه اسکن */}
      <button
        onClick={onScan}
        disabled={isScanning || needsPermission}
        className="w-full py-3 bg-amber-500 hover:bg-amber-400 disabled:bg-slate-800 disabled:text-slate-500 text-slate-950 font-bold rounded-2xl text-sm transition cursor-pointer inline-flex items-center justify-center gap-2"
      >
        {isScanning ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            در حال اسکن…
          </>
        ) : (
          <>
            <ScanLine className="w-4 h-4" />
            اسکن پیامک‌ها و استخراج تخفیف‌ها
          </>
        )}
      </button>

      {/* پیشرفت */}
      {progress && (
        <div className="space-y-2">
          <p className="text-[11px] text-slate-400">{progress.message}</p>
          {progress.total ? (
            <div className="h-1.5 bg-slate-800 rounded-full overflow-hidden">
              <div
                className="h-full bg-amber-500 transition-all duration-300"
                style={{ width: `${Math.round(((progress.done ?? 0) / progress.total) * 100)}%` }}
              />
            </div>
          ) : null}
        </div>
      )}

      {/* آمار آخرین اسکن */}
      {lastStats && !isScanning && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <Stat label="کل پیامک" value={lastStats.total} />
          <Stat label="تبلیغاتی" value={lastStats.promotional} tone="amber" />
          <Stat label="شخصی و بانکی" value={lastStats.personal + lastStats.banking} tone="emerald" />
          <Stat label="ارسال به AI" value={lastStats.sentToAi} tone="sky" />
        </div>
      )}

      {/* خطاها */}
      {errors.length > 0 && (
        <div className="flex items-start gap-2 bg-rose-500/10 border border-rose-500/20 rounded-2xl p-3 text-[11px] text-rose-200 leading-relaxed">
          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <div className="space-y-1">
            {errors.slice(0, 3).map((e, i) => (
              <p key={i}>{e}</p>
            ))}
            {errors.length > 3 && <p className="opacity-70">و {errors.length - 3} خطای دیگر…</p>}
          </div>
        </div>
      )}
    </div>
  );
};

const Stat: React.FC<{ label: string; value: number; tone?: 'amber' | 'emerald' | 'sky' }> = ({
  label,
  value,
  tone,
}) => {
  const color =
    tone === 'amber'
      ? 'text-amber-400'
      : tone === 'emerald'
      ? 'text-emerald-400'
      : tone === 'sky'
      ? 'text-sky-400'
      : 'text-slate-200';
  return (
    <div className="bg-slate-950 border border-slate-800 rounded-2xl px-3 py-2.5 text-center">
      <div className={`text-lg font-bold font-mono ${color}`}>{value}</div>
      <div className="text-[10px] text-slate-500 mt-0.5">{label}</div>
    </div>
  );
};
