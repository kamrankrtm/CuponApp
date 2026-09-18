import { analyzeAll, toPromoCode, type AiSettings } from './ai';
import { classifySms, filterInbox, type FilterStats } from './smsFilter';
import { addAnalyzedFingerprints, loadAnalyzedFingerprints } from './settings';
import { readInbox, type PendingPromo } from '../native/smsReader';
import { isExpiredNow, resolveExpiresAt } from './expiry';
import type { PromoCode, RawSms } from '../types';

/**
 * جریان کامل اسکن: خواندن پیامک از گوشی ← فیلتر محلی ← تحلیل هوش مصنوعی.
 *
 * ترتیب این سه مرحله مهم است: هیچ پیامکی پیش از عبور از فیلتر محلی
 * به شبکه نمی‌رود.
 */

export interface ScanProgress {
  phase: 'reading' | 'filtering' | 'analyzing' | 'done';
  message: string;
  done?: number;
  total?: number;
}

export interface ScanResult {
  smsList: RawSms[];
  promoCodes: PromoCode[];
  stats: FilterStats;
  errors: string[];
}

export async function runScan(
  settings: AiSettings,
  options: {
    sinceDays?: number;
    limit?: number;
    onProgress?: (p: ScanProgress) => void;
    signal?: AbortSignal;
  } = {}
): Promise<ScanResult> {
  const { onProgress } = options;

  onProgress?.({ phase: 'reading', message: 'در حال خواندن پیامک‌های گوشی…' });
  const raw = await readInbox({
    sinceDays: options.sinceDays ?? 60,
    limit: options.limit ?? 500,
  });

  onProgress?.({
    phase: 'filtering',
    message: `${raw.length} پیامک خوانده شد. در حال جداسازی محلی پیام‌های شخصی و بانکی…`,
  });
  const analyzedFingerprints = await loadAnalyzedFingerprints();
  const { all, toAnalyze, fingerprints, stats } = filterInbox(raw, {
    strictness: settings.strictness,
    analyzedFingerprints,
  });

  if (toAnalyze.length === 0) {
    const skipped = stats.duplicates + stats.cached;
    onProgress?.({
      phase: 'done',
      message: skipped > 0
        ? `پیامک تازه‌ای برای تحلیل نبود (${skipped} مورد تکراری یا قبلاً بررسی‌شده رد شد).`
        : 'پیامک تبلیغاتی قابل تحلیلی پیدا نشد.',
    });
    return { smsList: all, promoCodes: [], stats, errors: [] };
  }

  onProgress?.({
    phase: 'analyzing',
    message: `${toAnalyze.length} پیامک تبلیغاتی برای استخراج کد تخفیف ارسال می‌شود…`,
    done: 0,
    total: toAnalyze.length,
  });

  const { results, errors } = await analyzeAll(settings, toAnalyze, {
    signal: options.signal,
    onProgress: (done, total) =>
      onProgress?.({
        phase: 'analyzing',
        message: `تحلیل هوش مصنوعی: ${done} از ${total}`,
        done,
        total,
      }),
  });

  const byId = new Map(toAnalyze.map((s) => [s.id, s]));
  const promoCodes: PromoCode[] = [];
  const analyzedNow: string[] = [];

  for (const result of results) {
    // چه کد تخفیف داشت چه نداشت، تحلیل شده حساب می‌شود تا دوباره ارسال نشود
    const fp = fingerprints.get(result.smsId);
    if (fp) analyzedNow.push(fp);

    if (!result.hasPromoCode) continue;
    const sms = byId.get(result.smsId);
    if (!sms) continue;
    promoCodes.push(toPromoCode(result, sms));
  }

  if (analyzedNow.length > 0) {
    await addAnalyzedFingerprints(analyzedNow);
  }

  onProgress?.({
    phase: 'done',
    message: `${promoCodes.length} کد تخفیف استخراج شد.`,
  });

  return { smsList: all, promoCodes, stats, errors };
}

function normalizeForKey(value: string): string {
  const fa = '۰۱۲۳۴۵۶۷۸۹';
  const ar = '٠١٢٣٤٥٦٧٨٩';
  return value
    .replace(/[۰-۹٠-٩]/g, (ch) => {
      const i = fa.indexOf(ch);
      return i > -1 ? String(i) : String(ar.indexOf(ch));
    })
    .replace(/[\u200c\s]+/g, '')
    .toLowerCase();
}

/**
 * کلید یکتای یک پیشنهاد تخفیف: برند + کد.
 *
 * یک کمپین اغلب با چند پیامک متفاوت تبلیغ می‌شود و هر کدام یک کد یکسان
 * دارند. بدون این کلید، کاربر چند کارت تکراری با یک کد می‌بیند. ارقام
 * فارسی و لاتین هم یکسان‌سازی می‌شوند تا «۱ میلیون» و «1 میلیون» یکی شوند.
 */
function promoKey(promo: PromoCode): string {
  return `${normalizeForKey(promo.brand)}|${normalizeForKey(promo.code)}`;
}

/** میان دو نسخه از یک پیشنهاد، کاملش را نگه می‌دارد */
function richer(a: PromoCode, b: PromoCode): PromoCode {
  const score = (p: PromoCode) =>
    (p.expiresAt ? 4 : 0) + (p.minOrder ? 2 : 0) + (p.description ? 1 : 0);
  if (score(b) > score(a)) return b;
  if (score(a) > score(b)) return a;
  // امتیاز برابر: تازه‌ترین دریافت برنده است
  return new Date(b.receivedAt) > new Date(a.receivedAt) ? b : a;
}

/**
 * ادغام نتایج تازه با داده‌های موجود.
 *
 * وضعیتی که کاربر دستی تعیین کرده («استفاده شد» یا «کار نکرد») همیشه
 * حفظ می‌شود، و کارت‌های تکراری با یک برند و کد در هم ادغام می‌گردند.
 */
export function mergePromoCodes(existing: PromoCode[], incoming: PromoCode[]): PromoCode[] {
  const byKey = new Map<string, PromoCode>();

  for (const promo of [...existing, ...incoming]) {
    const key = promoKey(promo);
    const prev = byKey.get(key);

    if (!prev) {
      byKey.set(key, promo);
      continue;
    }

    const merged = richer(prev, promo);
    byKey.set(key, {
      ...merged,
      // وضعیت غیرفعال هرگز با یک اسکن تازه بازنشانی نمی‌شود
      status: prev.status !== 'active' ? prev.status : merged.status,
      invalidReason: prev.invalidReason ?? merged.invalidReason,
    });
  }

  return Array.from(byKey.values()).sort(
    (a, b) => new Date(b.receivedAt).getTime() - new Date(a.receivedAt).getTime()
  );
}

export function mergeSmsList(existing: RawSms[], incoming: RawSms[]): RawSms[] {
  const byId = new Map(existing.map((s) => [s.id, s]));
  for (const sms of incoming) byId.set(sms.id, sms);
  return Array.from(byId.values()).sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );
}

/**
 * تبدیل کدهایی که گیرنده پیامک در پس‌زمینه پیدا کرده به مدل داخلی اپ.
 *
 * انقضا اینجا دوباره حل می‌شود تا با همان منطق اسکن دستی بخواند؛ سمت
 * نیتیو فقط متن خام و تاریخ پیشنهادی مدل را می‌فرستد.
 */
export function fromPendingPromos(items: PendingPromo[]): PromoCode[] {
  return items.map((item) => {
    const receivedAt = new Date(item.timestamp || Date.now()).toISOString();
    const expiresAt = resolveExpiresAt(
      item.expiryDateText || undefined,
      receivedAt,
      item.expiresAt || undefined
    );

    return {
      id: `promo-bg-${item.timestamp}-${item.code}`,
      smsId: `sms-bg-${item.timestamp}`,
      brand: item.brand || 'نامشخص',
      brandEn: '',
      category: '',
      categorySlug: (item.categorySlug as PromoCode['categorySlug']) || 'other',
      code: item.code || 'بدون کد',
      discountAmount: item.discountAmount || 'تخفیف',
      description: '',
      minOrder: item.minOrder || undefined,
      instructions: item.instructions || 'در مرحله تسویه‌حساب کد را وارد کنید.',
      expiryDateText: item.expiryDateText || 'نامشخص',
      expiresAt,
      isExpired: isExpiredNow(expiresAt),
      status: 'active',
      sender: item.sender || 'نامشخص',
      recipientSim: (item.simLabel || 'SIM 1') as PromoCode['recipientSim'],
      originalSmsBody: item.body || '',
      receivedAt,
    };
  });
}

/**
 * پاکسازی کدهایی که نباید هرگز استخراج می‌شدند.
 *
 * نسخه‌های پیشین فیلتر، بعضی پیامک‌های کد ورود را تبلیغاتی تشخیص می‌دادند
 * و از آن‌ها کارت می‌ساختند. این تابع هنگام بالا آمدن اپ متن اصلی هر کارت
 * را با فیلتر فعلی دوباره می‌سنجد و هر چیزی را که حالا حساس تشخیص داده
 * می‌شود دور می‌ریزد. بدون این، داده‌ی نشتی تا ابد در اپ می‌ماند.
 */
export function purgeSensitive(promos: PromoCode[]): {
  kept: PromoCode[];
  removed: number;
} {
  const kept = promos.filter((promo) => {
    // کارتی که متن اصلی ندارد قابل بازبینی نیست؛ نگه داشته می‌شود
    if (!promo.originalSmsBody) return true;
    const verdict = classifySms(promo.sender, promo.originalSmsBody, 'balanced');
    return verdict.type === 'promotional';
  });

  return { kept, removed: promos.length - kept.length };
}
