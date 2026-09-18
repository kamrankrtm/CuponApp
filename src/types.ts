export type SmsType = 'promotional' | 'personal' | 'banking';

export type SimSlot = 'SIM 1 (همراه اول)' | 'SIM 2 (ایرانسل)' | 'SIM 1' | 'SIM 2';

export interface RawSms {
  id: string;
  sender: string;
  recipientSim: SimSlot;
  timestamp: string; // ISO date or Persian formatted date
  body: string;
  type: SmsType;
  processed: boolean;
}

export interface PromoCode {
  id: string;
  smsId: string;
  brand: string;
  brandEn: string;
  category: string;
  categorySlug: 'food' | 'transport' | 'ecommerce' | 'entertainment' | 'supermarket' | 'other';
  code: string;
  discountAmount: string; // e.g. "۵۰,۰۰۰ تومان" or "۳۵٪"
  description: string;
  minOrder?: string;
  instructions: string; // راه گرفتن تخفیف
  expiryDateText: string;
  expiresAt?: string; // ISO date if resolvable
  isExpired: boolean;
  status: 'active' | 'used' | 'invalid';
  invalidReason?: string;
  sender: string;
  recipientSim: SimSlot;
  originalSmsBody: string;
  receivedAt: string;
}

export interface AnalysisSummary {
  totalSms: number;
  promotionalCount: number;
  personalCount: number;
  bankingCount: number;
  extractedPromoCount: number;
  activePromoCount: number;
}
