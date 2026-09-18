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
