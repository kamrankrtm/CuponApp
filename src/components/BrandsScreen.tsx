import React, { useMemo } from 'react';
import { ChevronLeft, Search, Inbox } from 'lucide-react';
import type { PromoCode } from '../types';
import { brandInitial, brandTone, categoryMeta } from '../lib/categories';
import { expiryLabel } from '../lib/expiry';

export interface BrandGroup {
  brand: string;
  codes: PromoCode[];
  /** نزدیک‌ترین انقضا در میان کدهای این برند */
  soonest?: string;
  categorySlug: PromoCode['categorySlug'];
}

/** گروه‌بندی کدهای فعال بر اساس برند، مرتب‌شده بر اساس فوریت */
export function groupByBrand(codes: PromoCode[]): BrandGroup[] {
  const map = new Map<string, PromoCode[]>();
  for (const code of codes) {
    const key = code.brand || 'نامشخص';
    const list = map.get(key);
    if (list) list.push(code);
    else map.set(key, [code]);
  }

  const groups: BrandGroup[] = Array.from(map.entries()).map(([brand, list]) => {
    const dated = list
      .map((c) => c.expiresAt)
      .filter((d): d is string => !!d)
      .sort();
    return {
      brand,
      codes: list.sort((a, b) => (a.expiresAt ?? '￿').localeCompare(b.expiresAt ?? '￿')),
      soonest: dated[0],
      categorySlug: list[0]?.categorySlug ?? 'other',
    };
  });

  // برندی که کدش زودتر منقضی می‌شود بالاتر می‌آید؛ بدون تاریخ‌ها آخر
  return groups.sort((a, b) => {
    if (a.soonest && b.soonest) return a.soonest.localeCompare(b.soonest);
    if (a.soonest) return -1;
    if (b.soonest) return 1;
    return b.codes.length - a.codes.length;
  });
}

interface Props {
  codes: PromoCode[];
  query: string;
  onQueryChange: (q: string) => void;
  onSelectBrand: (brand: string) => void;
  onScan: () => void;
}

export const BrandsScreen: React.FC<Props> = ({
  codes,
  query,
  onQueryChange,
  onSelectBrand,
  onScan,
}) => {
  const groups = useMemo(() => {
    const q = query.trim().toLowerCase();
    const filtered = q
      ? codes.filter(
          (c) =>
            c.brand.toLowerCase().includes(q) ||
            c.brandEn?.toLowerCase().includes(q) ||
            c.code.toLowerCase().includes(q) ||
            c.discountAmount.toLowerCase().includes(q)
        )
      : codes;
    return groupByBrand(filtered);
  }, [codes, query]);

  return (
    <div className="space-y-3">
      {/* جستجو */}
      <div className="relative">
        <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500 pointer-events-none" />
        <input
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="جستجوی برند یا کد…"
          className="w-full bg-white/[0.04] border border-white/6 rounded-xl pr-10 pl-3 py-2.5 text-[13px] text-slate-100 placeholder:text-slate-600 outline-none focus:border-amber-500/40 transition"
        />
      </div>

      {groups.length === 0 ? (
        <EmptyBrands hasQuery={!!query.trim()} onScan={onScan} />
      ) : (
        <div className="space-y-2">
          {groups.map((group) => (
            <BrandRow key={group.brand} group={group} onSelect={onSelectBrand} />
          ))}
        </div>
      )}
    </div>
  );
};

const BrandRow: React.FC<{ group: BrandGroup; onSelect: (b: string) => void }> = ({
  group,
  onSelect,
}) => {
  const cat = categoryMeta(group.categorySlug);
  const expiry = expiryLabel(group.soonest);
  const urgent = expiry.tone === 'urgent';

  return (
    <button
      onClick={() => onSelect(group.brand)}
      className="w-full flex items-center gap-3 rounded-2xl border border-white/6 bg-white/[0.03] hover:bg-white/[0.05] px-3 py-3 transition text-right active:scale-[0.995] cursor-pointer"
    >
      {/* آواتار برند */}
      <span
        className={`shrink-0 w-11 h-11 rounded-xl ring-1 flex items-center justify-center text-[17px] font-bold ${brandTone(
          group.brand
        )}`}
      >
        {brandInitial(group.brand)}
      </span>

      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2">
          <span className="text-[14px] font-bold text-white truncate">{group.brand}</span>
          <span className="shrink-0 text-[10px] text-slate-500">{cat.emoji}</span>
        </span>
        <span
          className={`block text-[11px] mt-0.5 truncate ${
            urgent ? 'text-rose-300' : 'text-slate-500'
          }`}
        >
          {group.codes.length} کد فعال
          {group.soonest ? ` · ${expiry.text}` : ''}
        </span>
      </span>

      {/* شمارنده */}
      <span className="shrink-0 min-w-6 h-6 px-1.5 rounded-lg bg-amber-500/15 text-amber-300 text-[12px] font-bold font-mono flex items-center justify-center">
        {group.codes.length}
      </span>
      <ChevronLeft className="shrink-0 w-4 h-4 text-slate-600" />
    </button>
  );
};

const EmptyBrands: React.FC<{ hasQuery: boolean; onScan: () => void }> = ({
  hasQuery,
  onScan,
}) => (
  <div className="text-center py-16 px-6 space-y-3">
    <div className="w-14 h-14 mx-auto rounded-2xl bg-white/[0.04] flex items-center justify-center text-slate-600">
      <Inbox className="w-6 h-6" />
    </div>
    <h3 className="text-[14px] font-bold text-slate-300">
      {hasQuery ? 'چیزی پیدا نشد' : 'هنوز کد تخفیفی نداری'}
    </h3>
    <p className="text-[12px] text-slate-500 max-w-xs mx-auto leading-relaxed">
      {hasQuery
        ? 'عبارت دیگری را امتحان کن.'
        : 'پیامک‌های گوشی را اسکن کن تا کدهای تخفیف استخراج شوند.'}
    </p>
    {!hasQuery && (
      <button
        onClick={onScan}
        className="mt-1 px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl text-[13px] transition cursor-pointer"
      >
        اسکن پیامک‌ها
      </button>
    )}
  </div>
);
