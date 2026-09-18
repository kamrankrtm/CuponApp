import type { NativeSms } from '../native/smsReader';
import type { RawSms, SimSlot, SmsType } from '../types';

/**
 * موتور دسته‌بندی محلی پیامک‌ها.
 *
 * این لایه تضمین حریم خصوصی اپ است: پیامک‌های شخصی، بانکی و رمزهای یک‌بارمصرف
 * همین‌جا روی خود گوشی کنار گذاشته می‌شوند و هرگز به سرویس هوش مصنوعی
 * فرستاده نمی‌شوند. فقط پیامک‌هایی که اینجا «تبلیغاتی» تشخیص داده شوند
 * برای استخراج کد تخفیف ارسال می‌گردند.
 */

/** الگوهای قطعی پیامک بانکی و تراکنش مالی */
const BANKING_PATTERNS = [
  /رمز\s*(?:پویا|یک\s*بار\s*مصرف|دوم)/,
  /مانده[:\s]/,
  /موجودی/,
  /واریز\s*(?:به|:)/,
  /برداشت\s*(?:از|:)/,
  /انتقال\s*وجه/,
  /شماره\s*(?:شبا|حساب|کارت)/,
  /\bشبا\b/,
  /صورتحساب/,
  /قبض/,
  /چک\s*(?:برگشتی|صیادی)/,
  /تسهیلات|اقساط|وام/,
  /بانک\s*(?:ملت|ملی|صادرات|تجارت|سپه|پاسارگاد|سامان|پارسیان|رفاه|کشاورزی|مسکن|شهر|دی|سینا|اقتصاد)/,
  /(?:بدهکار|بستانکار)/,
];

/** الگوهای رمز یک‌بارمصرف و کد تایید — حساس، هرگز ارسال نمی‌شوند */
const OTP_PATTERNS = [
  /کد\s*(?:تایید|تأیید|ورود|فعال\s*سازی|احراز|امنیتی)/,
  /رمز\s*عبور/,
  /verification\s*code/i,
  /one[-\s]?time\s*(?:password|code)/i,
  /\bOTP\b/i,
];

/** نشانه‌های پیامک تبلیغاتی و تخفیف */
const PROMO_PATTERNS = [
  /تخفیف/,
  /کد\s*تخفیف/,
  /جشنواره/,
  /پیشنهاد\s*(?:ویژه|شگفت)/,
  /حراج/,
  /off\b/i,
  /\d+\s*(?:٪|درصد|%)/,
  /هدیه/,
  /اعتبار\s*هدیه/,
  /رایگان/,
  /کوپن/,
  /شگفت\s*انگیز/,
  /فروش\s*ویژه/,
  /لغو\s*(?:۱۱|11)/,
  /خرید\s*کنید/,
];

/** شماره موبایل ایران: 09xxxxxxxxx یا +989xxxxxxxxx یا 00989... */
const PERSONAL_SENDER = /^(?:\+?98|0098|0)?9\d{9}$/;

function normalizeDigits(input: string): string {
  // تبدیل ارقام فارسی و عربی به لاتین تا الگوها روی هر دو کار کنند
  const fa = '۰۱۲۳۴۵۶۷۸۹';
  const ar = '٠١٢٣٤٥٦٧٨٩';
  return input.replace(/[۰-۹٠-٩]/g, (ch) => {
    const i = fa.indexOf(ch);
    if (i > -1) return String(i);
    return String(ar.indexOf(ch));
  });
}

function matchesAny(patterns: RegExp[], text: string): boolean {
  return patterns.some((re) => re.test(text));
}

/** آیا فرستنده یک شماره موبایل شخصی است؟ */
function isPersonalSender(sender: string): boolean {
  const cleaned = normalizeDigits(sender).replace(/[\s\-()]/g, '');
  return PERSONAL_SENDER.test(cleaned);
}

export interface Classification {
  type: SmsType;
  /** آیا مجاز است به سرویس هوش مصنوعی ارسال شود؟ */
  safeToSend: boolean;
  /** دلیل تصمیم، برای نمایش شفاف به کاربر */
  reason: string;
}

/**
 * دسته‌بندی یک پیامک، فقط با قواعد محلی و بدون هیچ تماس شبکه‌ای.
 *
 * ترتیب بررسی عمدی است: ابتدا موارد حساس (رمز یک‌بارمصرف و بانکی) کنار
 * گذاشته می‌شوند، سپس پیام شخصی، و در آخر تبلیغاتی.
 */
export function classifySms(sender: string, body: string): Classification {
  const text = normalizeDigits(body);

  if (matchesAny(OTP_PATTERNS, text)) {
    return {
      type: 'banking',
      safeToSend: false,
      reason: 'رمز یک‌بارمصرف یا کد تایید — روی گوشی باقی می‌ماند',
    };
  }

  if (matchesAny(BANKING_PATTERNS, text)) {
    return {
      type: 'banking',
      safeToSend: false,
      reason: 'پیامک بانکی یا تراکنش مالی — روی گوشی باقی می‌ماند',
    };
  }

  const personalSender = isPersonalSender(sender);
  const hasPromoSignal = matchesAny(PROMO_PATTERNS, text);

  if (personalSender && !hasPromoSignal) {
    return {
      type: 'personal',
      safeToSend: false,
      reason: 'پیام شخصی از یک شماره موبایل — روی گوشی باقی می‌ماند',
    };
  }

  if (hasPromoSignal) {
    return {
      type: 'promotional',
      safeToSend: true,
      reason: 'پیامک تبلیغاتی با نشانه تخفیف — برای استخراج کد ارسال می‌شود',
    };
  }

  // فرستنده سرشماره ولی بدون نشانه تخفیف: تبلیغاتی در نظر گرفته می‌شود
  // اما برای صرفه‌جویی در هزینه به هوش مصنوعی فرستاده نمی‌شود.
  if (!personalSender) {
    return {
      type: 'promotional',
      safeToSend: false,
      reason: 'پیامک خدماتی بدون نشانه تخفیف — ارسال نمی‌شود',
    };
  }

  return {
    type: 'personal',
    safeToSend: false,
    reason: 'دسته‌بندی نامشخص — محافظه‌کارانه شخصی در نظر گرفته شد',
  };
}

function toSimSlot(label: string): SimSlot {
  if (/2/.test(label)) return label.includes('(') ? (label as SimSlot) : 'SIM 2';
  return label.includes('(') ? (label as SimSlot) : 'SIM 1';
}

/** تبدیل پیامک خام نیتیو به مدل داخلی اپ، همراه با دسته‌بندی محلی */
export function toRawSms(sms: NativeSms): RawSms & { safeToSend: boolean; reason: string } {
  const { type, safeToSend, reason } = classifySms(sms.sender, sms.body);
  return {
    id: `sms-${sms.id}`,
    sender: sms.sender || 'نامشخص',
    recipientSim: toSimSlot(sms.simLabel || 'SIM 1'),
    timestamp: new Date(sms.date || Date.now()).toISOString(),
    body: sms.body || '',
    type,
    processed: false,
    safeToSend,
    reason,
  };
}

export interface FilterStats {
  total: number;
  promotional: number;
  personal: number;
  banking: number;
  /** تعداد پیامک‌هایی که واقعاً به هوش مصنوعی ارسال می‌شوند */
  sentToAi: number;
}

/**
 * اجرای فیلتر محلی روی کل صندوق ورودی.
 * خروجی: همه پیامک‌ها (برای نمایش) + فقط زیرمجموعه‌ی مجاز برای ارسال.
 */
export function filterInbox(messages: NativeSms[]): {
  all: RawSms[];
  toAnalyze: RawSms[];
  stats: FilterStats;
} {
  const mapped = messages.map(toRawSms);
  const toAnalyze = mapped.filter((m) => m.safeToSend);

  const stats: FilterStats = {
    total: mapped.length,
    promotional: mapped.filter((m) => m.type === 'promotional').length,
    personal: mapped.filter((m) => m.type === 'personal').length,
    banking: mapped.filter((m) => m.type === 'banking').length,
    sentToAi: toAnalyze.length,
  };

  // فیلدهای کمکی فیلتر از مدل نهایی حذف می‌شوند
  const strip = ({ safeToSend, reason, ...rest }: RawSms & { safeToSend: boolean; reason: string }) =>
    rest as RawSms;

  return {
    all: mapped.map(strip),
    toAnalyze: toAnalyze.map(strip),
    stats,
  };
}
