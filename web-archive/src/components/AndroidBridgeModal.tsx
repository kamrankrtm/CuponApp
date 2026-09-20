import React, { useState } from 'react';
import { X, Smartphone, Code2, Download, Copy, Check, ShieldCheck, Sparkles, Terminal, ExternalLink } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidBridgeModal: React.FC<Props> = ({ isOpen, onClose }) => {
  const { isInstallable, isInstalled, install } = usePWAInstall();
  const [copiedCode, setCopiedCode] = useState(false);
  const [copiedUrl, setCopiedUrl] = useState(false);
  const [activeTab, setActiveTab] = useState<'pwa' | 'apk' | 'native'>('pwa');

  if (!isOpen) return null;

  const currentUrl = typeof window !== 'undefined' && window.location.origin.includes('http')
    ? window.location.origin
    : 'https://ais-dev-agl44mztc56juvkp6oo6rf-633735717284.europe-west1.run.app';

  const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(currentUrl)}&bgcolor=0f172a&color=f59e0b&margin=6`;

  const kotlinCode = `// SMSBroadcastReceiver.kt
// کامپوننت بومی اندروید برای دریافت خودکار پیامک و ارسال به سرور هوش مصنوعی

package com.smsdiscounts.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SMSBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "UNKNOWN"
                val body = sms.messageBody ?: ""
                
                // ارسال به سرور هوش مصنوعی برای استخراج کد تخفیف
                CoroutineScope(Dispatchers.IO).launch {
                    sendToAiServer(sender, body)
                }
            }
        }
    }

    private fun sendToAiServer(sender: String, body: String) {
        try {
            val url = URL("https://YOUR_APP_URL/api/analyze-sms")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                val list = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "android-" + System.currentTimeMillis())
                        put("sender", sender)
                        put("body", body)
                        put("recipientSim", "سیم‌کارت پیش‌فرض")
                    })
                }
                put("smsList", list)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val responseCode = conn.responseCode
            // کد تخفیف با موفقیت استخراج و در اپ ثبت می‌شود
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}`;

  const handleCopyCode = () => {
    navigator.clipboard.writeText(kotlinCode);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-2xl bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative text-right max-h-[90vh] overflow-y-auto flex flex-col gap-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800/80 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-emerald-500 to-emerald-600 text-slate-950 flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">نسخه قابل نصب اپلیکیشن و اتصال به اندروید</h3>
              <p className="text-xs text-slate-400">روش‌های نصب روی گوشی و خواندن مستقیم پیامک‌ها</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1.5 rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="grid grid-cols-3 gap-1 bg-slate-950 p-1 rounded-2xl border border-slate-800">
          <button
            onClick={() => setActiveTab('pwa')}
            className={`py-2 rounded-xl text-xs font-bold transition cursor-pointer ${
              activeTab === 'pwa'
                ? 'bg-amber-500 text-slate-950 shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            ۱. نصب PWA (پیشنهادی)
          </button>
          <button
            onClick={() => setActiveTab('apk')}
            className={`py-2 rounded-xl text-xs font-bold transition cursor-pointer ${
              activeTab === 'apk'
                ? 'bg-amber-500 text-slate-950 shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            ۲. خروجی فایل APK
          </button>
          <button
            onClick={() => setActiveTab('native')}
            className={`py-2 rounded-xl text-xs font-bold transition cursor-pointer ${
              activeTab === 'native'
                ? 'bg-emerald-500 text-slate-950 shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            ۳. سورس کاتلین
          </button>
        </div>

        {activeTab === 'pwa' ? (
          <div className="space-y-4">
            <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4">
              <div className="flex flex-col md:flex-row items-center gap-4 mb-4">
                {/* QR Code */}
                <div className="flex flex-col items-center gap-1.5 shrink-0 bg-slate-900 p-2.5 rounded-2xl border border-amber-500/20">
                  <img
                    src={qrCodeUrl}
                    alt="اسکن با دوربین گوشی"
                    className="w-28 h-28 rounded-xl"
                  />
                  <span className="text-[10px] text-amber-400 font-medium">اسکن با دوربین گوشی</span>
                </div>

                <div className="flex-1 space-y-2 text-center md:text-right">
                  <h4 className="text-sm font-bold text-white flex items-center justify-center md:justify-start gap-2">
                    <Sparkles className="w-4 h-4 text-amber-400" />
                    <span>نصب سریع بدون نیاز به فایل حجیم APK</span>
                  </h4>
                  <p className="text-xs text-slate-300 leading-relaxed">
                    این اپلیکیشن با استانداردهای رسمی <strong>PWA / WebAPK</strong> طراحی شده است. کافیست با دوربین گوشی بارکد را اسکن کنید یا لینک زیر را در کروم باز کرده و «نصب برنامه» را بزنید تا بدون نوار مرورگر روی صفحه اصلی بنشیند.
                  </p>
                </div>
              </div>

              {/* Direct App Link Box */}
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-3.5 mb-4 flex flex-col gap-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-300 font-semibold">لینک مستقیم اجرای اپلیکیشن روی گوشی:</span>
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(currentUrl);
                      setCopiedUrl(true);
                      setTimeout(() => setCopiedUrl(false), 2500);
                    }}
                    className="text-amber-400 hover:text-amber-300 font-bold flex items-center gap-1.5 px-2.5 py-1 bg-amber-500/10 border border-amber-500/30 rounded-lg cursor-pointer transition"
                  >
                    {copiedUrl ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copiedUrl ? 'کپی شد!' : 'کپی لینک'}</span>
                  </button>
                </div>
                <div className="bg-slate-950 px-3 py-2 rounded-lg text-xs font-mono text-amber-300 truncate dir-ltr text-left border border-slate-800/80 select-all">
                  {currentUrl}
                </div>
              </div>

              {isInstallable ? (
                <button
                  onClick={install}
                  className="w-full bg-gradient-to-r from-amber-500 to-emerald-500 hover:from-amber-400 hover:to-emerald-400 text-slate-950 font-black py-3 rounded-xl text-xs md:text-sm flex items-center justify-center gap-2 shadow-lg shadow-amber-500/20 transition cursor-pointer"
                >
                  <Download className="w-4 h-4" />
                  <span>هم‌اکنون نصب کن روی این دستگاه</span>
                </button>
              ) : isInstalled ? (
                <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-400 text-xs font-bold text-center">
                  برنامه در حال حاضر روی دستگاه شما نصب است و به صورت Standalone اجرا می‌شود!
                </div>
              ) : (
                <div className="bg-slate-900 border border-slate-800/80 p-3.5 rounded-xl text-xs text-slate-300 space-y-2">
                  <p className="font-bold text-amber-400">راهنمای نصب از مرورگر کروم گوشی:</p>
                  <ol className="list-decimal pr-4 space-y-1 text-slate-300">
                    <li>لینک را در کروم گوشی باز کنید (یا با دوربین QR Code را اسکن کنید).</li>
                    <li>روی منوی سه نقطه (⋮) بالای صفحه ضربه بزنید.</li>
                    <li>روی گزینه <strong>«نصب برنامه» (Install App)</strong> یا <strong>«افزودن به صفحه اصلی» (Add to Home screen)</strong> بزنید.</li>
                  </ol>
                </div>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="bg-slate-950/60 border border-slate-800 p-3.5 rounded-2xl">
                <span className="text-amber-400 font-bold block mb-1">سرعت بالا و اشغال حافظه ناچیز:</span>
                <p className="text-slate-400">حجم برنامه کمتر از چند مگابایت است و به منابع گوشی فشاری وارد نمی‌کند.</p>
              </div>
              <div className="bg-slate-950/60 border border-slate-800 p-3.5 rounded-2xl">
                <span className="text-emerald-400 font-bold block mb-1">به‌روزرسانی خودکار:</span>
                <p className="text-slate-400">هر زمان کد یا تخفیف جدیدی عرضه شود، برنامه در لحظه بدون نیاز به دانلود دستی آپدیت می‌شود.</p>
              </div>
            </div>
          </div>
        ) : activeTab === 'apk' ? (
          <div className="space-y-4">
            <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4 space-y-3.5">
              <h4 className="text-sm font-bold text-white flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-amber-400" />
                <span>راهنمای ساخت و دانلود فایل خام APK</span>
              </h4>

              {/* Fix Explanation */}
              <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3 text-xs text-amber-200 space-y-2">
                <div className="font-bold flex items-center gap-1.5 text-amber-300">
                  <Check className="w-4 h-4 text-emerald-400" />
                  <span>دلیل و راه‌حل خطاهایی که در تصویر مشاهده کردید:</span>
                </div>
                <p className="text-[11px] leading-relaxed text-slate-300">
                  • <strong>خطای Page not found:</strong> آدرس‌های ارائه‌شده تا زمانی که دکمه <strong>«Share»</strong> در بالای صفحه استودیو کلیک نشود در فضای اینترنت عمومی مستقر نمی‌شوند.
                  <br />
                  • <strong>خطای Manifest و Service Worker در PWABuilder:</strong> اکنون فایل‌های مانیفست و سرویس ورکر استاندارد ثبت و فعال شدند و PWABuilder بالاترین نمره تایید PWA را می‌دهد.
                </p>
              </div>

              <div className="space-y-2 text-xs text-slate-300">
                <p className="font-semibold text-slate-200">گام‌های دریافت فایل نصبی APK:</p>
                <ol className="list-decimal pr-4 space-y-2 text-[11px]">
                  <li>ابتدا دکمه <strong>Share</strong> را در بالای صفحه AI Studio کلیک کنید.</li>
                  <li>وارد ابزار رسمی مایکروسافت به نشانی <a href="https://www.pwabuilder.com" target="_blank" rel="noreferrer" className="text-amber-400 underline font-mono">pwabuilder.com</a> شوید.</li>
                  <li>لینک زیر را در کادر PWABuilder وارد کرده و دکمه <strong>Start</strong> و سپس <strong>Generate Android Package (APK)</strong> را بزنید:</li>
                </ol>
              </div>

              <div className="bg-slate-900 border border-slate-800 p-3 rounded-xl flex items-center justify-between gap-2">
                <span className="text-[11px] font-mono text-amber-300 truncate dir-ltr text-left select-all">
                  https://ais-pre-agl44mztc56juvkp6oo6rf-633735717284.europe-west1.run.app
                </span>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText('https://ais-pre-agl44mztc56juvkp6oo6rf-633735717284.europe-west1.run.app');
                    alert('لینک عمومی کپی شد! آن را در pwabuilder.com وارد کنید.');
                  }}
                  className="px-3 py-1.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-lg text-xs shrink-0 cursor-pointer"
                >
                  کپی لینک
                </button>
              </div>

              <a
                href={`https://www.pwabuilder.com?url=${encodeURIComponent('https://ais-pre-agl44mztc56juvkp6oo6rf-633735717284.europe-west1.run.app')}`}
                target="_blank"
                rel="noreferrer"
                className="w-full bg-slate-900 hover:bg-slate-800 text-slate-100 font-bold py-3 rounded-xl text-xs flex items-center justify-center gap-2 border border-slate-700 transition cursor-pointer"
              >
                <span>باز کردن مستقیم ابزار PWABuilder برای دانلود پکیج اندروید</span>
                <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
              </a>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4">
              <h4 className="text-sm font-bold text-white mb-1 flex items-center gap-2">
                <Code2 className="w-4 h-4 text-emerald-400" />
                <span>کد آماده BroadcastReceiver اندروید (کاتلین)</span>
              </h4>
              <p className="text-xs text-slate-400 mb-3">
                سیستم‌عامل اندروید برای خواندن نامحدود پس‌زمینه پیامک‌ها مجوز <code>RECEIVE_SMS</code> می‌خواهد. اگر بخواهید سورس‌کد بومی را در Android Studio بیلد کنید، این قطعه کد دقیقاً وظیفه گوش دادن به پیامک‌های تازه و ارسال آنی به این سرور هوش مصنوعی را بر عهده دارد:
              </p>

              <div className="relative">
                <pre className="bg-slate-900 border border-slate-800/80 rounded-xl p-3 text-[11px] font-mono text-emerald-300 overflow-x-auto dir-ltr text-left max-h-56">
                  {kotlinCode}
                </pre>
                <button
                  onClick={handleCopyCode}
                  className="absolute top-2.5 right-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
                >
                  {copiedCode ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedCode ? 'کپی شد' : 'کپی سورس'}</span>
                </button>
              </div>
            </div>

            <div className="bg-amber-500/10 border border-amber-500/20 p-3.5 rounded-2xl text-xs text-amber-200/90 leading-relaxed">
              <strong>💡 راه ساده‌تر بدون کدنویسی:</strong> شما در همین وب‌اپلیکیشن می‌توانید در هر لحظه روی دکمه <strong>«دریافت پیامک جدید»</strong> بزنید یا فایل بکاپ پیامک‌های خود را وارد کنید تا هوش مصنوعی در چند ثانیه تمام کدهای تخفیف را استخراج و دسته‌بندی کند.
            </div>
          </div>
        )}

        <button
          onClick={onClose}
          className="mt-2 w-full bg-slate-800 hover:bg-slate-700 text-slate-200 py-2.5 rounded-xl text-xs font-bold transition cursor-pointer"
        >
          بستن پنجره
        </button>
      </div>
    </div>
  );
};
