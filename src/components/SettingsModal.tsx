import React, { useEffect, useState } from 'react';
import {
  X, KeyRound, Cpu, Check, Loader2, AlertTriangle, ShieldCheck, RefreshCw, Trash2, Filter,
} from 'lucide-react';
import {
  DEFAULT_BASE_URL, SUGGESTED_MODELS, listModels, testConnection, type AiSettings,
} from '../lib/ai';
import { clearAnalyzedFingerprints, clearSettings, saveSettings } from '../lib/settings';
import {
  STRICTNESS_LABELS, STRICTNESS_THRESHOLD, type StrictnessLevel,
} from '../lib/smsFilter';

interface Props {
  isOpen: boolean;
  settings: AiSettings;
  onClose: () => void;
  onSave: (settings: AiSettings) => void;
}

export const SettingsModal: React.FC<Props> = ({ isOpen, settings, onClose, onSave }) => {
  const [draft, setDraft] = useState<AiSettings>(settings);
  const [status, setStatus] = useState<
    { kind: 'idle' } | { kind: 'busy'; message: string } | { kind: 'ok'; message: string } | { kind: 'error'; message: string }
  >({ kind: 'idle' });
  const [availableModels, setAvailableModels] = useState<string[]>([]);

  useEffect(() => {
    if (isOpen) {
      setDraft(settings);
      setStatus({ kind: 'idle' });
    }
  }, [isOpen, settings]);

  if (!isOpen) return null;

  const handleTest = async () => {
    setStatus({ kind: 'busy', message: 'در حال بررسی کلید…' });
    try {
      const res = await testConnection(draft);
      setStatus({ kind: 'ok', message: `اتصال برقرار شد — ${res.modelCount} مدل در دسترس است.` });
    } catch (e: any) {
      setStatus({ kind: 'error', message: e?.message ?? 'اتصال برقرار نشد' });
    }
  };

  const handleFetchModels = async () => {
    setStatus({ kind: 'busy', message: 'در حال دریافت فهرست مدل‌ها…' });
    try {
      const models = await listModels(draft);
      setAvailableModels(models);
      setStatus({ kind: 'ok', message: `${models.length} مدل دریافت شد.` });
    } catch (e: any) {
      setStatus({ kind: 'error', message: e?.message ?? 'دریافت مدل‌ها ناموفق بود' });
    }
  };

  const handleSave = async () => {
    await saveSettings(draft);
    onSave(draft);
    onClose();
  };

  const handleClear = async () => {
    await clearSettings();
    const reset = {
      apiKey: '',
      model: draft.model,
      baseUrl: DEFAULT_BASE_URL,
      strictness: draft.strictness,
    };
    setDraft(reset);
    onSave(reset);
    setStatus({ kind: 'ok', message: 'کلید از روی دستگاه پاک شد.' });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl text-right max-h-[90vh] overflow-y-auto flex flex-col gap-5">
        <div className="flex items-center justify-between border-b border-slate-800/80 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-amber-500 to-amber-600 text-slate-950 flex items-center justify-center">
              <KeyRound className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">تنظیمات هوش مصنوعی</h3>
              <p className="text-xs text-slate-400">کلید و مدل مورد استفاده برای تحلیل پیامک‌ها</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1.5 rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* توضیح حریم خصوصی */}
        <div className="flex items-start gap-2.5 bg-emerald-500/10 border border-emerald-500/20 rounded-2xl p-3.5">
          <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
          <p className="text-[11px] leading-relaxed text-emerald-200/90">
            کلید فقط روی حافظه خصوصی همین گوشی ذخیره می‌شود و به هیچ سروری جز سرویس
            هوش مصنوعی انتخابی ارسال نمی‌گردد. پیامک‌های شخصی، بانکی و رمزهای
            یک‌بارمصرف پیش از هر تماس شبکه‌ای روی خود گوشی کنار گذاشته می‌شوند.
          </p>
        </div>

        {/* کلید API */}
        <div className="space-y-2">
          <label className="text-xs font-bold text-slate-300">کلید API اول‌ای‌آی (AvalAI)</label>
          <input
            type="password"
            dir="ltr"
            value={draft.apiKey}
            onChange={(e) => setDraft({ ...draft, apiKey: e.target.value.trim() })}
            placeholder="aa-..."
            className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-slate-100 font-mono outline-none focus:border-amber-500 transition"
          />
          <p className="text-[11px] text-slate-500">
            کلید را از پنل کاربری avalai.ir بگیرید و اینجا وارد کنید.
          </p>
        </div>

        {/* آدرس سرویس */}
        <div className="space-y-2">
          <label className="text-xs font-bold text-slate-300">آدرس سرویس</label>
          <input
            type="text"
            dir="ltr"
            value={draft.baseUrl}
            onChange={(e) => setDraft({ ...draft, baseUrl: e.target.value.trim() })}
            className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-slate-100 font-mono outline-none focus:border-amber-500 transition"
          />
        </div>

        {/* انتخاب مدل */}
        <div className="space-y-2">
          <label className="text-xs font-bold text-slate-300 flex items-center gap-1.5">
            <Cpu className="w-3.5 h-3.5" />
            مدل
          </label>
          <input
            type="text"
            dir="ltr"
            list="model-options"
            value={draft.model}
            onChange={(e) => setDraft({ ...draft, model: e.target.value.trim() })}
            className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-slate-100 font-mono outline-none focus:border-amber-500 transition"
          />
          <datalist id="model-options">
            {(availableModels.length > 0
              ? availableModels
              : SUGGESTED_MODELS.map((m) => m.id)
            ).map((id) => (
              <option key={id} value={id} />
            ))}
          </datalist>

          <div className="flex flex-wrap gap-1.5 pt-1">
            {SUGGESTED_MODELS.map((m) => (
              <button
                key={m.id}
                onClick={() => setDraft({ ...draft, model: m.id })}
                className={`text-[11px] px-2.5 py-1 rounded-lg border transition cursor-pointer ${
                  draft.model === m.id
                    ? 'bg-amber-500 text-slate-950 border-amber-500 font-bold'
                    : 'bg-slate-950 text-slate-400 border-slate-800 hover:border-slate-700'
                }`}
              >
                {m.label}
              </button>
            ))}
          </div>
        </div>

        {/* سخت‌گیری فیلتر */}
        <div className="space-y-2 border-t border-slate-800/80 pt-4">
          <label className="text-xs font-bold text-slate-300 flex items-center gap-1.5">
            <Filter className="w-3.5 h-3.5" />
            سخت‌گیری فیلتر
          </label>
          <p className="text-[11px] text-slate-500 leading-relaxed">
            تعیین می‌کند یک پیامک چقدر باید نشانه تخفیف داشته باشد تا ارزش ارسال
            به هوش مصنوعی را پیدا کند. هرچه سخت‌گیرتر، هزینه کمتر — ولی احتمال
            رد شدن تخفیف‌های با جمله‌بندی غیرمعمول بیشتر.
          </p>
          <div className="grid grid-cols-1 gap-1.5">
            {(['relaxed', 'balanced', 'strict'] as StrictnessLevel[]).map((level) => (
              <button
                key={level}
                onClick={() => setDraft({ ...draft, strictness: level })}
                className={`text-right px-3 py-2 rounded-xl border text-[11px] transition cursor-pointer flex items-center justify-between gap-2 ${
                  draft.strictness === level
                    ? 'bg-amber-500/15 border-amber-500/40 text-amber-200'
                    : 'bg-slate-950 border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                <span>{STRICTNESS_LABELS[level]}</span>
                <span className="font-mono text-[10px] opacity-70 shrink-0">
                  آستانه {STRICTNESS_THRESHOLD[level]}
                </span>
              </button>
            ))}
          </div>
          <button
            onClick={async () => {
              await clearAnalyzedFingerprints();
              setStatus({ kind: 'ok', message: 'کش پاک شد؛ اسکن بعدی همه پیامک‌ها را از نو بررسی می‌کند.' });
            }}
            className="text-[11px] text-slate-400 hover:text-amber-400 underline transition cursor-pointer"
          >
            پاک کردن کش پیامک‌های تحلیل‌شده
          </button>
        </div>

        {/* وضعیت */}
        {status.kind !== 'idle' && (
          <div
            className={`flex items-start gap-2 rounded-2xl p-3 text-[11px] leading-relaxed ${
              status.kind === 'ok'
                ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-200'
                : status.kind === 'error'
                ? 'bg-rose-500/10 border border-rose-500/20 text-rose-200'
                : 'bg-slate-800/50 border border-slate-700 text-slate-300'
            }`}
          >
            {status.kind === 'busy' && <Loader2 className="w-3.5 h-3.5 animate-spin shrink-0 mt-0.5" />}
            {status.kind === 'ok' && <Check className="w-3.5 h-3.5 shrink-0 mt-0.5" />}
            {status.kind === 'error' && <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />}
            <span>{status.message}</span>
          </div>
        )}

        {/* دکمه‌ها */}
        <div className="flex flex-wrap items-center gap-2 pt-1">
          <button
            onClick={handleTest}
            disabled={!draft.apiKey || status.kind === 'busy'}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 rounded-xl text-xs font-semibold transition cursor-pointer"
          >
            تست اتصال
          </button>
          <button
            onClick={handleFetchModels}
            disabled={!draft.apiKey || status.kind === 'busy'}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 rounded-xl text-xs font-semibold transition cursor-pointer inline-flex items-center gap-1.5"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            دریافت لیست مدل‌ها
          </button>
          <button
            onClick={handleClear}
            disabled={!draft.apiKey}
            className="px-4 py-2 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/20 disabled:opacity-40 text-rose-300 rounded-xl text-xs font-semibold transition cursor-pointer inline-flex items-center gap-1.5"
          >
            <Trash2 className="w-3.5 h-3.5" />
            پاک کردن کلید
          </button>
          <button
            onClick={handleSave}
            className="mr-auto px-5 py-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl text-xs transition cursor-pointer"
          >
            ذخیره
          </button>
        </div>
      </div>
    </div>
  );
};
