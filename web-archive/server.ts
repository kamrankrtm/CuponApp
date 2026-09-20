import express from "express";
import http from "http";
import path from "path";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI, Type } from "@google/genai";
import dotenv from "dotenv";

dotenv.config();

const app = express();
const PORT = 3000;

app.use(express.json({ limit: "10mb" }));

// Lazy initializer for Gemini client
let geminiClient: GoogleGenAI | null = null;
function getGeminiClient(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return null;
  }
  if (!geminiClient) {
    geminiClient = new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          "User-Agent": "aistudio-build",
        },
      },
    });
  }
  return geminiClient;
}

// Health check
app.get("/api/health", (req, res) => {
  res.json({
    status: "ok",
    hasApiKey: !!process.env.GEMINI_API_KEY,
    timestamp: new Date().toISOString(),
  });
});

// Explicit PWA static endpoints
app.get(["/manifest.json", "/manifest.webmanifest"], (req, res) => {
  res.setHeader("Content-Type", "application/manifest+json; charset=utf-8");
  res.sendFile(path.join(process.cwd(), "public", "manifest.json"));
});

app.get("/sw.js", (req, res) => {
  res.setHeader("Content-Type", "application/javascript; charset=utf-8");
  res.setHeader("Service-Worker-Allowed", "/");
  res.sendFile(path.join(process.cwd(), "public", "sw.js"));
});

// Single SMS or Batch SMS AI Analysis
app.post("/api/analyze-sms", async (req, res) => {
  try {
    const { smsList } = req.body;

    if (!smsList || !Array.isArray(smsList) || smsList.length === 0) {
      return res.status(400).json({ error: "smsList array is required" });
    }

    const ai = getGeminiClient();

    // Fallback heuristic if API key is not yet set
    if (!ai) {
      console.warn("GEMINI_API_KEY not configured, using smart regex/rule-based engine");
      const analyzed = smsList.map((item: any) => analyzeSmsHeuristic(item));
      return res.json({ results: analyzed, engine: "heuristic" });
    }

    // Call Gemini 3.8 Flash for structured extraction
    const prompt = `شما یک دستیار هوشمند استخراج کدهای تخفیف و تفکیک پیامک‌های فارسی هستید.
لیست پیامک‌های زیر را با دقت بررسی کنید:
1. نوع هر پیامک را مشخص کنید:
   - "promotional": اگر تبلیغاتی است، حاوی پیشنهاد خرید، تخفیف، جشنواره یا اعلان تجاری است (معمولاً از شماره‌های سرشماره ۱۰۰۰، ۲۰۰۰، ۳۰۰۰، ۵۰۰۰، ۹۰۰۰ یا نام برندها).
   - "personal": اگر پیام شخصی بین افراد است (معمولاً از شماره‌های ۰۹...).
   - "banking": اگر تراکنش بانکی، رمز پویا، مانده حساب یا واریز/برداشت است.
2. اگر پیامک "promotional" است و دارای کد تخفیف یا پیشنهاد تخفیف‌دار است:
   - نام برند به فارسی (brand) و انگلیسی (brandEn)
   - دسته‌بندی (category): یکی از موارد [غذا و رستوران, تاکسی اینترنتی, فروشگاه آنلاین, فیلم و سریال, سوپرمارکت, خدمات و بیمه, دیگر]
   - slug دسته‌بندی: یکی از [food, transport, ecommerce, entertainment, supermarket, other]
   - کد تخفیف (code): رشته دقیق کد مثل "OFF50", "YALDA", در صورت عدم وجود کد صریح ولی تخفیف بدون کد "بدون کد" درج شود.
   - مقدار تخفیف (discountAmount): مثلا "۵۰,۰۰۰ تومان" یا "۳۰٪"
   - حداقل خرید یا شرایط (minOrder): مثلا "برای خریدهای بالای ۲۰۰ هزار تومان"
   - نحوه و راه گرفتن تخفیف (instructions): به صورت چند کلمه شفاف، کاربر چه کاری باید در اپلیکیشن مربوطه انجام دهد تا این تخفیف اعمال شود.
   - تاریخ انقضا (expiryDateText): مثلا "تا پایان امشب", "۲ روز آینده"
   - وضعیت انقضا (isExpired): با توجه به زمان حال بررسی کنید اگر منقضی شده true وگرنه false.

پیامک‌ها برای تحلیل:
${JSON.stringify(smsList, null, 2)}`;

    // Call Gemini 3.8 Flash with a 10s timeout, falling back smoothly to heuristics if unavailable
    const geminiPromise = ai.models.generateContent({
      model: "gemini-3.8-flash",
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.ARRAY,
          items: {
            type: Type.OBJECT,
            properties: {
              smsId: { type: Type.STRING },
              type: {
                type: Type.STRING,
                description: "promotional, personal, or banking",
              },
              hasPromoCode: { type: Type.BOOLEAN },
              brand: { type: Type.STRING },
              brandEn: { type: Type.STRING },
              category: { type: Type.STRING },
              categorySlug: { type: Type.STRING },
              code: { type: Type.STRING },
              discountAmount: { type: Type.STRING },
              description: { type: Type.STRING },
              minOrder: { type: Type.STRING },
              instructions: { type: Type.STRING },
              expiryDateText: { type: Type.STRING },
              isExpired: { type: Type.BOOLEAN },
            },
            required: ["smsId", "type", "hasPromoCode"],
          },
        },
      },
    });

    const timeoutPromise = new Promise<never>((_, reject) => {
      setTimeout(() => reject(new Error("Gemini request timed out")), 10000);
    });

    const response = await Promise.race([geminiPromise, timeoutPromise]);

    const parsedJson = JSON.parse(response.text || "[]");
    return res.json({ results: parsedJson, engine: "gemini-3.8-flash" });
  } catch (error: any) {
    console.error("Gemini analysis error:", error);
    // Graceful fallback to heuristic extraction
    const fallbackResults = (req.body.smsList || []).map((item: any) =>
      analyzeSmsHeuristic(item)
    );
    return res.json({
      results: fallbackResults,
      engine: "heuristic-fallback",
      error: error.message,
    });
  }
});

// Heuristic rule-based extractor
function analyzeSmsHeuristic(sms: { id: string; sender: string; body: string }) {
  const body = sms.body || "";
  const sender = (sms.sender || "").trim();

  // Detect Personal numbers (e.g. 0912..., 0935..., +989...)
  const isPersonalSender = /^(09|\+989)/.test(sender) && !body.includes("کد تخفیف") && !body.includes("تخفیف");
  
  // Detect Bank
  const isBanking = /بانک|واریز|برداشت|رمز پویا|مانده حساب|حساب|ریال/i.test(body) && !body.includes("تخفیف");

  if (isBanking) {
    return {
      smsId: sms.id,
      type: "banking",
      hasPromoCode: false,
    };
  }

  if (isPersonalSender) {
    return {
      smsId: sms.id,
      type: "personal",
      hasPromoCode: false,
    };
  }

  // Promotional
  let brand = "سایر فروشگاه‌ها";
  let brandEn = "Store";
  let category = "فروشگاه آنلاین";
  let categorySlug = "ecommerce";

  if (/اسنپ[\s‌]?فود/i.test(body)) {
    brand = "اسنپ‌فود";
    brandEn = "SnappFood";
    category = "غذا و رستوران";
    categorySlug = "food";
  } else if (/تپسی[\s‌]?فود/i.test(body)) {
    brand = "تپسی‌فود";
    brandEn = "Tapsi Food";
    category = "غذا و رستوران";
    categorySlug = "food";
  } else if (/دیجی[\s‌]?کالا/i.test(body)) {
    brand = "دیجی‌کالا";
    brandEn = "Digikala";
    category = "فروشگاه آنلاین";
    categorySlug = "ecommerce";
  } else if (/فیلیمو/i.test(body)) {
    brand = "فیلیمو";
    brandEn = "Filimo";
    category = "فیلم و سریال";
    categorySlug = "entertainment";
  } else if (/سینما[\s‌]?تیکت/i.test(body)) {
    brand = "سینماتیکت";
    brandEn = "CinemaTicket";
    category = "تفریح و سینما";
    categorySlug = "entertainment";
  } else if (/اکالا|افق کوروش/i.test(body)) {
    brand = "اکالا";
    brandEn = "Okala";
    category = "سوپرمارکت";
    categorySlug = "supermarket";
  } else if (/باسلام/i.test(body)) {
    brand = "باسلام";
    brandEn = "Basalam";
    category = "فروشگاه آنلاین";
    categorySlug = "ecommerce";
  } else if (/اسنپ[\s‌]?مارکت/i.test(body)) {
    brand = "اسنپ‌مارکت";
    brandEn = "SnappMarket";
    category = "سوپرمارکت";
    categorySlug = "supermarket";
  } else if (/تپسی/i.test(body)) {
    brand = "تپسی";
    brandEn = "Tapsi";
    category = "تاکسی اینترنتی";
    categorySlug = "transport";
  } else if (/اسنپ/i.test(body)) {
    brand = "اسنپ";
    brandEn = "Snapp";
    category = "تاکسی اینترنتی";
    categorySlug = "transport";
  }

  // Extract promo code
  const codeMatch = body.match(/(?:کد(?:\sتخفیف)?[:\s]+)([a-zA-Z0-9_\-]+)/i) ||
                    body.match(/([A-Z0-9]{4,12})/);
  const code = codeMatch ? codeMatch[1].trim() : "PROMO";

  // Extract discount amount
  const discountMatch = body.match(/(\d+[\s‌]*(?:هزار تومان|درصد|٪|تومان))/i) ||
                        body.match(/(\d+٪)/);
  const discountAmount = discountMatch ? discountMatch[1] : "تخفیف ویژه";

  // Extract min order
  const minOrderMatch = body.match(/(?:بالای|حداقل خرید)\s*([۰-۹0-9,]+(?:\s*هزار)?\s*تومان)/i);
  const minOrder = minOrderMatch ? `حداقل خرید ${minOrderMatch[1]}` : "بدون حداقل خرید مشخص";

  // Extract expiry
  const expiryMatch = body.match(/(?:اعتبار تا|مهلت تا|انقضا:?|تا پایان)\s*([^\.\n]+)/i);
  const expiryDateText = expiryMatch ? expiryMatch[1].trim() : "معتبر تا اطلاع ثانوی";

  return {
    smsId: sms.id,
    type: "promotional",
    hasPromoCode: true,
    brand,
    brandEn,
    category,
    categorySlug,
    code,
    discountAmount,
    description: `تخفیف ${discountAmount} ویژه ${brand}`,
    minOrder,
    instructions: `وارد برنامه یا وبسایت ${brand} شوید، کالا یا خدمات مدنظر را به سبد خرید اضافه کنید و در صفحه تسویه‌حساب کد تخفیف را ثبت کنید.`,
    expiryDateText,
    isExpired: false,
  };
}

async function startServer() {
  const server = http.createServer(app);

  if (process.env.NODE_ENV !== "production") {
    const isHmrDisabled = process.env.DISABLE_HMR === "true";
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        hmr: isHmrDisabled ? false : { server },
      },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  server.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
