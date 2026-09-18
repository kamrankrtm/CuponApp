import { analyzeAll, toPromoCode, type AiSettings } from './ai';
import { filterInbox, type FilterStats } from './smsFilter';
import { readInbox } from '../native/smsReader';
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
  const { all, toAnalyze, stats } = filterInbox(raw);

  if (toAnalyze.length === 0) {
    onProgress?.({ phase: 'done', message: 'پیامک تبلیغاتی قابل تحلیلی پیدا نشد.' });
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
  for (const result of results) {
    if (!result.hasPromoCode) continue;
    const sms = byId.get(result.smsId);
    if (!sms) continue;
    promoCodes.push(toPromoCode(result, sms));
  }

  onProgress?.({
    phase: 'done',
    message: `${promoCodes.length} کد تخفیف استخراج شد.`,
  });

  return { smsList: all, promoCodes, stats, errors };
}

/** ادغام نتایج تازه با داده‌های موجود، بدون از دست رفتن وضعیت «استفاده شد» */
export function mergePromoCodes(existing: PromoCode[], incoming: PromoCode[]): PromoCode[] {
  const byId = new Map(existing.map((p) => [p.id, p]));
  for (const promo of incoming) {
    const prev = byId.get(promo.id);
    if (prev) {
      // وضعیتی که کاربر دستی تعیین کرده حفظ می‌شود
      byId.set(promo.id, { ...promo, status: prev.status, invalidReason: prev.invalidReason });
    } else {
      byId.set(promo.id, promo);
    }
  }
  return Array.from(byId.values()).sort(
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
