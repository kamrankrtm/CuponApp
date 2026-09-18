import { Preferences } from '@capacitor/preferences';
import { DEFAULT_SETTINGS, type AiSettings } from './ai';

/**
 * ذخیره‌سازی تنظیمات.
 *
 * روی اندروید از Preferences نیتیو استفاده می‌شود (فضای خصوصی خود اپ)،
 * و در مرورگر به localStorage برمی‌گردد. کلید API هرگز به جایی ارسال
 * نمی‌شود مگر مستقیماً به سرویس هوش مصنوعی انتخاب‌شده.
 */

const KEY = 'cuponapp.ai.settings';
const CACHE_KEY = 'cuponapp.analyzed.fingerprints';

/** سقف اندازه کش؛ قدیمی‌ترها هرس می‌شوند تا حافظه بی‌نهایت رشد نکند */
const MAX_CACHE_ENTRIES = 3000;

export async function loadSettings(): Promise<AiSettings> {
  try {
    const { value } = await Preferences.get({ key: KEY });
    if (!value) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(value);
    return { ...DEFAULT_SETTINGS, ...parsed };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export async function saveSettings(settings: AiSettings): Promise<void> {
  await Preferences.set({ key: KEY, value: JSON.stringify(settings) });
}

export async function clearSettings(): Promise<void> {
  await Preferences.remove({ key: KEY });
}

/** نمایش امن کلید در رابط کاربری: فقط چند کاراکتر ابتدا و انتها */
export function maskKey(key: string): string {
  if (!key) return '';
  if (key.length <= 12) return '••••••••';
  return `${key.slice(0, 6)}••••••••${key.slice(-4)}`;
}


/**
 * کش پیامک‌های تحلیل‌شده.
 *
 * هر اثر انگشت یعنی «این پیامک قبلاً به هوش مصنوعی رفته». اسکن بعدی از
 * روی آن رد می‌شود تا دوباره هزینه ندهیم.
 */
export async function loadAnalyzedFingerprints(): Promise<Set<string>> {
  try {
    const { value } = await Preferences.get({ key: CACHE_KEY });
    if (!value) return new Set();
    const parsed = JSON.parse(value);
    return new Set(Array.isArray(parsed) ? parsed : []);
  } catch {
    return new Set();
  }
}

export async function addAnalyzedFingerprints(fingerprints: Iterable<string>): Promise<void> {
  const existing = await loadAnalyzedFingerprints();
  for (const fp of fingerprints) existing.add(fp);

  // نگه داشتن تازه‌ترین‌ها؛ Set ترتیب درج را حفظ می‌کند
  const list = Array.from(existing);
  const trimmed = list.length > MAX_CACHE_ENTRIES ? list.slice(-MAX_CACHE_ENTRIES) : list;
  await Preferences.set({ key: CACHE_KEY, value: JSON.stringify(trimmed) });
}

/** پاک کردن کش، برای وقتی کاربر می‌خواهد همه‌چیز از نو تحلیل شود */
export async function clearAnalyzedFingerprints(): Promise<void> {
  await Preferences.remove({ key: CACHE_KEY });
}
