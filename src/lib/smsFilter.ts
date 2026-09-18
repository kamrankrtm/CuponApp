import type { NativeSms } from '../native/smsReader';
import type { RawSms, SimSlot, SmsType } from '../types';

/**
 * موتور دسته‌بندی و فیلتر محلی پیامک‌ها.
 *
 * دو هدف همزمان دارد:
 *  ۱. حریم خصوصی — پیام‌های شخصی، بانکی و رمزهای یک‌بارمصرف همین‌جا روی
 *     گوشی کنار گذاشته می‌شوند و هرگز به شبکه نمی‌روند.
 *  ۲. هزینه — از هر پیامکی که به هوش مصنوعی فرستاده شود پول کم می‌شود،
 *     پس فقط پیامک‌هایی می‌روند که واقعاً شانس داشتن کد تخفیف دارند.
 *
 * هدف دوم با امتیازدهی انجام می‌شود، نه با یک قانون بله/خیر. هر نشانه در
 * متن وزنی دارد و فقط پیامک‌هایی که مجموع وزنشان از آستانه بگذرد ارسال
 * می‌شوند. این کار جلوی دو خطا را می‌گیرد: ارسال بی‌هدف همه‌چیز، و از دست
 * دادن تخفیفی که با کلمات غیرمعمول نوشته شده.
 */

// ── مسدودکننده‌های قطعی ───────────────────────────────────────────────────

/** رمز یک‌بارمصرف و کد تایید — حساس‌ترین دسته، هرگز ارسال نمی‌شوند */
const OTP_PATTERNS = [
  /کد\s*(?:تایید|تأیید|ورود|فعال\s*سازی|احراز|امنیتی|یکبار|یک\s*بار)/,
  /رمز\s*(?:عبور|ورود|موقت|یکبار|یک\s*بار)/,
  /verification\s*code/i,
  /one[-\s]?time\s*(?:password|code)/i,
  /\bOTP\b/i,
];

/** پیامک بانکی و تراکنش مالی */
const BANKING_PATTERNS = [
  /رمز\s*(?:پویا|دوم)/,
  /مانده[:\s]/,
  /موجودی/,
  /واریز\s*(?:به|:)/,
  /برداشت\s*(?:از|:)/,
  /انتقال\s*وجه/,
  /شماره\s*(?:شبا|حساب|کارت)/,
  /\bشبا\b/,
  /\bIR\d{20,}\b/,
  /صورتحساب/,
  /چک\s*(?:برگشتی|صیادی)/,
  /تسهیلات|اقساط|وام/,
  /بانک\s*(?:ملت|ملی|صادرات|تجارت|سپه|پاسارگاد|سامان|پارسیان|رفاه|کشاورزی|مسکن|شهر|دی|سینا|اقتصاد|آینده|قوامین)/,
  /(?:بدهکار|بستانکار)/,
  /سامانه\s*پیامکی\s*بانک/,
];

/** شماره موبایل ایران: 09xxxxxxxxx یا +989xxxxxxxxx */
const PERSONAL_SENDER = /^(?:\+?98|0098|0)?9\d{9}$/;

// ── نشانه‌های تخفیف، همراه با وزن ────────────────────────────────────────

interface Signal {
  pattern: RegExp;
  weight: number;
  label: string;
}

/**
 * وزن‌ها عمداً طوری چیده شده‌اند که یک نشانه‌ی قوی به تنهایی برای ارسال
 * کافی باشد، ولی نشانه‌های ضعیف فقط وقتی با هم جمع شوند اثر کنند.
 */
const SIGNALS: Signal[] = [
  // قوی: عملاً قطعی است که پیامک درباره تخفیف است
  { pattern: /تخفیف/, weight: 3, label: 'تخفیف' },
  { pattern: /کوپن/, weight: 3, label: 'کوپن' },
  { pattern: /حراج/, weight: 3, label: 'حراج' },
  { pattern: /کش\s*بک|cashback/i, weight: 3, label: 'کش‌بک' },
  { pattern: /بن\s*(?:خرید|تخفیف)/, weight: 3, label: 'بن خرید' },
  { pattern: /\bآف\b|\boff\b/i, weight: 3, label: 'آف' },
  { pattern: /کد\s*(?:تخفیف|هدیه|معرف|ترویجی)/, weight: 3, label: 'کد تخفیف' },

  // متوسط: احتمال بالا، ولی به تنهایی قطعی نیست
  { pattern: /جشنواره/, weight: 2, label: 'جشنواره' },
  { pattern: /پیشنهاد\s*(?:ویژه|شگفت|لحظه)/, weight: 2, label: 'پیشنهاد ویژه' },
  { pattern: /شگفت\s*انگیز/, weight: 2, label: 'شگفت‌انگیز' },
  { pattern: /فروش\s*ویژه/, weight: 2, label: 'فروش ویژه' },
  { pattern: /هدیه/, weight: 2, label: 'هدیه' },
  { pattern: /رایگان/, weight: 2, label: 'رایگان' },
  { pattern: /اعتبار\s*(?:هدیه|رایگان)/, weight: 2, label: 'اعتبار هدیه' },
  { pattern: /\d+\s*(?:٪|درصد|%)/, weight: 2, label: 'درصد عددی' },
  { pattern: /نیم\s*بها|نصف\s*قیمت/, weight: 2, label: 'نیم‌بها' },

  // ضعیف: فقط در ترکیب با بقیه معنا دارند
  { pattern: /\d{1,3}(?:[,،]\d{3})+\s*(?:تومان|ریال)/, weight: 1, label: 'مبلغ' },
  { pattern: /\d+\s*هزار\s*تومان/, weight: 1, label: 'مبلغ هزار تومان' },
  { pattern: /(?:^|\s)کد[:\s]+[A-Za-z0-9]{3,15}(?:\s|$|\.)/, weight: 1, label: 'کد شبه‌تخفیف' },
  { pattern: /\b[A-Z]{3,}[0-9]{1,4}\b/, weight: 1, label: 'توکن کددار' },
  { pattern: /https?:\/\/|\b\w+\.(?:ir|com|co)\b/i, weight: 1, label: 'لینک' },
  { pattern: /خرید\s*(?:کنید|کن)|سفارش\s*(?:دهید|بده)/, weight: 1, label: 'دعوت به خرید' },
  { pattern: /مهلت|تا\s*پایان|فقط\s*امروز|فقط\s*تا/, weight: 1, label: 'فوریت زمانی' },
  { pattern: /لغو\s*(?:11|۱۱)/, weight: 1, label: 'لغو ۱۱' },
  {
    pattern:
      /اسنپ|تپسی|دیجی\s*کالا|فیلیمو|نماوا|باسلام|اکالا|okala|digikala|snapp|tapsi|filimo|torob|ترب|بانی\s*مد|زرین|علی\s*بابا|اسنپ\s*تریپ|مقصد|شیپور|دیوار/i,
    weight: 1,
    label: 'برند شناخته‌شده',
  },
];

// ── سطح سخت‌گیری ─────────────────────────────────────────────────────────

export type StrictnessLevel = 'relaxed' | 'balanced' | 'strict';

/**
 * آستانه امتیاز برای ارسال به هوش مصنوعی.
 *
 * - relaxed (۲): هر نشانه متوسطی کافی است. کمترین احتمال از دست دادن
 *   تخفیف، بیشترین هزینه.
 * - balanced (۳): یک نشانه قوی، یا ترکیب دو نشانه متوسط. پیش‌فرض.
 * - strict (۵): نیاز به چند نشانه همزمان. کمترین هزینه، احتمال کمی از
 *   دست دادن پیامک‌های با جمله‌بندی غیرمعمول.
 */
export const STRICTNESS_THRESHOLD: Record<StrictnessLevel, number> = {
  relaxed: 2,
  balanced: 3,
  strict: 5,
};

export const STRICTNESS_LABELS: Record<StrictnessLevel, string> = {
  relaxed: 'آزاد — کمترین احتمال از دست دادن تخفیف، هزینه بیشتر',
  balanced: 'متعادل — پیشنهادی',
  strict: 'سخت‌گیر — کمترین هزینه',
};

/** حداقل طول متن؛ پیامک کوتاه‌تر از این عملاً کد تخفیف ندارد */
const MIN_BODY_LENGTH = 25;

/** متن‌های بلندتر از این هنگام ارسال کوتاه می‌شوند تا توکن هدر نرود */
export const MAX_BODY_LENGTH = 600;

// ── ابزار ────────────────────────────────────────────────────────────────

function normalizeDigits(input: string): string {
  const fa = '۰۱۲۳۴۵۶۷۸۹';
  const ar = '٠١٢٣٤٥٦٧٨٩';
  return input.replace(/[۰-۹٠-٩]/g, (ch) => {
    const i = fa.indexOf(ch);
    return i > -1 ? String(i) : String(ar.indexOf(ch));
  });
}

/** یکسان‌سازی متن برای مقایسه و تشخیص تکراری */
export function normalizeBody(body: string): string {
  return normalizeDigits(body)
    .replace(/[‌‏‎]/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/[.,،؛:!؟?]/g, '')
    .trim()
    .toLowerCase();
}

/**
 * اثر انگشت پیامک برای کش.
 *
 * ارقام به # تبدیل می‌شوند تا پیامک‌های یکسانی که فقط مبلغ یا شماره
 * پیگیری‌شان فرق دارد یک اثر انگشت بگیرند و دوباره تحلیل نشوند. کدهای
 * تخفیف حروفی دست‌نخورده می‌مانند تا کد متفاوت، تحلیل جدا بگیرد.
 */
export function fingerprint(sender: string, body: string): string {
  const base = `${sender}|${normalizeBody(body).replace(/\d+/g, '#')}`;
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < base.length; i++) {
    const c = base.charCodeAt(i);
    h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0;
    h2 = Math.imul(h2 + c, 0x85ebca6b) >>> 0;
  }
  return `${h1.toString(36)}${h2.toString(36)}`;
}

function matchesAny(patterns: RegExp[], text: string): boolean {
  return patterns.some((re) => re.test(text));
}

function isPersonalSender(sender: string): boolean {
  const cleaned = normalizeDigits(sender).replace(/[\s\-()]/g, '');
  return PERSONAL_SENDER.test(cleaned);
}

/** امتیاز تخفیف‌بودن متن، به همراه نشانه‌هایی که پیدا شدند */
export function scorePromo(text: string): { score: number; matched: string[] } {
  let score = 0;
  const matched: string[] = [];
  for (const signal of SIGNALS) {
    if (signal.pattern.test(text)) {
      score += signal.weight;
      matched.push(signal.label);
    }
  }
  return { score, matched };
}

// ── دسته‌بندی ────────────────────────────────────────────────────────────

export interface Classification {
  type: SmsType;
  /** آیا مجاز و ارزشمند است که به هوش مصنوعی ارسال شود؟ */
  safeToSend: boolean;
  /** امتیاز نشانه‌های تخفیف */
  score: number;
  /** نشانه‌های پیدا شده، برای شفافیت در رابط کاربری */
  matched: string[];
  reason: string;
}

/**
 * دسته‌بندی یک پیامک، فقط با قواعد محلی و بدون هیچ تماس شبکه‌ای.
 *
 * ترتیب بررسی عمدی است: ابتدا موارد حساس کنار گذاشته می‌شوند، سپس پیام
 * شخصی، و در آخر امتیاز تخفیف محاسبه می‌گردد.
 */
export function classifySms(
  sender: string,
  body: string,
  strictness: StrictnessLevel = 'balanced'
): Classification {
  const text = normalizeDigits(body);
  const empty = { score: 0, matched: [] as string[] };

  if (matchesAny(OTP_PATTERNS, text)) {
    return {
      type: 'banking',
      ...empty,
      safeToSend: false,
      reason: 'رمز یک‌بارمصرف یا کد تایید — روی گوشی باقی می‌ماند',
    };
  }

  if (matchesAny(BANKING_PATTERNS, text)) {
    return {
      type: 'banking',
      ...empty,
      safeToSend: false,
      reason: 'پیامک بانکی یا تراکنش مالی — روی گوشی باقی می‌ماند',
    };
  }

  const { score, matched } = scorePromo(text);
  const threshold = STRICTNESS_THRESHOLD[strictness];
  const personalSender = isPersonalSender(sender);

  // از شماره شخصی فقط وقتی عبور می‌کنیم که نشانه تخفیف قوی باشد؛
  // وگرنه پیام دوستانه‌ای است که نباید از گوشی خارج شود.
  if (personalSender) {
    if (score >= Math.max(threshold, 3)) {
      return {
        type: 'promotional',
        score,
        matched,
        safeToSend: true,
        reason: `از شماره شخصی ولی با نشانه قوی تخفیف (امتیاز ${score})`,
      };
    }
    return {
      type: 'personal',
      score,
      matched,
      safeToSend: false,
      reason: 'پیام شخصی از یک شماره موبایل — روی گوشی باقی می‌ماند',
    };
  }

  if (text.trim().length < MIN_BODY_LENGTH) {
    return {
      type: 'promotional',
      score,
      matched,
      safeToSend: false,
      reason: 'متن کوتاه‌تر از آن است که کد تخفیف داشته باشد',
    };
  }

  if (score >= threshold) {
    return {
      type: 'promotional',
      score,
      matched,
      safeToSend: true,
      reason: `نشانه‌های تخفیف: ${matched.join('، ')} (امتیاز ${score} از آستانه ${threshold})`,
    };
  }

  return {
    type: 'promotional',
    score,
    matched,
    safeToSend: false,
    reason:
      matched.length > 0
        ? `نشانه ضعیف (امتیاز ${score}، آستانه ${threshold}) — ارسال نشد`
        : 'پیامک خدماتی بدون نشانه تخفیف — ارسال نشد',
  };
}

// ── اجرای فیلتر روی کل صندوق ─────────────────────────────────────────────

function toSimSlot(label: string): SimSlot {
  if (/2/.test(label)) return label.includes('(') ? (label as SimSlot) : 'SIM 2';
  return label.includes('(') ? (label as SimSlot) : 'SIM 1';
}

export interface FilterStats {
  total: number;
  promotional: number;
  personal: number;
  banking: number;
  /** حذف‌شده چون امتیازشان به آستانه نرسید */
  belowThreshold: number;
  /** حذف‌شده چون تکرار پیامک دیگری در همین اسکن بودند */
  duplicates: number;
  /** حذف‌شده چون در اسکن‌های قبلی تحلیل شده بودند */
  cached: number;
  /** تعدادی که واقعاً به هوش مصنوعی ارسال می‌شود */
  sentToAi: number;
}

export interface FilterOptions {
  strictness?: StrictnessLevel;
  /** اثر انگشت پیامک‌هایی که قبلاً تحلیل شده‌اند */
  analyzedFingerprints?: Set<string>;
}

/**
 * اجرای فیلتر محلی روی کل صندوق ورودی.
 *
 * سه صافی پشت سر هم: حریم خصوصی، امتیاز تخفیف، و حذف تکراری‌ها.
 * خروجی شامل همه پیامک‌ها (برای نمایش) و فقط زیرمجموعه‌ی ارسالی است.
 */
export function filterInbox(
  messages: NativeSms[],
  options: FilterOptions = {}
): {
  all: RawSms[];
  toAnalyze: RawSms[];
  /** اثر انگشت پیامک‌های ارسالی، برای افزودن به کش پس از تحلیل موفق */
  fingerprints: Map<string, string>;
  stats: FilterStats;
} {
  const strictness = options.strictness ?? 'balanced';
  const cache = options.analyzedFingerprints ?? new Set<string>();

  const all: RawSms[] = [];
  const toAnalyze: RawSms[] = [];
  const fingerprints = new Map<string, string>();
  const seenInThisScan = new Set<string>();

  let belowThreshold = 0;
  let duplicates = 0;
  let cached = 0;

  for (const sms of messages) {
    const sender = sms.sender || 'نامشخص';
    const body = sms.body || '';
    const { type, safeToSend } = classifySms(sender, body, strictness);

    const mapped: RawSms = {
      id: `sms-${sms.id}`,
      sender,
      recipientSim: toSimSlot(sms.simLabel || 'SIM 1'),
      timestamp: new Date(sms.date || Date.now()).toISOString(),
      body,
      type,
      processed: false,
    };
    all.push(mapped);

    if (!safeToSend) {
      if (type === 'promotional') belowThreshold++;
      continue;
    }

    const fp = fingerprint(sender, body);

    // تکراری در همین اسکن: پیامک‌های تبلیغاتی اغلب چند بار فرستاده می‌شوند
    if (seenInThisScan.has(fp)) {
      duplicates++;
      continue;
    }
    // قبلاً تحلیل شده: در اسکن‌های بعدی دوباره پول نمی‌دهیم
    if (cache.has(fp)) {
      cached++;
      continue;
    }

    seenInThisScan.add(fp);
    fingerprints.set(mapped.id, fp);
    toAnalyze.push({ ...mapped, body: body.slice(0, MAX_BODY_LENGTH) });
  }

  const stats: FilterStats = {
    total: all.length,
    promotional: all.filter((m) => m.type === 'promotional').length,
    personal: all.filter((m) => m.type === 'personal').length,
    banking: all.filter((m) => m.type === 'banking').length,
    belowThreshold,
    duplicates,
    cached,
    sentToAi: toAnalyze.length,
  };

  return { all, toAnalyze, fingerprints, stats };
}
