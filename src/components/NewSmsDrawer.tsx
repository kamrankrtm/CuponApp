import React, { useState } from 'react';
import { 
  X, 
  Send, 
  Sparkles, 
  Smartphone, 
  MessageSquarePlus, 
  CheckCircle2, 
  AlertCircle, 
  ShieldAlert,
  HelpCircle,
  Zap
} from 'lucide-react';
import { RawSms, SimSlot, PromoCode } from '../types';
import { analyzeBatch, toPromoCode, type AiSettings } from '../lib/ai';
import { classifySms } from '../lib/smsFilter';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  settings: AiSettings;
  onSmsProcessed: (newSms: RawSms, newPromo: PromoCode | null) => void;
}

export const NewSmsDrawer: React.FC<Props> = ({ isOpen, onClose, settings, onSmsProcessed }) => {
  const [sender, setSender] = useState('10008585');
  const [sim, setSim] = useState<SimSlot>('SIM 1 (همراه اول)');
  const [body, setBody] = useState('');
  const [loading, setLoading] = useState(false);
  const [resultMsg, setResultMsg] = useState<{
    type: 'success' | 'personal' | 'banking' | 'error';
    text: string;
    details?: string;
  } | null>(null);

  if (!isOpen) return null;

  const presets = [
    {
      title: 'پیامک تخفیف جدید اسنپ‌فود',
      sender: '10008585',
      sim: 'SIM 1 (همراه اول)' as SimSlot,
      text: 'اسنپ‌فود: تخفیف ویژه آخر هفته! ۴۵ هزار تومان تخفیف سفارش فست‌فود و پیتزا بالای ۱۸۰ هزار تومان با کد: FAST45 . فقط تا امشب ساعت ۲۴ معتبر است.',
    },
    {
      title: 'پیامک تخفیف جدید تپسی‌فود',
      sender: '90001010',
      sim: 'SIM 2 (ایرانسل)' as SimSlot,
      text: 'تپسی‌فود: هوس یه برگر داغ کردی؟ ۳۰٪ تخفیف بدون محدودیت با کد BURGER30 برای همه کاربران. مهلت تا ۴۸ ساعت آینده در تپسی‌فود.',
    },
    {
      title: 'پیامک شخصی دوستانه',
      sender: '09129876543',
      sim: 'SIM 1 (همراه اول)' as SimSlot,
      text: 'سلام مهدی جان، رسیدی خونه حتما باهام تماس بگیر در مورد پروژه باهم صحبت کنیم.',
    },
    {
      title: 'پیامک بانکی تراکنش',
      sender: 'BankSaman',
      sim: 'SIM 2 (ایرانسل)' as SimSlot,
      text: 'بانک سامان: برداشت از حساب ۱۲۸... مبلغ: ۱,۲۰۰,۰۰۰ ریال. خرید اینترنتی. موجودی: ۸,۹۰۰,۰۰۰ ریال',
    },
  ];

  const handleApplyPreset = (preset: typeof presets[0]) => {
    setSender(preset.sender);
    setSim(preset.sim);
    setBody(preset.text);
    setResultMsg(null);
  };

  const handleAnalyze = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!body.trim()) return;

    setLoading(true);
    setResultMsg(null);

    const smsId = 'sms-' + Date.now();
    const smsItem: RawSms = {
      id: smsId,
      sender: sender.trim() || 'UNKNOWN',
      recipientSim: sim,
      timestamp: new Date().toISOString(),
      body: body.trim(),
      type: 'promotional', // will be decided by AI
      processed: true,
    };

    try {
      // مرحله ۱: دسته‌بندی محلی. پیام شخصی یا بانکی اصلاً به شبکه نمی‌رود.
      const classification = classifySms(smsItem.sender, smsItem.body);
      smsItem.type = classification.type;

      if (classification.type === 'personal') {
        setResultMsg({
          type: 'personal',
          text: 'این پیامک شخصی تشخیص داده شد و در بخش پیام‌های شخصی بایگانی گردید.',
          details: classification.reason,
        });
        onSmsProcessed(smsItem, null);
      } else if (classification.type === 'banking') {
        setResultMsg({
          type: 'banking',
          text: 'پیامک بانکی یا رمز یک‌بارمصرف تشخیص داده شد و به هوش مصنوعی ارسال نشد.',
          details: classification.reason,
        });
        onSmsProcessed(smsItem, null);
      } else if (!classification.safeToSend) {
        setResultMsg({
          type: 'banking',
          text: 'پیامک خدماتی بدون نشانه تخفیف است و برای صرفه‌جویی ارسال نشد.',
          details: classification.reason,
        });
        onSmsProcessed(smsItem, null);
      } else {
        // مرحله ۲: فقط پیامک تبلیغاتی به هوش مصنوعی می‌رود
        const results = await analyzeBatch(settings, [smsItem]);
        const analysis = results[0];

        if (!analysis || !analysis.hasPromoCode) {
          setResultMsg({
            type: 'banking',
            text: 'پیامک تبلیغاتی بود ولی کد تخفیف قابل استخراجی نداشت.',
          });
          onSmsProcessed(smsItem, null);
          setLoading(false);
          return;
        }

        const promo = toPromoCode(analysis, smsItem);

        setResultMsg({
          type: 'success',
          text: `کد تخفیف «${promo.code}» برای «${promo.brand}» با موفقیت شناسایی و به کدهای فعال اضافه شد!`,
          details: `مقدار: ${promo.discountAmount} | انقضا: ${promo.expiryDateText}`,
        });

        onSmsProcessed(smsItem, promo);
        setBody('');
      }
    } catch (err: any) {
      setResultMsg({
        type: 'error',
        text: 'خطا در ارتباط با هوش مصنوعی: ' + (err.message || 'لطفاً دوباره تلاش کنید'),
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-xl bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative text-right max-h-[90vh] overflow-y-auto">
        {/* Top Header */}
        <div className="flex items-center justify-between border-b border-slate-800/80 pb-4 mb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-amber-500 to-amber-600 text-slate-950 flex items-center justify-center shadow-lg shadow-amber-500/20">
              <MessageSquarePlus className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">دریافت و تحلیل پیامک جدید</h3>
              <p className="text-xs text-slate-400">شبیه‌سازی دریافت زنده SMS و پردازش فوری با Gemini</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1.5 rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Presets Quick Picker */}
        <div className="mb-4">
          <span className="text-[11px] text-slate-400 font-medium block mb-1.5">انتخاب سریع پیامک نمونه:</span>
          <div className="grid grid-cols-2 gap-2">
            {presets.map((preset, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => handleApplyPreset(preset)}
                className="text-right p-2.5 rounded-xl bg-slate-950 border border-slate-800/80 hover:border-amber-500/40 text-slate-300 hover:text-amber-400 text-xs transition cursor-pointer flex flex-col gap-1"
              >
                <span className="font-bold flex items-center gap-1">
                  <Zap className="w-3 h-3 text-amber-500" />
                  {preset.title}
                </span>
                <span className="text-[10px] text-slate-500 truncate">{preset.sender}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Input Form */}
        <form onSubmit={handleAnalyze} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-slate-300 font-medium mb-1 block">
                سرشماره یا نام فرستنده:
              </label>
              <input
                type="text"
                value={sender}
                onChange={(e) => setSender(e.target.value)}
                placeholder="مثلاً 10008585 یا 0912..."
                required
                className="w-full bg-slate-950 border border-slate-800 focus:border-amber-500/60 rounded-xl py-2 px-3 text-xs text-slate-100 outline-none dir-ltr text-right"
              />
            </div>

            <div>
              <label className="text-xs text-slate-300 font-medium mb-1 block">
                سیم‌کارت دریافت پیامک:
              </label>
              <select
                value={sim}
                onChange={(e) => setSim(e.target.value as SimSlot)}
                className="w-full bg-slate-950 border border-slate-800 focus:border-amber-500/60 rounded-xl py-2 px-3 text-xs text-slate-100 outline-none cursor-pointer"
              >
                <option value="SIM 1 (همراه اول)">SIM 1 (همراه اول)</option>
                <option value="SIM 2 (ایرانسل)">SIM 2 (ایرانسل)</option>
              </select>
            </div>
          </div>

          <div>
            <label className="text-xs text-slate-300 font-medium mb-1 block">
              متن کامل پیامک دریافت شده:
            </label>
            <textarea
              rows={4}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="متن پیامک تبلیغاتی یا شخصی را اینجا بنویسید یا الصاق (Paste) کنید..."
              required
              className="w-full bg-slate-950 border border-slate-800 focus:border-amber-500/60 rounded-xl p-3 text-xs text-slate-100 placeholder-slate-500 outline-none resize-none leading-relaxed"
            />
          </div>

          {/* Result Alert if any */}
          {resultMsg && (
            <div
              className={`p-3.5 rounded-2xl text-xs flex items-start gap-2.5 ${
                resultMsg.type === 'success'
                  ? 'bg-emerald-500/10 border border-emerald-500/30 text-emerald-300'
                  : resultMsg.type === 'personal'
                  ? 'bg-blue-500/10 border border-blue-500/30 text-blue-300'
                  : resultMsg.type === 'banking'
                  ? 'bg-purple-500/10 border border-purple-500/30 text-purple-300'
                  : 'bg-rose-500/10 border border-rose-500/30 text-rose-300'
              }`}
            >
              {resultMsg.type === 'success' && <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />}
              {resultMsg.type === 'personal' && <ShieldAlert className="w-5 h-5 text-blue-400 shrink-0 mt-0.5" />}
              {resultMsg.type === 'banking' && <AlertCircle className="w-5 h-5 text-purple-400 shrink-0 mt-0.5" />}
              {resultMsg.type === 'error' && <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />}
              <div>
                <p className="font-bold">{resultMsg.text}</p>
                {resultMsg.details && <p className="text-[11px] opacity-80 mt-1">{resultMsg.details}</p>}
              </div>
            </div>
          )}

          {/* Submit Button */}
          <button
            type="submit"
            disabled={loading || !body.trim()}
            className="w-full bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 disabled:opacity-50 text-slate-950 font-bold py-3 rounded-xl text-xs md:text-sm flex items-center justify-center gap-2 shadow-lg shadow-amber-500/20 transition cursor-pointer"
          >
            {loading ? (
              <>
                <div className="w-4 h-4 border-2 border-slate-950 border-t-transparent rounded-full animate-spin" />
                <span>در حال تحلیل هوشمند توسط Gemini AI...</span>
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4" />
                <span>تحلیل پیامک و استخراج کد تخفیف</span>
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
};
