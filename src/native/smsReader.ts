import { Capacitor, registerPlugin } from '@capacitor/core';

export interface NativeSms {
  id: string;
  sender: string;
  body: string;
  /** میلی‌ثانیه از epoch */
  date: number;
  subscriptionId: number;
  simLabel: string;
}

export interface SimInfo {
  subscriptionId: number;
  slotIndex: number;
  carrierName: string;
  displayName: string;
}

type PermissionValue = 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale';

/** یک رخداد در مسیر دریافت خودکار پیامک */
export interface BackgroundEvent {
  at: number;
  stage:
    | 'received'
    | 'skipped'
    | 'filtered'
    | 'no-key'
    | 'duplicate'
    | 'sending'
    | 'found'
    | 'no-promo'
    | 'error';
  detail: string;
}

export interface Diagnostics {
  readSms: PermissionValue;
  receiveSms: PermissionValue;
  notifications: PermissionValue;
  /** آیا کاربر اعلان‌های اپ را از تنظیمات سیستم خاموش کرده است؟ */
  notificationsEnabled: boolean;
  hasApiKey: boolean;
  events: BackgroundEvent[];
}

/** کد تخفیفی که گیرنده پیامک در پس‌زمینه پیدا کرده است */
export interface PendingPromo {
  brand: string;
  code: string;
  discountAmount: string;
  minOrder: string;
  instructions: string;
  expiryDateText: string;
  expiresAt: string;
  categorySlug: string;
  sender: string;
  body: string;
  timestamp: number;
  simLabel: string;
}

export interface SmsReaderPlugin {
  isAvailable(): Promise<{ available: boolean; granted: boolean }>;
  checkPermissions(): Promise<{
    sms: PermissionValue;
    receiveSms: PermissionValue;
    notifications: PermissionValue;
  }>;
  requestPermissions(): Promise<{ sms: PermissionValue }>;
  requestNotificationPermission(): Promise<{ notifications: PermissionValue }>;
  consumePendingPromos(): Promise<{ promos: PendingPromo[]; count: number }>;
  getDiagnostics(): Promise<Diagnostics>;
  clearDiagnostics(): Promise<void>;
  readInbox(options?: { sinceDays?: number; limit?: number }): Promise<{
    messages: NativeSms[];
    count: number;
  }>;
  getSimInfo(): Promise<{ sims: SimInfo[] }>;
}

const SmsReader = registerPlugin<SmsReaderPlugin>('SmsReader');

/** آیا روی اپ نیتیو اندروید اجرا می‌شویم؟ (در مرورگر false است) */
export function isNativeAndroid(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

/**
 * وضعیت مجوز پیامک؛ در مرورگر همیشه denied.
 *
 * هر دو مجوز لازم‌اند: خواندن صندوق برای اسکن دستی و دریافت لحظه‌ای برای
 * پیامک‌های تازه. تا وقتی هر دو داده نشده باشند granted گزارش نمی‌شود،
 * وگرنه کاربر فکر می‌کند همه‌چیز آماده است ولی اعلانی نمی‌آید.
 */
export async function checkSmsPermission(): Promise<PermissionValue> {
  if (!isNativeAndroid()) return 'denied';
  const res = await SmsReader.checkPermissions();
  if (res.sms === 'granted' && res.receiveSms === 'granted') return 'granted';
  if (res.sms === 'denied' || res.receiveSms === 'denied') return 'denied';
  return 'prompt';
}

/** درخواست هر دو مجوز پیامک از کاربر */
export async function requestSmsPermission(): Promise<PermissionValue> {
  if (!isNativeAndroid()) return 'denied';
  await SmsReader.requestPermissions();
  return checkSmsPermission();
}

/** خواندن پیامک‌های صندوق ورودی؛ در مرورگر آرایه خالی برمی‌گرداند */
export async function readInbox(
  options: { sinceDays?: number; limit?: number } = {}
): Promise<NativeSms[]> {
  if (!isNativeAndroid()) return [];
  const res = await SmsReader.readInbox(options);
  return res.messages ?? [];
}

/** اطلاعات سیم‌کارت‌های فعال؛ در صورت نبود مجوز، آرایه خالی */
export async function getSimInfo(): Promise<SimInfo[]> {
  if (!isNativeAndroid()) return [];
  try {
    const res = await SmsReader.getSimInfo();
    return res.sims ?? [];
  } catch {
    return [];
  }
}

export default SmsReader;


/** وضعیت مجوز نمایش اعلان */
export async function checkNotificationPermission(): Promise<PermissionValue> {
  if (!isNativeAndroid()) return 'denied';
  const res = await SmsReader.checkPermissions();
  return res.notifications;
}

/** درخواست مجوز نمایش اعلان کدهای تخفیف تازه */
export async function requestNotificationPermission(): Promise<PermissionValue> {
  if (!isNativeAndroid()) return 'denied';
  const res = await SmsReader.requestNotificationPermission();
  return res.notifications;
}

/**
 * برداشتن کدهایی که در پس‌زمینه پیدا شده‌اند.
 * صف پس از خواندن خالی می‌شود، پس نتیجه باید بی‌درنگ ذخیره گردد.
 */
export async function consumePendingPromos(): Promise<PendingPromo[]> {
  if (!isNativeAndroid()) return [];
  try {
    const res = await SmsReader.consumePendingPromos();
    return res.promos ?? [];
  } catch {
    return [];
  }
}


/** وضعیت کامل دریافت خودکار، برای وقتی اعلانی نمی‌آید */
export async function getDiagnostics(): Promise<Diagnostics | null> {
  if (!isNativeAndroid()) return null;
  try {
    return await SmsReader.getDiagnostics();
  } catch {
    return null;
  }
}

export async function clearDiagnostics(): Promise<void> {
  if (!isNativeAndroid()) return;
  try {
    await SmsReader.clearDiagnostics();
  } catch {
    /* بی‌اهمیت */
  }
}
