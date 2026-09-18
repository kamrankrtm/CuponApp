/**
 * محاسبه تاریخ انقضای کد تخفیف.
 *
 * مشکلی که این ماژول حل می‌کند: پیامک‌ها انقضا را نسبی می‌نویسند («تا امشب»،
 * «۲ روز آینده») و این عبارت‌ها فقط نسبت به **زمان دریافت همان پیامک** معنا
 * دارند، نه نسبت به امروز. علاوه بر آن، انقضا نباید یک بولین منجمد باشد که
 * لحظه اسکن ثبت شود؛ باید هر بار هنگام نمایش از نو سنجیده شود تا کدها
 * خودبه‌خود با گذشت زمان منقضی شوند.
 */

const DAY_MS = 24 * 60 * 60 * 1000;

function normalizeDigits(input: string): string {
  const fa = '۰۱۲۳۴۵۶۷۸۹';
  const ar = '٠١٢٣٤٥٦٧٨٩';
  return input.replace(/[۰-۹٠-٩]/g, (ch) => {
    const i = fa.indexOf(ch);
    return i > -1 ? String(i) : String(ar.indexOf(ch));
  });
}

/** پایان روزِ یک تاریخ (۲۳:۵۹:۵۹ به وقت محلی) */
function endOfDay(d: Date): Date {
  const out = new Date(d);
  out.setHours(23, 59, 59, 999);
  return out;
}

// ── تبدیل تاریخ شمسی به میلادی ───────────────────────────────────────────

const JALALI_MONTHS: Record<string, number> = {
  فروردین: 1, اردیبهشت: 2, خرداد: 3, تیر: 4, مرداد: 5, شهریور: 6,
  مهر: 7, آبان: 8, آذر: 9, دی: 10, بهمن: 11, اسفند: 12,
};

/** الگوریتم استاندارد تبدیل تاریخ جلالی به میلادی */
function jalaliToGregorian(jy: number, jm: number, jd: number): Date {
  let gy = jy <= 979 ? 621 : 1600;
  const jyAdj = jy <= 979 ? jy : jy - 979;

  let days =
    365 * jyAdj +
    Math.floor(jyAdj / 33) * 8 +
    Math.floor(((jyAdj % 33) + 3) / 4) +
    78 +
    jd +
    (jm < 7 ? (jm - 1) * 31 : (jm - 7) * 30 + 186);

  gy += 400 * Math.floor(days / 146097);
  days %= 146097;
  if (days > 36524) {
    gy += 100 * Math.floor(--days / 36524);
    days %= 36524;
    if (days >= 365) days++;
  }
  gy += 4 * Math.floor(days / 1461);
  days %= 1461;
  if (days > 365) {
    gy += Math.floor((days - 1) / 365);
    days = (days - 1) % 365;
  }

  let gd = days + 1;
  const isLeap = (gy % 4 === 0 && gy % 100 !== 0) || gy % 400 === 0;
  const monthLengths = [0, 31, isLeap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

  let gm = 0;
  for (gm = 1; gm <= 12 && gd > monthLengths[gm]; gm++) {
    gd -= monthLengths[gm];
  }

  return new Date(gy, gm - 1, gd, 23, 59, 59, 999);
}

/** سال جلالی جاری، برای وقتی پیامک فقط «۲۵ شهریور» نوشته و سال نداده */
function currentJalaliYear(at: Date): number {
  const fmt = new Intl.DateTimeFormat('en-u-ca-persian', { year: 'numeric' });
  const parsed = parseInt(fmt.format(at).replace(/\D/g, ''), 10);
  return Number.isFinite(parsed) ? parsed : 1405;
}

// ── تفسیر عبارت‌های انقضا ────────────────────────────────────────────────

/**
 * تبدیل متن انقضا به تاریخ واقعی، با لنگرِ زمان دریافت پیامک.
 *
 * فقط عبارت‌هایی را تفسیر می‌کند که مطمئن است؛ برای بقیه undefined
 * برمی‌گرداند تا کد به اشتباه منقضی اعلام نشود.
 */
export function parseExpiry(expiryText: string, receivedAt: string | Date): Date | undefined {
  if (!expiryText) return undefined;

  const received = new Date(receivedAt);
  if (Number.isNaN(received.getTime())) return undefined;

  const text = normalizeDigits(expiryText).replace(/‌/g, ' ').trim();

  // «امشب»، «تا پایان امروز»، «تا پایان روز»
  if (/(امشب|پایان\s*امروز|پایان\s*روز|همین\s*امروز|آخر\s*امروز)/.test(text)) {
    return endOfDay(received);
  }

  // «پس فردا» پیش از «فردا» بررسی می‌شود تا با آن اشتباه نگیرد
  if (/پس\s*فردا/.test(text)) {
    return endOfDay(new Date(received.getTime() + 2 * DAY_MS));
  }
  if (/فردا/.test(text)) {
    return endOfDay(new Date(received.getTime() + DAY_MS));
  }

  // «تا پایان هفته»
  if (/پایان\s*(?:این\s*)?هفته|آخر\s*هفته/.test(text)) {
    return endOfDay(new Date(received.getTime() + 7 * DAY_MS));
  }

  // «۳ روز آینده»، «تا ۵ روز دیگر»
  const daysMatch = text.match(/(\d+)\s*روز/);
  if (daysMatch) {
    const n = parseInt(daysMatch[1], 10);
    if (n > 0 && n < 365) {
      return endOfDay(new Date(received.getTime() + n * DAY_MS));
    }
  }

  // «۲ هفته»
  const weeksMatch = text.match(/(\d+)\s*هفته/);
  if (weeksMatch) {
    const n = parseInt(weeksMatch[1], 10);
    if (n > 0 && n < 54) {
      return endOfDay(new Date(received.getTime() + n * 7 * DAY_MS));
    }
  }

  // «تا پایان ماه»
  if (/پایان\s*(?:این\s*)?ماه/.test(text)) {
    return endOfDay(new Date(received.getTime() + 30 * DAY_MS));
  }

  // تاریخ شمسی صریح: «۲۵ شهریور» یا «۲۵ شهریور ۱۴۰۵»
  const jalaliMatch = text.match(
    /(\d{1,2})\s*(فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)\s*(\d{4})?/
  );
  if (jalaliMatch) {
    const day = parseInt(jalaliMatch[1], 10);
    const month = JALALI_MONTHS[jalaliMatch[2]];
    const year = jalaliMatch[3]
      ? parseInt(jalaliMatch[3], 10)
      : currentJalaliYear(received);
    if (day >= 1 && day <= 31 && month) {
      const resolved = jalaliToGregorian(year, month, day);
      // اگر تاریخ خیلی قبل از دریافت پیامک افتاد، احتمالاً سال بعدی است
      if (resolved.getTime() < received.getTime() - 30 * DAY_MS) {
        return jalaliToGregorian(year + 1, month, day);
      }
      return resolved;
    }
  }

  return undefined;
}

/**
 * تاریخ انقضای نهایی یک کد.
 *
 * ترتیب اولویت: تفسیر محلی متن پیامک (قابل اتکاترین، چون لنگر زمانی دقیق
 * دارد)، بعد تاریخی که هوش مصنوعی داده، و اگر هیچ‌کدام نبود undefined.
 */
export function resolveExpiresAt(
  expiryText: string | undefined,
  receivedAt: string,
  aiExpiresAt?: string
): string | undefined {
  const local = parseExpiry(expiryText ?? '', receivedAt);
  if (local) return local.toISOString();

  if (aiExpiresAt) {
    const d = new Date(aiExpiresAt);
    if (!Number.isNaN(d.getTime())) return endOfDay(d).toISOString();
  }

  return undefined;
}

/**
 * آیا این کد همین الان منقضی است؟
 *
 * هر بار هنگام نمایش صدا زده می‌شود، پس کدها با گذشت زمان خودبه‌خود از
 * لیست فعال بیرون می‌روند بدون اینکه دوباره تحلیل شوند.
 */
export function isExpiredNow(expiresAt?: string, now: Date = new Date()): boolean {
  if (!expiresAt) return false; // انقضای نامشخص = محافظه‌کارانه فعال بماند
  const d = new Date(expiresAt);
  if (Number.isNaN(d.getTime())) return false;
  return d.getTime() < now.getTime();
}

/** متن خوانا از وضعیت انقضا، مثل «۳ روز مانده» یا «امروز آخرین روز» */
export function expiryLabel(expiresAt: string | undefined, now: Date = new Date()): {
  text: string;
  tone: 'expired' | 'urgent' | 'soon' | 'normal' | 'unknown';
} {
  if (!expiresAt) return { text: 'انقضا نامشخص', tone: 'unknown' };

  const d = new Date(expiresAt);
  if (Number.isNaN(d.getTime())) return { text: 'انقضا نامشخص', tone: 'unknown' };

  const diff = d.getTime() - now.getTime();
  if (diff < 0) return { text: 'منقضی شده', tone: 'expired' };

  const days = Math.floor(diff / DAY_MS);
  if (days === 0) return { text: 'امروز آخرین روز', tone: 'urgent' };
  if (days === 1) return { text: 'تا فردا', tone: 'urgent' };
  if (days <= 3) return { text: `${days} روز مانده`, tone: 'soon' };
  if (days <= 30) return { text: `${days} روز مانده`, tone: 'normal' };

  return {
    text: new Intl.DateTimeFormat('fa-IR', { day: 'numeric', month: 'long' }).format(d),
    tone: 'normal',
  };
}
