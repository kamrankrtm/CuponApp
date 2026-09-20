import React from 'react';
import { 
  Sparkles, 
  MessageSquarePlus, 
  Smartphone, 
  ShieldCheck, 
  Flame, 
  Layers, 
  Zap,
  RefreshCw
} from 'lucide-react';
import { PWAInstallButton } from './PWAInstallButton';
import { AnalysisSummary } from '../types';

interface Props {
  summary: AnalysisSummary;
  onOpenNewSms: () => void;
  onOpenAndroidBridge: () => void;
  onRescanAll: () => void;
  isScanning: boolean;
}

export const Header: React.FC<Props> = ({
  summary,
  onOpenNewSms,
  onOpenAndroidBridge,
  onRescanAll,
  isScanning,
}) => {
  return (
    <header className="border-b border-slate-800/80 bg-slate-950/80 backdrop-blur-xl sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 py-3 md:py-4">
        {/* Top bar with Branding and Actions */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          {/* Brand Logo & Name */}
          <div className="flex items-center gap-3 self-start sm:self-center">
            <div className="relative">
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-amber-500 via-rose-500 to-indigo-600 p-0.5 shadow-lg shadow-amber-500/20 flex items-center justify-center overflow-hidden">
                <img
                  src="/icon.svg"
                  alt="تخفیف‌یاب"
                  className="w-full h-full object-cover rounded-[14px]"
                  referrerPolicy="no-referrer"
                />
              </div>
              <span className="absolute -bottom-1 -left-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-emerald-500 ring-2 ring-slate-950">
                <span className="h-1.5 w-1.5 rounded-full bg-white animate-pulse" />
              </span>
            </div>

            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg md:text-xl font-black tracking-tight text-white flex items-center gap-1.5">
                  <span>تخفیف‌یاب هوشمند پیامک</span>
                  <span className="text-[10px] bg-amber-500/20 border border-amber-500/40 text-amber-400 font-bold px-2 py-0.5 rounded-md">
                    AI Powered
                  </span>
                </h1>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                جداسازی خودکار پیامک‌های شخصی از تبلیغاتی و دسته‌بندی کدهای تخفیف فعال
              </p>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto justify-start sm:justify-end">
            {/* New SMS / Live Analyzer */}
            <button
              onClick={onOpenNewSms}
              className="flex items-center gap-1.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-bold px-3.5 py-2 rounded-xl text-xs shadow-md shadow-amber-500/20 transition active:scale-95 cursor-pointer"
            >
              <MessageSquarePlus className="w-4 h-4" />
              <span>دریافت پیامک جدید</span>
            </button>

            {/* Rescan / Sync button */}
            <button
              onClick={onRescanAll}
              disabled={isScanning}
              className="flex items-center gap-1.5 bg-slate-900 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 px-3 py-2 rounded-xl text-xs font-semibold transition active:scale-95 disabled:opacity-50 cursor-pointer"
              title="بررسی و پالایش مجدد پیامک‌های ۲ ماهه اخیر با هوش مصنوعی"
            >
              <RefreshCw className={`w-3.5 h-3.5 text-amber-400 ${isScanning ? 'animate-spin' : ''}`} />
              <span className="hidden sm:inline">همگام‌سازی پیامک‌ها</span>
            </button>

            {/* Android APK / Native code modal */}
            <button
              onClick={onOpenAndroidBridge}
              className="flex items-center gap-1.5 bg-slate-900 hover:bg-slate-800 text-emerald-400 border border-emerald-500/30 px-3 py-2 rounded-xl text-xs font-semibold transition cursor-pointer"
              title="نسخه اندروید، وب‌اپ و اتصال به پیامک گوشی"
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span>نسخه اندروید</span>
            </button>

            {/* PWA Install Button */}
            <PWAInstallButton />
          </div>
        </div>

        {/* Stats Strip */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mt-3 pt-3 border-t border-slate-900/80">
          <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-2.5 flex items-center justify-between px-3">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Layers className="w-4 h-4 text-slate-400" />
              <span>کل پیامک‌های ۲ ماه:</span>
            </div>
            <span className="font-mono font-bold text-slate-200 text-sm">{summary.totalSms}</span>
          </div>

          <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-2.5 flex items-center justify-between px-3">
            <div className="flex items-center gap-2 text-xs text-amber-400">
              <Flame className="w-4 h-4" />
              <span>کدهای تخفیف فعال:</span>
            </div>
            <span className="font-mono font-bold text-amber-400 text-sm">{summary.activePromoCount}</span>
          </div>

          <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-2.5 flex items-center justify-between px-3">
            <div className="flex items-center gap-2 text-xs text-blue-400">
              <ShieldCheck className="w-4 h-4" />
              <span>پیام‌های شخصی تفکیک‌شده:</span>
            </div>
            <span className="font-mono font-bold text-blue-400 text-sm">{summary.personalCount}</span>
          </div>

          <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-2.5 flex items-center justify-between px-3">
            <div className="flex items-center gap-2 text-xs text-emerald-400">
              <Sparkles className="w-4 h-4" />
              <span>سیم‌کارت‌های متصل:</span>
            </div>
            <span className="text-[11px] font-bold text-slate-300">سیم ۱ و ۲ فعال</span>
          </div>
        </div>
      </div>
    </header>
  );
};
