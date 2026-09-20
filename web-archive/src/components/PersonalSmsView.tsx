import React from 'react';
import { ShieldCheck, UserCheck, Smartphone, Calendar, MessageSquare, Lock } from 'lucide-react';
import { RawSms } from '../types';

interface Props {
  messages: RawSms[];
}

export const PersonalSmsView: React.FC<Props> = ({ messages }) => {
  return (
    <div className="flex flex-col gap-4">
      {/* Privacy Notice Banner */}
      <div className="bg-blue-500/10 border border-blue-500/20 rounded-3xl p-4 md:p-5 flex items-start gap-3.5 text-right">
        <div className="p-2.5 bg-blue-500/20 border border-blue-500/30 rounded-2xl text-blue-400 shrink-0 mt-0.5">
          <Lock className="w-5 h-5" />
        </div>
        <div>
          <h4 className="text-sm font-bold text-white mb-1">
            پیامک‌های شخصی و خانوادگی (تفکیک و محافظت‌شده)
          </h4>
          <p className="text-xs text-slate-300 leading-relaxed">
            بر اساس شماره فرستنده (پیش‌شماره‌های تلفن همراه شخصی مثل ۰۹۱۲، ۰۹۳۵ و عدم وجود ساختار بازرگانی)، این پیام‌ها از پیامک‌های تبلیغاتی جدا شده و هیچ‌گونه کوپن تبلیغاتی از آن‌ها استخراج نمی‌شود.
          </p>
        </div>
      </div>

      {messages.length === 0 ? (
        <div className="text-center py-12 bg-slate-900/60 border border-slate-800 rounded-3xl p-8">
          <p className="text-sm text-slate-400">هیچ پیامک شخصی در صندوق ورودی وجود ندارد.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
          {messages.map((sms) => (
            <div
              key={sms.id}
              className="bg-slate-900/80 border border-slate-800/90 rounded-3xl p-4 shadow-md flex flex-col justify-between gap-3 text-right"
            >
              <div>
                <div className="flex items-center justify-between gap-2 border-b border-slate-800/80 pb-2.5 mb-2.5">
                  <div className="flex items-center gap-1.5 text-xs text-blue-400 font-bold">
                    <UserCheck className="w-4 h-4" />
                    <span className="font-mono dir-ltr">{sms.sender}</span>
                  </div>
                  <div className="flex items-center gap-1 text-[11px] text-slate-400 bg-slate-950 px-2 py-0.5 rounded-lg border border-slate-800">
                    <Smartphone className="w-3 h-3 text-emerald-400" />
                    <span>{sms.recipientSim}</span>
                  </div>
                </div>

                <p className="text-xs md:text-sm text-slate-200 leading-relaxed font-medium">
                  {sms.body}
                </p>
              </div>

              <div className="flex items-center justify-between text-[11px] text-slate-500 pt-2 border-t border-slate-800/60">
                <div className="flex items-center gap-1">
                  <Calendar className="w-3 h-3" />
                  <span>
                    {new Date(sms.timestamp).toLocaleDateString('fa-IR', {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                </div>
                <span className="text-blue-400/80 font-medium">پیامک شخصی</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
