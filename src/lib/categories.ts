import type { PromoCode } from '../types';

export type CategorySlug = PromoCode['categorySlug'];

export interface CategoryMeta {
  slug: CategorySlug;
  label: string;
  /** کلاس رنگ متن و پس‌زمینه، برای نشان دسته روی کارت */
  text: string;
  bg: string;
  ring: string;
  emoji: string;
}

export const CATEGORIES: CategoryMeta[] = [
  { slug: 'food',          label: 'غذا',        emoji: '🍔', text: 'text-orange-300', bg: 'bg-orange-500/12', ring: 'ring-orange-500/25' },
  { slug: 'transport',     label: 'سفر',        emoji: '🚕', text: 'text-sky-300',    bg: 'bg-sky-500/12',    ring: 'ring-sky-500/25' },
  { slug: 'ecommerce',     label: 'فروشگاه',    emoji: '🛍️', text: 'text-violet-300', bg: 'bg-violet-500/12', ring: 'ring-violet-500/25' },
  { slug: 'entertainment', label: 'سرگرمی',     emoji: '🎬', text: 'text-pink-300',   bg: 'bg-pink-500/12',   ring: 'ring-pink-500/25' },
  { slug: 'supermarket',   label: 'سوپرمارکت',  emoji: '🛒', text: 'text-emerald-300',bg: 'bg-emerald-500/12',ring: 'ring-emerald-500/25' },
  { slug: 'other',         label: 'متفرقه',     emoji: '🎁', text: 'text-slate-300',  bg: 'bg-slate-500/12',  ring: 'ring-slate-500/25' },
];

const BY_SLUG = new Map(CATEGORIES.map((c) => [c.slug, c]));

export function categoryMeta(slug: CategorySlug | undefined): CategoryMeta {
  return BY_SLUG.get(slug ?? 'other') ?? CATEGORIES[CATEGORIES.length - 1];
}

/**
 * حرف اول برند برای آواتار، با پرش از حروف ربط.
 * برای برندهای لاتین حرف بزرگ انگلیسی برمی‌گرداند.
 */
export function brandInitial(brand: string): string {
  const cleaned = brand.replace(/[‌\s]+/g, ' ').trim();
  return cleaned.charAt(0).toUpperCase() || '؟';
}

/** رنگ ثابت و تکرارپذیر برای آواتار هر برند */
const AVATAR_TONES = [
  'bg-amber-500/15 text-amber-300 ring-amber-500/25',
  'bg-sky-500/15 text-sky-300 ring-sky-500/25',
  'bg-violet-500/15 text-violet-300 ring-violet-500/25',
  'bg-emerald-500/15 text-emerald-300 ring-emerald-500/25',
  'bg-pink-500/15 text-pink-300 ring-pink-500/25',
  'bg-orange-500/15 text-orange-300 ring-orange-500/25',
];

export function brandTone(brand: string): string {
  let hash = 0;
  for (let i = 0; i < brand.length; i++) {
    hash = (hash * 31 + brand.charCodeAt(i)) >>> 0;
  }
  return AVATAR_TONES[hash % AVATAR_TONES.length];
}
