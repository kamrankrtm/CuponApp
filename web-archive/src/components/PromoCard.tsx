import React, { useState } from 'react';
import { 
  Copy, 
  Check, 
  Clock, 
  ExternalLink, 
  CheckCircle2, 
  XCircle, 
  MessageSquare, 
  Tag, 
  Smartphone,
  Utensils,
  Car,
  ShoppingBag,
  Film,
  Store,
  Sparkles,
  Info
} from 'lucide-react';
import { PromoCode } from '../types';

interface Props {
  promo: PromoCode;
  onMarkUsed: (id: string) => void;
  onMarkInvalid: (id: string) => void;
  onViewOriginal: (promo: PromoCode) => void;
}

export const PromoCard: React.FC<Props> = ({
  promo,
  onMarkUsed,
  onMarkInvalid,
  onViewOriginal,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(promo.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getCategoryIcon = (slug: string) => {
    switch (slug) {
      case 'food':
        return <Utensils className="w-3.5 h-3.5 text-orange-400" />;
      case 'transport':
        return <Car className="w-3.5 h-3.5 text-blue-400" />;
      case 'ecommerce':
        return <ShoppingBag className="w-3.5 h-3.5 text-red-400" />;
      case 'entertainment':
        return <Film className="w-3.5 h-3.5 text-purple-400" />;
      case 'supermarket':
        return <Store className="w-3.5 h-3.5 text-emerald-400" />;
      default:
        return <Tag className="w-3.5 h-3.5 text-amber-400" />;
    }
  };

  const getBrandColors = (brand: string) => {
    if (brand.includes('اسنپ‌فود') || brand.includes('اسنپ فود')) {
      return {
        badgeBg: 'bg-rose-500/10 border-rose-500/30 text-rose-400',
        gradient: 'from-rose-500/20 via-transparent to-transparent',
        tagBg: 'bg-rose-500 text-white',
      };
    }
    if (brand.includes('تپسی‌فود') || brand.includes('تپسی فود')) {
      return {
        badgeBg: 'bg-amber-500/10 border-amber-500/30 text-amber-400',
        gradient: 'from-amber-500/20 via-transparent to-transparent',
        tagBg: 'bg-amber-500 text-slate-950',
      };
    }
    if (brand.includes('دیجی‌کالا') || brand.includes('دیجیکالا')) {
      return {
        badgeBg: 'bg-red-500/10 border-red-500/30 text-red-400',
        gradient: 'from-red-500/20 via-transparent to-transparent',
        tagBg: 'bg-red-500 text-white',
      };
    }
    if (brand.includes('فیلیمو')) {
      return {
        badgeBg: 'bg-orange-500/10 border-orange-500/30 text-orange-400',
        gradient: 'from-orange-500/20 via-transparent to-transparent',
        tagBg: 'bg-orange-500 text-slate-950',
      };
    }
    if (brand.includes('اکالا') || brand.includes('افق')) {
      return {
        badgeBg: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400',
        gradient: 'from-emerald-500/20 via-transparent to-transparent',
        tagBg: 'bg-emerald-500 text-white',
      };
    }
    return {
      badgeBg: 'bg-indigo-500/10 border-indigo-500/30 text-indigo-400',
      gradient: 'from-indigo-500/20 via-transparent to-transparent',
      tagBg: 'bg-indigo-500 text-white',
    };
  };

  const brandStyles = getBrandColors(promo.brand);

  return (
    <div
      id={`promo-card-${promo.id}`}
      className="group relative bg-slate-900/90 border border-slate-800 hover:border-slate-700/80 rounded-3xl p-5 shadow-xl transition-all duration-300 hover:-translate-y-0.5 flex flex-col justify-between overflow-hidden"
    >
      {/* Subtle brand glow accent at top right */}
      <div className={`absolute top-0 right-0 w-48 h-48 bg-gradient-to-br ${brandStyles.gradient} rounded-bl-full pointer-events-none opacity-40 group-hover:opacity-70 transition-opacity`} />

      <div>
        {/* Top bar: Brand & Category & SIM */}
        <div className="flex items-center justify-between gap-2 mb-3 relative z-10">
          <div className="flex items-center gap-2">
            <span className={`px-3 py-1 rounded-xl text-xs font-bold border ${brandStyles.badgeBg}`}>
              {promo.brand}
            </span>
            <div className="flex items-center gap-1 bg-slate-950/70 border border-slate-800 px-2 py-0.5 rounded-lg text-[11px] text-slate-400">
              {getCategoryIcon(promo.categorySlug)}
              <span>{promo.category}</span>
            </div>
          </div>

          <div className="flex items-center gap-1.5 text-[11px] text-slate-400 bg-slate-950/70 px-2 py-1 rounded-lg border border-slate-800/80 shrink-0">
            <Smartphone className="w-3 h-3 text-emerald-400" />
            <span className="truncate max-w-[105px]">{promo.recipientSim}</span>
          </div>
        </div>

        {/* Discount Amount Headline */}
        <div className="flex items-baseline justify-between gap-2 my-2 relative z-10">
          <h3 className="text-lg font-black text-white group-hover:text-amber-300 transition-colors">
            {promo.discountAmount}
          </h3>
          <span className="text-xs text-slate-400 font-medium">{promo.minOrder || 'تخفیف ویژه'}</span>
        </div>

        {/* Code Box with 1-Click Copy */}
        <div className="my-3.5 relative z-10">
          <div
            onClick={handleCopy}
            className="flex items-center justify-between bg-slate-950/90 border-2 border-dashed border-amber-500/40 hover:border-amber-400/80 rounded-2xl p-2.5 px-3.5 transition-all cursor-pointer select-none group/code hover:bg-amber-500/5 active:scale-[0.99]"
            title="کلیک برای کپی کد تخفیف"
          >
            <div className="flex items-center gap-2">
              <span className="text-[11px] text-slate-400 font-medium">کد تخفیف:</span>
              <span className="font-mono font-black text-amber-400 text-base tracking-wider">
                {promo.code}
              </span>
            </div>

            <button
              type="button"
              className={`flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded-xl transition ${
                copied
                  ? 'bg-emerald-500 text-slate-950 shadow-md shadow-emerald-500/20'
                  : 'bg-amber-500/20 hover:bg-amber-500/30 text-amber-300'
              }`}
            >
              {copied ? (
                <>
                  <Check className="w-3.5 h-3.5" />
                  <span>کپی شد!</span>
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5" />
                  <span>کپی کد</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Instructions: "راه گرفتن تخفیف" */}
        <div className="bg-slate-950/60 border border-slate-800/80 rounded-2xl p-3 my-3 text-xs leading-relaxed relative z-10">
          <div className="flex items-center gap-1.5 font-bold text-amber-400 mb-1 text-[11px]">
            <Sparkles className="w-3.5 h-3.5" />
            <span>راه و شرایط گرفتن تخفیف:</span>
          </div>
          <p className="text-slate-300 text-[12px]">{promo.instructions}</p>
        </div>

        {/* Expiry & Source info */}
        <div className="flex items-center justify-between text-[11px] text-slate-400 mb-3 px-1">
          <div className="flex items-center gap-1 text-amber-400/90">
            <Clock className="w-3 h-3" />
            <span>مهلت: {promo.expiryDateText}</span>
          </div>
          <span className="text-slate-500 dir-ltr text-[11px]">{promo.sender}</span>
        </div>
      </div>

      {/* Action Buttons as requested by the user:
          1. استفاده شد (Used) -> removes code
          2. کار نمی‌کنه (Doesn't work) -> removes code
          3. متن اصلی پیامک (Original SMS) -> opens detailed modal
      */}
      <div className="pt-3 border-t border-slate-800/80 flex flex-wrap items-center gap-1.5 relative z-10">
        <button
          onClick={() => onMarkUsed(promo.id)}
          className="flex-1 flex items-center justify-center gap-1.5 bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/30 hover:border-emerald-500/50 text-emerald-300 py-2 rounded-xl text-xs font-semibold transition active:scale-95 cursor-pointer"
          title="این کد را مصرف کردم و دیگر نیازی به نمایش آن نیست"
        >
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
          <span>استفاده شد</span>
        </button>

        <button
          onClick={() => onMarkInvalid(promo.id)}
          className="flex-1 flex items-center justify-center gap-1.5 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 hover:border-rose-500/50 text-rose-300 py-2 rounded-xl text-xs font-semibold transition active:scale-95 cursor-pointer"
          title="کد منقضی شده یا کار نمی‌کند"
        >
          <XCircle className="w-3.5 h-3.5 text-rose-400" />
          <span>کار نمی‌کنه</span>
        </button>

        <button
          onClick={() => onViewOriginal(promo)}
          className="px-2.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white rounded-xl text-xs font-medium flex items-center gap-1 transition cursor-pointer"
          title="مشاهده متن کامل پیامک و سرشماره ارسال‌کننده"
        >
          <MessageSquare className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">متن پیامک</span>
        </button>
      </div>
    </div>
  );
};
