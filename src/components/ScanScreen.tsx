import React from 'react';
import { ShieldCheck, Loader2, ScanLine, AlertTriangle, Lock, KeyRound, Smartphone } from 'lucide-react';
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

export const ScanScreen: React.FC<Props> = ({
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
  const needsPermission = isNative && permission !== 'granted';
  const blocked = !isNative || needsPermission;

  return (
    <div className="space-y-4">
      {/* توضیح حریم خصوصی */}
      <div className="rounded-2xl border border-emerald-500/15 bg-emerald-500/[0.06] p-4 flex gap-3">
        <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
        <p className="text-[12px] text-emerald-100/80 leading-relaxed">
          پیامک‌های شخصی، بانکی و رمزهای یک‌بارمصرف روی خود گوشی جدا می‌شوند و
          هرگز به هوش مصنوعی ارسال نمی‌گردند. فقط پیامک‌های تبلیغاتی نشانه‌دار
          بررسی می‌شوند.
        </p>
      </div>

      {/* پیش‌نیازها */}
      {!isNative && (
        <Blocker
          icon={<Smartphone className="w-4 h-4" />}
          text="خواندن پیامک فقط در اپ اندروید ممکن است. مرورگرها به صندوق پیامک دسترسی ندارند."
        />
      )}

      {needsPermission && (
        <Blocker
          icon={<Lock className="w-4 h-4" />}
          text="برای خواندن پیامک‌ها به مجوز «خواندن پیامک» نیاز است."
          action={{ label: 'اجازه می‌دهم', onClick: onRequestPermission }}
        />
      )}

      {!hasApiKey && (
        <Blocker
          icon={<KeyRound className="w-4 h-4" />}
          tone="amber"
          text="کلید هوش مصنوعی وارد نشده. بدون کلید، کد تخفیفی استخراج نمی‌شود."
          action={{ label: 'تنظیم کلید', onClick: onOpenSettings }}
        />
      )}

      {/* دکمه اسکن */}
      <button
        onClick={onScan}
        disabled={isScanning || blocked}
        className="w-full py-3.5 bg-amber-500 hover:bg-amber-400 disabled:bg-white/[0.05] disabled:text-slate-600 text-slate-950 font-bold rounded-2xl text-[14px] transition inline-flex items-center justify-center gap-2 cursor-pointer disabled:cursor-not-allowed"
      >
        {isScanning ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            در حال اسکن…
          </>
        ) : (
          <>
            <ScanLine className="w-4 h-4" />
            اسکن پیامک‌های ۲ ماه اخیر
          </>
        )}
      </button>

      {/* پیشرفت */}
      {progress && (
        <div className="space-y-2">
          <p className="text-[12px] text-slate-400 text-center">{progress.message}</p>
          {progress.total ? (
            <div className="h-1 bg-white/[0.06] rounded-full overflow-hidden">
              <div
                className="h-full bg-amber-500 transition-all duration-300"
                style={{ width: `${Math.round(((progress.done ?? 0) / progress.total) * 100)}%` }}
              />
            </div>
          ) : null}
        </div>
      )}

      {/* نتیجه آخرین اسکن */}
      {lastStats && !isScanning && (
        <div className="rounded-2xl border border-white/6 bg-white/[0.02] p-4 space-y-3">
          <h3 className="text-[12px] font-bold text-slate-300">نتیجه آخرین اسکن</h3>

          <div className="space-y-1.5">
            <Row label="کل پیامک خوانده‌شده" value={lastStats.total} />
            <Row
              label="شخصی و بانکی — حذف شد"
              value={lastStats.personal + lastStats.banking}
              tone="emerald"
            />
            <Row label="بدون نشانه تخفیف — رد شد" value={lastStats.belowThreshold} />
            {lastStats.duplicates > 0 && (
              <Row label="تکراری — رد شد" value={lastStats.duplicates} />
            )}
            {lastStats.cached > 0 && (
              <Row label="قبلاً بررسی‌شده — رد شد" value={lastStats.cached} />
            )}
            <div className="h-px bg-white/6 my-1" />
            <Row label="ارسال‌شده به هوش مصنوعی" value={lastStats.sentToAi} tone="amber" bold />
          </div>

          {lastStats.total > 0 && (
            <p className="text-[11px] text-slate-500 leading-relaxed pt-1">
              یعنی بابت{' '}
              <span className="text-slate-300 font-mono">
                {lastStats.total - lastStats.sentToAi}
              </span>{' '}
              پیامک از {lastStats.total} تا، هیچ هزینه‌ای نداده‌ای.
            </p>
          )}
        </div>
      )}

      {/* خطاها */}
      {errors.length > 0 && (
        <div className="rounded-2xl border border-rose-500/20 bg-rose-500/[0.06] p-4 flex gap-2.5">
          <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
          <div className="space-y-1 text-[12px] text-rose-200/90 leading-relaxed">
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

const Row: React.FC<{
  label: string;
  value: number;
  tone?: 'amber' | 'emerald';
  bold?: boolean;
}> = ({ label, value, tone, bold }) => (
  <div className="flex items-center justify-between text-[12px]">
    <span className={bold ? 'text-slate-200 font-bold' : 'text-slate-500'}>{label}</span>
    <span
      className={`font-mono font-bold ${
        tone === 'amber' ? 'text-amber-400' : tone === 'emerald' ? 'text-emerald-400' : 'text-slate-300'
      }`}
    >
      {value}
    </span>
  </div>
);

const Blocker: React.FC<{
  icon: React.ReactNode;
  text: string;
  tone?: 'amber';
  action?: { label: string; onClick: () => void };
}> = ({ icon, text, tone, action }) => (
  <div
    className={`rounded-2xl border p-3.5 flex items-center gap-2.5 ${
      tone === 'amber'
        ? 'border-amber-500/20 bg-amber-500/[0.06] text-amber-200/90'
        : 'border-white/6 bg-white/[0.02] text-slate-400'
    }`}
  >
    <span className="shrink-0">{icon}</span>
    <span className="text-[12px] leading-relaxed flex-1">{text}</span>
    {action && (
      <button
        onClick={action.onClick}
        className="shrink-0 px-3 py-1.5 bg-white/[0.08] hover:bg-white/[0.14] text-slate-100 rounded-lg text-[11px] font-bold transition cursor-pointer"
      >
        {action.label}
      </button>
    )}
  </div>
);
