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
  RefreshCw
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

export default function App() {
  // Local storage persisted state
  const [smsList, setSmsList] = useState<RawSms[]>(() => {
    try {
      const saved = localStorage.getItem('sms_discount_sms_list');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.error(e);
    }
    return INITIAL_SMS_DATA;
  });

  const [promoCodes, setPromoCodes] = useState<PromoCode[]>(() => {
    try {
      const saved = localStorage.getItem('sms_discount_promo_codes');
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.error(e);
    }
    return INITIAL_PROMO_CODES;
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
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'info' } | null>(null);

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

  // Rescan all SMS using Gemini API
  const handleRescanAll = async () => {
    setIsScanning(true);
    showToast('در حال ارسال پیامک‌های ۲ ماهه اخیر به هوش مصنوعی Gemini...', 'info');

    try {
      const response = await fetch('/api/analyze-sms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ smsList }),
      });

      const data = await response.json();
      if (data.results && Array.isArray(data.results)) {
        const newPromos: PromoCode[] = [];
        data.results.forEach((item: any) => {
          if (item.hasPromoCode && item.type === 'promotional') {
            const original = smsList.find((s) => s.id === item.smsId);
            newPromos.push({
              id: 'promo-' + item.smsId,
              smsId: item.smsId,
              brand: item.brand || 'فروشگاه',
              brandEn: item.brandEn || 'Store',
              category: item.category || 'تخفیف',
              categorySlug: item.categorySlug || 'ecommerce',
              code: item.code || 'PROMO',
              discountAmount: item.discountAmount || 'تخفیف',
              description: item.description || '',
              minOrder: item.minOrder,
              instructions: item.instructions || 'در مرحله تسویه‌حساب اعمال شود.',
              expiryDateText: item.expiryDateText || 'معتبر',
              isExpired: item.isExpired || false,
              status: 'active',
              sender: original?.sender || 'UNKNOWN',
              recipientSim: original?.recipientSim || 'SIM 1',
              originalSmsBody: original?.body || '',
              receivedAt: original?.timestamp || new Date().toISOString(),
            });
          }
        });

        if (newPromos.length > 0) {
          setPromoCodes(newPromos);
          showToast(`تحلیل کامل شد: ${newPromos.length} کد تخفیف معتبر شناسایی شد.`);
        } else {
          showToast('تحلیل پیامک‌ها با موفقیت انجام شد.');
        }
      }
    } catch (e: any) {
      showToast('خطا در تحلیل دسته‌ای پیامک‌ها: ' + e.message, 'info');
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
        onRescanAll={handleRescanAll}
        isScanning={isScanning}
      />

      {/* Main Content Area */}
      <main className="max-w-7xl mx-auto px-4 py-6 flex-1 w-full space-y-6">
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
            <span>سامانه هوشمند استخراج کدهای تخفیف با موتور Google Gemini 3.8 Flash</span>
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
        onSmsProcessed={handleSmsProcessed}
      />

      <AndroidBridgeModal
        isOpen={isAndroidBridgeOpen}
        onClose={() => setIsAndroidBridgeOpen(false)}
      />

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
