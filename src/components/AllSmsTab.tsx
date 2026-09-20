import React, { useState } from 'react';
import { Search, CheckCheck, MessageSquare, Sparkles, Filter, ShieldCheck } from 'lucide-react';
import type { RawSms } from '../types';
import { formatRelativeJalali, formatJalaliDateTime } from '../lib/jalali';

interface Props {
  smsList: RawSms[];
  onMarkAllRead: () => void;
  onAnalyzeSms?: (sms: RawSms) => void;
  unreadCount: number;
}

export const AllSmsTab: React.FC<Props> = ({
  smsList,
  onMarkAllRead,
  onAnalyzeSms,
  unreadCount,
}) => {
  const [query, setQuery] = useState('');
  const [filterType, setFilterType] = useState<'all' | 'promotional' | 'personal' | 'banking'>('all');

  const filtered = smsList.filter((sms) => {
    const matchesType = filterType === 'all' || sms.type === filterType;
    const q = query.trim().toLowerCase();
    const matchesQuery =
      !q ||
      sms.sender.toLowerCase().includes(q) ||
      sms.body.toLowerCase().includes(q) ||
      sms.recipientSim.toLowerCase().includes(q);
    return matchesType && matchesQuery;
  });

  return (
    <div className="space-y-3">
      {/* نوار بالای لیست پیامک‌ها همراه دکمه علامت‌گذاری همه به عنوان خوانده‌شده */}
      <div className="flex items-center justify-between gap-2">
        <div className="relative flex-1">
          <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500 pointer-events-none" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="جستجو در متن یا فرستنده پیامک‌ها…"
            className="w-full bg-white/[0.04] border border-white/6 rounded-xl pr-10 pl-3 py-2 text-[13px] text-slate-100 placeholder:text-slate-600 outline-none focus:border-amber-500/40 transition"
          />
        </div>

        {unreadCount > 0 && (
          <button
            onClick={onMarkAllRead}
            className="shrink-0 px-3 py-2 bg-amber-500/15 hover:bg-amber-500/25 border border-amber-500/30 text-amber-300 rounded-xl text-[12px] font-bold transition flex items-center gap-1.5 cursor-pointer"
            title="علامت‌گذاری همه به عنوان خوانده‌شده"
          >
            <CheckCheck className="w-4 h-4" />
            <span>خوانده‌شدن همه ({unreadCount})</span>
          </button>
        )}
      </div>

      {/* فیلتر نوع پیامک */}
      <div className="flex gap-1.5 overflow-x-auto pb-0.5 [scrollbar-width:none]">
        {(
          [
            { id: 'all', label: 'همه پیامک‌ها', emoji: '📬' },
            { id: 'promotional', label: 'تبلیغاتی و تخفیف', emoji: '🎁' },
            { id: 'banking', label: 'بانکی و مالی', emoji: '💳' },
            { id: 'personal', label: 'شخصی و رمز', emoji: '🔐' },
          ] as const
        ).map((item) => (
          <button
            key={item.id}
            onClick={() => setFilterType(item.id)}
            className={`shrink-0 inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border text-[11px] font-medium transition cursor-pointer ${
              filterType === item.id
                ? 'bg-amber-500 border-amber-500 text-slate-950 font-bold'
                : 'bg-white/[0.03] border-white/6 text-slate-300 hover:bg-white/[0.06]'
            }`}
          >
            <span>{item.emoji}</span>
            <span>{item.label}</span>
          </button>
        ))}
      </div>

      {/* لیست پیامک‌ها */}
      {filtered.length === 0 ? (
        <div className="text-center py-16 px-4 space-y-2 border border-white/5 rounded-2xl bg-white/[0.01]">
          <MessageSquare className="w-8 h-8 text-slate-600 mx-auto" />
          <p className="text-[13px] text-slate-400 font-bold">پیامکی یافت نشد</p>
          <p className="text-[11px] text-slate-500">
            {smsList.length === 0
              ? 'هنوز پیامکی خوانده نشده است. دکمه اسکن را بزنید.'
              : 'با این عبارت یا فیلتر پیامکی پیدا نشد.'}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map((sms) => (
            <div
              key={sms.id}
              className={`p-3.5 rounded-2xl border transition space-y-2 ${
                sms.processed
                  ? 'border-white/6 bg-white/[0.02]'
                  : 'border-amber-500/20 bg-amber-500/[0.04]'
              }`}
            >
              <div className="flex items-center justify-between text-[11px]">
                <div className="flex items-center gap-2">
                  <span className="font-bold text-slate-200 dir-ltr">{sms.sender}</span>
                  <span className="px-2 py-0.5 rounded-md bg-white/5 text-slate-400 text-[10px]">
                    {sms.recipientSim}
                  </span>
                </div>
                {/* تاریخ شمسی */}
                <span className="text-slate-400 font-mono text-[10px]">
                  {formatRelativeJalali(sms.timestamp)}
                </span>
              </div>

              <p className="text-[12px] text-slate-300 leading-relaxed break-words whitespace-pre-wrap">
                {sms.body}
              </p>

              <div className="flex items-center justify-between pt-1 border-t border-white/5 text-[10px]">
                <span
                  className={`px-2 py-0.5 rounded-md ${
                    sms.type === 'promotional'
                      ? 'bg-amber-500/10 text-amber-300'
                      : sms.type === 'banking'
                      ? 'bg-emerald-500/10 text-emerald-300'
                      : 'bg-slate-500/10 text-slate-400'
                  }`}
                >
                  {sms.type === 'promotional'
                    ? 'تبلیغاتی'
                    : sms.type === 'banking'
                    ? 'بانکی'
                    : 'شخصی / رمز'}
                </span>

                {onAnalyzeSms && sms.type === 'promotional' && (
                  <button
                    onClick={() => onAnalyzeSms(sms)}
                    className="inline-flex items-center gap-1 text-amber-400 hover:text-amber-300 font-bold cursor-pointer"
                  >
                    <Sparkles className="w-3 h-3" />
                    تحلیل مجدد با هوش مصنوعی
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
