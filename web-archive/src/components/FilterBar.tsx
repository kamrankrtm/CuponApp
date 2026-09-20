import React from 'react';
import { Search, SlidersHorizontal, Smartphone, Tag, ShieldCheck, CheckCircle2, Flame } from 'lucide-react';
import { SimSlot } from '../types';

interface Props {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  selectedBrand: string;
  onBrandChange: (brand: string) => void;
  selectedCategory: string;
  onCategoryChange: (cat: string) => void;
  selectedSim: string;
  onSimChange: (sim: string) => void;
  availableBrands: string[];
  activeTab: 'active' | 'personal' | 'used';
  onTabChange: (tab: 'active' | 'personal' | 'used') => void;
  activeCount: number;
  personalCount: number;
  usedCount: number;
}

export const FilterBar: React.FC<Props> = ({
  searchQuery,
  onSearchChange,
  selectedBrand,
  onBrandChange,
  selectedCategory,
  onCategoryChange,
  selectedSim,
  onSimChange,
  availableBrands,
  activeTab,
  onTabChange,
  activeCount,
  personalCount,
  usedCount,
}) => {
  const categories = [
    { id: 'all', label: 'همه دسته‌ها' },
    { id: 'food', label: 'غذا و رستوران' },
    { id: 'ecommerce', label: 'خرید اینترنتی' },
    { id: 'supermarket', label: 'سوپرمارکت' },
    { id: 'transport', label: 'تاکسی اینترنتی' },
    { id: 'entertainment', label: 'فیلم و سرگرمی' },
  ];

  return (
    <div className="flex flex-col gap-4 bg-slate-900/80 border border-slate-800 rounded-3xl p-4 md:p-5 shadow-xl backdrop-blur-md">
      {/* Top View Tabs: Active Promos vs Personal SMS vs History */}
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-800/80 pb-3">
        <button
          onClick={() => onTabChange('active')}
          className={`flex items-center gap-2 px-4 py-2 rounded-2xl text-xs md:text-sm font-bold transition-all cursor-pointer ${
            activeTab === 'active'
              ? 'bg-amber-500 text-slate-950 shadow-md shadow-amber-500/20'
              : 'bg-slate-950/60 hover:bg-slate-800 text-slate-300'
          }`}
        >
          <Flame className="w-4 h-4 text-amber-500 group-hover:text-amber-400" />
          <span>کدهای تخفیف فعال</span>
          <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${
            activeTab === 'active' ? 'bg-slate-950/20 text-slate-950' : 'bg-slate-800 text-amber-400'
          }`}>
            {activeCount}
          </span>
        </button>

        <button
          onClick={() => onTabChange('personal')}
          className={`flex items-center gap-2 px-4 py-2 rounded-2xl text-xs md:text-sm font-bold transition-all cursor-pointer ${
            activeTab === 'personal'
              ? 'bg-blue-600 text-white shadow-md shadow-blue-500/20'
              : 'bg-slate-950/60 hover:bg-slate-800 text-slate-300'
          }`}
        >
          <ShieldCheck className="w-4 h-4 text-blue-400" />
          <span>پیامک‌های شخصی محفوظ</span>
          <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${
            activeTab === 'personal' ? 'bg-white/20 text-white' : 'bg-slate-800 text-blue-400'
          }`}>
            {personalCount}
          </span>
        </button>

        <button
          onClick={() => onTabChange('used')}
          className={`flex items-center gap-2 px-4 py-2 rounded-2xl text-xs md:text-sm font-bold transition-all cursor-pointer ${
            activeTab === 'used'
              ? 'bg-slate-700 text-slate-100 shadow-md'
              : 'bg-slate-950/60 hover:bg-slate-800 text-slate-400'
          }`}
        >
          <CheckCircle2 className="w-4 h-4 text-slate-400" />
          <span>کدهای مصرف‌شده یا باطله</span>
          <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-slate-800 text-slate-400">
            {usedCount}
          </span>
        </button>
      </div>

      {activeTab === 'active' && (
        <>
          {/* Search bar & SIM Filter */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {/* Search Input */}
            <div className="md:col-span-2 relative">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => onSearchChange(e.target.value)}
                placeholder="جستجو در برندها، کد تخفیف، درصد یا شرایط..."
                className="w-full bg-slate-950 border border-slate-800 focus:border-amber-500/60 rounded-2xl py-2.5 pr-10 pl-4 text-xs md:text-sm text-slate-100 placeholder-slate-500 outline-none transition"
              />
              <Search className="w-4 h-4 text-slate-400 absolute right-3.5 top-3" />
            </div>

            {/* SIM Slot Filter */}
            <div className="relative">
              <select
                value={selectedSim}
                onChange={(e) => onSimChange(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 focus:border-amber-500/60 rounded-2xl py-2.5 px-3.5 text-xs md:text-sm text-slate-200 outline-none appearance-none cursor-pointer"
              >
                <option value="all">همه سیم‌کارت‌ها (SIM 1 و SIM 2)</option>
                <option value="SIM 1">فقط سیم‌کارت ۱ (همراه اول)</option>
                <option value="SIM 2">فقط سیم‌کارت ۲ (ایرانسل)</option>
              </select>
              <Smartphone className="w-4 h-4 text-emerald-400 absolute left-3.5 top-3 pointer-events-none" />
            </div>
          </div>

          {/* Brand Filter Pills */}
          <div className="flex flex-col gap-2">
            <span className="text-[11px] text-slate-400 font-medium">فیلتر بر اساس برند مورد نظر:</span>
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
              <button
                onClick={() => onBrandChange('all')}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold shrink-0 transition cursor-pointer ${
                  selectedBrand === 'all'
                    ? 'bg-amber-500 text-slate-950 shadow-sm'
                    : 'bg-slate-950 border border-slate-800 text-slate-300 hover:bg-slate-800'
                }`}
              >
                همه برندها
              </button>
              {availableBrands.map((brand) => (
                <button
                  key={brand}
                  onClick={() => onBrandChange(brand)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold shrink-0 transition cursor-pointer ${
                    selectedBrand === brand
                      ? 'bg-amber-500 text-slate-950 shadow-sm'
                      : 'bg-slate-950 border border-slate-800 text-slate-300 hover:bg-slate-800'
                  }`}
                >
                  {brand}
                </button>
              ))}
            </div>
          </div>

          {/* Category Chips */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none pt-1 border-t border-slate-800/60">
            {categories.map((cat) => (
              <button
                key={cat.id}
                onClick={() => onCategoryChange(cat.id)}
                className={`px-3 py-1 rounded-lg text-[11px] font-medium shrink-0 transition cursor-pointer ${
                  selectedCategory === cat.id
                    ? 'bg-slate-800 text-amber-400 border border-amber-500/40 font-bold'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-950'
                }`}
              >
                {cat.label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
};
