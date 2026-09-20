import { RawSms } from '../types';

export const INITIAL_SMS_DATA: RawSms[] = [
  {
    id: 'sms-01',
    sender: '10008585', // SnappFood typical shortcode
    recipientSim: 'SIM 1 (همراه اول)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(), // 3 hours ago
    body: 'اسنپ‌فود: ۵۰ هزار تومان تخفیف سفارش شام و ناهار ویژه رستوران‌های منتخب! کد تخفیف: SFSHAM50 برای سفارش بالای ۲۰۰ هزار تومان. اعتبار تا پایان فردا شب. snpfd.ir/app',
    type: 'promotional',
    processed: true,
  },
  {
    id: 'sms-02',
    sender: '90001010', // Tapsi Food
    recipientSim: 'SIM 2 (ایرانسل)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 8).toISOString(), // 8 hours ago
    body: 'تپسی‌فود: طعم واقعی تخفیف! ۳۵٪ تخفیف تا سقف ۶۰,۰۰۰ تومان برای خرید از کافه و شیرینی. کد: SWEET35 - همین الان وارد اپ شو و با انتخاب شیرینی یا کافه در مرحله پرداخت کد را وارد کن. اعتبار تا ۲ روز آینده.',
    type: 'promotional',
    processed: true,
  },
  {
    id: 'sms-03',
    sender: '09123456789', // Personal
    recipientSim: 'SIM 1 (همراه اول)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 14).toISOString(),
    body: 'سلام علی جان، جزوه جلسه قبل رو برام می‌فرستی؟ ممنون میشم تا فردا صبح بفرستی.',
    type: 'personal',
    processed: true,
  },
  {
    id: 'sms-04',
    sender: '20003232', // Digikala
    recipientSim: 'SIM 1 (همراه اول)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 26).toISOString(), // Yesterday
    body: 'دیجی‌کالا: حراج بزرگ آخر فصل شروع شد! ۱۰۰ هزار تومان تخفیف روی سبد خرید بالای ۵۰۰ هزار تومان در دسته‌بندی مد و پوشاک. کد تخفیف: DKSTYLE100 . مهلت استفاده تا پایان جمعه.',
    type: 'promotional',
    processed: true,
  },
  {
    id: 'sms-05',
    sender: 'BankMellat', // Banking
    recipientSim: 'SIM 2 (ایرانسل)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 30).toISOString(),
    body: 'بانک ملت: واریز به حساب ۶۷۲۳... مبلغ: ۴,۵۰۰,۰۰۰ ریال از طرف پایا. موجودی جدید: ۲۱,۴۵۰,۰۰۰ ریال. ساعت ۱۴:۳۰',
    type: 'banking',
    processed: true,
  },
  {
    id: 'sms-06',
    sender: '30008888', // Filimo
    recipientSim: 'SIM 2 (ایرانسل)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(), // 2 days ago
    body: 'فیلیمو: اکران آنلاین فیلم پرمخاطب سال آغاز شد! ۴۰ درصد تخفیف خرید اشتراک ۳ ماهه فیلیمو با کد تخفیف: MOVIE40 . برای تماشا وارد اپلیکیشن فیلیمو شوید، اشتراک ۳ ماهه را انتخاب کرده و کد را در بخش کوپن وارد نمایید. انقضا: ۴۸ ساعت آینده.',
    type: 'promotional',
    processed: true,
  },
  {
    id: 'sms-07',
    sender: '09351112233', // Personal
    recipientSim: 'SIM 2 (ایرانسل)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 55).toISOString(),
    body: 'مامان: پسرم رسیدی خبر بده نگران نشم. کلید یدک هم روی جاکفشیه.',
    type: 'personal',
    processed: true,
  },
  {
    id: 'sms-08',
    sender: '50004545', // Okala (Supermarket)
    recipientSim: 'SIM 1 (همراه اول)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(), // 3 days ago
    body: 'اکالا: یخچال پر از تخفیف! ۷۰ هزار تومان تخفیف خرید اول از سوپرمارکت اکالا و افق کوروش برای خریدهای بالای ۲۵۰ هزار تومان. کد: OKL70 . ارسال رایگان سفارش اول. مهلت استفاده فقط ۳ روز.',
    type: 'promotional',
    processed: true,
  },
  {
    id: 'sms-09',
    sender: '10004455', // CinemaTicket
    recipientSim: 'SIM 1 (همراه اول)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 96).toISOString(), // 4 days ago
    body: 'سینماتیکت: سه‌شنبه‌های نیم‌بها سینماها رو از دست نده! به علاوه ۲۰ درصد تخفیف مضاعف با کد: CINE20 روی بلیت تمام پردیس‌های سینمایی کشور. انقضا: تا سه‌شنبه ساعت ۲۴.',
    type: 'promotional',
    processed: true,
  },
  {
    id: 'sms-10',
    sender: '90008899', // SnappMarket
    recipientSim: 'SIM 2 (ایرانسل)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 120).toISOString(), // 5 days ago
    body: 'اسنپ‌مارکت: سبد هفتگیت رو ارزان بخر! ۴۵ هزار تومان تخفیف خرید میوه و سبزیجات هایپراستار. حداقل خرید ۱۸۰ هزار تومان. کد تخفیف: FRESH45 . اعتبار تا آخر این هفته.',
    type: 'promotional',
    processed: true,
  },
  {
    id: 'sms-11',
    sender: '09198765432', // Personal
    recipientSim: 'SIM 1 (همراه اول)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 150).toISOString(),
    body: 'سلام، فردا ساعت ۵ عصر جلسه کاری تشکیل میشه، حتما لپ‌تاپ همراهت باشه.',
    type: 'personal',
    processed: true,
  },
  {
    id: 'sms-12',
    sender: '20004949', // Basalam
    recipientSim: 'SIM 2 (ایرانسل)',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 180).toISOString(),
    body: 'باسلام: دست‌سازه‌ها و محصولات محلی با تخفیف ویژه! ۶۰ هزار تومان تخفیف خرید بالای ۲۵۰ هزار تومان از تمام غرفه‌ها. کد: SALAM60 . مهلت تا پایان هفته.',
    type: 'promotional',
    processed: true,
  }
];
