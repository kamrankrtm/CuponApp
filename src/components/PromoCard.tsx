import React, { useEffect, useState } from 'react';
import { Copy, Check, XCircle, FileText, RotateCcw, Clock } from 'lucide-react';
import type { PromoCode } from '../types';
import { usePressAndHold } from '../hooks/usePressAndHold';
import { expiryLabel } from '../lib/expiry';
import { categoryMeta } from '../lib/categories';

interface Props {
  promo: PromoCode;
  variant?: 'active' | 'archived';
  onUse?: (id: string) => void;
  onInvalid?: (id: string) => void;
  onRestore?: (id: string) => void;
  /** نمایش متن پیامک؛ با null یعنی بسته شود */
  onPeekSms: (promo: PromoCode | null) => void;
}

const TONE_STYLES: Record<string, string> = {
  expired: 'text-rose-300 bg-rose-500/10',
  urgent: 'text-rose-300 bg-rose-500/10',
  soon: 'text-amber-300 bg-amber-500/10',
  normal: 'text-slate-400 bg-slate-500/10',
  unknown: 'text-slate-500 bg-slate-500/10',
};

export const PromoCard: React.FC<Props> = ({
  promo,
  variant = 'active',
  onUse,
  onInvalid,
  onRestore,
  onPeekSms,
}) => {
  const [copied, setCopied] = useState(false);
  const peek = usePressAndHold();
  const cat = categoryMeta(promo.categorySlug);
  const expiry = expiryLabel(promo.expiresAt);
  const hasCode = promo.code && promo.code !== 'بدون کد';
  const archived = variant === 'archived';

  // نگه‌داشتن روی «متن پیامک» آن را باز می‌کند، برداشتن انگشت می‌بندد
  useEffect(() => {
    onPeekSms(peek.isHeld ? promo : null);
  }, [peek.isHeld]);

  const handleCopy = async () => {
    if (!hasCode) return;
    try {
      await navigator.clipboard.writeText(promo.code);
    } catch {
      // کلیپ‌بورد در دسترس نبود؛ کاربر می‌تواند کد را دستی بخواند
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };

  return (
    <article
      className={`rounded-2xl border border-white/6 bg-white/[0.03] overflow-hidden ${
        archived ? 'opacity-60' : ''
      }`}
    >
      <div className="p-4 space-y-3">
        {/* مبلغ تخفیف — مهم‌ترین چیز، بزرگ‌ترین چیز */}
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <h3 className="text-[19px] font-bold text-white leading-tight truncate">
              {promo.discountAmount}
            </h3>
            {promo.minOrder && (
              <p className="text-[11px] text-slate-500 mt-1 truncate">{promo.minOrder}</p>
            )}
          </div>

          <span
            className={`shrink-0 text-[10px] px-2 py-1 rounded-lg ring-1 ${cat.bg} ${cat.text} ${cat.ring}`}
          >
            {cat.emoji} {cat.label}
          </span>
        </div>

        {/* کد تخفیف — بزرگ‌ترین هدف لمسی کارت */}
        {hasCode ? (
          <button
            onClick={handleCopy}
            disabled={archived}
            className="w-full group flex items-center gap-2 rounded-xl border border-dashed border-amber-500/30 bg-amber-500/[0.07] px-3 py-3 transition active:scale-[0.99] disabled:active:scale-100 cursor-pointer disabled:cursor-default"
          >
            <span
              dir="ltr"
              className="flex-1 text-center font-mono font-bold text-[17px] tracking-[0.12em] text-amber-300"
            >
              {promo.code}
            </span>
            <span className="shrink-0 text-amber-400/70 group-active:text-amber-300">
              {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            </span>
          </button>
        ) : (
          <div className="rounded-xl border border-white/6 bg-white/[0.02] px-3 py-2.5 text-center text-[12px] text-slate-400">
            تخفیف بدون کد — خودکار اعمال می‌شود
          </div>
        )}

        {copied && (
          <p className="text-[11px] text-emerald-400 text-center -mt-1">کد کپی شد</p>
        )}

        {/* راهنمای استفاده */}
        {promo.instructions && (
          <p className="text-[12px] text-slate-400 leading-relaxed line-clamp-2">
            {promo.instructions}
          </p>
        )}

        {/* انقضا */}
        <div className="flex items-center gap-2">
          <span
            className={`inline-flex items-center gap-1 text-[11px] px-2 py-1 rounded-lg ${
              TONE_STYLES[expiry.tone]
            }`}
          >
            <Clock className="w-3 h-3" />
            {expiry.text}
          </span>
          <button
            {...peek.handlers}
            className={`mr-auto inline-flex items-center gap-1 text-[11px] px-2 py-1 -my-1 rounded-lg transition cursor-pointer ${
              peek.isHeld ? 'bg-white/[0.08] text-slate-200' : 'text-slate-500'
            }`}
          >
            <FileText className="w-3 h-3" />
            متن پیامک
          </button>
        </div>
      </div>

      {/* اقدام‌ها */}
      <div className="flex border-t border-white/6 divide-x divide-x-reverse divide-white/6">
        {archived ? (
          <button
            onClick={() => onRestore?.(promo.id)}
            className="flex-1 py-2.5 text-[12px] font-medium text-slate-300 hover:bg-white/[0.04] transition inline-flex items-center justify-center gap-1.5 cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            بازگرداندن
          </button>
        ) : (
          <>
            <button
              onClick={() => onUse?.(promo.id)}
              className="flex-1 py-2.5 text-[12px] font-medium text-emerald-300 hover:bg-emerald-500/10 transition inline-flex items-center justify-center gap-1.5 cursor-pointer"
            >
              <Check className="w-3.5 h-3.5" />
              استفاده کردم
            </button>
            <button
              onClick={() => onInvalid?.(promo.id)}
              className="flex-1 py-2.5 text-[12px] font-medium text-slate-400 hover:bg-white/[0.04] transition inline-flex items-center justify-center gap-1.5 cursor-pointer"
            >
              <XCircle className="w-3.5 h-3.5" />
              کار نکرد
            </button>
          </>
        )}
      </div>
    </article>
  );
};
