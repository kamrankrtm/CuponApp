/**
 * بررسی نسخه جدید اپ.
 *
 * هر بار که روی گیت‌هاب نسخه تازه‌ای منتشر شود، اپ آن را می‌بیند و به کاربر
 * پیشنهاد نصب می‌دهد. اندروید اجازه نمی‌دهد یک اپ معمولی خودش را بی‌صدا
 * جایگزین کند، پس بیشترین کاری که می‌شود کرد این است: تشخیص نسخه جدید و
 * باز کردن فایل APK در مرورگر تا نصب‌کننده سیستم کار را تمام کند.
 */

export const REPO_OWNER = 'kamrankrtm';
export const REPO_NAME = 'CuponApp';

/** نسخه‌ای که هنگام بیلد داخل اپ جاسازی می‌شود */
export const APP_VERSION: string =
  typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : '0.0.0';

export const RELEASES_PAGE = `https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/latest`;

export interface UpdateInfo {
  version: string;
  apkUrl: string;
  releaseUrl: string;
  notes: string;
  publishedAt: string;
}

/**
 * مقایسه دو نسخه به سبک semver ساده: «1.0.12» جدیدتر از «1.0.9» است.
 * مقایسه رشته‌ای اینجا کار نمی‌کند، چون "9" > "12" می‌شود.
 */
export function isNewerVersion(candidate: string, current: string): boolean {
  const parse = (v: string) =>
    v
      .replace(/^v/i, '')
      .split(/[.\-+]/)
      .map((p) => parseInt(p, 10))
      .map((n) => (Number.isFinite(n) ? n : 0));

  const a = parse(candidate);
  const b = parse(current);
  const len = Math.max(a.length, b.length);

  for (let i = 0; i < len; i++) {
    const x = a[i] ?? 0;
    const y = b[i] ?? 0;
    if (x > y) return true;
    if (x < y) return false;
  }
  return false;
}

/**
 * گرفتن آخرین نسخه منتشرشده از گیت‌هاب.
 *
 * برای مخزن عمومی نیازی به توکن نیست. اگر مخزن خصوصی باشد، گیت‌هاب ۴۰۴
 * برمی‌گرداند و کاربر باید یک توکن فقط-خواندنی در تنظیمات وارد کند.
 */
export async function fetchLatestRelease(token?: string): Promise<UpdateInfo | null> {
  const headers: Record<string, string> = {
    Accept: 'application/vnd.github+json',
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(
    `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest`,
    { headers }
  );

  if (res.status === 404) {
    throw new UpdateError(
      token
        ? 'نسخه‌ای پیدا نشد یا توکن دسترسی ندارد.'
        : 'مخزن خصوصی است. برای بررسی خودکار نسخه، یک توکن گیت‌هاب در تنظیمات وارد کنید یا مخزن را عمومی کنید.'
    );
  }
  if (res.status === 401 || res.status === 403) {
    throw new UpdateError('توکن گیت‌هاب پذیرفته نشد یا محدودیت نرخ فعال است.');
  }
  if (!res.ok) {
    throw new UpdateError(`بررسی نسخه ناموفق بود (${res.status}).`);
  }

  const data = await res.json();
  const apkAsset = (data.assets ?? []).find((a: any) =>
    String(a?.name ?? '').endsWith('.apk')
  );

  if (!apkAsset) return null;

  return {
    version: String(data.tag_name ?? '').replace(/^v/i, ''),
    apkUrl: apkAsset.browser_download_url,
    releaseUrl: data.html_url ?? RELEASES_PAGE,
    notes: String(data.body ?? '').slice(0, 400),
    publishedAt: data.published_at ?? '',
  };
}

export class UpdateError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UpdateError';
  }
}

/** آیا نسخه منتشرشده از نسخه نصب‌شده جلوتر است؟ */
export async function checkForUpdate(token?: string): Promise<UpdateInfo | null> {
  const latest = await fetchLatestRelease(token);
  if (!latest) return null;
  return isNewerVersion(latest.version, APP_VERSION) ? latest : null;
}
