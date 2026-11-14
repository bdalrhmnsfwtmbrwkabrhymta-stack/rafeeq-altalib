import 'package:flutter/material.dart';
import '../services/prayer_service.dart';

class PrayerScreen extends StatelessWidget {
  const PrayerScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final times = PrayerService.calculateTodayTimes();
    final prayers = [
      {"name": "الفجر", "time": times.fajr, "icon": Icons.wb_twilight},
      {"name": "الشروق", "time": times.sunrise, "icon": Icons.wb_sunny_outlined},
      {"name": "الظهر", "time": times.dhuhr, "icon": Icons.wb_sunny},
      {"name": "العصر", "time": times.asr, "icon": Icons.filter_drama},
      {"name": "المغرب", "time": times.maghrib, "icon": Icons.nights_stay_outlined},
      {"name": "العشاء", "time": times.isha, "icon": Icons.nights_stay},
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('مواقيت الصلاة والأذكار 🕌', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF2E7D32),
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF2E7D32).withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFF2E7D32).withOpacity(0.3)),
              ),
              child: Column(
                children: [
                  const Text('الصلاة القادمة', style: TextStyle(color: Colors.grey, fontSize: 12)),
                  const SizedBox(height: 4),
                  Text('صلاة ${times.nextPrayerName}', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Color(0xFF2E7D32))),
                  Text('متبقي: ${times.timeRemaining}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                ],
              ),
            ),
            const SizedBox(height: 16),
            const Text('مواقيت اليوم (توقيت القاهرة ومصر):', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            const SizedBox(height: 8),
            ...prayers.map((p) => Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  shape: RoundedCornerShape(12),
                  child: ListTile(
                    leading: Icon(p["icon"] as IconData, color: const Color(0xFF2E7D32)),
                    title: Text(p["name"] as String, style: const TextStyle(fontWeight: FontWeight.bold)),
                    trailing: Text(p["time"] as String, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                    onTap: () {
                      final athkar = PrayerService.getPostPrayerAthkar(p["name"] as String);
                      showDialog(
                        context: context,
                        builder: (_) => AlertDialog(
                          title: Text(athkar["title"]!),
                          content: SingleChildScrollView(child: Text(athkar["content"]!, style: const TextStyle(height: 1.6))),
                          actions: [
                            TextButton(onPressed: () => Navigator.pop(context), child: const Text('إغلاق')),
                          ],
                        ),
                      );
                    },
                  ),
                )),
          ],
        ),
      ),
    );
  }
}
