import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowRight, Archive, Settings, ScanLine, Ticket, Check } from 'lucide-react';

import type { PromoCode, RawSms } from './types';
import { PromoCard } from './components/PromoCard';
import { BrandsScreen, groupByBrand } from './components/BrandsScreen';
import { ScanScreen } from './components/ScanScreen';
import { SettingsModal } from './components/SettingsModal';
import { SmsDetailSheet } from './components/SmsDetailSheet';
import { UpdateBanner } from './components/UpdateBanner';

import { DEFAULT_SETTINGS, type AiSettings } from './lib/ai';
import { loadSettings } from './lib/settings';
import {
  fromPendingPromos,
  mergePromoCodes,
  mergeSmsList,
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

type Screen = 'brands' | 'brand' | 'archive' | 'scan';

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
  const [smsList, setSmsList] = useState<RawSms[]>(() => loadStored(STORAGE_SMS, []));
  const [promoCodes, setPromoCodes] = useState<PromoCode[]>(() =>
    loadStored(STORAGE_PROMOS, [])
  );

  const [screen, setScreen] = useState<Screen>('brands');
  const [activeBrand, setActiveBrand] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('all');

  const [settings, setSettings] = useState<AiSettings>(DEFAULT_SETTINGS);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [inspect, setInspect] = useState<PromoCode | null>(null);
  /** پیامکی که کاربر با نگه‌داشتن انگشت در حال دیدن آن است */
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

      // بررسی نسخه جدید؛ خطایش نباید اپ را مختل کند
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
   *
   * بدون این، دکمه سخت‌افزاری یا ژست بازگشت مستقیم اپ را می‌بندد، چون
   * ناوبری اپ داخل React است و WebView تاریخچه‌ای برای برگشتن ندارد.
   * ترتیب اینجا مهم است: اول لایه‌های رویی بسته می‌شوند، بعد صفحه، و
   * فقط در صفحه اصلی اپ بسته می‌شود.
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
   *
   * صف سمت نیتیو با خواندن خالی می‌شود، پس نتیجه بی‌درنگ در همین جا ادغام
   * و ذخیره می‌گردد. هم هنگام باز شدن اپ اجرا می‌شود و هم هر بار که اپ از
   * پس‌زمینه برمی‌گردد، چون ممکن است در این فاصله پیامکی رسیده باشد.
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

  // ── تفکیک کدهای فعال و بایگانی ────────────────────────────────────────
  // انقضا هر بار اینجا از نو سنجیده می‌شود، نه از روی بولین ذخیره‌شده،
  // تا کدها با گذشت زمان خودبه‌خود از لیست فعال بیرون بروند.
  const { active, archived } = useMemo(() => {
    const now = new Date();
    const active: PromoCode[] = [];
    const archived: PromoCode[] = [];
    for (const promo of promoCodes) {
      const expired = isExpiredNow(promo.expiresAt, now);
      if (promo.status === 'active' && !expired) active.push(promo);
      else archived.push(promo);
    }
    return { active, archived };
  }, [promoCodes]);

  const brandCodes = useMemo(() => {
    if (!activeBrand) return [];
    return groupByBrand(active).find((g) => g.brand === activeBrand)?.codes ?? [];
  }, [active, activeBrand]);

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
      showToast('اسکن پیامک فقط در اپ اندروید کار می‌کند.');
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
        showToast(`${result.promoCodes.length} کد تخفیف پیدا شد.`);
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
  const canGoBack = screen === 'brand' || screen === 'archive' || screen === 'scan';
  const title =
    screen === 'brand'
      ? activeBrand ?? ''
      : screen === 'archive'
      ? 'بایگانی'
      : screen === 'scan'
      ? 'اسکن پیامک‌ها'
      : 'تخفیف‌یاب';

  const goBack = () => {
    setScreen('brands');
    setActiveBrand(null);
  };

  return (
    <div className="min-h-screen bg-[#080b12] text-slate-100 flex flex-col">
      {/* نوار بالا — باریک و بدون آمار اضافه */}
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

          {screen === 'brands' && (
            <>
              {active.length > 0 && (
                <span className="text-[11px] font-mono text-slate-500 px-2">
                  {active.length} کد
                </span>
              )}
              <IconButton
                label="بایگانی"
                onClick={() => setScreen('archive')}
                badge={archived.length || undefined}
              >
                <Archive className="w-[18px] h-[18px]" />
              </IconButton>
              <IconButton label="اسکن" onClick={() => setScreen('scan')}>
                <ScanLine className="w-[18px] h-[18px]" />
              </IconButton>
            </>
          )}

          <IconButton label="تنظیمات" onClick={() => setIsSettingsOpen(true)}>
            <Settings className="w-[18px] h-[18px]" />
          </IconButton>
        </div>
      </header>

      <main className="flex-1 max-w-2xl w-full mx-auto px-4 py-4 space-y-4">
        {/* اعلان نسخه جدید */}
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

        {screen === 'archive' && (
          <div className="space-y-3">
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

      {/* نگاه سریع با نگه‌داشتن انگشت؛ اولویت با آن است */}
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
