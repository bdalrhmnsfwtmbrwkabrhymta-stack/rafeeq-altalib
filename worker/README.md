# 🚀 Cloudflare Worker Webhook — بوت "رفيق الطالب" (24/7 Free Deployment)

دليل تشغيل ونشر الـ **Cloudflare Worker** ليعمل بوت التليجرام الرسمي (`@RafeeqAlTalebBot`) بنظام **Telegram Webhook** على مدار 24 ساعة مجاناً بالكامل دون الحاجة إلى تشغيل جهاز محلي ودون تكاليف إضافية ضمن حدود باقة Cloudflare Workers Free (100,000 طلب يومياً).

---

## 🏗️ الهيكلية البرمجية (System Flow)

```
Android App
      ↓
Firebase / Backend
      ↓
Telegram Account Linking (Deep Link with secure single-use token)
      ↓
Telegram Bot (@RafeeqAlTalebBot)
      ↓
Cloudflare Worker (POST /telegram/webhook with X-Telegram-Bot-Api-Secret-Token)
      ↓
Existing Telegram Bot Logic & Storage
      ↓
Digital Storage & Database (SQLite / D1 / KV / Telegram Media Storage)
```

---

## 🔒 حماية الـ Secrets (أمان التوكن)

لا يتم وضع `BOT_TOKEN` أو `SECRET_TOKEN` داخل الكود. يتم تعيينها كـ Cloudflare Secrets:

```bash
# 1. الدخول لمجلد الـ Worker
cd worker

# 2. تعيين توكن البوت
npx wrangler secret put TELEGRAM_BOT_TOKEN

# 3. تعيين رمز الأمان المشفر للـ Webhook
npx wrangler secret put TELEGRAM_SECRET_TOKEN
```

---

## 📡 نقاط النهاية (Endpoints)

| النقطة (Endpoint) | الطريقة (Method) | الوصف |
|---|---|---|
| `/telegram/webhook` | `POST` | استقبال وتأكيد تحديثات Telegram مع فحص `X-Telegram-Bot-Api-Secret-Token` |
| `/health` | `GET` | فحص صحة الخادم ومراقبة عدد الطلبات اليومية وحدود الباقة المجانية |
| `/api/link-token` | `POST` | إنشاء Deep Link token آمن أحادي الاستخدام لربط حساب الطالب في التطبيق |
| `/api/link-status` | `GET` | التحقق من حالة ربط رقم هاتف الطالب وحفظ `telegram_chat_id` |
| `/api/send-message` | `POST` | إرسال رسائل أو تقارير للمستخدم عبر `chat_id` مباشرة من التطبيق/السيرفر |
| `/api/set-webhook` | `POST` | تسجيل رابط الـ Webhook لدى سيرفرات Telegram Bot API تلقائياً |

---

## 🚀 طريقة النشر الفوري (Deployment Steps)

```bash
# 1. تثبيت الحزم
npm install

# 2. نشر الـ Worker على Cloudflare
npm run deploy

# 3. تفعيل الـ Webhook مع تليجرام
curl -X POST https://rafeeq-telegram-worker.<your-subdomain>.workers.dev/api/set-webhook
```

---

## ⏰ المهام المجدولة السحابية 24/7 (Cron Triggers)

يعمل مجدول المهام المدمج في الـ Worker تلقائياً بدون سيرفرات إضافية:
- 🌅 **أذكار الصباح:** يومياً الساعة 7:00 صباحاً بتوقيت القاهرة (`0 5 * * *` UTC).
- 🌆 **أذكار المساء:** يومياً الساعة 5:00 مساءً بتوقيت القاهرة (`0 15 * * *` UTC).
- 🕌 **تذكير ليلة الجمعة:** الخميس الساعة 8:00 مساءً (`0 18 * * 4` UTC).
- 📖 **تذكير يوم الجمعة وسورة الكهف:** الجمعة 8:30 صباحاً (`30 6 * * 5` UTC).
- 📊 **تقرير الإنجاز اليومي:** منتصف الليل 00:00 (`0 22 * * *` UTC).
