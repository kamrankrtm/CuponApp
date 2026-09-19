import React, { useCallback, useEffect, useState } from 'react';
import { Activity, RefreshCw, Trash2, CheckCircle2, XCircle, AlertCircle } from 'lucide-react';
import {
  clearDiagnostics,
  getDiagnostics,
  type BackgroundEvent,
  type Diagnostics,
} from '../native/smsReader';

/** توضیح فارسی هر مرحله، به همراه اینکه آیا مشکل است یا مسیر عادی */
const STAGE_INFO: Record<
  BackgroundEvent['stage'],
  { label: string; tone: 'ok' | 'warn' | 'bad' | 'muted' }
> = {
  received: { label: 'پیامک رسید', tone: 'muted' },
  skipped: { label: 'رد شد', tone: 'muted' },
  filtered: { label: 'فیلتر محلی نگه داشت', tone: 'muted' },
  'no-key': { label: 'کلید هوش مصنوعی نبود', tone: 'bad' },
  duplicate: { label: 'تکراری بود', tone: 'muted' },
  sending: { label: 'به هوش مصنوعی رفت', tone: 'warn' },
  found: { label: 'کد تخفیف پیدا شد', tone: 'ok' },
  'no-promo': { label: 'کد تخفیفی نداشت', tone: 'muted' },
  error: { label: 'خطا', tone: 'bad' },
};

const TONE_CLASS = {
  ok: 'text-emerald-300',
  warn: 'text-amber-300',
  bad: 'text-rose-300',
  muted: 'text-slate-400',
};

export const DiagnosticsPanel: React.FC = () => {
  const [diag, setDiag] = useState<Diagnostics | null>(null);
  const [open, setOpen] = useState(false);

  const refresh = useCallback(async () => {
    setDiag(await getDiagnostics());
  }, []);

  useEffect(() => {
    if (open) refresh();
  }, [open, refresh]);

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="w-full inline-flex items-center justify-center gap-1.5 py-2 text-[11px] text-slate-500 hover:text-slate-300 transition cursor-pointer"
      >
        <Activity className="w-3.5 h-3.5" />
        چرا اعلان نمی‌آید؟
      </button>
    );
  }

  return (
    <div className="rounded-2xl border border-white/6 bg-white/[0.02] p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-[12px] font-bold text-slate-300">وضعیت دریافت خودکار</h3>
        <div className="flex items-center gap-1">
          <button
            onClick={refresh}
            aria-label="تازه‌سازی"
            className="p-1.5 text-slate-500 hover:text-slate-200 transition cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={async () => {
              await clearDiagnostics();
              refresh();
            }}
            aria-label="پاک کردن"
            className="p-1.5 text-slate-500 hover:text-rose-300 transition cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {!diag ? (
        <p className="text-[11px] text-slate-500">فقط در اپ اندروید در دسترس است.</p>
      ) : (
        <>
          {/* پیش‌نیازها */}
          <div className="space-y-1.5">
            <Check label="مجوز خواندن پیامک" ok={diag.readSms === 'granted'} />
            <Check
              label="مجوز دریافت لحظه‌ای پیامک"
              ok={diag.receiveSms === 'granted'}
              hint="بدون این، پیامک تازه اصلاً به اپ نمی‌رسد"
            />
            <Check label="مجوز اعلان" ok={diag.notifications === 'granted'} />
            <Check
              label="اعلان‌ها در تنظیمات گوشی روشن است"
              ok={diag.notificationsEnabled}
              hint="از تنظیمات گوشی → برنامه‌ها → تخفیف‌یاب روشن کنید"
            />
            <Check label="کلید هوش مصنوعی" ok={diag.hasApiKey} />
          </div>

          {/* رخدادها */}
          <div className="pt-2 border-t border-white/6 space-y-1.5">
            <p className="text-[11px] font-bold text-slate-400">
              آخرین رخدادها ({diag.events.length})
            </p>

            {diag.events.length === 0 ? (
              <p className="text-[11px] text-slate-500 leading-relaxed">
                هیچ رخدادی ثبت نشده. یعنی از زمان نصب این نسخه، هیچ پیامکی به
                گیرنده اپ نرسیده است — معمولاً یعنی مجوز «دریافت لحظه‌ای» داده
                نشده یا سیستم اپ را محدود کرده.
              </p>
            ) : (
              <div className="space-y-1 max-h-56 overflow-y-auto">
                {[...diag.events].reverse().map((e, i) => {
                  const info = STAGE_INFO[e.stage] ?? {
                    label: e.stage,
                    tone: 'muted' as const,
                  };
                  return (
                    <div key={i} className="flex items-start gap-2 text-[11px]">
                      <span className="shrink-0 font-mono text-slate-600 tabular-nums">
                        {new Intl.DateTimeFormat('fa-IR', {
                          hour: '2-digit',
                          minute: '2-digit',
                        }).format(new Date(e.at))}
                      </span>
                      <span className={`shrink-0 font-medium ${TONE_CLASS[info.tone]}`}>
                        {info.label}
                      </span>
                      <span className="text-slate-500 break-words">{e.detail}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
};

const Check: React.FC<{ label: string; ok: boolean; hint?: string }> = ({ label, ok, hint }) => (
  <div className="flex items-start gap-2 text-[11px]">
    {ok ? (
      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-px" />
    ) : (
      <XCircle className="w-3.5 h-3.5 text-rose-400 shrink-0 mt-px" />
    )}
    <span className={ok ? 'text-slate-400' : 'text-rose-200'}>
      {label}
      {!ok && hint && <span className="block text-slate-500 mt-0.5">{hint}</span>}
    </span>
  </div>
);
