import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';
import '../models/user_profile.dart';
import '../models/task_item.dart';

class TelegramService {
  static const String botToken = "8895277938:AAE9p2QAx-SW33O_CAiIfKgTsVShi8Pbhmo";
  static const String botUsername = "RafeeqAlTalebBot";
  static const String storageChannelLink = "https://t.me/+zAsB3Bq2cwRlYjc0";

  /// إرسال رسالة مباشرة إلى التليجرام عبر واجهة Telegram Bot API
  static Future<bool> sendMessageViaBotApi({
    required String chatIdOrUsername,
    required String message,
  }) async {
    try {
      final url = Uri.parse("https://api.telegram.org/bot$botToken/sendMessage");
      final response = await http.post(
        url,
        headers: {"Content-Type": "application/json"},
        body: jsonEncode({
          "chat_id": chatIdOrUsername,
          "text": message,
          "parse_mode": "Markdown",
        }),
      );
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }

  /// فتح تطبيق التليجرام مباشرة مع النص المجهز
  static Future<void> launchTelegramWithReport(String message) async {
    final encoded = Uri.encodeComponent(message);
    final tgUri = Uri.parse("tg://msg?text=$encoded");
    final webUri = Uri.parse("https://t.me/share/url?url=&text=$encoded");

    if (await canLaunchUrl(tgUri)) {
      await launchUrl(tgUri, mode: LaunchMode.externalApplication);
    } else {
      await launchUrl(webUri, mode: LaunchMode.externalApplication);
    }
  }

  /// فتح البوت الرسمي في تطبيق التليجرام
  static Future<void> openOfficialBot() async {
    final uri = Uri.parse("https://t.me/$botUsername");
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  /// فتح قناة المخزن الرقمي السحابي
  static Future<void> openStorageChannel() async {
    final uri = Uri.parse(storageChannelLink);
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  /// توليد نص التقرير اليومي الشامل للإرسال عبر التليجرام
  static String generateDailySummaryMessage({
    required UserProfile profile,
    required List<TaskItem> tasks,
  }) {
    final completed = tasks.where((t) => t.isCompleted).length;
    final total = tasks.length;
    final pct = total > 0 ? ((completed / total) * 100).toInt() : 100;
    final isFriday = DateTime.now().weekday == DateTime.friday;

    final buffer = StringBuffer();
    buffer.writeln("🎓 *تقرير الإنجاز والتذكيرات عبر Telegram* ✈️");
    buffer.writeln("👤 *الطالب:* ${profile.fullName.isNotEmpty ? profile.fullName : 'طالب العلم المثابر'}");
    buffer.writeln("📅 *التاريخ:* ${DateTime.now().year}/${DateTime.now().month}/${DateTime.now().day}");
    buffer.writeln("━━━━━━━━━━━━━━━━━━━\n");

    buffer.writeln("📋 *1. تقرير المهام الدراسية:*");
    buffer.writeln("✅ المهام المكتملة: $completed من أصل $total ($pct%)");
    for (var t in tasks.take(5)) {
      buffer.writeln("   ${t.isCompleted ? '✓' : '▫️'} ${t.title} (${t.subject})");
    }
    buffer.writeln("");

    buffer.writeln("💧 *2. صحة وترطيب العقل:*");
    buffer.writeln("🥤 شرب الماء: ${profile.waterCups} من أصل ${profile.waterGoal} أكواب");
    if (profile.waterCups >= profile.waterGoal) {
      buffer.writeln("✨ رائع! خلايا مخك رطبة وفي قمة التركيز.");
    } else {
      buffer.writeln("⚠️ تذكير: متبقي لك ${profile.waterGoal - profile.waterCups} أكواب لتنشيط الذاكرة!");
    }
    buffer.writeln("");

    if (isFriday) {
      buffer.writeln("🕌 *3. سنن ونفحات يوم الجمعة:*");
      buffer.writeln("📖 قراءة سورة الكهف وسورة المنافقون.");
      buffer.writeln("🌸 الإكثار من الصلاة على النبي ﷺ والدعاء في ساعة الإجابة.");
      buffer.writeln("");
    }

    buffer.writeln("💡 *هدف اليوم:* ${profile.dailyGoal}");
    buffer.writeln("━━━━━━━━━━━━━━━━━━━");
    buffer.writeln("🚀 صادر تلقائياً من تطبيق *رفيق الطالب* (@$botUsername) ✨");

    return buffer.toString();
  }
}
