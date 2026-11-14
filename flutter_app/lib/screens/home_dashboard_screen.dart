import 'package:flutter/material.dart';
import '../models/user_profile.dart';
import '../models/task_item.dart';
import '../services/telegram_service.dart';
import '../services/prayer_service.dart';
import 'quran_screen.dart';
import 'prayer_screen.dart';
import 'tasks_screen.dart';
import 'calculator_screen.dart';
import 'pomodoro_screen.dart';
import 'dictionary_screen.dart';
import 'cloud_storage_screen.dart';
import 'login_setup_screen.dart';

class HomeDashboardScreen extends StatefulWidget {
  final UserProfile profile;
  final Function(UserProfile) onUpdateProfile;

  const HomeDashboardScreen({
    Key? key,
    required this.profile,
    required this.onUpdateProfile,
  }) : super(key: key);

  @override
  State<HomeDashboardScreen> createState() => _HomeDashboardScreenState();
}

class _HomeDashboardScreenState extends State<HomeDashboardScreen> {
  late UserProfile _profile;
  late PrayerTimesInfo _prayerInfo;

  List<TaskItem> _tasks = [
    TaskItem(id: '1', title: 'حل نموذج امتحان الفيزياء الشامل', subject: 'فيزياء', date: 'اليوم', isCompleted: true),
    TaskItem(id: '2', title: 'مراجعة كلمات وقواعد Unit 7', subject: 'لغة إنجليزية', date: 'اليوم', isCompleted: true),
    TaskItem(id: '3', title: 'استذكار الباب الرابع كيمياء عضوية', subject: 'كيمياء', date: 'اليوم', isCompleted: false),
    TaskItem(id: '4', title: 'حفظ ومراجعة سورة الكهف وسورة المنافقون', subject: 'إسلاميات', date: 'يوم الجمعة', isCompleted: false),
  ];

  @override
  void initState() {
    super.initState();
    _profile = widget.profile;
    _prayerInfo = PrayerService.calculateTodayTimes();
  }

  void _incrementWater() {
    setState(() {
      _profile.waterCups++;
      widget.onUpdateProfile(_profile);
    });
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('بارك الله فيك! تم تسجيل كوب ماء 🥤 ترطيب عقلك يزيد من تركيزك!'), duration: Duration(seconds: 1)),
    );
  }

  void _decrementWater() {
    if (_profile.waterCups > 0) {
      setState(() {
        _profile.waterCups--;
        widget.onUpdateProfile(_profile);
      });
    }
  }

  void _sendTelegramDailyReport() {
    final msg = TelegramService.generateDailySummaryMessage(
      profile: _profile,
      tasks: _tasks,
    );
    TelegramService.launchTelegramWithReport(msg);
  }

  @override
  Widget build(BuildContext context) {
    final completedCount = _tasks.where((t) => t.isCompleted).length;
    final isFriday = DateTime.now().weekday == DateTime.friday || DateTime.now().weekday == DateTime.thursday;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 1,
        title: Row(
          children: [
            const CircleAvatar(
              backgroundColor: Color(0xFF0088CC),
              radius: 18,
              child: Icon(Icons.school, color: Colors.white, size: 20),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _profile.fullName.isNotEmpty ? _profile.fullName : 'طالب العلم 🎓',
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.black87),
                ),
                Text(
                  _profile.studentGradeYear,
                  style: const TextStyle(fontSize: 11, color: Colors.grey),
                ),
              ],
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.send, color: Color(0xFF0088CC)),
            tooltip: 'فتح بوت رفيق الطالب',
            onPressed: () => TelegramService.openOfficialBot(),
          ),
          IconButton(
            icon: const Icon(Icons.cloud_download, color: Color(0xFF0088CC)),
            tooltip: 'المخزن السحابي',
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const CloudStorageScreen())),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 1. بطاقة تقرير الإنجاز والتذكيرات عبر Telegram (البطاقة الرئيسية)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF0088CC), Color(0xFF005580)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFF0088CC).withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: const [
                          Icon(Icons.send, color: Colors.white, size: 24),
                          SizedBox(width: 8),
                          Text(
                            'تقرير الإنجاز والتذكيرات عبر Telegram ✈️',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          _profile.telegramIdOrPhone.isNotEmpty ? _profile.telegramIdOrPhone : 'مفعل آلياً',
                          style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'يتم إرسال التنبيهات والأذكار وجدول المذاكرة وملخص الإنجازات تلقائياً عبر بوت @RafeeqAlTalebBot',
                    style: TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                  const SizedBox(height: 14),

                  // Quick Achievement Stats
                  Row(
                    children: [
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.15),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('المهام المنجزة', style: TextStyle(color: Colors.white70, fontSize: 10)),
                              Text('$completedCount من ${_tasks.length}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.15),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('الترطيب 💧', style: TextStyle(color: Colors.white70, fontSize: 10)),
                              Text('${_profile.waterCups} / ${_profile.waterGoal} أكواب', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.15),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text('النقاط 🌟', style: TextStyle(color: Colors.white70, fontSize: 10)),
                              Text('${_profile.points} نقطة', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  // Action Buttons
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _sendTelegramDailyReport,
                          icon: const Icon(Icons.send_rounded, size: 16),
                          label: const Text('إرسال التقرير لـ Telegram', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.white,
                            foregroundColor: const Color(0xFF0088CC),
                            shape: RoundedCornerShape(12),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      OutlinedButton.icon(
                        onPressed: () => TelegramService.openOfficialBot(),
                        icon: const Icon(Icons.smart_toy_outlined, color: Colors.white, size: 16),
                        label: const Text('البوت', style: TextStyle(color: Colors.white, fontSize: 12)),
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: Colors.white70),
                          shape: RoundedCornerShape(12),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // 2. بطاقة تذكير الصلاة التالية
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: Row(
                children: [
                  const CircleAvatar(
                    backgroundColor: Color(0xFFE8F5E9),
                    radius: 20,
                    child: Icon(Icons.mosque, color: Color(0xFF2E7D32), size: 22),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('الصلاة القادمة: صلاة ${_prayerInfo.nextPrayerName} (${_prayerInfo.dhuhr})',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                        Text('متبقي: ${_prayerInfo.timeRemaining} • القاهرة',
                            style: const TextStyle(fontSize: 11, color: Colors.grey)),
                      ],
                    ),
                  ),
                  TextButton(
                    onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PrayerScreen())),
                    child: const Text('المواقيت والأذكار', style: TextStyle(fontSize: 11, color: Color(0xFF2E7D32))),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // 3. قسم الأدوات والخدمات المدمجة الشاملة (Quran, Dictionary, Pomodoro, Calculator, Tasks, Cloud)
            const Text('الأدوات الدراسية والإسلامية 🛠️', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            const SizedBox(height: 10),
            GridView.count(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              crossAxisCount: 3,
              mainAxisSpacing: 10,
              crossAxisSpacing: 10,
              childAspectRatio: 1.05,
              children: [
                _buildToolCard(
                  title: 'المصحف الشريف',
                  icon: Icons.menu_book,
                  color: const Color(0xFF2E7D32),
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const QuranScreen())),
                ),
                _buildToolCard(
                  title: 'القاموس واللغات',
                  icon: Icons.translate,
                  color: const Color(0xFF673AB7),
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const DictionaryScreen())),
                ),
                _buildToolCard(
                  title: 'بومودورو التركيز',
                  icon: Icons.timer,
                  color: const Color(0xFFE53935),
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PomodoroScreen())),
                ),
                _buildToolCard(
                  title: 'الآلة الحاسبة',
                  icon: Icons.calculate,
                  color: const Color(0xFFFB8C00),
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const CalculatorScreen())),
                ),
                _buildToolCard(
                  title: 'المهام والملاحظات',
                  icon: Icons.check_circle_outline,
                  color: const Color(0xFF00897B),
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TasksScreen())),
                ),
                _buildToolCard(
                  title: 'المخزن السحابي',
                  icon: Icons.cloud_queue,
                  color: const Color(0xFF0088CC),
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const CloudStorageScreen())),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // 4. تتبع شرب الماء ("هل شربت ماء كافي اليوم؟")
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFF0288D1).withOpacity(0.2)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: const [
                          Icon(Icons.water_drop, color: Color(0xFF0288D1), size: 20),
                          SizedBox(width: 8),
                          Text('هل شربت ماء كافي اليوم؟ 💧', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                        ],
                      ),
                      Text('${_profile.waterCups * 250} مل / ${_profile.waterGoal * 250} مل',
                          style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF0288D1), fontSize: 12)),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: Row(
                          children: List.generate(8, (index) {
                            return Icon(
                              Icons.local_drink,
                              size: 20,
                              color: index < _profile.waterCups ? const Color(0xFF0288D1) : Colors.grey.shade300,
                            );
                          }),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.remove_circle_outline, color: Colors.grey),
                        onPressed: _decrementWater,
                      ),
                      IconButton(
                        icon: const Icon(Icons.add_circle, color: Color(0xFF0288D1)),
                        onPressed: _incrementWater,
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // 5. بطاقة سُنن يوم الجمعة (سورة الكهف والمنافقون)
            if (isFriday) ...[
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFF2E7D32).withOpacity(0.08),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: const Color(0xFF2E7D32).withOpacity(0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: const [
                        Icon(Icons.mosque, color: Color(0xFF2E7D32), size: 20),
                        SizedBox(width: 8),
                        Text('سُنن ونفحات يوم وفجر الجمعة المبارك 🕌', style: TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF2E7D32), fontSize: 13)),
                      ],
                    ),
                    const SizedBox(height: 8),
                    CheckboxListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: const Text('1. قراءة سورة الكهف (نور ما بين الجمعتين)', style: TextStyle(fontSize: 12)),
                      value: _profile.fridayKahfRead,
                      onChanged: (val) {
                        setState(() {
                          _profile.fridayKahfRead = val ?? false;
                          widget.onUpdateProfile(_profile);
                        });
                      },
                    ),
                    CheckboxListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: const Text('2. قراءة سورة المنافقون وسورة الجمعة والصلاة على النبي ﷺ', style: TextStyle(fontSize: 12)),
                      value: _profile.fridayMunafiqoonRead,
                      onChanged: (val) {
                        setState(() {
                          _profile.fridayMunafiqoonRead = val ?? false;
                          widget.onUpdateProfile(_profile);
                        });
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildToolCard({
    required String title,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.grey.shade200),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircleAvatar(
              backgroundColor: color.withOpacity(0.12),
              radius: 20,
              child: Icon(icon, color: color, size: 22),
            ),
            const SizedBox(height: 8),
            Text(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
            ),
          ],
        ),
      ),
    );
  }
}
