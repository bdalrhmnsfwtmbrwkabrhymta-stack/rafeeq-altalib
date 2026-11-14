/**
 * =============================================================================
 * 🤖 24/7 Cloudflare Worker Webhook for "رفيق الطالب" (Student Companion Bot)
 * =============================================================================
 * Official Bot: @RafeeqAlTalebBot
 * Endpoints:
 * - POST /telegram/webhook (Secure Telegram Webhook with secret-token verification)
 * - GET  /health (System status, uptime, & Cloudflare Free tier limit monitoring)
 * - POST /api/link-token (Generate secure short-lived deep-link token for phone linking)
 * - GET  /api/link-status (Verify Telegram link status for a phone number)
 * - POST /api/send-message (Internal dispatch endpoint for app reports & reminders)
 * - POST /api/set-webhook (Automated webhook registration with Telegram API)
 * =============================================================================
 */

// Worker Types Declarations for Editor Type-Safety
declare global {
  interface KVNamespace {
    get(key: string, type?: any): Promise<any>;
    put(key: string, value: any, options?: any): Promise<any>;
    delete(key: string): Promise<any>;
    list(options?: any): Promise<any>;
  }
  interface D1Database {
    prepare(query: string): any;
    batch(statements: any[]): Promise<any>;
    exec(query: string): Promise<any>;
  }
  interface ScheduledEvent {
    cron: string;
    type?: string;
    scheduledTime?: number;
  }
  interface ExecutionContext {
    waitUntil(promise: Promise<any>): void;
    passThroughOnException?(): void;
  }
}

export interface Env {
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_SECRET_TOKEN?: string;
  API_SECRET_KEY?: string;
  BOT_USERNAME?: string;
  APP_NAME?: string;
  STORAGE_CHANNEL_LINK?: string;
  PLAY_STORE_URL?: string;
  TIMEZONE?: string;
  STORAGE_KV?: KVNamespace;
  DB?: D1Database;
}

// In-Memory Fallback State (Ephemeral for fast serverless execution across worker instances)
interface LinkedUser {
  chat_id: number;
  username: string;
  full_name: string;
  phone_or_id: string;
  app_user_id: string;
  join_date: string;
  is_active: number;
  rated_app: number;
  telegram_linked: number;
  telegram_linked_at: string;
}

interface CloudMediaItem {
  id: number;
  file_id: string;
  file_unique_id: string;
  file_type: string;
  file_name: string;
  file_size: number;
  mime_type: string;
  caption: string;
  category: string;
  uploaded_by: string;
  created_at: string;
}

interface PendingLinkToken {
  token: string;
  app_user_id: string;
  phone: string;
  created_at: number;
  expires_at: number;
}

const memoryUsers = new Map<number, LinkedUser>();
const memoryMedia: CloudMediaItem[] = [];
const memoryLinkTokens = new Map<string, PendingLinkToken>();
let dailyRequestCount = 0;
let lastResetDay = new Date().getUTCDate();

// Default Constants
const DEFAULT_STORAGE_CHANNEL = "https://t.me/+zAsB3Bq2cwRlYjc0";
const DEFAULT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.aistudio.studentcompanion.app";

// =============================================================================
// Islamic & Educational Content Templates (Exact Arabic Copy)
// =============================================================================

const MORNING_ATHKAR_TEXT = `🌅 *أذكار الصباح المباركة - رفيق الطالب* 🎓
━━━━━━━━━━━━━━━━━━━
بسم الله الرحمن الرحيم، أسعد الله صباحك بكل خير وبركة وتفوق دراسي ☀️

✨ *آية الكرسي:*
«اللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ لاَ تَأْخُذُهُ سِنَةٌ وَلاَ نَوْمٌ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الأَرْضِ مَن ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلاَّ بِإِذْنِهِ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ وَلاَ يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلاَّ بِمَا شَاء وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالأَرْضَ وَلاَ يَؤُودُهُ حِفْظُهُمَا وَهُوَ الْعَلِيُّ الْعَظِيمُ»

✨ *المعوذات (الإخلاص، الفلق، الناس) [3 مرات]:*
• قُلْ هُوَ اللَّهُ أَحَدٌ...
• قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ...
• قُلْ أَعُوذُ بِرَبِّ النَّاسِ...

✨ *سيد الاستغفار:*
«اللَّهُمَّ أَنْتَ رَبِّي لاَ إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عهدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنُوبَ إِلاَّ أَنْتَ»

✨ *أذكار التوفيق وانشراح الصدر:*
• «أَصْبَحْنَا وَأَصْبَحَ المُلْكُ لِلَّهِ، وَالحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ المُلْكُ وَلَهُ الحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ»
• «اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا، وَرِزْقًا طَيِّبًا، وَعَمَلًا مُتَقَبَّلًا»
• «بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ العَلِيمُ» (3 مرات)
• «يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ»

💡 *دعاء بداية المذاكرة واليوم الدراسي:*
«اللهم إني أسألك فهم النبيين وحفظ المرسلين وإلهام الملائكة المقربين، اللهم اجعل ألسنتنا عامرة بذكرك وقلوبنا بخشيتك وأسرارنا بطاعتك، إنك على كل شيء قدير» 📚✨`;

const EVENING_ATHKAR_TEXT = `🌆 *أذكار المساء وحفظ النفس - رفيق الطالب* 🎓
━━━━━━━━━━━━━━━━━━━
تقبل الله طاعاتكم وبارك في مسائكم بعد يوم حافل بالجد والاجتهاد 🌙

✨ *آية الكرسي:*
«اللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ لاَ تَأْخُذُهُ سِنَةٌ وَلاَ نَوْمٌ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الأَرْضِ مَن ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلاَّ بِإِذْنِهِ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ وَلاَ يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلاَّ بِمَا شَاء وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالأَرْضَ وَلاَ يَؤُودُهُ حِفْظُهُمَا وَهُوَ الْعَلِيُّ الْعَظِيمُ»

✨ *المعوذات [3 مرات]:*
• سورة الإخلاص
• سورة الفلق
• سورة الناس

✨ *سيد الاستغفار:*
«اللَّهُمَّ أَنْتَ رَبِّي لاَ إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنُوبَ إِلاَّ أَنْتَ»

✨ *أذكار المساء:*
• «أَمْسَيْنَا وَأَمْسَى المُلْكُ لِلَّهِ، وَالحَمْدُ لِلَّهِ، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ»
• «اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ المَصِيرُ»
• «أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ» (3 مرات)
• «بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ العَلِيمُ» (3 مرات)
• «سُبْحَانَ اللَّهِ وَبِحَمْدِهِ» (100 مرة)

💡 *تذكير ختام المساء:*
قم بمراجعة خطتك وإنجازاتك اليومية عبر تطبيق *رفيق الطالب* لتنظيم جدول الغد بكل ثقة وهدوء 📖🌟`;

const SALAWAT_PERIODIC_TEXT = `🌸 *تذكير الصلاة والسلام على رسول الله ﷺ* 🌸
━━━━━━━━━━━━━━━━━━━
قال رسول الله ﷺ: «أَوْلَى النَّاسِ بِي يَوْمَ الْقِيَامَةِ أَكْثَرُهُمْ عَلَيَّ صَلَاةً»

✨ *الصيغة الإبراهيمية المباركة:*
«اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ، اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ»

🌿 صلوا عليه وسلموا تسليماً تطيب به القلوب وتنشرح به الصدور وتتيسر به المذاكرة والامتحانات!`;

const FRIDAY_DAY_REMINDER = `🕌🌟 *جمعة مباركة طيبة من تطبيق رفيق الطالب* 🎓🌟
━━━━━━━━━━━━━━━━━━━
قال رسول الله ﷺ: «إِنَّ مِنْ أَفْضَلِ أَيَّامِكُمْ يَوْمَ الْجُمُعَةِ، فِيهِ خُلِقَ آدَمُ، وَفِيهِ قُبِضَ... فَأَكْثِرُوا عَلَيَّ مِنَ الصَّلَاةِ فِيهِ، فَإِنَّ صَلَاتَكُمْ مَعْرُوضَةٌ عَلَيَّ»

🌿 *سنن يوم الجمعة المستحبة:*
1. 📖 قراءة سورة الكهف وسورة الجمعة والمنافقون.
2. 🌸 الإكثار المستمر من الصلاة والسلام على النبي المختار ﷺ.
3. 🚿 الغسل والتطيب ولبس أحسن الثياب.
4. ⏰ التبكير لصلاة الجمعة والإنصات للخطبة.
5. 🤲 تحري ساعة الاستجابة (آخر ساعة قبل الغروب) والدعاء بالتفوق والنجاح والبركة في العلم.

اللهم صل وسلم وبارك على سيدنا محمد صلاة تفتح لنا بها أبواب الخير والتيسير 🤍`;

const STUDY_GOALS_REMINDER = `🎯 *تذكير الأهداف الدراسية وجلسات التركيز اليومية* ⏰
━━━━━━━━━━━━━━━━━━━
عزيزي الطالب المجتهد:
نصف يومك يبدأ الآن، والهدف الذي كتبته في بداية يومك بانتظار إنجازك!

💡 *خطوات عملية للتفوق الآن:*
1. 📴 تفعيل وضع التركيز (Focus Mode) وإبعاد المشتتات.
2. ⏱️ بدء جلسة بومودورو عبر التطبيق (25 دقيقة تركيز تام).
3. 💧 كوب ماء نقي لإنعاش الذاكرة.
4. 📝 تدوين النقاط الصعبة في كشكول الملاحظات.

«وقل اعملوا فسيرى الله عملكم ورسوله والمؤمنون» — انطلق واصنع فخرك وفخر والديك! 🌟`;

const DAILY_MIDNIGHT_SUMMARY = `📊 *ملخص الإنجاز والتقرير اليومي - رفيق الطالب* 🎓
━━━━━━━━━━━━━━━━━━━
أحسنت صنعاً وجهداً اليوم! مع دقات منتصف الليل، حان وقت تقييم مسيرتك:

✅ *قائمة الإنجاز والتقييم اليومي:*
🕌 *الصلوات الخمس:* هل حافظت على أدائها في أوقاتها مع الأذكار؟
📿 *الأذكار والورد القرآني:* هل قرأت وردك اليومي وأذكار الصباح والمساء؟
📚 *ساعات المذاكرة والمهام:* هل حققت جلسات التركيز المجدولة بالبومودورو؟
💧 *ترطيب العقل والجسد:* هل شربت 8 أكواب ماء لضمان صفاء الذهن وقوة الحفظ؟

💡 غداً يوم جديد وصفحة بيضاء جديدة لصناعة التميز والتفوق! استعن بالله ولا تعجز 🚀✨`;

const RATING_REQUEST_TEXT = `👋 مرحباً بك يا بطل في تطبيقك *رفيق الطالب*! 🎓
━━━━━━━━━━━━━━━━━━━
يسعدنا جداً أن نكون رفيقك في دراستك وتنظيم وقتك وصلاتك 🌟

هل يعجبك التطبيق وأدواته (المصحف، مواقيت الصلاة، الآلة الحاسبة، القاموس، البومودورو، والمخزن الرقمي)؟
⭐ *رأيك يهمنا جداً ويشجعنا على الاستمرار في التطوير والإبداع!*

يسعدنا جداً أن تمنحنا تقييماً 5 نجوم وكلمة تشجيعية على متجر Google Play عبر الزر أدناه 👇✨`;

// =============================================================================
// Helper Functions & Telegram API Client
// =============================================================================

function checkDailyUsageReset() {
  const currentDay = new Date().getUTCDate();
  if (currentDay !== lastResetDay) {
    dailyRequestCount = 0;
    lastResetDay = currentDay;
  }
}

function sanitizeMarkdownText(text: string): string {
  return (text || "").replace(/[*_`\[\]()~>#+\-=|{}.!]/g, " ");
}

async function callTelegramApi(
  env: Env,
  method: string,
  payload: Record<string, unknown>
): Promise<{ ok: boolean; result?: unknown; description?: string; error_code?: number }> {
  const token = env.TELEGRAM_BOT_TOKEN?.trim();
  if (!token) {
    console.error("[Telegram Worker] Error: TELEGRAM_BOT_TOKEN is not configured in Cloudflare environment secrets.");
    return { ok: false, description: "TELEGRAM_BOT_TOKEN is not configured in worker environment secrets." };
  }

  const url = `https://api.telegram.org/bot${token}/${method}`;
  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = (await response.json()) as { ok: boolean; result?: unknown; description?: string; error_code?: number };
    if (!data.ok) {
      console.error(`[Telegram Worker] API Error on '${method}': Status ${response.status} - ${data.description || "Unknown error"}`);
    } else {
      console.log(`[Telegram Worker] API Success on '${method}': Status 200, ok=true`);
    }
    return data;
  } catch (error: any) {
    console.error(`[Telegram Worker] Fetch Error on '${method}': ${error.message}`);
    return { ok: false, description: error.message || "Failed to communicate with Telegram API" };
  }
}

async function sendTelegramMessage(
  env: Env,
  chatId: number | string,
  text: string,
  replyMarkup?: Record<string, unknown>,
  parseMode: string = "Markdown"
) {
  const firstAttempt = await callTelegramApi(env, "sendMessage", {
    chat_id: chatId,
    text: text,
    parse_mode: parseMode,
    reply_markup: replyMarkup
  });

  // If Markdown parsing fails (e.g. invalid entities), retry cleanly without parse_mode
  if (!firstAttempt.ok && firstAttempt.description && firstAttempt.description.toLowerCase().includes("can't parse entities")) {
    console.warn("[Telegram Worker] Retrying sendMessage without Markdown parse_mode due to entity parsing error...");
    return await callTelegramApi(env, "sendMessage", {
      chat_id: chatId,
      text: text.replace(/[*_`#]/g, ""),
      reply_markup: replyMarkup
    });
  }

  return firstAttempt;
}

// =============================================================================
// State Management & Deep Link Token Store
// =============================================================================

async function createLinkToken(env: Env, appUserId: string, phone: string, customToken?: string): Promise<string> {
  let token = (customToken || "").trim();
  if (!token) {
    const randomBytes = new Uint8Array(16);
    crypto.getRandomValues(randomBytes);
    token = Array.from(randomBytes).map(b => b.toString(16).padStart(2, "0")).join("");
  }

  const item: PendingLinkToken = {
    token,
    app_user_id: appUserId.trim(),
    phone: phone.trim(),
    created_at: Date.now(),
    expires_at: Date.now() + 15 * 60 * 1000 // 15 minutes validity
  };

  memoryLinkTokens.set(token, item);

  if (env.STORAGE_KV) {
    await env.STORAGE_KV.put(`link_token:${token}`, JSON.stringify(item), { expirationTtl: 900 });
  }

  return token;
}

async function consumeLinkToken(env: Env, token: string): Promise<PendingLinkToken | null> {
  if (!token || !token.trim()) return null;
  const cleanToken = token.trim();

  let item = memoryLinkTokens.get(cleanToken) || null;

  if (!item && env.STORAGE_KV) {
    const raw = await env.STORAGE_KV.get(`link_token:${cleanToken}`);
    if (raw) {
      try {
        item = JSON.parse(raw);
      } catch {}
    }
  }

  // Support base64 client-signed tokens as fallback if created locally: "rafiq_PHONE_USERID_TIMESTAMP"
  if (!item) {
    try {
      let decoded = "";
      try {
        decoded = atob(cleanToken);
      } catch {
        const padded = cleanToken.replace(/-/g, "+").replace(/_/g, "/") + "==".slice(0, (4 - cleanToken.length % 4) % 4);
        decoded = atob(padded);
      }
      if (decoded && decoded.startsWith("rafiq_")) {
        const parts = decoded.split("_");
        if (parts.length >= 4) {
          const phone = parts[1];
          const appUserId = parts[2];
          const timestamp = parseInt(parts[3], 10);
          if (Date.now() - timestamp < 15 * 60 * 1000) {
            item = {
              token: cleanToken,
              app_user_id: appUserId,
              phone,
              created_at: timestamp,
              expires_at: timestamp + 15 * 60 * 1000
            };
          }
        }
      }
    } catch {}
  }

  if (!item) return null;

  // Single-use token: remove immediately to protect against replay / token reuse
  memoryLinkTokens.delete(cleanToken);
  if (env.STORAGE_KV) {
    await env.STORAGE_KV.delete(`link_token:${cleanToken}`);
  }

  if (Date.now() > item.expires_at) {
    return null;
  }

  return item;
}

async function saveOrUpdateUser(
  env: Env,
  chatId: number,
  username: string,
  fullName: string,
  phoneOrId: string = "",
  appUserId: string = ""
): Promise<LinkedUser> {
  let existing = memoryUsers.get(chatId);
  if (!existing && env.STORAGE_KV) {
    const raw = await env.STORAGE_KV.get(`user:${chatId}`);
    if (raw) {
      try { existing = JSON.parse(raw); } catch {}
    }
  }
  const nowStr = new Date().toISOString();

  const finalPhone = phoneOrId || existing?.phone_or_id || "";
  const finalAppUserId = appUserId || existing?.app_user_id || "";
  const isLinked = (finalPhone || finalAppUserId || existing?.telegram_linked) ? 1 : 0;
  const linkedAt = (phoneOrId || appUserId) ? nowStr : (existing?.telegram_linked_at || (isLinked ? nowStr : ""));

  const user: LinkedUser = {
    chat_id: chatId,
    username: username || existing?.username || "",
    full_name: fullName || existing?.full_name || "طالب العلم المجتهد",
    phone_or_id: finalPhone,
    app_user_id: finalAppUserId,
    join_date: existing?.join_date || nowStr,
    is_active: 1,
    rated_app: existing?.rated_app || 0,
    telegram_linked: isLinked,
    telegram_linked_at: linkedAt
  };

  memoryUsers.set(chatId, user);

  if (env.STORAGE_KV) {
    await env.STORAGE_KV.put(`user:${chatId}`, JSON.stringify(user));
    if (user.phone_or_id) {
      await env.STORAGE_KV.put(`phone_user:${user.phone_or_id}`, JSON.stringify(user));
    }
  }

  if (env.DB) {
    try {
      await env.DB.prepare(`
        INSERT INTO users (chat_id, username, full_name, phone_or_id, app_user_id, join_date, is_active, telegram_linked, telegram_linked_at)
        VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
        ON CONFLICT(chat_id) DO UPDATE SET
          username=excluded.username,
          full_name=excluded.full_name,
          phone_or_id=CASE WHEN excluded.phone_or_id != '' THEN excluded.phone_or_id ELSE users.phone_or_id END,
          app_user_id=CASE WHEN excluded.app_user_id != '' THEN excluded.app_user_id ELSE users.app_user_id END,
          telegram_linked=excluded.telegram_linked,
          telegram_linked_at=excluded.telegram_linked_at,
          is_active=1
      `).bind(
        chatId,
        user.username,
        user.full_name,
        user.phone_or_id,
        user.app_user_id,
        user.join_date,
        user.telegram_linked,
        user.telegram_linked_at
      ).run();
    } catch (e) {
      console.warn("D1 query error:", e);
    }
  }

  return user;
}

async function saveCloudMedia(env: Env, media: Omit<CloudMediaItem, "id">): Promise<boolean> {
  const newItem: CloudMediaItem = {
    id: memoryMedia.length + 1,
    ...media
  };
  memoryMedia.unshift(newItem);

  if (env.STORAGE_KV) {
    await env.STORAGE_KV.put(`media:${media.file_id}`, JSON.stringify(newItem));
  }

  if (env.DB) {
    try {
      await env.DB.prepare(`
        INSERT INTO cloud_storage (file_id, file_unique_id, file_type, file_name, file_size, mime_type, caption, category, uploaded_by, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(file_id) DO UPDATE SET
          caption=excluded.caption,
          category=excluded.category,
          file_name=excluded.file_name
      `).bind(
        media.file_id,
        media.file_unique_id,
        media.file_type,
        media.file_name,
        media.file_size,
        media.mime_type,
        media.caption,
        media.category,
        media.uploaded_by,
        media.created_at
      ).run();
    } catch (e) {
      console.warn("D1 media save error:", e);
    }
  }

  return true;
}

async function searchCloudMedia(env: Env, query: string = "", category: string = "", fileType: string = ""): Promise<CloudMediaItem[]> {
  const itemsMap = new Map<string, CloudMediaItem>();
  for (const item of memoryMedia) {
    itemsMap.set(item.file_id, item);
  }
  if (env.STORAGE_KV) {
    const listResult = await env.STORAGE_KV.list({ prefix: "media:" });
    for (const key of listResult.keys) {
      const raw = await env.STORAGE_KV.get(key.name);
      if (raw) {
        try {
          const item: CloudMediaItem = JSON.parse(raw);
          itemsMap.set(item.file_id, item);
        } catch {}
      }
    }
  }
  return Array.from(itemsMap.values()).filter(item => {
    if (query && !item.file_name.toLowerCase().includes(query.toLowerCase()) && !item.caption.toLowerCase().includes(query.toLowerCase())) return false;
    if (category && item.category !== category) return false;
    if (fileType && item.file_type !== fileType) return false;
    return true;
  }).slice(0, 15);
}

async function getAllActiveUsers(env: Env): Promise<LinkedUser[]> {
  const usersMap = new Map<number, LinkedUser>();
  for (const u of memoryUsers.values()) {
    usersMap.set(u.chat_id, u);
  }
  if (env.STORAGE_KV) {
    const listResult = await env.STORAGE_KV.list({ prefix: "user:" });
    for (const key of listResult.keys) {
      const raw = await env.STORAGE_KV.get(key.name);
      if (raw) {
        try {
          const user: LinkedUser = JSON.parse(raw);
          usersMap.set(user.chat_id, user);
        } catch {}
      }
    }
  }
  return Array.from(usersMap.values()).filter(u => u.is_active === 1);
}

// =============================================================================
// Webhook Update Processing (Commands, Callbacks, Media)
// =============================================================================

async function handleTelegramUpdate(env: Env, update: any): Promise<Response> {
  try {
    const botUsername = env.BOT_USERNAME || "RafeeqAlTalebBot";
    const playStoreUrl = env.PLAY_STORE_URL || DEFAULT_PLAY_STORE_URL;
    const storageLink = env.STORAGE_CHANNEL_LINK || DEFAULT_STORAGE_CHANNEL;

  // 1. Handle Callback Queries (Inline button clicks)
  if (update.callback_query) {
    const query = update.callback_query;
    const chatId = query.message?.chat?.id;
    const data = query.data;

    // Answer callback query to stop loading spinner
    await callTelegramApi(env, "answerCallbackQuery", { callback_query_id: query.id });

    if (!chatId) return new Response("OK", { status: 200 });

    if (data === "cmd_morning") {
      await sendTelegramMessage(env, chatId, MORNING_ATHKAR_TEXT);
    } else if (data === "cmd_evening") {
      await sendTelegramMessage(env, chatId, EVENING_ATHKAR_TEXT);
    } else if (data === "cmd_friday") {
      await sendTelegramMessage(env, chatId, FRIDAY_DAY_REMINDER);
    } else if (data === "cmd_salawat") {
      await sendTelegramMessage(env, chatId, SALAWAT_PERIODIC_TEXT);
    } else if (data === "cmd_study") {
      await sendTelegramMessage(env, chatId, STUDY_GOALS_REMINDER);
    } else if (data === "cmd_summary") {
      await sendTelegramMessage(env, chatId, DAILY_MIDNIGHT_SUMMARY);
    } else if (data === "cmd_storage") {
      const items = await searchCloudMedia(env);
      if (items.length === 0) {
        await sendTelegramMessage(env, chatId, `☁️ *المخزن الرقمي السحابي - رفيق الطالب* 📁\n\nرابط قناة المخزن السحابي المباشر:\n${storageLink}\n\nلم يتم رفع ملفات جديدة بعد. يمكنك رفع أي مذكرات أو صوتيات في القناة وسيحفظها البوت تلقائياً!`, {
          inline_keyboard: [[{ text: "🔗 فتح قناة المخزن الرقمي السحابي", url: storageLink }]]
        });
      } else {
        const keyboard = items.slice(0, 6).map(it => ([{
          text: `📥 ${(it.file_name || it.caption || "ملف").slice(0, 28)}`,
          callback_data: `get_file_${it.id}`
        }]));
        keyboard.push([{ text: "🔗 زيارة قناة المخزن السحابي الرسمية", url: storageLink }]);
        await sendTelegramMessage(env, chatId, "📂 *اختر الملف الذي تود تحميله من المخزن السحابي:*", { inline_keyboard: keyboard });
      }
    } else if (data.startsWith("get_file_")) {
      const fileIdNum = parseInt(data.replace("get_file_", ""), 10);
      const found = memoryMedia.find(m => m.id === fileIdNum);
      if (found) {
        const cap = `📂 ${found.caption || found.file_name}\n\n📲 من تطبيق رفيق الطالب 🎓`;
        if (found.file_type === "pdf" || found.file_type === "document") {
          await callTelegramApi(env, "sendDocument", { chat_id: chatId, document: found.file_id, caption: cap });
        } else if (found.file_type === "audio") {
          await callTelegramApi(env, "sendAudio", { chat_id: chatId, audio: found.file_id, caption: cap });
        } else if (found.file_type === "video") {
          await callTelegramApi(env, "sendVideo", { chat_id: chatId, video: found.file_id, caption: cap });
        } else if (found.file_type === "image") {
          await callTelegramApi(env, "sendPhoto", { chat_id: chatId, photo: found.file_id, caption: cap });
        } else {
          await callTelegramApi(env, "sendDocument", { chat_id: chatId, document: found.file_id, caption: cap });
        }
      } else {
        await sendTelegramMessage(env, chatId, "عذراً، الملف المطلوب غير متوفر حالياً.");
      }
    }

    return new Response("OK", { status: 200 });
  }

  // 2. Handle Messages
  const message = update.message || update.channel_post;
  if (!message) return new Response("OK", { status: 200 });

  const chatId = message.chat?.id;
  const user = message.from;
  const text = message.text?.trim() || "";

  // Media Harvester (Private message or Channel post)
  let fileId: string | null = null;
  let fileUniqueId: string = "";
  let fileType = "unknown";
  let fileName = "";
  let fileSize = 0;
  let mimeType = "";
  const caption = message.caption || text || "";
  const uploadedBy = user ? `${user.first_name || ""} ${user.last_name || ""}`.trim() : "Channel Admin";

  if (message.document) {
    fileId = message.document.file_id;
    fileUniqueId = message.document.file_unique_id || "";
    fileName = message.document.file_name || "وثيقة";
    fileSize = message.document.file_size || 0;
    mimeType = message.document.mime_type || "";
    fileType = mimeType.toLowerCase().includes("pdf") || fileName.endsWith(".pdf") ? "pdf" : "document";
  } else if (message.audio) {
    fileId = message.audio.file_id;
    fileUniqueId = message.audio.file_unique_id || "";
    fileName = message.audio.file_name || message.audio.title || "ملف صوتي";
    fileSize = message.audio.file_size || 0;
    mimeType = message.audio.mime_type || "audio/mpeg";
    fileType = "audio";
  } else if (message.voice) {
    fileId = message.voice.file_id;
    fileUniqueId = message.voice.file_unique_id || "";
    fileName = "تسجيل صوتي";
    fileSize = message.voice.file_size || 0;
    mimeType = message.voice.mime_type || "audio/ogg";
    fileType = "audio";
  } else if (message.video) {
    fileId = message.video.file_id;
    fileUniqueId = message.video.file_unique_id || "";
    fileName = message.video.file_name || "فيديو تعليمي";
    fileSize = message.video.file_size || 0;
    mimeType = message.video.mime_type || "video/mp4";
    fileType = "video";
  } else if (message.photo && Array.isArray(message.photo) && message.photo.length > 0) {
    const photo = message.photo[message.photo.length - 1]; // Highest resolution
    fileId = photo.file_id;
    fileUniqueId = photo.file_unique_id || "";
    fileName = "صورة";
    fileSize = photo.file_size || 0;
    mimeType = "image/jpeg";
    fileType = "image";
  }

  if (fileId) {
    let category = "general";
    const lowerCap = `${caption} ${fileName}`.toLowerCase();
    if (lowerCap.includes("قرآن") || lowerCap.includes("سورة") || lowerCap.includes("مصحف") || lowerCap.includes("تلاوة")) {
      category = "quran";
    } else if (lowerCap.includes("أذان") || lowerCap.includes("اذان") || lowerCap.includes("صلاة")) {
      category = "adhan";
    } else if (lowerCap.includes("أذكار") || lowerCap.includes("اذكار") || lowerCap.includes("دعاء")) {
      category = "azkar";
    } else if (lowerCap.includes("مذكرة") || lowerCap.includes("ملخص") || lowerCap.includes("كتاب") || lowerCap.includes("امتحان") || lowerCap.includes("pdf")) {
      category = "study";
    }

    await saveCloudMedia(env, {
      file_id: fileId,
      file_unique_id: fileUniqueId,
      file_type: fileType,
      file_name: fileName,
      file_size: fileSize,
      mime_type: mimeType,
      caption: caption,
      category: category,
      uploaded_by: uploadedBy,
      created_at: new Date().toISOString()
    });

    if (message.chat?.type === "private" && chatId) {
      await sendTelegramMessage(
        env,
        chatId,
        `✅ تم حفظ وأرشفة الملف بنجاح في *المخزن الرقمي السحابي* لتطبيق رفيق الطالب!\n📁 *الاسم:* ${fileName}\n🏷️ *التصنيف:* ${category}\n🆔 *معرف الملف:* \`${fileId}\``
      );
    }

    return new Response("OK", { status: 200 });
  }

  if (!chatId) return new Response("OK", { status: 200 });

  // 3. Command Routing
  const startMatch = text.match(/^\/start(?:@\w+)?(?:\s+(.+))?$/i);
  if (startMatch) {
    const startParam = (startMatch[1] || "").trim();
    const fullName = user ? `${user.first_name || ""} ${user.last_name || ""}`.trim() : "طالب العلم المجتهد";
    const cleanName = sanitizeMarkdownText(fullName);
    const username = user?.username || "";

    const standardKeyboard = {
      inline_keyboard: [
        [
          { text: "🌅 أذكار الصباح", callback_data: "cmd_morning" },
          { text: "🌆 أذكار المساء", callback_data: "cmd_evening" }
        ],
        [
          { text: "☁️ المخزن السحابي", callback_data: "cmd_storage" },
          { text: "📖 تذكير الجمعة", callback_data: "cmd_friday" }
        ],
        [
          { text: "🌸 الصلاة على النبي", callback_data: "cmd_salawat" },
          { text: "🎯 الأهداف والجدول", callback_data: "cmd_study" }
        ],
        [
          { text: "📊 تقرير الإنجاز", callback_data: "cmd_summary" },
          { text: "⭐ تقييم التطبيق", url: playStoreUrl }
        ]
      ]
    };

    if (!startParam) {
      // -----------------------------------------------------------------------
      // CASE 1: Normal /start (No linking parameter passed)
      // -----------------------------------------------------------------------
      // 1. Do NOT look up or consume any linking token.
      // 2. Do NOT treat lack of parameter as an error.
      // 3. Save/Update general user record without wiping previous links.
      // 4. Send standard welcome message immediately.
      await saveOrUpdateUser(env, chatId, username, fullName);

      const welcomeText = `👋 *أهلاً وسهلاً بك يا ${cleanName} في بوت "رفيق الطالب" الرسمي!* 🎓\n━━━━━━━━━━━━━━━━━━━\nتم تفعيل حسابك واستقبال تليجرام بنجاح ✅\n\n📱 هذا البوت يعمل 24/7 عبر السحابة، وسيرسل لك تلقائياً:\n🌅 أذكار الصباح والمساء يومياً (7:00 ص و 5:00 م).\n🕌 مواقيت الصلوات الخمس وأذكار ما بعد الصلاة تلقائياً.\n🌸 تذكيرات دورية بالصلاة على النبي ﷺ.\n🕌 تذكيرات ليلة وفجر يوم الجمعة (سورة الكهف وسورة المنافقون والسنن).\n📚 تذكيرات الجداول الدراسية، الأهداف اليومية، ومواعيد الامتحانات.\n📊 ملخص الإنجازات اليومي عند منتصف الليل.\n☁️ إمكانية استعراض وتحميل كتب وملفات وصوتيات المخزن الرقمي السحابي.\n\nاختر من القائمة أدناه للوصول السريع للخدمات:`;

      await sendTelegramMessage(env, chatId, welcomeText, standardKeyboard);
      return new Response("OK", { status: 200 });
    }

    // -----------------------------------------------------------------------
    // CASE 2: Deep Link /start <parameter> (Account Linking Flow)
    // -----------------------------------------------------------------------
    // 1. Validate parameter against single-use token store (memory / KV / decodable format).
    // 2. If valid & unexpired: bind real chat_id, phone, app_user_id, mark consumed.
    // 3. If invalid/expired/reused: show polite error with retry guidance.
    const tokenData = await consumeLinkToken(env, startParam);

    if (tokenData) {
      const linkedPhone = tokenData.phone;
      const linkedAppUserId = tokenData.app_user_id;

      await saveOrUpdateUser(env, chatId, username, fullName, linkedPhone, linkedAppUserId);

      const successText = `🎉 *تم ربط حسابك في تطبيق رفيق الطالب بنجاح!* ✅\n━━━━━━━━━━━━━━━━━━━\n📱 *رقم الهاتف المرتبط:* \`${linkedPhone}\`\n🆔 *معرف المحادثة (Telegram chat_id):* \`${chatId}\`\n👤 *الاسم:* ${cleanName}\n\n🌟 أصبح بإمكانك الآن استلام كافة تقارير دراستك وتنبيهات أذكارك ومواقيت صلواتك عبر التليجرام مباشرة!\n\nاختر من القائمة أدناه لبدء الاستخدام:`;

      await sendTelegramMessage(env, chatId, successText, standardKeyboard);
      return new Response("OK", { status: 200 });
    } else {
      // Invalid, expired, or already consumed token
      const expiredText = `❌ *رابط الربط غير صالح أو انتهت صلاحيته.* ⚠️\n━━━━━━━━━━━━━━━━━━━\nرمز التأكيد المستخدم غير متطابق أو تم استخدامه مسبقاً أو انتهت مدة صلاحيته (15 دقيقة).\n\n💡 *لإعادة ربط حسابك:* افتح تطبيق *رفيق الطالب* ⬅️ شاشة *الإعدادات* أو *التسجيل* ⬅️ اضغط زر *«ربط Telegram»* للحصول على رابط جديد مباشر.`;

      const errorKeyboard = {
        inline_keyboard: [
          [{ text: "📲 فتح تطبيق رفيق الطالب على المتجر", url: playStoreUrl }],
          [{ text: "☁️ تصفح المخزن السحابي", callback_data: "cmd_storage" }]
        ]
      };

      await sendTelegramMessage(env, chatId, expiredText, errorKeyboard);
      return new Response("OK", { status: 200 });
    }
  } else if (text.startsWith("/help")) {
    const helpText = `📖 *دليل أوامر واستخدام بوت رفيق الطالب:*
━━━━━━━━━━━━━━━━━━━
/start - بدء استخدام البوت وتأكيد تسجيل الحساب
/storage - استعراض ملفات وميديا المخزن الرقمي السحابي
/quran - تلاوات وملفات المصحف الشريف السحابية
/audio - ملفات الصوت (أذان، أذكار، تلاوات)
/pdf - الكتب والمذكرات الدراسية بصيغة PDF
/search <كلمة> - البحث عن أي ملف أو ملخص في المخزن
/athkar - أذكار الصباح والمساء
/friday - تذكير وسنن يوم الجمعة (سورة الكهف والمنافقون)
/salawat - الصلاة والسلام على النبي ﷺ
/study - تذكير الأهداف والجدول الدراسي
/summary - تقرير وملخص الإنجاز اليومي
/rate - تقييم تطبيق رفيق الطالب على متجر جوجل بلاي
/help - عرض هذه القائمة التعليمية`;
    await sendTelegramMessage(env, chatId, helpText);
  } else if (text.startsWith("/storage") || text.startsWith("/files")) {
    const items = await searchCloudMedia(env);
    if (items.length === 0) {
      await sendTelegramMessage(env, chatId, `☁️ *المخزن الرقمي السحابي - رفيق الطالب* 📁\n\nرابط قناة المخزن السحابي المباشر:\n${storageLink}\n\nلم يتم رفع ملفات جديدة بعد، يمكنك رفع مذكراتك وصوتياتك في القناة وسيقوم البوت بتخزينها وإتاحتها فوراً!`, {
        inline_keyboard: [[{ text: "🔗 فتح قناة المخزن الرقمي السحابي", url: storageLink }]]
      });
    } else {
      const keyboard = items.slice(0, 8).map(it => ([{
        text: `📥 ${(it.file_name || it.caption || "ملف").slice(0, 28)}`,
        callback_data: `get_file_${it.id}`
      }]));
      keyboard.push([{ text: "🔗 زيارة قناة المخزن السحابي الرسمية", url: storageLink }]);
      await sendTelegramMessage(env, chatId, `☁️ *أحدث محتويات المخزن الرقمي السحابي (${items.length} ملف):*\n━━━━━━━━━━━━━━━━━━━`, { inline_keyboard: keyboard });
    }
  } else if (text.startsWith("/search")) {
    const query = text.replace("/search", "").trim();
    if (!query) {
      await sendTelegramMessage(env, chatId, "🔍 يرجى كتابة الكلمة المراد البحث عنها بعد الأمر، مثال:\n`/search فيزياء` أو `/search أذكار`");
    } else {
      const results = await searchCloudMedia(env, query);
      if (results.length === 0) {
        await sendTelegramMessage(env, chatId, `لم يتم العثور على نتائج تطابق *${query}* في المخزن السحابي.`);
      } else {
        const keyboard = results.slice(0, 6).map(it => ([{
          text: `📥 ${(it.file_name || it.caption || "ملف").slice(0, 30)}`,
          callback_data: `get_file_${it.id}`
        }]));
        await sendTelegramMessage(env, chatId, `🔍 *نتائج البحث عن (${query}) في المخزن السحابي:*`, { inline_keyboard: keyboard });
      }
    }
  } else if (text.startsWith("/athkar")) {
    await sendTelegramMessage(env, chatId, "اختر الورد المطلوب:", {
      inline_keyboard: [
        [
          { text: "🌅 أذكار الصباح", callback_data: "cmd_morning" },
          { text: "🌆 أذكار المساء", callback_data: "cmd_evening" }
        ]
      ]
    });
  } else if (text.startsWith("/friday")) {
    await sendTelegramMessage(env, chatId, FRIDAY_DAY_REMINDER);
  } else if (text.startsWith("/salawat")) {
    await sendTelegramMessage(env, chatId, SALAWAT_PERIODIC_TEXT);
  } else if (text.startsWith("/study")) {
    await sendTelegramMessage(env, chatId, STUDY_GOALS_REMINDER);
  } else if (text.startsWith("/summary")) {
    await sendTelegramMessage(env, chatId, DAILY_MIDNIGHT_SUMMARY);
  } else if (text.startsWith("/rate")) {
    await sendTelegramMessage(env, chatId, RATING_REQUEST_TEXT, {
      inline_keyboard: [[{ text: "⭐ تقييم تطبيق رفيق الطالب على متجر جوجل بلاي", url: playStoreUrl }]]
    });
  }

  return new Response("OK", { status: 200 });
} catch (err: any) {
  console.error("[Telegram Worker] Error in handleTelegramUpdate:", err.stack || err.message);
  return new Response("Internal Error", { status: 500 });
}
}

// =============================================================================
// Main Worker Router (Fetch Handler)
// =============================================================================

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    checkDailyUsageReset();
    dailyRequestCount++;

    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    // CORS Headers for client app integration
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Telegram-Bot-Api-Secret-Token, X-Api-Key"
    };

    if (method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // 1. Health & Free Tier Monitoring Endpoint
    if (path === "/health" || path === "/") {
      const freeTierDailyLimit = 100000;
      const isApproachingLimit = dailyRequestCount >= 80000;

      let telegramStatus = "UNKNOWN";
      let botId: number | null = null;
      let webhookUrl: string | null = null;
      let pendingUpdates: number | null = null;

      if (env.TELEGRAM_BOT_TOKEN) {
        try {
          const meRes = await callTelegramApi(env, "getMe", {});
          if (meRes.ok && meRes.result) {
            telegramStatus = "OK";
            botId = (meRes.result as any).id || null;
          } else {
            telegramStatus = "FAIL";
          }
          const hookRes = await callTelegramApi(env, "getWebhookInfo", {});
          if (hookRes.ok && hookRes.result) {
            webhookUrl = (hookRes.result as any).url || null;
            pendingUpdates = (hookRes.result as any).pending_update_count || 0;
          }
        } catch {
          telegramStatus = "FAIL";
        }
      } else {
        telegramStatus = "FAIL";
      }

      return new Response(
        JSON.stringify({
          ok: true,
          status: "healthy",
          service: "rafiq-al-talib-telegram-worker",
          bot_username: env.BOT_USERNAME || "RafeeqAlTalebBot",
          app_name: env.APP_NAME || "رفيق الطالب",
          telegram: telegramStatus,
          bot_id: botId,
          webhook_url: webhookUrl,
          pending_updates: pendingUpdates,
          timezone: env.TIMEZONE || "Africa/Cairo",
          daily_requests: dailyRequestCount,
          free_tier_limit: freeTierDailyLimit,
          warning: isApproachingLimit ? "⚠️ WARNING: Approaching Cloudflare Workers Free daily limit (100k requests/day)." : null,
          users_in_memory: memoryUsers.size,
          media_in_memory: memoryMedia.length,
          timestamp: new Date().toISOString()
        }, null, 2),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // 2. Telegram Webhook Endpoint (POST /telegram/webhook)
    if (path === "/telegram/webhook") {
      if (method !== "POST") {
        return new Response(JSON.stringify({ error: "Method not allowed. Only POST accepted." }), {
          status: 405,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });
      }

      // Security Check: Secret Webhook Token validation
      const expectedSecret = env.TELEGRAM_SECRET_TOKEN;
      if (expectedSecret) {
        const receivedSecret = request.headers.get("X-Telegram-Bot-Api-Secret-Token");
        if (!receivedSecret || receivedSecret !== expectedSecret) {
          return new Response(JSON.stringify({ error: "Unauthorized: Invalid or missing Telegram secret token." }), {
            status: 403,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
          });
        }
      }

      let update: any;
      try {
        update = await request.json();
      } catch (err) {
        return new Response(JSON.stringify({ error: "Invalid JSON payload" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });
      }

      if (!update || typeof update !== "object") {
        return new Response(JSON.stringify({ error: "Invalid Telegram update structure" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });
      }

      // Execute update handler asynchronously within worker execution context
      ctx.waitUntil(handleTelegramUpdate(env, update));
      return new Response("OK", { status: 200, headers: corsHeaders });
    }

    // 3. Deep Link Token Generation (POST /api/link-token)
    if (path === "/api/link-token" && method === "POST") {
      try {
        const body: any = await request.json();
        const appUserId = body.app_user_id || "guest";
        const phone = (body.phone || "").trim();

        if (!phone) {
          return new Response(JSON.stringify({ success: false, error: "Phone number is required." }), {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
          });
        }

        const token = await createLinkToken(env, appUserId, phone);
        const botUsername = env.BOT_USERNAME || "RafeeqAlTalebBot";
        const deepLink = `https://t.me/${botUsername}?start=${token}`;

        return new Response(
          JSON.stringify({
            success: true,
            token,
            deep_link: deepLink,
            expires_in_seconds: 900
          }),
          { headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      } catch (e: any) {
        return new Response(JSON.stringify({ success: false, error: e.message }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });
      }
    }

    // 4. Link Status Verification (GET /api/link-status)
    if (path === "/api/link-status" && method === "GET") {
      const phone = url.searchParams.get("phone") || "";
      const appUserId = url.searchParams.get("app_user_id") || "";

      let matchedUser: LinkedUser | null = null;
      for (const u of memoryUsers.values()) {
        if ((phone && u.phone_or_id === phone) || (appUserId && u.app_user_id === appUserId)) {
          matchedUser = u;
          break;
        }
      }

      if (!matchedUser && env.STORAGE_KV && phone) {
        const raw = await env.STORAGE_KV.get(`phone_user:${phone}`);
        if (raw) {
          try { matchedUser = JSON.parse(raw); } catch {}
        }
      }

      return new Response(
        JSON.stringify({
          linked: !!matchedUser && matchedUser.telegram_linked === 1,
          chat_id: matchedUser?.chat_id || null,
          full_name: matchedUser?.full_name || null,
          linked_at: matchedUser?.telegram_linked_at || null
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Send Message API (POST /api/send-message)
    if (path === "/api/send-message" && method === "POST") {
      try {
        const body: any = await request.json();
        const chatId = body.chat_id;
        const messageText = body.text;
        const parseMode = body.parse_mode || "Markdown";

        if (!chatId || !messageText) {
          return new Response(JSON.stringify({ success: false, error: "chat_id and text are required." }), {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
          });
        }

        const res = await sendTelegramMessage(env, chatId, messageText, undefined, parseMode);
        return new Response(JSON.stringify(res), {
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });
      } catch (e: any) {
        return new Response(JSON.stringify({ success: false, error: e.message }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        });
      }
    }

    // 6. Set Webhook Registration Helper (POST /api/set-webhook)
    if (path === "/api/set-webhook" && method === "POST") {
      const webhookUrl = `${url.origin}/telegram/webhook`;
      const secretToken = env.TELEGRAM_SECRET_TOKEN || "";

      const res = await callTelegramApi(env, "setWebhook", {
        url: webhookUrl,
        secret_token: secretToken || undefined,
        drop_pending_updates: true,
        allowed_updates: ["message", "callback_query", "channel_post"]
      });

      return new Response(JSON.stringify({ webhook_url: webhookUrl, result: res }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" }
      });
    }

    return new Response(JSON.stringify({ error: "Endpoint not found" }), {
      status: 404,
      headers: { ...corsHeaders, "Content-Type": "application/json" }
    });
  },

  // =============================================================================
  // Cloudflare Cron Scheduled Background Broadcasts (24/7 Serverless Automation)
  // =============================================================================
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    const cron = event.cron;
    const allUsers = await getAllActiveUsers(env);

    if (cron === "0 5 * * *") {
      // 7:00 AM Cairo Time -> Broadcast Morning Athkar
      for (const u of allUsers) {
        ctx.waitUntil(sendTelegramMessage(env, u.chat_id, MORNING_ATHKAR_TEXT));
      }
    } else if (cron === "0 15 * * *") {
      // 5:00 PM Cairo Time -> Broadcast Evening Athkar
      for (const u of allUsers) {
        ctx.waitUntil(sendTelegramMessage(env, u.chat_id, EVENING_ATHKAR_TEXT));
      }
    } else if (cron === "0 18 * * 4") {
      // Thursday 8:00 PM Cairo Time -> Friday Eve Reminder
      for (const u of allUsers) {
        ctx.waitUntil(sendTelegramMessage(env, u.chat_id, FRIDAY_DAY_REMINDER));
      }
    } else if (cron === "30 6 * * 5") {
      // Friday 8:30 AM Cairo Time -> Friday Day Reminder
      for (const u of allUsers) {
        ctx.waitUntil(sendTelegramMessage(env, u.chat_id, FRIDAY_DAY_REMINDER));
      }
    } else if (cron === "0 22 * * *") {
      // Midnight 00:00 Cairo Time -> Midnight Summary Report
      for (const u of allUsers) {
        ctx.waitUntil(sendTelegramMessage(env, u.chat_id, DAILY_MIDNIGHT_SUMMARY));
      }
    }
  }
};
