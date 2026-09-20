import React from 'react';
import { CheckCircle2, XCircle, RotateCcw, MessageSquare, Clock } from 'lucide-react';
import { PromoCode } from '../types';

interface Props {
  codes: PromoCode[];
  onRestore: (id: string) => void;
  onViewOriginal: (promo: PromoCode) => void;
}

export const UsedCodesView: React.FC<Props> = ({ codes, onRestore, onViewOriginal }) => {
  return (
    <div className="flex flex-col gap-4">
      <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-3.5 text-xs text-slate-400 flex items-center justify-between">
        <span>کدهایی که قبلاً علامت «استفاده شد» یا «کار نمی‌کنه» خورده‌اند در اینجا آرشیو شده‌اند:</span>
        <span className="font-bold text-slate-200">{codes.length} مورد</span>
      </div>

      {codes.length === 0 ? (
        <div className="text-center py-12 bg-slate-900/40 border border-slate-800 rounded-3xl p-8">
          <p className="text-sm text-slate-400">هنوز کدی مصرف یا باطل نشده است.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
          {codes.map((promo) => (
            <div
              key={promo.id}
              className="bg-slate-900/60 border border-slate-800/80 rounded-3xl p-4 flex flex-col justify-between gap-3 opacity-80 hover:opacity-100 transition"
            >
              <div>
                <div className="flex items-center justify-between gap-2 mb-2">
                  <span className="text-xs font-bold text-slate-300">{promo.brand}</span>
                  {promo.status === 'used' ? (
                    <span className="flex items-center gap-1 text-[11px] font-medium text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-lg">
                      <CheckCircle2 className="w-3 h-3" />
                      مصرف شده
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-[11px] font-medium text-rose-400 bg-rose-500/10 px-2 py-0.5 rounded-lg">
                      <XCircle className="w-3 h-3" />
                      کار نکرد
                    </span>
                  )}
                </div>

                <div className="font-mono text-sm font-bold text-slate-300 bg-slate-950 p-2 rounded-xl text-center line-through opacity-70">
                  {promo.code}
                </div>

                <p className="text-xs text-slate-400 mt-2 line-clamp-2">
                  {promo.discountAmount} - {promo.instructions}
                </p>
              </div>

              <div className="flex items-center gap-2 pt-2 border-t border-slate-800/60">
                <button
                  onClick={() => onRestore(promo.id)}
                  className="flex-1 flex items-center justify-center gap-1.5 bg-slate-800 hover:bg-slate-700 text-amber-300 py-1.5 rounded-xl text-xs font-medium transition cursor-pointer"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>بازگردانی به فعال</span>
                </button>
                <button
                  onClick={() => onViewOriginal(promo)}
                  className="p-1.5 bg-slate-800 hover:bg-slate-700 text-slate-400 rounded-xl transition cursor-pointer"
                  title="مشاهده متن اصلی پیامک"
                >
                  <MessageSquare className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
