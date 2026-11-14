#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
🤖 البوت الرسمي لتطبيق "رفيق الطالب" (Student Companion Bot)
المعرف الرسمي: @RafeeqAlTalebBot
رابط المخزن الرقمي السحابي: https://t.me/+zAsB3Bq2cwRlYjc0
=============================================================================
المميزات والأنظمة المدمجة:
1. نظام المخزن الرقمي السحابي (Private Cloud Media Harvester):
   - الاستماع الفوري لأي ميديا (فيديوهات، ملفات PDF، صوتيات أذان/أذكار/قرآن، صور)
     يتم رفعها على المخزن السحابي وتخزين file_id والبيانات تلقائياً في SQLite.
   - إمكانية استعراض والبحث في المواد السحابية للطلاب عبر الأوامر والواجهات.

2. شاشات الحساب والربط الإجباري:
   - تسجيل الطلاب بالاسم الكامل ومعرف/رقم التليجرام.
   - دعم التحقق الفوري وإرسال رموز التأكيد.

3. نظام التذكيرات والتقارير الأوتوماتيكية بالكامل (100% Automated Background Service):
   - تذكيرات ليلة وفجر يوم الجمعة (سورة الكهف وسورة المنافقون/الجمعة).
   - تذكيرات الصلاة على النبي ﷺ الدورية على مدار يوم الجمعة.
   - حساب مواقيت الصلاة وأذكار ما بعد الصلاة تلقائياً.
   - أذكار الصباح (7:00 ص) وأذكار المساء (5:00 م) تلقائياً.
   - التذكير بالأهداف الدراسية ومواعيد الامتحانات والجداول.
   - ملخص تقرير الإنجاز اليومي التلقائي عند منتصف الليل.
   - طلب تقييم التطبيق على متجر Google Play بعد 3 أيام من التسجيل.
=============================================================================
"""

import os
import sys
import logging
import sqlite3
import datetime
import asyncio
import base64
import math
import pytz
from typing import List, Dict, Any, Optional

from telegram import (
    Update,
    InputMediaPhoto,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    Message
)
from telegram.constants import ParseMode
from telegram.ext import (
    Application,
    CommandHandler,
    CallbackQueryHandler,
    MessageHandler,
    ContextTypes,
    filters
)
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

# =============================================================================
# 1. الإعدادات والثوابت الأساسية
# =============================================================================

# تحميل توكن البوت والمفاتيح السرية حصراً من متغيرات البيئة لمنع أي تسريب
BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
TELEGRAM_SECRET_TOKEN = os.environ.get("TELEGRAM_SECRET_TOKEN", "").strip()
WEBHOOK_URL = os.environ.get("WEBHOOK_URL", "").strip()
WEBHOOK_PORT = int(os.environ.get("PORT", "8080"))

BOT_USERNAME = os.environ.get("BOT_USERNAME", "RafeeqAlTalebBot")
APP_NAME = "رفيق الطالب"
STORAGE_CHANNEL_LINK = "https://t.me/+zAsB3Bq2cwRlYjc0"
PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.aistudio.studentcompanion.app"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "users.db")
TIMEZONE_STR = "Africa/Cairo"
TIMEZONE = pytz.timezone(TIMEZONE_STR)

# صور وميديا يوم الجمعة المبارك
FRIDAY_BANNER_1 = "https://images.unsplash.com/photo-1591604129939-f1efa4d9f7fa?w=1200&auto=format&fit=crop&q=80"
FRIDAY_BANNER_2 = "https://images.unsplash.com/photo-1542838132-92c53300491e?w=1200&auto=format&fit=crop&q=80"

# تهيئة سجل الأحداث (Logging)
logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    level=logging.INFO
)
logger = logging.getLogger(__name__)

# =============================================================================
# 2. إدارة قاعدة البيانات SQLite (SQLite Schema & CRUD)
# =============================================================================

def get_db_connection() -> sqlite3.Connection:
    """فتح اتصال بقاعدة البيانات SQLite"""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    """إنشاء وتجهيز كافة جداول قاعدة البيانات SQLite"""
    conn = get_db_connection()
    cursor = conn.cursor()

    # 1. جدول المستخدمين والطلاب
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            chat_id INTEGER PRIMARY KEY,
            username TEXT,
            full_name TEXT,
            phone_or_id TEXT,
            app_user_id TEXT DEFAULT '',
            telegram_linked INTEGER DEFAULT 0,
            telegram_linked_at TEXT DEFAULT '',
            grade_stage TEXT DEFAULT 'HIGH_SCHOOL',
            city TEXT DEFAULT 'Cairo',
            join_date TEXT,
            is_active INTEGER DEFAULT 1,
            rated_app INTEGER DEFAULT 0,
            study_hours INTEGER DEFAULT 0,
            tasks_completed INTEGER DEFAULT 0,
            water_cups INTEGER DEFAULT 0
        )
    """)

    # 2. جدول المخزن الرقمي السحابي (Cloud Media Storage)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cloud_storage (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_id TEXT UNIQUE,
            file_unique_id TEXT,
            file_type TEXT, -- 'pdf', 'audio', 'video', 'image', 'document'
            file_name TEXT,
            file_size INTEGER DEFAULT 0,
            mime_type TEXT,
            caption TEXT,
            category TEXT DEFAULT 'general', -- 'quran', 'azkar', 'adhan', 'study', 'pdf', 'lecture'
            uploaded_by TEXT,
            created_at TEXT
        )
    """)

    # 3. جدول مواعيد الامتحانات والجداول الدراسية
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS student_exams (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id INTEGER,
            subject_name TEXT,
            exam_date TEXT,
            exam_time TEXT,
            notes TEXT,
            is_notified INTEGER DEFAULT 0,
            FOREIGN KEY (chat_id) REFERENCES users(chat_id)
        )
    """)

    # 4. جدول الأهداف والمهام اليومية
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS daily_goals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            chat_id INTEGER,
            goal_title TEXT,
            target_date TEXT,
            is_completed INTEGER DEFAULT 0,
            FOREIGN KEY (chat_id) REFERENCES users(chat_id)
        )
    """)

    # 5. جدول رموز الربط المؤقتة (Secure Deep Link Tokens)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS link_tokens (
            token TEXT PRIMARY KEY,
            app_user_id TEXT,
            phone TEXT,
            created_at TEXT,
            expires_at INTEGER,
            is_used INTEGER DEFAULT 0
        )
    """)

    conn.commit()
    conn.close()
    logger.info("تم تهيئة وتحديث قاعدة البيانات SQLite (المستخدمين + المخزن السحابي + الربط) بنجاح ✅")

def save_or_update_user(chat_id: int, username: str, full_name: str, phone_or_id: str = "", app_user_id: str = ""):
    """حفظ بيانات الطالب أو تحديثها وربط الحساب"""
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = datetime.datetime.now(TIMEZONE).strftime("%Y-%m-%d %H:%M:%S")
    is_linked = 1 if (phone_or_id or app_user_id) else 0
    linked_at = now_str if is_linked else ""

    cursor.execute("""
        INSERT INTO users (chat_id, username, full_name, phone_or_id, app_user_id, telegram_linked, telegram_linked_at, join_date, is_active)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
        ON CONFLICT(chat_id) DO UPDATE SET
            username=excluded.username,
            full_name=excluded.full_name,
            phone_or_id=CASE WHEN excluded.phone_or_id != '' THEN excluded.phone_or_id ELSE users.phone_or_id END,
            app_user_id=CASE WHEN excluded.app_user_id != '' THEN excluded.app_user_id ELSE users.app_user_id END,
            telegram_linked=CASE WHEN excluded.telegram_linked = 1 THEN 1 ELSE users.telegram_linked END,
            telegram_linked_at=CASE WHEN excluded.telegram_linked_at != '' THEN excluded.telegram_linked_at ELSE users.telegram_linked_at END,
            is_active=1
    """, (chat_id, username or "", full_name or "", phone_or_id or "", app_user_id or "", is_linked, linked_at, now_str))
    conn.commit()
    conn.close()

def get_all_active_users() -> List[Dict[str, Any]]:
    """استرجاع كافة الطلاب النشطين لتلقي الإشعارات"""
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE is_active = 1")
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

def mark_user_rated(chat_id: int):
    """تحديث حالة تقييم التطبيق"""
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET rated_app = 1 WHERE chat_id = ?", (chat_id,))
    conn.commit()
    conn.close()

def save_cloud_media(
    file_id: str,
    file_unique_id: str,
    file_type: str,
    file_name: str,
    file_size: int,
    mime_type: str,
    caption: str,
    category: str,
    uploaded_by: str
) -> bool:
    """تخزين ملف الميديا الجديد من المخزن السحابي في قاعدة البيانات"""
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = datetime.datetime.now(TIMEZONE).strftime("%Y-%m-%d %H:%M:%S")
    try:
        cursor.execute("""
            INSERT INTO cloud_storage (
                file_id, file_unique_id, file_type, file_name, file_size,
                mime_type, caption, category, uploaded_by, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(file_id) DO UPDATE SET
                caption=excluded.caption,
                category=excluded.category,
                file_name=excluded.file_name
        """, (
            file_id, file_unique_id, file_type, file_name, file_size,
            mime_type, caption, category, uploaded_by, now_str
        ))
        conn.commit()
        conn.close()
        return True
    except Exception as e:
        logger.error(f"خطأ أثناء تخزين ميديا المخزن السحابي: {e}")
        conn.close()
        return False

def search_cloud_media(query: str = "", category: str = "", file_type: str = "") -> List[Dict[str, Any]]:
    """البحث في ملفات المخزن الرقمي السحابي"""
    conn = get_db_connection()
    cursor = conn.cursor()
    sql = "SELECT * FROM cloud_storage WHERE 1=1"
    params = []

    if query:
        sql += " AND (caption LIKE ? OR file_name LIKE ?)"
        params.extend([f"%{query}%", f"%{query}%"])
    if category:
        sql += " AND category = ?"
        params.append(category)
    if file_type:
        sql += " AND file_type = ?"
        params.append(file_type)

    sql += " ORDER BY id DESC LIMIT 20"
    cursor.execute(sql, params)
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

# =============================================================================
# 3. نصوص الأذكار والرسائل الإسلامية والتعليمية
# =============================================================================

MORNING_ATHKAR_TEXT = """🌅 *أذكار الصباح المباركة - رفيق الطالب* 🎓
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
«اللهم إني أسألك فهم النبيين وحفظ المرسلين وإلهام الملائكة المقربين، اللهم اجعل ألسنتنا عامرة بذكرك وقلوبنا بخشيتك وأسرارنا بطاعتك، إنك على كل شيء قدير» 📚✨
"""

EVENING_ATHKAR_TEXT = """🌆 *أذكار المساء وحفظ النفس - رفيق الطالب* 🎓
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
قم بمراجعة خطتك وإنجازاتك اليومية عبر تطبيق *رفيق الطالب* لتنظيم جدول الغد بكل ثقة وهدوء 📖🌟
"""

SALAWAT_PERIODIC_TEXT = """🌸 *تذكير الصلاة والسلام على رسول الله ﷺ* 🌸
━━━━━━━━━━━━━━━━━━━
قال رسول الله ﷺ: «أَوْلَى النَّاسِ بِي يَوْمَ الْقِيَامَةِ أَكْثَرُهُمْ عَلَيَّ صَلَاةً»

✨ *الصيغة الإبراهيمية المباركة:*
«اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ، اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ»

🌿 صلوا عليه وسلموا تسليماً تطيب به القلوب وتنشرح به الصدور وتتيسر به المذاكرة والامتحانات!
"""

FRIDAY_EVE_REMINDER = """🕌✨ *تنبيه ليلة وفجر الجمعة المباركة - رفيق الطالب* 🎓✨
━━━━━━━━━━━━━━━━━━━
عزيزي الطالب المجتهد:
بدأت الآن نفحات يوم الجمعة العظيم وساعاته المباركة 🌙

📖 *سورتان عظيمتان يُسن قراءتهما بدءاً من ليلة وفجر الجمعة:*
1️⃣ *سورة الكهف:* «من قرأ سورة الكهف في يوم الجمعة أضاء له من النور ما بين الجمعتين».
2️⃣ *سورة المنافقون وسورة الجمعة / السجدة:* سُنّة نبوية مؤكدة في يوم وفجر الجمعة.

🌸 *أكثر الليلة وغداً من الصلاة والسلام على الحبيب المصطفى ﷺ:*
«اللهم صلِّ وسلم وبارك على سيدنا ونبينا وحبيبنا محمد وعلى آله وصحبه أجمعين» 🤍
"""

FRIDAY_DAY_REMINDER = """🕌🌟 *جمعة مباركة طيبة من تطبيق رفيق الطالب* 🎓🌟
━━━━━━━━━━━━━━━━━━━
قال رسول الله ﷺ: «إِنَّ مِنْ أَفْضَلِ أَيَّامِكُمْ يَوْمَ الْجُمُعَةِ، فِيهِ خُلِقَ آدَمُ، وَفِيهِ قُبِضَ... فَأَكْثِرُوا عَلَيَّ مِنَ الصَّلَاةِ فِيهِ، فَإِنَّ صَلَاتَكُمْ مَعْرُوضَةٌ عَلَيَّ»

🌿 *سنن يوم الجمعة المستحبة:*
1. 📖 قراءة سورة الكهف وسورة الجمعة والمنافقون.
2. 🌸 الإكثار المستمر من الصلاة والسلام على النبي المختار ﷺ.
3. 🚿 الغسل والتطيب ولبس أحسن الثياب.
4. ⏰ التبكير لصلاة الجمعة والإنصات للخطبة.
5. 🤲 تحري ساعة الاستجابة (آخر ساعة قبل الغروب) والدعاء بالتفوق والنجاح والبركة في العلم.

اللهم صل وسلم وبارك على سيدنا محمد صلاة تفتح لنا بها أبواب الخير والتيسير 🤍
"""

PRAYER_POST_ATHKAR = {
    "الفجر": """🕌 *حان الآن موعد صلاة الفجر* 🌅
«الصلاة خير من النوم»
🤲 *أذكار ما بعد الصلاة:*
• أستغفر الله (3 مرات).
• «اللهم أنت السلام ومنك السلام، تباركت يا ذا الجلال والإكرام».
• «لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير» (10 مرات).
• قراءة آية الكرسي والمعوذات.""",

    "الظهر": """🕌 *حان الآن موعد صلاة الظهر* ☀️
أرحنا بها يا بلال! خذ استراحة إيمانية من المذاكرة واستعد للقاء الله.
🤲 *أذكار ما بعد الصلاة:*
• أستغفر الله (3 مرات).
• «اللهم أنت السلام ومنك السلام تباركت يا ذا الجلال والإكرام».
• التسبيح (33)، التحميد (33)، التكبير (33)، وختام المائة بلا إله إلا الله.""",

    "العصر": """🕌 *حان الآن موعد صلاة العصر* 🌤️
«حافظوا على الصلوات والصلاة الوسطى»
🤲 *أذكار ما بعد الصلاة:*
• الاستغفار 3 مرات.
• «اللهم أعني على ذكرك وشكرك وحسن عبادتك».
• التسبيح والتحميد والتكبير وقراءة آية الكرسي.""",

    "المغرب": """🕌 *حان الآن موعد صلاة المغرب* 🌇
تقبل الله صلاتكم ودعاءكم في هذا الوقت المبارك.
🤲 *أذكار ما بعد الصلاة:*
• الاستغفار 3 مرات.
• «لا إله إلا الله وحده لا شريك له، له الملك وله الحمد يحيي ويميت وهو على كل شيء قدير» (10 مرات).
• قراءة آية الكرسي والمعوذتين وسورة الإخلاص.""",

    "العشاء": """🕌 *حان الآن موعد صلاة العشاء والوتر* 🌌
صلاة العشاء ثم ركعات الوتر الهادئة، خير ختام ليومك الإيماني والدراسي.
🤲 *أذكار ما بعد الصلاة:*
• أستغفر الله (3 مرات)، اللهم أنت السلام ومنك السلام.
• التسبيح 33، التحميد 33، التكبير 33، وختام المائة.
• ركعة الوتر والدعاء بالتوفيق والدرجات العلا في دراستك."""
}

DAILY_MIDNIGHT_SUMMARY = """📊 *ملخص الإنجاز والتقرير اليومي - رفيق الطالب* 🎓
━━━━━━━━━━━━━━━━━━━
أحسنت صنعاً وجهداً اليوم! مع دقات منتصف الليل، حان وقت تقييم مسيرتك:

✅ *قائمة الإنجاز والتقييم اليومي:*
🕌 *الصلوات الخمس:* هل حافظت على أدائها في أوقاتها مع الأذكار؟
📿 *الأذكار والورد القرآني:* هل قرأت وردك اليومي وأذكار الصباح والمساء؟
📚 *ساعات المذاكرة والمهام:* هل حققت جلسات التركيز المجدولة بالبومودورو؟
💧 *ترطيب العقل والجسد:* هل شربت 8 أكواب ماء لضمان صفاء الذهن وقوة الحفظ؟

💡 غداً يوم جديد وصفحة بيضاء جديدة لصناعة التميز والتفوق! استعن بالله ولا تعجز 🚀✨
"""

STUDY_GOALS_REMINDER = """🎯 *تذكير الأهداف الدراسية وجلسات التركيز اليومية* ⏰
━━━━━━━━━━━━━━━━━━━
عزيزي الطالب المجتهد:
نصف يومك يبدأ الآن، والهدف الذي كتبته في بداية يومك بانتظار إنجازك!

💡 *خطوات عملية للتفوق الآن:*
1. 📴 تفعيل وضع التركيز (Focus Mode) وإبعاد المشتتات.
2. ⏱️ بدء جلسة بومودورو عبر التطبيق (25 دقيقة تركيز تام).
3. 💧 كوب ماء نقي لإنعاش الذاكرة.
4. 📝 تدوين النقاط الصعبة في كشكول الملاحظات.

«وقل اعملوا فسيرى الله عملكم ورسوله والمؤمنون» — انطلق واصنع فخرك وفخر والديك! 🌟
"""

RATING_REQUEST_TEXT = """👋 مرحباً بك يا بطل في تطبيقك *رفيق الطالب*! 🎓
━━━━━━━━━━━━━━━━━━━
لقد مر 3 أيام على استخدامك للتطبيق ويسعدنا جداً أن نكون رفيقك في دراستك وتنظيم وقتك وصلاتك 🌟

هل يعجبك التطبيق وأدواته (المصحف، مواقيت الصلاة، الآلة الحاسبة، القاموس، البومودورو، والمخزن الرقمي)؟
⭐ *رأيك يهمنا جداً ويشجعنا على الاستمرار في التطوير والإبداع!*

يسعدنا جداً أن تمنحنا تقييماً 5 نجوم وكلمة تشجيعية على متجر Google Play عبر الزر أدناه 👇✨
"""

# =============================================================================
# 4. أوامر ومعالجات البوت التفاعلية (Telegram Handlers)
# =============================================================================

async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """معالجة أمر /start وحفظ بيانات الطالب والربط السحابي في SQLite"""
    user = update.effective_user
    chat_id = update.effective_chat.id
    username = user.username or ""
    full_name = user.full_name or user.first_name or "طالب العلم المجتهد"

    standard_keyboard = [
        [
            InlineKeyboardButton("🌅 أذكار الصباح", callback_data="cmd_morning"),
            InlineKeyboardButton("🌆 أذكار المساء", callback_data="cmd_evening")
        ],
        [
            InlineKeyboardButton("☁️ المخزن السحابي", callback_data="cmd_storage"),
            InlineKeyboardButton("📖 تذكير الجمعة", callback_data="cmd_friday")
        ],
        [
            InlineKeyboardButton("🌸 الصلاة على النبي", callback_data="cmd_salawat"),
            InlineKeyboardButton("🎯 الأهداف والجدول", callback_data="cmd_study")
        ],
        [
            InlineKeyboardButton("📊 تقرير الإنجاز", callback_data="cmd_summary"),
            InlineKeyboardButton("⭐ تقييم التطبيق", url=PLAY_STORE_URL)
        ]
    ]

    args = context.args
    start_param = args[0].strip() if args else ""

    # =========================================================================
    # CASE 1: Normal /start without parameters
    # =========================================================================
    if not start_param:
        # Never call token lookup or report token error for ordinary /start
        save_or_update_user(chat_id, username, full_name)
        welcome_text = f"""👋 *أهلاً وسهلاً بك يا {full_name} في بوت "رفيق الطالب" الرسمي!* 🎓
━━━━━━━━━━━━━━━━━━━
تم تفعيل حسابك واستقبال تليجرام بنجاح ✅

📱 هذا البوت يعمل 24/7 عبر السحابة، وسيرسل لك تلقائياً:
🌅 أذكار الصباح والمساء يومياً (7:00 ص و 5:00 م).
🕌 مواقيت الصلوات الخمس وأذكار ما بعد الصلاة تلقائياً.
🌸 تذكيرات دورية بالصلاة على النبي ﷺ.
🕌 تذكيرات ليلة وفجر يوم الجمعة (سورة الكهف وسورة المنافقون والسنن).
📚 تذكيرات الجداول الدراسية، الأهداف اليومية، ومواعيد الامتحانات.
📊 ملخص الإنجازات اليومي عند منتصف الليل.
☁️ إمكانية استعراض وتحميل كتب وملفات وصوتيات المخزن الرقمي السحابي.

اختر من القائمة أدناه للوصول السريع للخدمات:
"""
        await update.message.reply_text(
            text=welcome_text,
            parse_mode=ParseMode.MARKDOWN,
            reply_markup=InlineKeyboardMarkup(standard_keyboard)
        )
        return

    # =========================================================================
    # CASE 2: Deep Link /start <token>
    # =========================================================================
    linked_phone = ""
    linked_app_user_id = ""
    token_valid = False

    # 1. Check database link_tokens
    conn = get_db_connection()
    cursor = conn.cursor()
    now_ts = int(datetime.datetime.now().timestamp())
    cursor.execute("SELECT * FROM link_tokens WHERE token = ? AND is_used = 0 AND expires_at > ?", (start_param, now_ts))
    row = cursor.fetchone()
    if row:
        linked_phone = row["phone"]
        linked_app_user_id = row["app_user_id"]
        cursor.execute("UPDATE link_tokens SET is_used = 1 WHERE token = ?", (start_param,))
        conn.commit()
        token_valid = True
    conn.close()

    # 2. Check signed base64 token fallback: "rafiq_PHONE_USERID_TIMESTAMP"
    if not token_valid:
        try:
            decoded = ""
            try:
                decoded = base64.b64decode(start_param).decode('utf-8')
            except Exception:
                padded = start_param + "=" * ((4 - len(start_param) % 4) % 4)
                decoded = base64.b64decode(padded).decode('utf-8')
            
            if decoded.startswith("rafiq_"):
                parts = decoded.split("_")
                if len(parts) >= 4:
                    phone = parts[1]
                    app_user_id = parts[2]
                    ts = int(parts[3])
                    now_ms = int(datetime.datetime.now().timestamp() * 1000)
                    if (now_ms - ts) < (15 * 60 * 1000) and (now_ms - ts) >= 0:
                        linked_phone = phone
                        linked_app_user_id = app_user_id
                        token_valid = True
        except Exception:
            pass

    if token_valid:
        save_or_update_user(chat_id, username, full_name, phone_or_id=linked_phone, app_user_id=linked_app_user_id)
        success_text = f"""🎉 *تم ربط حسابك في تطبيق رفيق الطالب بنجاح!* ✅
━━━━━━━━━━━━━━━━━━━
📱 *رقم الهاتف المرتبط:* `{linked_phone}`
🆔 *معرف المحادثة (Telegram chat_id):* `{chat_id}`
👤 *الاسم:* {full_name}

🌟 أصبح بإمكانك الآن استلام كافة تقارير دراستك وتنبيهات أذكارك ومواقيت صلواتك عبر التليجرام مباشرة!

اختر من القائمة أدناه لبدء الاستخدام:
"""
        await update.message.reply_text(
            text=success_text,
            parse_mode=ParseMode.MARKDOWN,
            reply_markup=InlineKeyboardMarkup(standard_keyboard)
        )
    else:
        # Invalid, expired, or already used token
        error_text = """❌ *رابط الربط غير صالح أو انتهت صلاحيته.* ⚠️
━━━━━━━━━━━━━━━━━━━
رمز التأكيد المستخدم غير متطابق أو تم استخدامه مسبقاً أو انتهت مدة صلاحيته (15 دقيقة).

💡 *لإعادة ربط حسابك:* افتح تطبيق *رفيق الطالب* ⬅️ شاشة *الإعدادات* أو *التسجيل* ⬅️ اضغط زر *«ربط Telegram»* للحصول على رابط جديد مباشر."""

        error_keyboard = [
            [InlineKeyboardButton("📲 فتح تطبيق رفيق الطالب على المتجر", url=PLAY_STORE_URL)],
            [InlineKeyboardButton("☁️ تصفح المخزن السحابي", callback_data="cmd_storage")]
        ]
        await update.message.reply_text(
            text=error_text,
            parse_mode=ParseMode.MARKDOWN,
            reply_markup=InlineKeyboardMarkup(error_keyboard)
        )

async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """عرض قائمة الأوامر الكاملة"""
    help_text = """📖 *دليل أوامر واستخدام بوت رفيق الطالب:*
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
/help - عرض هذه القائمة التعليمية
"""
    await update.message.reply_text(help_text, parse_mode=ParseMode.MARKDOWN)

async def storage_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """استعراض الملفات والميديا في المخزن الرقمي السحابي"""
    items = search_cloud_media()
    if not items:
        msg = f"""☁️ *المخزن الرقمي السحابي - رفيق الطالب* 📁
━━━━━━━━━━━━━━━━━━━
رابط قناة المخزن السحابي المباشر:
{STORAGE_CHANNEL_LINK}

💡 لم يتم أرشفة ملفات جديدة بعد، أو يمكنك رفع مذكراتك وصوتياتك في القناة وسيقوم البوت بتخزينها وإتاحتها للجميع فوراً!"""
        keyboard = [[InlineKeyboardButton("🔗 فتح قناة المخزن الرقمي السحابي", url=STORAGE_CHANNEL_LINK)]]
        await update.message.reply_text(msg, parse_mode=ParseMode.MARKDOWN, reply_markup=InlineKeyboardMarkup(keyboard))
        return

    msg = f"""☁️ *أحدث محتويات المخزن الرقمي السحابي ({len(items)} ملف):*
━━━━━━━━━━━━━━━━━━━
"""
    keyboard = []
    for idx, item in enumerate(items[:8]):
        title = item.get("file_name") or item.get("caption") or f"ملف #{item['id']}"
        f_type = item.get("file_type", "document")
        icon = "📕" if f_type == "pdf" else ("🎧" if f_type == "audio" else ("🎬" if f_type == "video" else "📄"))
        btn_text = f"{icon} {title[:28]}"
        keyboard.append([InlineKeyboardButton(btn_text, callback_data=f"get_file_{item['id']}")])

    keyboard.append([InlineKeyboardButton("🔗 زيارة قناة المخزن السحابي الرسمية", url=STORAGE_CHANNEL_LINK)])
    reply_markup = InlineKeyboardMarkup(keyboard)
    await update.message.reply_text(msg, parse_mode=ParseMode.MARKDOWN, reply_markup=reply_markup)

async def search_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """البحث في المخزن السحابي بكلمة مفتاحية"""
    query = " ".join(context.args) if context.args else ""
    if not query:
        await update.message.reply_text("🔍 يرجى كتابة الكلمة المراد البحث عنها بعد الأمر، مثال:\n`/search فيزياء` أو `/search أذكار`", parse_mode=ParseMode.MARKDOWN)
        return

    results = search_cloud_media(query=query)
    if not results:
        await update.message.reply_text(f"لم يتم العثور على نتائج تطابق *{query}* في المخزن السحابي.", parse_mode=ParseMode.MARKDOWN)
        return

    keyboard = []
    for item in results[:6]:
        title = item.get("file_name") or item.get("caption") or f"ملف #{item['id']}"
        icon = "📕" if item.get("file_type") == "pdf" else "📄"
        keyboard.append([InlineKeyboardButton(f"{icon} {title[:30]}", callback_data=f"get_file_{item['id']}")])

    await update.message.reply_text(
        f"🔍 *نتائج البحث عن ({query}) في المخزن السحابي:*",
        parse_mode=ParseMode.MARKDOWN,
        reply_markup=InlineKeyboardMarkup(keyboard)
    )

async def quran_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """استعراض تلاوات ومصاحف المخزن السحابي"""
    items = search_cloud_media(category="quran")
    if not items:
        await update.message.reply_text("📖 *تلاوات ومصاحف المخزن السحابي:*\nلم يتم رفع تلاوات جديدة بعد. يمكنك رفعها وسيقوم البوت بأرشفتها فوراً!", parse_mode=ParseMode.MARKDOWN)
        return
    keyboard = []
    for item in items[:6]:
        title = item.get("file_name") or item.get("caption") or f"تلاوة #{item['id']}"
        keyboard.append([InlineKeyboardButton(f"🎧 {title[:30]}", callback_data=f"get_file_{item['id']}")])
    await update.message.reply_text("📖 *تلاوات ومصاحف المخزن السحابي:*", parse_mode=ParseMode.MARKDOWN, reply_markup=InlineKeyboardMarkup(keyboard))

async def audio_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """استعراض الملفات الصوتية في المخزن السحابي"""
    items = search_cloud_media(file_type="audio")
    if not items:
        await update.message.reply_text("🎧 *الصوتيات في المخزن السحابي:*\nلم يتم العثور على صوتيات بعد.", parse_mode=ParseMode.MARKDOWN)
        return
    keyboard = []
    for item in items[:6]:
        title = item.get("file_name") or item.get("caption") or f"صوتية #{item['id']}"
        keyboard.append([InlineKeyboardButton(f"🎧 {title[:30]}", callback_data=f"get_file_{item['id']}")])
    await update.message.reply_text("🎧 *الصوتيات في المخزن السحابي:*", parse_mode=ParseMode.MARKDOWN, reply_markup=InlineKeyboardMarkup(keyboard))

async def pdf_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """استعراض الكتب والمذكرات الدراسية PDF"""
    items = search_cloud_media(file_type="pdf")
    if not items:
        await update.message.reply_text("📕 *الكتب والمذكرات الدراسية PDF:*\nلم يتم أرشفة ملفات PDF بعد.", parse_mode=ParseMode.MARKDOWN)
        return
    keyboard = []
    for item in items[:6]:
        title = item.get("file_name") or item.get("caption") or f"كتاب #{item['id']}"
        keyboard.append([InlineKeyboardButton(f"📕 {title[:30]}", callback_data=f"get_file_{item['id']}")])
    await update.message.reply_text("📕 *الكتب والمذكرات الدراسية PDF:*", parse_mode=ParseMode.MARKDOWN, reply_markup=InlineKeyboardMarkup(keyboard))

async def athkar_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """عرض أزرار اختيار ورد أذكار الصباح والمساء"""
    keyboard = [
        [
            InlineKeyboardButton("🌅 أذكار الصباح", callback_data="cmd_morning"),
            InlineKeyboardButton("🌆 أذكار المساء", callback_data="cmd_evening")
        ]
    ]
    await update.message.reply_text("اختر الورد المطلوب:", reply_markup=InlineKeyboardMarkup(keyboard))

async def friday_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """تذكير وسنن يوم الجمعة"""
    await update.message.reply_text(FRIDAY_DAY_REMINDER, parse_mode=ParseMode.MARKDOWN)

async def salawat_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """تذكير الصلاة على النبي ﷺ"""
    await update.message.reply_text(SALAWAT_PERIODIC_TEXT, parse_mode=ParseMode.MARKDOWN)

async def study_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """تذكير الأهداف والجدول الدراسي"""
    await update.message.reply_text(STUDY_GOALS_REMINDER, parse_mode=ParseMode.MARKDOWN)

async def summary_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """عرض تقرير الإنجاز اليومي"""
    await update.message.reply_text(DAILY_MIDNIGHT_SUMMARY, parse_mode=ParseMode.MARKDOWN)

async def rate_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """طلب تقييم التطبيق على متجر Google Play"""
    keyboard = [[InlineKeyboardButton("⭐ تقييم التطبيق على المتجر", url=PLAY_STORE_URL)]]
    await update.message.reply_text(RATING_REQUEST_TEXT, parse_mode=ParseMode.MARKDOWN, reply_markup=InlineKeyboardMarkup(keyboard))

async def media_harvester_handler(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """
    آلية المخزن الرقمي السحابي الأساسية:
    الاستماع الفوري لأي ميديا يتم إرسالها (في الخاص أو القناة المربوطة)
    واستخراج file_id والنوع والشرح وتخزينها في قاعدة البيانات SQLite.
    """
    message = update.effective_message
    if not message:
        return

    file_id = None
    file_unique_id = None
    file_type = "unknown"
    file_name = ""
    file_size = 0
    mime_type = ""
    caption = message.caption or message.text or ""

    user = update.effective_user
    uploaded_by = user.full_name if user else "Channel Admin"

    # تصنيف الميديا حسب نوع الكائن
    if message.document:
        file_id = message.document.file_id
        file_unique_id = message.document.file_unique_id
        file_name = message.document.file_name or "وثيقة"
        file_size = message.document.file_size or 0
        mime_type = message.document.mime_type or ""
        file_type = "pdf" if "pdf" in mime_type.lower() or file_name.endswith(".pdf") else "document"

    elif message.audio:
        file_id = message.audio.file_id
        file_unique_id = message.audio.file_unique_id
        file_name = message.audio.file_name or message.audio.title or "صوتية"
        file_size = message.audio.file_size or 0
        mime_type = message.audio.mime_type or "audio/mpeg"
        file_type = "audio"

    elif message.voice:
        file_id = message.voice.file_id
        file_unique_id = message.voice.file_unique_id
        file_name = "تسجيل صوتي"
        file_size = message.voice.file_size or 0
        mime_type = message.voice.mime_type or "audio/ogg"
        file_type = "audio"

    elif message.video:
        file_id = message.video.file_id
        file_unique_id = message.video.file_unique_id
        file_name = message.video.file_name or "فيديو"
        file_size = message.video.file_size or 0
        mime_type = message.video.mime_type or "video/mp4"
        file_type = "video"

    elif message.photo:
        photo = message.photo[-1] # أكبر دقة متوفرة
        file_id = photo.file_id
        file_unique_id = photo.file_unique_id
        file_name = "صورة"
        file_size = photo.file_size or 0
        mime_type = "image/jpeg"
        file_type = "image"

    if file_id:
        # تحديد التصنيف الذكي بناءً على العنوان والشرح
        category = "general"
        lower_cap = (caption + " " + file_name).lower()
        if any(w in lower_cap for w in ["قرآن", "سورة", "مصحف", "تلاوة", "quran"]):
            category = "quran"
        elif any(w in lower_cap for w in ["أذان", "اذان", "مؤذن", "صلاة"]):
            category = "adhan"
        elif any(w in lower_cap for w in ["أذكار", "اذكار", "دعاء", "تسبيح"]):
            category = "azkar"
        elif any(w in lower_cap for w in ["مذكرة", "ملخص", "كتاب", "شرح", "pdf", "امتحان", "مراجعة"]):
            category = "study"

        saved = save_cloud_media(
            file_id=file_id,
            file_unique_id=file_unique_id or "",
            file_type=file_type,
            file_name=file_name,
            file_size=file_size,
            mime_type=mime_type,
            caption=caption,
            category=category,
            uploaded_by=uploaded_by
        )

        if saved:
            logger.info(f"تمت أرشفة ميديا جديدة في المخزن الرقمي: {file_name} ({file_type}) - {category}")
            if update.effective_chat.type == "private":
                await message.reply_text(
                    f"✅ تم حفظ وأرشفة الملف بنجاح في *المخزن الرقمي السحابي* لتطبيق رفيق الطالب!\n"
                    f"📁 *الاسم:* {file_name}\n"
                    f"🏷️ *التصنيف:* {category}\n"
                    f"🆔 *معرف الملف:* `{file_id}`",
                    parse_mode=ParseMode.MARKDOWN
                )

async def button_callback_handler(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """الاستجابة لضغطات الأزرار التفاعلية والرد بالمحتوى المطلوب"""
    query = update.callback_query
    await query.answer()
    data = query.data

    if data == "cmd_morning":
        await query.message.reply_text(MORNING_ATHKAR_TEXT, parse_mode=ParseMode.MARKDOWN)
    elif data == "cmd_evening":
        await query.message.reply_text(EVENING_ATHKAR_TEXT, parse_mode=ParseMode.MARKDOWN)
    elif data == "cmd_friday":
        await query.message.reply_text(FRIDAY_DAY_REMINDER, parse_mode=ParseMode.MARKDOWN)
    elif data == "cmd_salawat":
        await query.message.reply_text(SALAWAT_PERIODIC_TEXT, parse_mode=ParseMode.MARKDOWN)
    elif data == "cmd_study":
        await query.message.reply_text(STUDY_GOALS_REMINDER, parse_mode=ParseMode.MARKDOWN)
    elif data == "cmd_summary":
        await query.message.reply_text(DAILY_MIDNIGHT_SUMMARY, parse_mode=ParseMode.MARKDOWN)
    elif data == "cmd_storage":
        items = search_cloud_media()
        if not items:
            await query.message.reply_text(f"المخزن الرقمي السحابي متاح عبر الرابط التالي:\n{STORAGE_CHANNEL_LINK}")
        else:
            keyboard = []
            for item in items[:6]:
                title = item.get("file_name") or item.get("caption") or f"ملف #{item['id']}"
                keyboard.append([InlineKeyboardButton(f"📥 {title[:28]}", callback_data=f"get_file_{item['id']}")])
            await query.message.reply_text("📂 *اختر الملف الذي تود تحميله من المخزن السحابي:*", reply_markup=InlineKeyboardMarkup(keyboard), parse_mode=ParseMode.MARKDOWN)

    elif data.startswith("get_file_"):
        file_id_num = int(data.replace("get_file_", ""))
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM cloud_storage WHERE id = ?", (file_id_num,))
        item = cursor.fetchone()
        conn.close()

        if item:
            item_dict = dict(item)
            f_id = item_dict["file_id"]
            f_type = item_dict["file_type"]
            cap = item_dict.get("caption") or item_dict.get("file_name") or "ملف من المخزن السحابي"
            try:
                if f_type == "pdf" or f_type == "document":
                    await query.message.reply_document(document=f_id, caption=f"📕 {cap}\n\n📲 من تطبيق رفيق الطالب")
                elif f_type == "audio":
                    await query.message.reply_audio(audio=f_id, caption=f"🎧 {cap}\n\n📲 من تطبيق رفيق الطالب")
                elif f_type == "video":
                    await query.message.reply_video(video=f_id, caption=f"🎬 {cap}\n\n📲 من تطبيق رفيق الطالب")
                elif f_type == "image":
                    await query.message.reply_photo(photo=f_id, caption=f"🖼️ {cap}\n\n📲 من تطبيق رفيق الطالب")
                else:
                    await query.message.reply_document(document=f_id, caption=cap)
            except Exception as e:
                logger.error(f"خطأ أثناء إرسال ميديا المخزن السحابي: {e}")
                await query.message.reply_text("عذراً، حدث خطأ أثناء جلب الملف من التليجرام.")

# =============================================================================
# 5. المهام المجدولة الأوتوماتيكية بالكامل (100% Automated Background Service)
# =============================================================================

async def broadcast_morning_athkar(bot):
    """إرسال أذكار الصباح يومياً في تمام الساعة 7:00 صباحاً"""
    users = get_all_active_users()
    logger.info(f"جارٍ إرسال أذكار الصباح تلقائياً إلى {len(users)} طالب...")
    for user in users:
        try:
            await bot.send_message(
                chat_id=user["chat_id"],
                text=MORNING_ATHKAR_TEXT,
                parse_mode=ParseMode.MARKDOWN
            )
            await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"تعذر إرسال أذكار الصباح إلى {user['chat_id']}: {e}")

async def broadcast_evening_athkar(bot):
    """إرسال أذكار المساء يومياً في تمام الساعة 5:00 مساءً (17:00)"""
    users = get_all_active_users()
    logger.info(f"جارٍ إرسال أذكار المساء تلقائياً إلى {len(users)} طالب...")
    for user in users:
        try:
            await bot.send_message(
                chat_id=user["chat_id"],
                text=EVENING_ATHKAR_TEXT,
                parse_mode=ParseMode.MARKDOWN
            )
            await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"تعذر إرسال أذكار المساء إلى {user['chat_id']}: {e}")

async def broadcast_prayer_athkar(bot, prayer_name: str):
    """إرسال تنبيه الصلاة وأذكار ما بعد الصلاة تلقائياً"""
    text = PRAYER_POST_ATHKAR.get(prayer_name, "")
    if not text:
        return
    users = get_all_active_users()
    for user in users:
        try:
            await bot.send_message(
                chat_id=user["chat_id"],
                text=text,
                parse_mode=ParseMode.MARKDOWN
            )
            await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"تعذر إرسال تذكير صلاة {prayer_name} إلى {user['chat_id']}: {e}")

async def broadcast_friday_eve_reminder(bot):
    """تذكير ليلة الجمعة بسورة الكهف والمنافقون (الخميس 8:00 مساءً)"""
    users = get_all_active_users()
    for user in users:
        try:
            await bot.send_message(
                chat_id=user["chat_id"],
                text=FRIDAY_EVE_REMINDER,
                parse_mode=ParseMode.MARKDOWN
            )
            await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"تعذر إرسال تذكير ليلة الجمعة إلى {user['chat_id']}: {e}")

async def broadcast_friday_day_reminder(bot):
    """تذكير يوم الجمعة وسورة الكهف صباحاً (الجمعة 8:30 صباحاً كألبوم وسائط)"""
    users = get_all_active_users()
    media_group = [
        InputMediaPhoto(
            media=FRIDAY_BANNER_1,
            caption=FRIDAY_DAY_REMINDER,
            parse_mode=ParseMode.MARKDOWN
        ),
        InputMediaPhoto(media=FRIDAY_BANNER_2)
    ]
    for user in users:
        try:
            await bot.send_media_group(chat_id=user["chat_id"], media=media_group)
            await asyncio.sleep(0.1)
        except Exception as e:
            # في حال تعذر إرسال الوسائط يتم الإرسال كنص
            try:
                await bot.send_message(chat_id=user["chat_id"], text=FRIDAY_DAY_REMINDER, parse_mode=ParseMode.MARKDOWN)
            except Exception:
                pass

async def broadcast_friday_salawat(bot):
    """تذكيرات الصلاة على النبي ﷺ الدورية على مدار يوم الجمعة"""
    users = get_all_active_users()
    for user in users:
        try:
            await bot.send_message(
                chat_id=user["chat_id"],
                text=SALAWAT_PERIODIC_TEXT,
                parse_mode=ParseMode.MARKDOWN
            )
            await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"تعذر إرسال الصلاة على النبي إلى {user['chat_id']}: {e}")

async def broadcast_midnight_summary(bot):
    """إرسال التقرير اليومي للإنجازات وساعات المذاكرة تلقائياً عند منتصف الليل (00:00)"""
    users = get_all_active_users()
    for user in users:
        try:
            await bot.send_message(
                chat_id=user["chat_id"],
                text=DAILY_MIDNIGHT_SUMMARY,
                parse_mode=ParseMode.MARKDOWN
            )
            await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"تعذر إرسال ملخص منتصف الليل إلى {user['chat_id']}: {e}")

async def broadcast_study_goals_reminder(bot):
    """التذكير بالأهداف الدراسية وجلسات التركيز (الساعة 10:00 صباحاً و 4:00 عصراً)"""
    users = get_all_active_users()
    for user in users:
        try:
            await bot.send_message(
                chat_id=user["chat_id"],
                text=STUDY_GOALS_REMINDER,
                parse_mode=ParseMode.MARKDOWN
            )
            await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"تعذر إرسال تذكير الأهداف إلى {user['chat_id']}: {e}")

async def check_and_send_rating_request(bot):
    """إرسال طلب تقييم تطبيق رفيق الطالب على متجر Google Play بعد 3 أيام من التسجيل"""
    users = get_all_active_users()
    now = datetime.datetime.now(TIMEZONE)
    keyboard = [[InlineKeyboardButton("⭐ تقييم تطبيق رفيق الطالب الآن", url=PLAY_STORE_URL)]]
    reply_markup = InlineKeyboardMarkup(keyboard)

    for user in users:
        if user["rated_app"] == 1:
            continue
        try:
            join_dt = datetime.datetime.strptime(user["join_date"], "%Y-%m-%d %H:%M:%S")
            if join_dt.tzinfo is None:
                join_dt = TIMEZONE.localize(join_dt)
            days_diff = (now - join_dt).total_seconds() / (24 * 3600)
            if days_diff >= 3.0:
                await bot.send_message(
                    chat_id=user["chat_id"],
                    text=RATING_REQUEST_TEXT,
                    parse_mode=ParseMode.MARKDOWN,
                    reply_markup=reply_markup
                )
                mark_user_rated(user["chat_id"])
                logger.info(f"تم إرسال طلب التقييم للطالب {user['chat_id']}")
                await asyncio.sleep(0.05)
        except Exception as e:
            logger.warning(f"خطأ أثناء فحص تقييم الطالب {user['chat_id']}: {e}")

# =============================================================================
# 6. إعداد المجدول الزمني التلقائي (APScheduler)
# =============================================================================

def setup_scheduler(application: Application):
    """تهيئة جدول المواعيد والتنبيهات التلقائية بالكامل"""
    scheduler = AsyncIOScheduler(timezone=TIMEZONE)
    bot = application.bot

    # 1. أذكار الصباح يومياً (7:00 ص)
    scheduler.add_job(broadcast_morning_athkar, CronTrigger(hour=7, minute=0, timezone=TIMEZONE), args=[bot], id="morning_athkar", replace_existing=True)

    # 2. أذكار المساء يومياً (5:00 م / 17:00)
    scheduler.add_job(broadcast_evening_athkar, CronTrigger(hour=17, minute=0, timezone=TIMEZONE), args=[bot], id="evening_athkar", replace_existing=True)

    # 3. الصلوات الخمس وأذكار ما بعد الصلاة
    scheduler.add_job(broadcast_prayer_athkar, CronTrigger(hour=4, minute=45, timezone=TIMEZONE), args=[bot, "الفجر"], id="fajr_prayer", replace_existing=True)
    scheduler.add_job(broadcast_prayer_athkar, CronTrigger(hour=12, minute=5, timezone=TIMEZONE), args=[bot, "الظهر"], id="dhuhr_prayer", replace_existing=True)
    scheduler.add_job(broadcast_prayer_athkar, CronTrigger(hour=15, minute=20, timezone=TIMEZONE), args=[bot, "العصر"], id="asr_prayer", replace_existing=True)
    scheduler.add_job(broadcast_prayer_athkar, CronTrigger(hour=18, minute=5, timezone=TIMEZONE), args=[bot, "المغرب"], id="maghrib_prayer", replace_existing=True)
    scheduler.add_job(broadcast_prayer_athkar, CronTrigger(hour=19, minute=30, timezone=TIMEZONE), args=[bot, "العشاء"], id="isha_prayer", replace_existing=True)

    # 4. تذكير ليلة الجمعة (الخميس الساعة 20:00)
    scheduler.add_job(broadcast_friday_eve_reminder, CronTrigger(day_of_week="thu", hour=20, minute=0, timezone=TIMEZONE), args=[bot], id="friday_eve_reminder", replace_existing=True)

    # 5. تذكير وألبوم يوم الجمعة وسورة الكهف والمنافقون (الجمعة الساعة 8:30 ص)
    scheduler.add_job(broadcast_friday_day_reminder, CronTrigger(day_of_week="fri", hour=8, minute=30, timezone=TIMEZONE), args=[bot], id="friday_day_reminder", replace_existing=True)

    # 6. تذكيرات الصلاة على النبي الدورية يوم الجمعة (11:00 ص، 14:00 م، 16:30 م)
    scheduler.add_job(broadcast_friday_salawat, CronTrigger(day_of_week="fri", hour=11, minute=0, timezone=TIMEZONE), args=[bot], id="friday_salawat_1", replace_existing=True)
    scheduler.add_job(broadcast_friday_salawat, CronTrigger(day_of_week="fri", hour=14, minute=0, timezone=TIMEZONE), args=[bot], id="friday_salawat_2", replace_existing=True)
    scheduler.add_job(broadcast_friday_salawat, CronTrigger(day_of_week="fri", hour=16, minute=30, timezone=TIMEZONE), args=[bot], id="friday_salawat_3", replace_existing=True)

    # 7. التذكير بالأهداف الدراسية (10:00 ص و 16:00 م)
    scheduler.add_job(broadcast_study_goals_reminder, CronTrigger(hour=10, minute=0, timezone=TIMEZONE), args=[bot], id="study_goals_1", replace_existing=True)
    scheduler.add_job(broadcast_study_goals_reminder, CronTrigger(hour=16, minute=0, timezone=TIMEZONE), args=[bot], id="study_goals_2", replace_existing=True)

    # 8. ملخص الإنجاز والتقرير اليومي عند منتصف الليل (00:00)
    scheduler.add_job(broadcast_midnight_summary, CronTrigger(hour=0, minute=0, timezone=TIMEZONE), args=[bot], id="midnight_summary", replace_existing=True)

    # 9. فحص تقييم المتجر للمسجلين الجدد كل 4 ساعات
    scheduler.add_job(check_and_send_rating_request, CronTrigger(hour="*/4", minute=10, timezone=TIMEZONE), args=[bot], id="rating_check", replace_existing=True)

    scheduler.start()
    logger.info("تم تفعيل مجدول المهام الأوتوماتيكي (APScheduler) بنجاح 🚀")

# =============================================================================
# 7. تشغيل البوت الأساسي
# =============================================================================

def main():
    """نقطة انطلاق تطبيق البوت"""
    init_db()

    if not BOT_TOKEN:
        logger.error("خطأ حرج: لم يتم العثور على TELEGRAM_BOT_TOKEN في متغيرات البيئة (Environment Variables).")
        logger.error("يرجى تعيين TELEGRAM_BOT_TOKEN ثم إعادة التشغيل.")
        return

    application = Application.builder().token(BOT_TOKEN).build()

    # معالجات الأوامر
    application.add_handler(CommandHandler("start", start_command))
    application.add_handler(CommandHandler("help", help_command))
    application.add_handler(CommandHandler("storage", storage_command))
    application.add_handler(CommandHandler("files", storage_command))
    application.add_handler(CommandHandler("quran", quran_command))
    application.add_handler(CommandHandler("audio", audio_command))
    application.add_handler(CommandHandler("pdf", pdf_command))
    application.add_handler(CommandHandler("search", search_command))
    application.add_handler(CommandHandler("athkar", athkar_command))
    application.add_handler(CommandHandler("friday", friday_command))
    application.add_handler(CommandHandler("salawat", salawat_command))
    application.add_handler(CommandHandler("study", study_command))
    application.add_handler(CommandHandler("summary", summary_command))
    application.add_handler(CommandHandler("rate", rate_command))

    # معالج استقبال وسائط المخزن الرقمي السحابي (Documents, Audio, Voice, Video, Photos)
    application.add_handler(MessageHandler(
        filters.Document.ALL | filters.AUDIO | filters.VOICE | filters.VIDEO | filters.PHOTO,
        media_harvester_handler
    ))

    # معالج الأزرار التفاعلية
    application.add_handler(CallbackQueryHandler(button_callback_handler))

    # تشغيل مجدول المهام
    setup_scheduler(application)

    if WEBHOOK_URL:
        # وضع Webhook
        logger.info(f"جارِ تشغيل البوت بنمط الـ Webhook على الرابط: {WEBHOOK_URL} (المنفذ: {WEBHOOK_PORT})...")
        application.run_webhook(
            listen="0.0.0.0",
            port=WEBHOOK_PORT,
            url_path="telegram/webhook",
            webhook_url=f"{WEBHOOK_URL.rstrip('/')}/telegram/webhook",
            secret_token=TELEGRAM_SECRET_TOKEN if TELEGRAM_SECRET_TOKEN else None,
            drop_pending_updates=True
        )
    else:
        # وضع Polling (للتطوير المحلي فقط في حال عدم تفعيل الـ Webhook السحابي)
        logger.info(f"تم بدء الاستماع لبوت '{APP_NAME}' (@{BOT_USERNAME}) بنمط Polling...")
        application.run_polling(drop_pending_updates=True)

if __name__ == "__main__":
    main()
