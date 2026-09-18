/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useMemo } from 'react';
import { 
  Flame, 
  Sparkles, 
  MessageSquarePlus, 
  Smartphone, 
  ShieldCheck, 
  Info, 
  Check, 
  Inbox, 
  SlidersHorizontal,
  RefreshCw,
  Settings
} from 'lucide-react';
import { RawSms, PromoCode, AnalysisSummary } from './types';
import { INITIAL_SMS_DATA } from './data/mockSms';
import { INITIAL_PROMO_CODES } from './data/initialPromos';
import { Header } from './components/Header';
import { FilterBar } from './components/FilterBar';
import { PromoCard } from './components/PromoCard';
import { OriginalSmsModal } from './components/OriginalSmsModal';
import { NewSmsDrawer } from './components/NewSmsDrawer';
import { AndroidBridgeModal } from './components/AndroidBridgeModal';
import { PersonalSmsView } from './components/PersonalSmsView';
import { UsedCodesView } from './components/UsedCodesView';
import { ScanPanel } from './components/ScanPanel';
import { SettingsModal } from './components/SettingsModal';
import { DEFAULT_SETTINGS, type AiSettings } from './lib/ai';
import { loadSettings } from './lib/settings';
import { mergePromoCodes, mergeSmsList, runScan, type ScanProgress } from './lib/scan';
import type { FilterStats } from './lib/smsFilter';
import {
  checkSmsPermission,
  isNativeAndroid,
  requestSmsPermission,
} from './native/smsReader';

export default function App() {
  // Local storage persisted state
  const [smsList, setSmsList] = useState<RawSms[]>(() => {
    try {
      const saved = localStorage.getItem('sms_discount_sms_list');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.error(e);
    }
    // روی گوشی داده نمونه بارگذاری نمی‌شود؛ منبع داده، صندوق پیامک واقعی است
    return isNativeAndroid() ? [] : INITIAL_SMS_DATA;
  });

  const [promoCodes, setPromoCodes] = useState<PromoCode[]>(() => {
    try {
      const saved = localStorage.getItem('sms_discount_promo_codes');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.error(e);
    }
    return isNativeAndroid() ? [] : INITIAL_PROMO_CODES;
  });

  // Filters & Tabs
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedBrand, setSelectedBrand] = useState('all');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [selectedSim, setSelectedSim] = useState('all');
  const [activeTab, setActiveTab] = useState<'active' | 'personal' | 'used'>('active');

  // Modals
  const [inspectPromo, setInspectPromo] = useState<PromoCode | null>(null);
  const [isNewSmsOpen, setIsNewSmsOpen] = useState(false);
  const [isAndroidBridgeOpen, setIsAndroidBridgeOpen] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  // تنظیمات هوش مصنوعی و وضعیت اسکن نیتیو
  const [aiSettings, setAiSettings] = useState<AiSettings>(DEFAULT_SETTINGS);
  const [smsPermission, setSmsPermission] = useState<
    'granted' | 'denied' | 'prompt' | 'prompt-with-rationale'
  >('denied');
  const [scanProgress, setScanProgress] = useState<ScanProgress | null>(null);
  const [scanStats, setScanStats] = useState<FilterStats | null>(null);
  const [scanErrors, setScanErrors] = useState<string[]>([]);

  const isNative = isNativeAndroid();
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'info' } | null>(null);

  // بارگذاری تنظیمات ذخیره‌شده و وضعیت مجوز هنگام باز شدن اپ
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const saved = await loadSettings();
      if (!cancelled) setAiSettings(saved);
      if (isNativeAndroid()) {
        const perm = await checkSmsPermission();
        if (!cancelled) setSmsPermission(perm);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Sync to local storage
  useEffect(() => {
    localStorage.setItem('sms_discount_sms_list', JSON.stringify(smsList));
  }, [smsList]);

  useEffect(() => {
    localStorage.setItem('sms_discount_promo_codes', JSON.stringify(promoCodes));
  }, [promoCodes]);

  const showToast = (message: string, type: 'success' | 'info' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  // Distinct brands for filter pills
  const availableBrands = useMemo(() => {
    const brands = new Set<string>();
    promoCodes.forEach((p) => {
      if (p.brand) brands.add(p.brand);
    });
    return Array.from(brands);
  }, [promoCodes]);

  // Handle Mark as Used
  const handleMarkUsed = (id: string) => {
    setPromoCodes((prev) =>
      prev.map((p) => (p.id === id ? { ...p, status: 'used' } : p))
    );
    showToast('کد تخفیف به عنوان «استفاده شد» علامت‌گذاری و از لیست فعال حذف شد.');
  };

  // Handle Mark as Invalid / Not working
  const handleMarkInvalid = (id: string) => {
    setPromoCodes((prev) =>
      prev.map((p) => (p.id === id ? { ...p, status: 'invalid' } : p))
    );
    showToast('کد تخفیف به عنوان «کار نمی‌کنه» گزارش و از لیست فعال خارج شد.', 'info');
  };

  // Restore back to active
  const handleRestore = (id: string) => {
    setPromoCodes((prev) =>
      prev.map((p) => (p.id === id ? { ...p, status: 'active' } : p))
    );
    showToast('کد تخفیف مجدداً به لیست کدهای فعال بازگردانده شد.');
  };

  // Handle new incoming SMS processed by AI
  const handleSmsProcessed = (newSms: RawSms, newPromo: PromoCode | null) => {
    setSmsList((prev) => [newSms, ...prev]);
    if (newPromo) {
      setPromoCodes((prev) => [newPromo, ...prev]);
      setActiveTab('active');
      setSelectedBrand('all');
      setSelectedCategory('all');
      showToast(`کد تخفیف جدید ${newPromo.brand} با موفقیت در اپ فعال شد!`);
    }
  };

  // درخواست مجوز خواندن پیامک از سیستم‌عامل
  const handleRequestPermission = async () => {
    const result = await requestSmsPermission();
    setSmsPermission(result);
    if (result === 'granted') {
      showToast('دسترسی خواندن پیامک داده شد. حالا می‌توانید اسکن کنید.');
    } else {
      showToast('بدون مجوز خواندن پیامک، اسکن خودکار ممکن نیست.', 'info');
    }
  };

  /**
   * اسکن واقعی صندوق پیامک گوشی.
   *
   * مسیر: خواندن نیتیو ← فیلتر محلی (حذف پیام‌های شخصی و بانکی) ←
   * ارسال فقط پیامک‌های تبلیغاتی به هوش مصنوعی.
   */
  const handleScan = async () => {
    if (!isNative) {
      showToast('اسکن پیامک فقط در نسخه اندروید اپ در دسترس است.', 'info');
      return;
    }

    setIsScanning(true);
    setScanErrors([]);
    setScanProgress(null);

    try {
      const result = await runScan(aiSettings, {
        sinceDays: 60,
        limit: 500,
        onProgress: setScanProgress,
      });

      setSmsList((prev) => mergeSmsList(prev, result.smsList));
      setScanStats(result.stats);
      setScanErrors(result.errors);

      if (result.promoCodes.length > 0) {
        setPromoCodes((prev) => mergePromoCodes(prev, result.promoCodes));
        setActiveTab('active');
        setSelectedBrand('all');
        setSelectedCategory('all');
        showToast(`${result.promoCodes.length} کد تخفیف از پیامک‌های شما استخراج شد.`);
      } else if (result.errors.length > 0) {
        showToast('اسکن انجام شد ولی تحلیل هوش مصنوعی با خطا مواجه شد.', 'info');
      } else {
        showToast('اسکن کامل شد؛ کد تخفیف جدیدی پیدا نشد.', 'info');
      }
    } catch (e: any) {
      const message = e?.message ?? String(e);
      setScanErrors([message]);
      showToast('خطا در اسکن پیامک‌ها: ' + message, 'info');
    } finally {
      setIsScanning(false);
    }
  };

  // Active non-expired codes
  const activePromoCodes = useMemo(() => {
    return promoCodes.filter(
      (p) => p.status === 'active' && !p.isExpired
    );
  }, [promoCodes]);

  // Used or Invalid codes
  const usedPromoCodes = useMemo(() => {
    return promoCodes.filter(
      (p) => p.status === 'used' || p.status === 'invalid' || p.isExpired
    );
  }, [promoCodes]);

  // Personal SMS messages
  const personalSmsList = useMemo(() => {
    return smsList.filter((s) => s.type === 'personal');
  }, [smsList]);

  // Filtered active codes based on query, brand, category, sim
  const filteredActiveCodes = useMemo(() => {
    return activePromoCodes.filter((p) => {
      // Brand match
      if (selectedBrand !== 'all' && p.brand !== selectedBrand) {
        return false;
      }
      // Category match
      if (selectedCategory !== 'all' && p.categorySlug !== selectedCategory) {
        return false;
      }
      // SIM match
      if (selectedSim !== 'all' && !p.recipientSim.includes(selectedSim)) {
        return false;
      }
      // Search query match
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const inBrand = p.brand.toLowerCase().includes(q);
        const inCode = p.code.toLowerCase().includes(q);
        const inDesc = p.description.toLowerCase().includes(q);
        const inAmount = p.discountAmount.toLowerCase().includes(q);
        const inInstructions = p.instructions.toLowerCase().includes(q);
        return inBrand || inCode || inDesc || inAmount || inInstructions;
      }
      return true;
    });
  }, [activePromoCodes, selectedBrand, selectedCategory, selectedSim, searchQuery]);

  // Summary stats
  const summary: AnalysisSummary = {
    totalSms: smsList.length,
    promotionalCount: smsList.filter((s) => s.type === 'promotional').length,
    personalCount: personalSmsList.length,
    bankingCount: smsList.filter((s) => s.type === 'banking').length,
    extractedPromoCount: promoCodes.length,
    activePromoCount: activePromoCodes.length,
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col selection:bg-amber-500 selection:text-black">
      {/* App Header */}
      <Header
        summary={summary}
        onOpenNewSms={() => setIsNewSmsOpen(true)}
        onOpenAndroidBridge={() => setIsAndroidBridgeOpen(true)}
        onRescanAll={handleScan}
        isScanning={isScanning}
      />

      {/* Main Content Area */}
      <main className="max-w-7xl mx-auto px-4 py-6 flex-1 w-full space-y-6">
        {/* اسکن پیامک‌های گوشی */}
        <ScanPanel
          isNative={isNative}
          hasApiKey={!!aiSettings.apiKey}
          permission={smsPermission}
          isScanning={isScanning}
          progress={scanProgress}
          lastStats={scanStats}
          errors={scanErrors}
          onRequestPermission={handleRequestPermission}
          onScan={handleScan}
          onOpenSettings={() => setIsSettingsOpen(true)}
        />

        {/* Brand/Category/Search Filter Bar */}
        <FilterBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          selectedBrand={selectedBrand}
          onBrandChange={setSelectedBrand}
          selectedCategory={selectedCategory}
          onCategoryChange={setSelectedCategory}
          selectedSim={selectedSim}
          onSimChange={setSelectedSim}
          availableBrands={availableBrands}
          activeTab={activeTab}
          onTabChange={setActiveTab}
          activeCount={activePromoCodes.length}
          personalCount={personalSmsList.length}
          usedCount={usedPromoCodes.length}
        />

        {/* Tab 1: Active Promo Codes */}
        {activeTab === 'active' && (
          <div>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Flame className="w-5 h-5 text-amber-500" />
                <h2 className="text-base md:text-lg font-bold text-white">
                  کدهای تخفیف معتبر آماده استفاده
                </h2>
                <span className="text-xs bg-slate-900 border border-slate-800 text-slate-400 px-2.5 py-0.5 rounded-full font-mono">
                  {filteredActiveCodes.length} کد یافت شد
                </span>
              </div>

              {selectedBrand !== 'all' && (
                <button
                  onClick={() => setSelectedBrand('all')}
                  className="text-xs text-amber-400 hover:text-amber-300 transition cursor-pointer"
                >
                  حذف فیلتر برند ({selectedBrand}) ✕
                </button>
              )}
            </div>

            {filteredActiveCodes.length === 0 ? (
              <div className="text-center py-16 bg-slate-900/40 border border-slate-800/80 rounded-3xl p-8 flex flex-col items-center justify-center gap-3">
                <div className="w-14 h-14 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-500">
                  <Inbox className="w-7 h-7" />
                </div>
                <h3 className="text-sm font-bold text-slate-300">هیچ کد تخفیف فعالی با این فیلترها پیدا نشد</h3>
                <p className="text-xs text-slate-500 max-w-sm">
                  می‌توانید فیلترها را پاک کنید یا روی «دریافت پیامک جدید» بزنید تا پیامک‌های تازه اضافه و پردازش شوند.
                </p>
                <div className="flex items-center gap-2 mt-2">
                  <button
                    onClick={() => {
                      setSelectedBrand('all');
                      setSelectedCategory('all');
                      setSelectedSim('all');
                      setSearchQuery('');
                    }}
                    className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold transition cursor-pointer"
                  >
                    پاکسازی همه فیلترها
                  </button>
                  <button
                    onClick={() => setIsNewSmsOpen(true)}
                    className="px-4 py-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl text-xs transition cursor-pointer"
                  >
                    ثبت و تحلیل پیامک جدید
                  </button>
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {filteredActiveCodes.map((promo) => (
                  <PromoCard
                    key={promo.id}
                    promo={promo}
                    onMarkUsed={handleMarkUsed}
                    onMarkInvalid={handleMarkInvalid}
                    onViewOriginal={setInspectPromo}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Personal Messages Separated for Privacy */}
        {activeTab === 'personal' && (
          <PersonalSmsView messages={personalSmsList} />
        )}

        {/* Tab 3: Used or Invalid Codes Archive */}
        {activeTab === 'used' && (
          <UsedCodesView
            codes={usedPromoCodes}
            onRestore={handleRestore}
            onViewOriginal={setInspectPromo}
          />
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-900 bg-slate-950/90 py-6 mt-12 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            <span>استخراج هوشمند کد تخفیف از پیامک‌های گوشی، با پردازش محلی حریم خصوصی</span>
          </div>
          <div className="flex items-center gap-4 text-slate-400">
            <button
              onClick={() => setIsAndroidBridgeOpen(true)}
              className="hover:text-amber-400 transition cursor-pointer"
            >
              راهنمای نصب اندروید
            </button>
            <span>•</span>
            <button
              onClick={() => setIsNewSmsOpen(true)}
              className="hover:text-amber-400 transition cursor-pointer"
            >
              تست پیامک جدید
            </button>
          </div>
        </div>
      </footer>

      {/* Modals */}
      <OriginalSmsModal
        promo={inspectPromo}
        onClose={() => setInspectPromo(null)}
      />

      <NewSmsDrawer
        isOpen={isNewSmsOpen}
        onClose={() => setIsNewSmsOpen(false)}
        settings={aiSettings}
        onSmsProcessed={handleSmsProcessed}
      />

      <AndroidBridgeModal
        isOpen={isAndroidBridgeOpen}
        onClose={() => setIsAndroidBridgeOpen(false)}
      />

      <SettingsModal
        isOpen={isSettingsOpen}
        settings={aiSettings}
        onClose={() => setIsSettingsOpen(false)}
        onSave={setAiSettings}
      />

      {/* دکمه شناور تنظیمات */}
      <button
        onClick={() => setIsSettingsOpen(true)}
        aria-label="تنظیمات"
        className="fixed bottom-6 left-6 z-40 w-12 h-12 rounded-2xl bg-slate-900 border border-slate-800 text-slate-300 hover:text-amber-400 hover:border-slate-700 shadow-2xl flex items-center justify-center transition cursor-pointer"
      >
        <Settings className="w-5 h-5" />
      </button>

      {/* Floating Toast Notification */}
      {toast && (
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 border border-slate-700 text-slate-100 px-4 py-3 rounded-2xl shadow-2xl flex items-center gap-2.5 text-xs font-medium animate-in slide-in-from-bottom duration-200">
          <Check className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{toast.message}</span>
        </div>
      )}
    </div>
  );
}
