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

export interface SmsReaderPlugin {
  isAvailable(): Promise<{ available: boolean; granted: boolean }>;
  checkPermissions(): Promise<{ sms: PermissionValue }>;
  requestPermissions(): Promise<{ sms: PermissionValue }>;
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

/** وضعیت مجوز خواندن پیامک؛ در مرورگر همیشه denied */
export async function checkSmsPermission(): Promise<PermissionValue> {
  if (!isNativeAndroid()) return 'denied';
  const res = await SmsReader.checkPermissions();
  return res.sms;
}

/** درخواست مجوز خواندن پیامک از کاربر */
export async function requestSmsPermission(): Promise<PermissionValue> {
  if (!isNativeAndroid()) return 'denied';
  const res = await SmsReader.requestPermissions();
  return res.sms;
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
