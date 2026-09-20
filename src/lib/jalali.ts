/**
 * تبدیل تاریخ به تقویم شمسی (جلالی) با ارقام فارسی
 */

const FA_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];

export function toFaDigits(input: string | number): string {
  return String(input).replace(/\d/g, (d) => FA_DIGITS[parseInt(d, 10)] || d);
}

/**
 * تبدیل تاریخ به عبارت شمسی
 * مثال: "۳۰ شهریور ۱۴۰۵"
 */
export function formatJalaliDate(dateInput: string | Date | number): string {
  if (!dateInput) return '';
  const d = new Date(dateInput);
  if (Number.isNaN(d.getTime())) return '';

  try {
    const formatter = new Intl.DateTimeFormat('fa-IR-u-ca-persian', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
    return formatter.format(d);
  } catch {
    return d.toLocaleDateString('fa-IR');
  }
}

/**
 * تبدیل تاریخ به شمسی با ساعت و دقیقه
 * مثال: "۳۰ شهریور ۱۴۰۵ - ۱۴:۳۰"
 */
export function formatJalaliDateTime(dateInput: string | Date | number): string {
  if (!dateInput) return '';
  const d = new Date(dateInput);
  if (Number.isNaN(d.getTime())) return '';

  try {
    const dateStr = new Intl.DateTimeFormat('fa-IR-u-ca-persian', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    }).format(d);

    const timeStr = new Intl.DateTimeFormat('fa-IR', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(d);

    return `${dateStr} - ${timeStr}`;
  } catch {
    return d.toLocaleString('fa-IR');
  }
}

/**
 * فرمت کوتای شمسی یا نسبی برای پیامک‌ها
 * مثال: "امروز ۱۴:۲۰" / "دیروز" / "۲۵ شهریور"
 */
export function formatRelativeJalali(dateInput: string | Date | number): string {
  if (!dateInput) return '';
  const d = new Date(dateInput);
  if (Number.isNaN(d.getTime())) return '';

  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  const timeStr = new Intl.DateTimeFormat('fa-IR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(d);

  if (diffDays === 0 && d.getDate() === now.getDate()) {
    return `امروز ${timeStr}`;
  }
  if (diffDays <= 1) {
    return `دیروز ${timeStr}`;
  }
  if (diffDays < 7) {
    return `${toFaDigits(diffDays)} روز پیش`;
  }

  try {
    return new Intl.DateTimeFormat('fa-IR-u-ca-persian', {
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d);
  } catch {
    return d.toLocaleDateString('fa-IR');
  }
}
