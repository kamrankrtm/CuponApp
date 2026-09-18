import type { PromoCode, RawSms } from '../types';
import type { StrictnessLevel } from './smsFilter';

/**
 * کلاینت هوش مصنوعی.
 *
 * مستقیم با AvalAI (اول‌ای‌آی) که API سازگار با OpenAI دارد صحبت می‌کند،
 * بنابراین اپ به هیچ سرور واسطی نیاز ندارد. کلید API فقط روی خود گوشی
 * ذخیره می‌شود و در مخزن کد یا بیلد قرار نمی‌گیرد.
 */

export const DEFAULT_BASE_URL = 'https://api.avalai.ir/v1';

/**
 * مدل پیش‌فرض: ارزان‌ترین گزینه‌ای که هم فارسی را خوب می‌فهمد و هم
 * خروجی JSON ساختاریافته می‌دهد. اگر در حساب شما در دسترس نبود،
 * از صفحه تنظیمات مدل دیگری انتخاب کنید.
 */
export const DEFAULT_MODEL = 'gemini-2.5-flash-lite';

/** پیشنهادهای مدل، از ارزان به گران */
export const SUGGESTED_MODELS = [
  { id: 'gemini-2.5-flash-lite', label: 'Gemini 2.5 Flash Lite — ارزان‌ترین، پیشنهادی' },
  { id: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash — دقیق‌تر، کمی گران‌تر' },
  { id: 'gpt-5-mini', label: 'GPT-5 mini — جایگزین OpenAI' },
  { id: 'gpt-4.1-mini', label: 'GPT-4.1 mini — جایگزین ارزان OpenAI' },
];

export interface AiSettings {
  apiKey: string;
  model: string;
  baseUrl: string;
  /** سطح سخت‌گیری فیلتر محلی — تعیین می‌کند چه چیزی ارزش ارسال دارد */
  strictness: StrictnessLevel;
}

export const DEFAULT_SETTINGS: AiSettings = {
  apiKey: '',
  model: DEFAULT_MODEL,
  baseUrl: DEFAULT_BASE_URL,
  strictness: 'balanced',
};

const SYSTEM_PROMPT = `تو یک دستیار دقیق استخراج کد تخفیف از پیامک‌های تبلیغاتی فارسی هستی.
برای هر پیامک ورودی، خروجی JSON بده.

قواعد:
- اگر پیامک هیچ تخفیف یا پیشنهاد تخفیف‌داری ندارد، hasPromoCode را false بگذار و بقیه فیلدها را خالی رها کن.
- code باید دقیقاً همان رشته کد تخفیف در متن باشد. اگر تخفیف بدون کد است، مقدار "بدون کد" بگذار. کد را از خودت نساز.
- discountAmount مثل "۵۰,۰۰۰ تومان" یا "۳۰٪".
- categorySlug فقط یکی از این‌ها: food, transport, ecommerce, entertainment, supermarket, other.
- instructions باید کوتاه و عملی باشد: کاربر دقیقاً چه کاری کند تا تخفیف اعمال شود.
- expiryDateText عیناً از متن پیامک برداشته شود، مثل "تا پایان امشب" یا "تا ۱۵ آذر".
- isExpired را با توجه به تاریخ امروز که در پیام کاربر آمده تعیین کن.
- هیچ اطلاعاتی را حدس نزن؛ چیزی که در متن نیست را ننویس.

پاسخ را فقط به صورت یک شیء JSON با کلید "results" که آرایه‌ای از نتایج است بده.`;

export interface AiResult {
  smsId: string;
  hasPromoCode: boolean;
  brand?: string;
  brandEn?: string;
  category?: string;
  categorySlug?: PromoCode['categorySlug'];
  code?: string;
  discountAmount?: string;
  description?: string;
  minOrder?: string;
  instructions?: string;
  expiryDateText?: string;
  isExpired?: boolean;
}

const RESPONSE_SCHEMA = {
  type: 'object',
  properties: {
    results: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          smsId: { type: 'string' },
          hasPromoCode: { type: 'boolean' },
          brand: { type: 'string' },
          brandEn: { type: 'string' },
          category: { type: 'string' },
          categorySlug: {
            type: 'string',
            enum: ['food', 'transport', 'ecommerce', 'entertainment', 'supermarket', 'other'],
          },
          code: { type: 'string' },
          discountAmount: { type: 'string' },
          description: { type: 'string' },
          minOrder: { type: 'string' },
          instructions: { type: 'string' },
          expiryDateText: { type: 'string' },
          isExpired: { type: 'boolean' },
        },
        required: ['smsId', 'hasPromoCode'],
        additionalProperties: false,
      },
    },
  },
  required: ['results'],
  additionalProperties: false,
} as const;

export class AiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
    this.name = 'AiError';
  }
}

function authHeaders(settings: AiSettings): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${settings.apiKey}`,
  };
}

/** گرفتن فهرست مدل‌های در دسترس حساب کاربر */
export async function listModels(settings: AiSettings): Promise<string[]> {
  const res = await fetch(`${settings.baseUrl.replace(/\/$/, '')}/models`, {
    headers: authHeaders(settings),
  });
  if (!res.ok) {
    throw new AiError(await describeError(res), res.status);
  }
  const data = await res.json();
  const ids: string[] = (data?.data ?? []).map((m: any) => m?.id).filter(Boolean);
  return ids.sort();
}

/** تست سریع اتصال و اعتبار کلید */
export async function testConnection(settings: AiSettings): Promise<{ ok: true; modelCount: number }> {
  const models = await listModels(settings);
  return { ok: true, modelCount: models.length };
}

async function describeError(res: Response): Promise<string> {
  let detail = '';
  try {
    const body = await res.json();
    detail = body?.error?.message || body?.message || '';
  } catch {
    /* بدنه JSON نبود */
  }
  if (res.status === 401 || res.status === 403) {
    return `کلید API پذیرفته نشد (${res.status}). کلید را در تنظیمات بررسی کنید. ${detail}`.trim();
  }
  if (res.status === 404) {
    return `مدل انتخابی در دسترس نیست (۴۰۴). از تنظیمات مدل دیگری انتخاب کنید. ${detail}`.trim();
  }
  if (res.status === 429) {
    return `محدودیت نرخ یا اتمام اعتبار حساب (۴۲۹). ${detail}`.trim();
  }
  return `خطای سرویس هوش مصنوعی (${res.status}). ${detail}`.trim();
}

/** استخراج JSON از پاسخ مدل، حتی اگر داخل بلوک markdown پیچیده شده باشد */
function parseModelJson(content: string): AiResult[] {
  const cleaned = content
    .replace(/^\s*```(?:json)?/i, '')
    .replace(/```\s*$/, '')
    .trim();

  let parsed: any;
  try {
    parsed = JSON.parse(cleaned);
  } catch {
    // آخرین تلاش: اولین شیء JSON موجود در متن را جدا کن
    const match = cleaned.match(/\{[\s\S]*\}/);
    if (!match) throw new AiError('پاسخ هوش مصنوعی قابل خواندن نبود');
    parsed = JSON.parse(match[0]);
  }

  if (Array.isArray(parsed)) return parsed;
  if (Array.isArray(parsed?.results)) return parsed.results;
  return [];
}

/**
 * تحلیل یک دسته پیامک.
 *
 * ورودی باید از قبل با فیلتر محلی پالایش شده باشد؛ این تابع هر چه بگیرد
 * را ارسال می‌کند و خودش دسته‌بندی حریم خصوصی انجام نمی‌دهد.
 */
export async function analyzeBatch(
  settings: AiSettings,
  messages: Pick<RawSms, 'id' | 'sender' | 'body'>[],
  signal?: AbortSignal
): Promise<AiResult[]> {
  if (!settings.apiKey) {
    throw new AiError('کلید API تنظیم نشده است. به تنظیمات بروید و کلید اول‌ای‌آی را وارد کنید.');
  }
  if (messages.length === 0) return [];

  const today = new Date().toISOString().slice(0, 10);
  const userContent = `تاریخ امروز: ${today}

پیامک‌ها:
${JSON.stringify(
  messages.map((m) => ({ smsId: m.id, sender: m.sender, body: m.body })),
  null,
  1
)}`;

  const res = await fetch(`${settings.baseUrl.replace(/\/$/, '')}/chat/completions`, {
    method: 'POST',
    headers: authHeaders(settings),
    signal,
    body: JSON.stringify({
      model: settings.model,
      temperature: 0,
      messages: [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: userContent },
      ],
      response_format: {
        type: 'json_schema',
        json_schema: {
          name: 'promo_extraction',
          strict: true,
          schema: RESPONSE_SCHEMA,
        },
      },
    }),
  });

  if (!res.ok) {
    // برخی مدل‌ها json_schema را پشتیبانی نمی‌کنند؛ با json_object دوباره تلاش کن
    if (res.status === 400 || res.status === 422) {
      return analyzeBatchJsonObject(settings, messages, userContent, signal);
    }
    throw new AiError(await describeError(res), res.status);
  }

  const data = await res.json();
  const content = data?.choices?.[0]?.message?.content ?? '';
  return parseModelJson(content);
}

/** مسیر جایگزین برای مدل‌هایی که فقط json_object را پشتیبانی می‌کنند */
async function analyzeBatchJsonObject(
  settings: AiSettings,
  _messages: Pick<RawSms, 'id' | 'sender' | 'body'>[],
  userContent: string,
  signal?: AbortSignal
): Promise<AiResult[]> {
  const res = await fetch(`${settings.baseUrl.replace(/\/$/, '')}/chat/completions`, {
    method: 'POST',
    headers: authHeaders(settings),
    signal,
    body: JSON.stringify({
      model: settings.model,
      temperature: 0,
      messages: [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: userContent },
      ],
      response_format: { type: 'json_object' },
    }),
  });

  if (!res.ok) {
    throw new AiError(await describeError(res), res.status);
  }

  const data = await res.json();
  const content = data?.choices?.[0]?.message?.content ?? '';
  return parseModelJson(content);
}

/**
 * تحلیل کل صندوق ورودی به صورت دسته‌های کوچک.
 *
 * دسته‌بندی کردن هم جلوی طولانی شدن بیش از حد درخواست را می‌گیرد و هم
 * باعث می‌شود یک خطا کل اسکن را از بین نبرد.
 */
export async function analyzeAll(
  settings: AiSettings,
  messages: Pick<RawSms, 'id' | 'sender' | 'body'>[],
  options: {
    batchSize?: number;
    onProgress?: (done: number, total: number) => void;
    signal?: AbortSignal;
  } = {}
): Promise<{ results: AiResult[]; errors: string[] }> {
  const batchSize = options.batchSize ?? 15;
  const results: AiResult[] = [];
  const errors: string[] = [];

  for (let i = 0; i < messages.length; i += batchSize) {
    const batch = messages.slice(i, i + batchSize);
    try {
      const batchResults = await analyzeBatch(settings, batch, options.signal);
      results.push(...batchResults);
    } catch (e: any) {
      if (e?.name === 'AbortError') throw e;
      errors.push(e?.message ?? String(e));
    }
    options.onProgress?.(Math.min(i + batchSize, messages.length), messages.length);
  }

  return { results, errors };
}

/** ساخت مدل نهایی کد تخفیف از خروجی هوش مصنوعی و پیامک اصلی */
export function toPromoCode(result: AiResult, sms: RawSms): PromoCode {
  return {
    id: `promo-${sms.id}`,
    smsId: sms.id,
    brand: result.brand || 'نامشخص',
    brandEn: result.brandEn || 'Unknown',
    category: result.category || 'دیگر',
    categorySlug: result.categorySlug || 'other',
    code: result.code || 'بدون کد',
    discountAmount: result.discountAmount || 'تخفیف',
    description: result.description || '',
    minOrder: result.minOrder,
    instructions: result.instructions || 'در مرحله تسویه‌حساب کد را وارد کنید.',
    expiryDateText: result.expiryDateText || 'نامشخص',
    isExpired: result.isExpired ?? false,
    status: 'active',
    sender: sms.sender,
    recipientSim: sms.recipientSim,
    originalSmsBody: sms.body,
    receivedAt: sms.timestamp,
  };
}
