import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowRight,
  Archive,
  Settings,
  ScanLine,
  Ticket,
  Check,
  CheckCheck,
  MessageSquare,
  Sparkles,
  Trash2,
} from 'lucide-react';

import type { PromoCode, RawSms } from './types';
import { PromoCard } from './components/PromoCard';
import { BrandsScreen, groupByBrand } from './components/BrandsScreen';
import { ScanScreen } from './components/ScanScreen';
import { SettingsModal } from './components/SettingsModal';
import { SmsDetailSheet } from './components/SmsDetailSheet';
import { UpdateBanner } from './components/UpdateBanner';
import { AllSmsTab } from './components/AllSmsTab';

import { DEFAULT_SETTINGS, type AiSettings } from './lib/ai';
import { loadSettings } from './lib/settings';
import {
  fromPendingPromos,
  mergePromoCodes,
  mergeSmsList,
  purgeSensitive,
  runScan,
  type ScanProgress,
} from './lib/scan';
import type { FilterStats } from './lib/smsFilter';
import { isExpiredNow } from './lib/expiry';
import { APP_VERSION, checkForUpdate, type UpdateInfo } from './lib/update';
import {
  checkNotificationPermission,
  checkSmsPermission,
  consumePendingPromos,
  isNativeAndroid,
  requestNotificationPermission,
  requestSmsPermission,
} from './native/smsReader';
import { App as CapacitorApp } from '@capacitor/app';
import { INITIAL_PROMO_CODES, INITIAL_RAW_SMS } from './data/initialPromos';

type Screen = 'brands' | 'brand' | 'all_sms' | 'scan' | 'archive';

const STORAGE_SMS = 'cuponapp.sms';
const STORAGE_PROMOS = 'cuponapp.promos';

function loadStored<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (raw) return JSON.parse(raw) as T;
  } catch {
    /* داده خراب بود؛ از صفر شروع می‌کنیم */
  }
  return fallback;
}

export default function App() {
  const [smsList, setSmsList] = useState<RawSms[]>(() => loadStored(STORAGE_SMS, INITIAL_RAW_SMS));
  const [promoCodes, setPromoCodes] = useState<PromoCode[]>(() => {
    const stored = loadStored<PromoCode[]>(STORAGE_PROMOS, INITIAL_PROMO_CODES);
    return purgeSensitive(stored).kept;
  });

  const [screen, setScreen] = useState<Screen>('brands');
  const [activeBrand, setActiveBrand] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('all');

  const [settings, setSettings] = useState<AiSettings>(DEFAULT_SETTINGS);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [inspect, setInspect] = useState<PromoCode | null>(null);
  const [peeked, setPeeked] = useState<PromoCode | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const [smsPermission, setSmsPermission] = useState<
    'granted' | 'denied' | 'prompt' | 'prompt-with-rationale'
  >('denied');
  const [notificationPermission, setNotificationPermission] = useState<
    'granted' | 'denied' | 'prompt' | 'prompt-with-rationale'
  >('denied');
  const [isScanning, setIsScanning] = useState(false);
  const [progress, setProgress] = useState<ScanProgress | null>(null);
  const [stats, setStats] = useState<FilterStats | null>(null);
  const [errors, setErrors] = useState<string[]>([]);

  const [update, setUpdate] = useState<UpdateInfo | null>(null);
  const [updateDismissed, setUpdateDismissed] = useState(false);

  const isNative = isNativeAndroid();

  const showToast = useCallback((message: string) => {
    setToast(message);
    setTimeout(() => setToast(null), 2600);
  }, []);

  // ── راه‌اندازی ─────────────────────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const saved = await loadSettings();
      if (cancelled) return;
      setSettings(saved);

      if (isNativeAndroid()) {
        const perm = await checkSmsPermission();
        if (!cancelled) setSmsPermission(perm);
        const notif = await checkNotificationPermission();
        if (!cancelled) setNotificationPermission(notif);
      }

      try {
        const found = await checkForUpdate(saved.githubToken || undefined);
        if (!cancelled) setUpdate(found);
      } catch {
        /* آفلاین یا مخزن خصوصی — بی‌صدا رد می‌شویم */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * دکمه بازگشت اندروید.
   */
  useEffect(() => {
    if (!isNative) return;

    let detach: (() => void) | undefined;
    CapacitorApp.addListener('backButton', () => {
      if (isSettingsOpen) {
        setIsSettingsOpen(false);
      } else if (inspect) {
        setInspect(null);
      } else if (screen !== 'brands') {
        setScreen('brands');
        setActiveBrand(null);
      } else {
        CapacitorApp.exitApp();
      }
    }).then((handle) => {
      detach = () => handle.remove();
    });

    return () => detach?.();
  }, [isNative, isSettingsOpen, inspect, screen]);

  /**
   * برداشتن کدهایی که گیرنده پیامک در پس‌زمینه پیدا کرده است.
   */
  const drainBackground = useCallback(async () => {
    const pending = await consumePendingPromos();
    if (pending.length === 0) return;

    const promos = fromPendingPromos(pending);
    setPromoCodes((prev) => mergePromoCodes(prev, promos));
    showToast(`${promos.length} کد تخفیف تازه از پیامک‌های جدید اضافه شد.`);
  }, [showToast]);

  useEffect(() => {
    if (!isNative) return;

    drainBackground();

    let detach: (() => void) | undefined;
    CapacitorApp.addListener('appStateChange', ({ isActive }) => {
      if (isActive) drainBackground();
    }).then((handle) => {
      detach = () => handle.remove();
    });

    return () => detach?.();
  }, [isNative, drainBackground]);

  useEffect(() => {
    localStorage.setItem(STORAGE_SMS, JSON.stringify(smsList));
  }, [smsList]);

  useEffect(() => {
    localStorage.setItem(STORAGE_PROMOS, JSON.stringify(promoCodes));
  }, [promoCodes]);

  // ── تفکیک کدهای فعال و بایگانی (خودکارسازی حذف و آرشیو کدهای منقضی) ──────
  const { active, archived } = useMemo(() => {
    const now = new Date();
    const active: PromoCode[] = [];
    const archived: PromoCode[] = [];
    for (const promo of promoCodes) {
      const expired = isExpiredNow(promo.expiresAt, now);
      if (promo.status === 'active' && !expired) {
        active.push(promo);
      } else {
        // کدهای منقضی‌شده خودکار به بایگانی می‌روند
        archived.push({
          ...promo,
          isExpired: expired || promo.isExpired,
        });
      }
    }
    return { active, archived };
  }, [promoCodes]);

  const brandCodes = useMemo(() => {
    if (!activeBrand) return [];
    return groupByBrand(active).find((g) => g.brand === activeBrand)?.codes ?? [];
  }, [active, activeBrand]);

  // ── علامت‌گذاری همه به عنوان خوانده‌شده (Mark All Read) ────────────────────
  const unreadCount = useMemo(() => {
    return smsList.filter((s) => !s.processed).length;
  }, [smsList]);

  const handleMarkAllRead = useCallback(() => {
    setSmsList((prev) => prev.map((sms) => ({ ...sms, processed: true })));
    showToast('تمامی پیامک‌ها به عنوان خوانده‌شده علامت‌گذاری شدند.');
  }, [showToast]);

  const handlePurgeArchived = useCallback(() => {
    setPromoCodes((prev) => prev.filter((p) => p.status === 'active' && !isExpiredNow(p.expiresAt)));
    showToast('کدهای منقضی و بایگانی‌شده حذف شدند.');
  }, [showToast]);

  // ── اقدام‌ها ──────────────────────────────────────────────────────────
  const setStatus = (id: string, status: PromoCode['status'], message: string) => {
    setPromoCodes((prev) => prev.map((p) => (p.id === id ? { ...p, status } : p)));
    showToast(message);
  };

  const handleRequestPermission = async () => {
    const result = await requestSmsPermission();
    setSmsPermission(result);
    showToast(
      result === 'granted' ? 'دسترسی داده شد. حالا می‌توانی اسکن کنی.' : 'بدون مجوز، اسکن ممکن نیست.'
    );
  };

  const handleRequestNotifications = async () => {
    const result = await requestNotificationPermission();
    setNotificationPermission(result);
    showToast(
      result === 'granted'
        ? 'از این پس کدهای تخفیف تازه را بی‌درنگ اطلاع می‌دهیم.'
        : 'بدون مجوز اعلان، کدها فقط داخل اپ دیده می‌شوند.'
    );
  };

  const handleScan = async () => {
    if (!isNative) {
      showToast('اسکن مستقیم پیامک‌ها در محیط نمونه‌خوان با داده‌های پیش‌فرض انجام می‌شود.');
      return;
    }
    setIsScanning(true);
    setErrors([]);
    setProgress(null);

    try {
      const result = await runScan(settings, { sinceDays: 60, limit: 500, onProgress: setProgress });
      setSmsList((prev) => mergeSmsList(prev, result.smsList));
      setStats(result.stats);
      setErrors(result.errors);

      if (result.promoCodes.length > 0) {
        setPromoCodes((prev) => mergePromoCodes(prev, result.promoCodes));
        setScreen('brands');
        setQuery('');
        setCategory('all');
        showToast(`${result.promoCodes.length} کد تخفیف جدید پیدا شد.`);
      } else if (result.errors.length === 0) {
        showToast('اسکن کامل شد؛ کد جدیدی نبود.');
      }
    } catch (e: any) {
      const message = e?.message ?? String(e);
      setErrors([message]);
      showToast('خطا در اسکن: ' + message);
    } finally {
      setIsScanning(false);
    }
  };

  // ── عنوان و ناوبری ────────────────────────────────────────────────────
  const canGoBack = screen === 'brand' || screen === 'archive' || screen === 'scan' || screen === 'all_sms';
  const title =
    screen === 'brand'
      ? activeBrand ?? ''
      : screen === 'archive'
      ? 'بایگانی و منقضی‌ها'
      : screen === 'scan'
      ? 'اسکن و هوش مصنوعی'
      : screen === 'all_sms'
      ? 'پیامک‌های ورودی'
      : 'تخفیف‌های فعال';

  const goBack = () => {
    setScreen('brands');
    setActiveBrand(null);
  };

  return (
    <div className="min-h-screen bg-[#080b12] text-slate-100 flex flex-col font-sans pb-16">
      {/* نوار بالا */}
      <header className="sticky top-0 z-30 bg-[#080b12]/95 backdrop-blur border-b border-white/6">
        <div className="max-w-2xl mx-auto px-4 h-14 flex items-center gap-2">
          {canGoBack ? (
            <button
              onClick={goBack}
              aria-label="بازگشت"
              className="w-9 h-9 -mr-1.5 rounded-xl flex items-center justify-center text-slate-300 hover:bg-white/[0.06] transition cursor-pointer"
            >
              <ArrowRight className="w-5 h-5" />
            </button>
          ) : (
            <span className="w-9 h-9 rounded-xl bg-amber-500/15 text-amber-400 flex items-center justify-center">
              <Ticket className="w-[18px] h-[18px]" />
            </span>
          )}

          <h1 className="text-[15px] font-bold text-white truncate flex-1">{title}</h1>

          {/* دکمه علامت همه به عنوان خوانده‌شده */}
          {unreadCount > 0 && (
            <button
              onClick={handleMarkAllRead}
              className="px-2.5 py-1 bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 border border-amber-500/25 rounded-lg text-[11px] font-bold transition flex items-center gap-1 cursor-pointer"
              title="علامت‌گذاری همه به عنوان خوانده‌شده"
            >
              <CheckCheck className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">خوانده‌شدن همه</span>
            </button>
          )}

          <IconButton label="تنظیمات" onClick={() => setIsSettingsOpen(true)}>
            <Settings className="w-[18px] h-[18px]" />
          </IconButton>
        </div>
      </header>

      {/* تب‌های اصلی برنامه‌ریزی‌شده */}
      <nav className="bg-[#0b101c] border-b border-white/6 sticky top-14 z-20">
        <div className="max-w-2xl mx-auto px-4 flex items-center justify-around h-11 text-[12px] font-medium">
          <button
            onClick={() => {
              setScreen('brands');
              setActiveBrand(null);
            }}
            className={`flex-1 h-full flex items-center justify-center gap-1.5 border-b-2 transition cursor-pointer ${
              screen === 'brands' || screen === 'brand'
                ? 'border-amber-500 text-amber-400 font-bold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Ticket className="w-4 h-4" />
            <span>کدهای تخفیف</span>
            {active.length > 0 && (
              <span className="bg-amber-500/20 text-amber-300 text-[10px] font-mono px-1.5 py-0.2 rounded-full">
                {active.length}
              </span>
            )}
          </button>

          <button
            onClick={() => setScreen('all_sms')}
            className={`flex-1 h-full flex items-center justify-center gap-1.5 border-b-2 transition cursor-pointer ${
              screen === 'all_sms'
                ? 'border-amber-500 text-amber-400 font-bold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <MessageSquare className="w-4 h-4" />
            <span>پیامک‌ها</span>
            {unreadCount > 0 && (
              <span className="bg-rose-500/20 text-rose-300 text-[10px] font-mono px-1.5 py-0.2 rounded-full">
                {unreadCount}
              </span>
            )}
          </button>

          <button
            onClick={() => setScreen('scan')}
            className={`flex-1 h-full flex items-center justify-center gap-1.5 border-b-2 transition cursor-pointer ${
              screen === 'scan'
                ? 'border-amber-500 text-amber-400 font-bold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Sparkles className="w-4 h-4" />
            <span>هوش مصنوعی</span>
          </button>

          <button
            onClick={() => setScreen('archive')}
            className={`flex-1 h-full flex items-center justify-center gap-1.5 border-b-2 transition cursor-pointer ${
              screen === 'archive'
                ? 'border-amber-500 text-amber-400 font-bold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Archive className="w-4 h-4" />
            <span>بایگانی</span>
            {archived.length > 0 && (
              <span className="bg-slate-700 text-slate-300 text-[10px] font-mono px-1.5 py-0.2 rounded-full">
                {archived.length}
              </span>
            )}
          </button>
        </div>
      </nav>

      {/* محتوای اصلی */}
      <main className="flex-1 max-w-2xl w-full mx-auto px-4 py-4 space-y-4">
        {!updateDismissed && screen === 'brands' && (
          <UpdateBanner update={update} onDismiss={() => setUpdateDismissed(true)} />
        )}

        {screen === 'brands' && (
          <BrandsScreen
            codes={active}
            query={query}
            onQueryChange={setQuery}
            category={category}
            onCategoryChange={setCategory}
            onSelectBrand={(brand) => {
              setActiveBrand(brand);
              setScreen('brand');
            }}
            onScan={() => setScreen('scan')}
          />
        )}

        {screen === 'brand' && (
          <div className="space-y-3">
            {brandCodes.map((promo) => (
              <PromoCard
                key={promo.id}
                promo={promo}
                onUse={(id) => setStatus(id, 'used', 'به بایگانی منتقل شد.')}
                onInvalid={(id) => setStatus(id, 'invalid', 'به عنوان «کار نکرد» ثبت شد.')}
                onPeekSms={setPeeked}
              />
            ))}
          </div>
        )}

        {screen === 'all_sms' && (
          <AllSmsTab
            smsList={smsList}
            onMarkAllRead={handleMarkAllRead}
            unreadCount={unreadCount}
          />
        )}

        {screen === 'archive' && (
          <div className="space-y-3">
            {archived.length > 0 && (
              <div className="flex justify-end pb-1">
                <button
                  onClick={handlePurgeArchived}
                  className="px-3 py-1.5 bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border border-rose-500/20 rounded-xl text-[11px] font-medium transition flex items-center gap-1.5 cursor-pointer"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>پاک کردن کامل منقضی‌ها</span>
                </button>
              </div>
            )}

            {archived.length === 0 ? (
              <p className="text-center text-[13px] text-slate-500 py-16">بایگانی خالی است.</p>
            ) : (
              archived.map((promo) => (
                <PromoCard
                  key={promo.id}
                  promo={promo}
                  variant="archived"
                  onRestore={(id) => setStatus(id, 'active', 'به لیست فعال برگشت.')}
                  onPeekSms={setPeeked}
                />
              ))
            )}
          </div>
        )}

        {screen === 'scan' && (
          <ScanScreen
            isNative={isNative}
            hasApiKey={!!settings.apiKey}
            permission={smsPermission}
            isScanning={isScanning}
            progress={progress}
            lastStats={stats}
            errors={errors}
            notificationPermission={notificationPermission}
            onRequestNotifications={handleRequestNotifications}
            onRequestPermission={handleRequestPermission}
            onScan={handleScan}
            onOpenSettings={() => setIsSettingsOpen(true)}
          />
        )}
      </main>

      <footer className="py-4 text-center text-[10px] text-slate-700">
        نسخه {APP_VERSION}
      </footer>

      {/* نگاه سریع با نگه‌داشتن انگشت */}
      <SmsDetailSheet promo={peeked} peek onClose={() => setPeeked(null)} />
      {!peeked && <SmsDetailSheet promo={inspect} onClose={() => setInspect(null)} />}

      <SettingsModal
        isOpen={isSettingsOpen}
        settings={settings}
        onClose={() => setIsSettingsOpen(false)}
        onSave={setSettings}
      />

      {toast && (
        <div className="fixed bottom-6 inset-x-4 z-50 flex justify-center pointer-events-none">
          <div className="bg-slate-800 border border-white/10 text-slate-100 px-4 py-2.5 rounded-xl shadow-2xl flex items-center gap-2 text-[12px]">
            <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
            {toast}
          </div>
        </div>
      )}
    </div>
  );
}

const IconButton: React.FC<{
  label: string;
  onClick: () => void;
  badge?: number;
  children: React.ReactNode;
}> = ({ label, onClick, badge, children }) => (
  <button
    onClick={onClick}
    aria-label={label}
    className="relative w-9 h-9 rounded-xl flex items-center justify-center text-slate-400 hover:text-slate-100 hover:bg-white/[0.06] transition cursor-pointer"
  >
    {children}
    {badge ? (
      <span className="absolute -top-0.5 -left-0.5 min-w-4 h-4 px-1 rounded-full bg-slate-700 text-slate-300 text-[9px] font-mono flex items-center justify-center">
        {badge > 99 ? '۹۹+' : badge}
      </span>
    ) : null}
  </button>
);
