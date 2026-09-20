import React, { useState } from 'react';
import { Download, Smartphone, Check, X } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';

export const PWAInstallButton: React.FC = () => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);
  const [showInstallGuide, setShowInstallGuide] = useState(false);

  if (isInstalled) {
    return (
      <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-medium">
        <Check className="w-3.5 h-3.5" />
        <span>اپلیکیشن روی گوشی نصب است</span>
      </div>
    );
  }

  if (isInstallable) {
    return (
      <button
        onClick={install}
        className="flex items-center gap-2 bg-gradient-to-r from-amber-500 to-emerald-500 hover:from-amber-400 hover:to-emerald-400 text-slate-950 font-bold px-4 py-2 rounded-xl text-xs md:text-sm shadow-lg shadow-amber-500/20 transition-all duration-200 active:scale-95 cursor-pointer"
        title="نصب نسخه اندروید به صورت اپلیکیشن مستقیم"
      >
        <Smartphone className="w-4 h-4 text-slate-950" />
        <span>نصب مستقیم روی گوشی</span>
      </button>
    );
  }

  // Fallback Guide for Chrome / Android / iOS
  return (
    <>
      <button
        onClick={() => setShowInstallGuide(true)}
        className="flex items-center gap-2 bg-slate-800 hover:bg-slate-700 text-amber-400 border border-amber-500/30 font-medium px-3.5 py-1.5 rounded-xl text-xs transition-all duration-200 cursor-pointer"
      >
        <Download className="w-3.5 h-3.5" />
        <span>دریافت نسخه اندروید (PWA / APK)</span>
      </button>

      {showInstallGuide && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-200">
          <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl relative text-right">
            <button
              onClick={() => setShowInstallGuide(false)}
              className="absolute top-4 left-4 text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-4">
              <div className="p-2.5 bg-amber-500/10 border border-amber-500/20 rounded-xl text-amber-400">
                <Smartphone className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">نصب آسان روی گوشی اندروید</h3>
                <p className="text-xs text-slate-400">بدون نیاز به دانلود فایل سنگین یا مجوزهای خطرناک</p>
              </div>
            </div>

            <div className="space-y-3 text-xs text-slate-300 bg-slate-950/60 p-4 rounded-xl border border-slate-800/80">
              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center font-bold text-[11px] shrink-0 mt-0.5">۱</span>
                <p>در مرورگر گوگل کروم (یا هر مرورگر گوشی)، روی آیکون <strong>سه نقطه (منو)</strong> در گوشه صفحه بزنید.</p>
              </div>
              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center font-bold text-[11px] shrink-0 mt-0.5">۲</span>
                <p>گزینه <strong>«افزودن به صفحه اصلی» (Add to Home screen)</strong> یا <strong>«نصب برنامه» (Install App)</strong> را انتخاب کنید.</p>
              </div>
              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center font-bold text-[11px] shrink-0 mt-0.5">۳</span>
                <p>آیکون برنامه در منوی گوشی شما مثل برنامه‌های بومی بازار و گوگل پلی ظاهر می‌شود و آفلاین هم کار می‌کند.</p>
              </div>
            </div>

            <button
              onClick={() => setShowInstallGuide(false)}
              className="mt-5 w-full bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-bold py-2.5 rounded-xl text-xs transition cursor-pointer"
            >
              متوجه شدم
            </button>
          </div>
        </div>
      )}
    </>
  );
};
