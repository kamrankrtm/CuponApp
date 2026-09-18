import React from 'react';
import { X, Smartphone, Calendar, User } from 'lucide-react';
import type { PromoCode } from '../types';

interface Props {
  promo: PromoCode | null;
  onClose: () => void;
  /**
   * حالت «نگاه سریع»: تا وقتی انگشت روی دکمه است باز می‌ماند.
   * در این حالت دکمه بستن و لمس پس‌زمینه معنا ندارند.
   */
  peek?: boolean;
}

/** نمایش متن خام پیامکی که این کد از آن استخراج شده */
export const SmsDetailSheet: React.FC<Props> = ({ promo, onClose, peek = false }) => {
  if (!promo) return null;

  const received = new Date(promo.receivedAt);
  const receivedText = Number.isNaN(received.getTime())
    ? promo.receivedAt
    : new Intl.DateTimeFormat('fa-IR', {
        day: 'numeric',
        month: 'long',
        hour: '2-digit',
        minute: '2-digit',
      }).format(received);

  return (
    <div
      className={`fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/70 backdrop-blur-sm ${
        peek ? 'pointer-events-none' : ''
      }`}
      onClick={peek ? undefined : onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full sm:max-w-md bg-[#0e1420] border-t sm:border border-white/8 sm:rounded-3xl rounded-t-3xl max-h-[85vh] overflow-y-auto"
      >
        <div className="sticky top-0 bg-[#0e1420] flex items-center justify-between px-5 py-4 border-b border-white/6">
          <h3 className="text-[14px] font-bold text-white">متن اصلی پیامک</h3>
          {peek ? (
            <span className="text-[10px] text-slate-500">انگشت را بردار تا بسته شود</span>
          ) : (
            <button
              onClick={onClose}
              className="text-slate-500 hover:text-white p-1 rounded-lg transition cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          )}
        </div>

        <div className="p-5 space-y-4">
          {/* فرادادهٔ پیامک */}
          <div className="grid grid-cols-2 gap-2">
            <Meta icon={<User className="w-3.5 h-3.5" />} label="فرستنده" value={promo.sender} ltr />
            <Meta
              icon={<Smartphone className="w-3.5 h-3.5" />}
              label="سیم‌کارت"
              value={promo.recipientSim}
            />
          </div>
          <Meta
            icon={<Calendar className="w-3.5 h-3.5" />}
            label="زمان دریافت"
            value={receivedText}
          />

          {/* متن خام */}
          <div className="rounded-2xl border border-white/6 bg-white/[0.02] p-4">
            <p className="text-[13px] text-slate-200 leading-loose whitespace-pre-wrap break-words">
              {promo.originalSmsBody}
            </p>
          </div>

          {!peek && (
            <button
              onClick={onClose}
              className="w-full py-3 bg-white/[0.06] hover:bg-white/[0.1] text-slate-200 rounded-xl text-[13px] font-medium transition cursor-pointer"
            >
              بستن
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

const Meta: React.FC<{
  icon: React.ReactNode;
  label: string;
  value: string;
  ltr?: boolean;
}> = ({ icon, label, value, ltr }) => (
  <div className="rounded-xl border border-white/6 bg-white/[0.02] px-3 py-2.5">
    <div className="flex items-center gap-1.5 text-slate-500 text-[10px]">
      {icon}
      {label}
    </div>
    <div
      dir={ltr ? 'ltr' : undefined}
      className={`text-[12px] text-slate-200 mt-1 truncate ${ltr ? 'text-right font-mono' : ''}`}
    >
      {value}
    </div>
  </div>
);
