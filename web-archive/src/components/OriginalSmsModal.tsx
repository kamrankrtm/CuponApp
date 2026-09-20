import React, { useState } from 'react';
import { X, MessageSquare, Copy, Check, Smartphone, Calendar, UserCheck } from 'lucide-react';
import { PromoCode } from '../types';

interface Props {
  promo: PromoCode | null;
  onClose: () => void;
}

export const OriginalSmsModal: React.FC<Props> = ({ promo, onClose }) => {
  const [copied, setCopied] = useState(false);

  if (!promo) return null;

  const handleCopyBody = () => {
    navigator.clipboard.writeText(promo.originalSmsBody);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative text-right flex flex-col gap-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800/80 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-400 flex items-center justify-center">
              <MessageSquare className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">متن اصلی و اطلاعات فرستنده پیامک</h3>
              <p className="text-xs text-slate-400">منبع اصلی تخفیف استخراج‌شده توسط هوش مصنوعی</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1.5 rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Metadata Badges */}
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 text-xs">
          <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-2.5 flex flex-col gap-1">
            <span className="text-slate-400 flex items-center gap-1.5">
              <UserCheck className="w-3.5 h-3.5 text-blue-400" />
              شماره فرستنده:
            </span>
            <span className="font-mono font-bold text-slate-200 dir-ltr text-right">{promo.sender}</span>
          </div>

          <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-2.5 flex flex-col gap-1">
            <span className="text-slate-400 flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5 text-emerald-400" />
              سیم‌کارت دریافت‌کننده:
            </span>
            <span className="font-medium text-slate-200">{promo.recipientSim}</span>
          </div>

          <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-2.5 flex flex-col gap-1 col-span-2 sm:col-span-1">
            <span className="text-slate-400 flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-amber-400" />
              زمان دریافت:
            </span>
            <span className="font-medium text-slate-200">
              {new Date(promo.receivedAt).toLocaleDateString('fa-IR', {
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </span>
          </div>
        </div>

        {/* Original SMS Body */}
        <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs text-slate-400 border-b border-slate-900 pb-2">
            <span>متن خام پیامک ذخیره‌شده در دستگاه:</span>
            <span className="text-[11px] text-slate-500 font-mono">{promo.originalSmsBody.length} کاراکتر</span>
          </div>
          <p className="text-sm leading-relaxed text-slate-200 font-medium whitespace-pre-wrap select-text selection:bg-amber-500/30">
            {promo.originalSmsBody}
          </p>
        </div>

        {/* Extracted Details pill */}
        <div className="p-3 bg-amber-500/5 border border-amber-500/20 rounded-xl text-xs flex items-center justify-between text-amber-300">
          <span>کد استخراج‌شده توسط AI:</span>
          <span className="font-mono font-bold bg-amber-500/20 px-2.5 py-1 rounded-lg text-amber-400 tracking-wider">
            {promo.code}
          </span>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2 pt-2">
          <button
            onClick={handleCopyBody}
            className="flex-1 flex items-center justify-center gap-2 bg-slate-800 hover:bg-slate-700 text-slate-200 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer"
          >
            {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'متن پیامک کپی شد' : 'کپی کل متن پیامک'}</span>
          </button>
          <button
            onClick={onClose}
            className="px-5 bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer"
          >
            بستن
          </button>
        </div>
      </div>
    </div>
  );
};
