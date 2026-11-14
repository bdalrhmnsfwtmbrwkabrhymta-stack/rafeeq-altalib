import 'package:flutter/material.dart';
import '../services/telegram_service.dart';

class CloudStorageScreen extends StatelessWidget {
  const CloudStorageScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final sampleFiles = [
      {"name": "ملخص شامل كيمياء عضوية للثانوية العامة.pdf", "type": "PDF", "category": "دراسة ومذكرات", "size": "4.2 MB", "icon": Icons.picture_as_pdf, "color": Colors.red},
      {"name": "تسجيل أذان القاهرة بصوت الشيخ محمد رفعت.mp3", "type": "صوت", "category": "أذان وأصوات", "size": "3.1 MB", "icon": Icons.audiotrack, "color": Colors.purple},
      {"name": "تلاوة سورة الكهف وسورة المنافقون كاملة.mp3", "type": "تلاوة", "category": "قرآن كريم", "size": "12.8 MB", "icon": Icons.menu_book, "color": Colors.green},
      {"name": "مذكرة نماذج امتحانات الفيزياء الحديثة وإجاباتها.pdf", "type": "PDF", "category": "امتحانات", "size": "6.5 MB", "icon": Icons.picture_as_pdf, "color": Colors.red},
      {"name": "تسجيل أذكار الصباح والمساء كاملة بصوت عذب.mp3", "type": "صوت", "category": "أذكار يومية", "size": "8.4 MB", "icon": Icons.headset, "color": Colors.blue},
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('المخزن الرقمي السحابي ☁️', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF0088CC),
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.open_in_new),
            tooltip: 'فتح القناة السحابية المباشرة',
            onPressed: () => TelegramService.openStorageChannel(),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF0088CC).withOpacity(0.08),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFF0088CC).withOpacity(0.2)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: const [
                      Icon(Icons.cloud_done, color: Color(0xFF0088CC)),
                      SizedBox(width: 8),
                      Text('سحابة رفيق الطالب الرقمية المربوطة', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Color(0xFF0088CC))),
                    ],
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'يتم أرشفة المذكرات الدراسية، ملفات الـ PDF، الصوتيات، والتلاوات فور رفعها في المخزن السحابي وتوفيرها للطلاب.',
                    style: TextStyle(fontSize: 12, color: Colors.black87),
                  ),
                  const SizedBox(height: 10),
                  ElevatedButton.icon(
                    onPressed: () => TelegramService.openStorageChannel(),
                    icon: const Icon(Icons.cloud_upload, size: 18),
                    label: const Text('زيارة المخزن الرقمي السحابي (Telegram)', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF0088CC),
                      foregroundColor: Colors.white,
                      shape: RoundedCornerShape(12),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            const Text('الملفات والصوتيات المؤرشفة مؤخراً:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            const SizedBox(height: 10),
            ...sampleFiles.map((file) => Card(
                  margin: const EdgeInsets.only(bottom: 10),
                  shape: RoundedCornerShape(12),
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: (file["color"] as Color).withOpacity(0.12),
                      child: Icon(file["icon"] as IconData, color: file["color"] as Color),
                    ),
                    title: Text(file["name"] as String, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                    subtitle: Text('${file["category"]} • ${file["size"]}'),
                    trailing: IconButton(
                      icon: const Icon(Icons.download, color: Color(0xFF0088CC)),
                      onPressed: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text('جارٍ فتح وتحميل ${file["name"]} من المخزن السحابي 📥')),
                        );
                        TelegramService.openStorageChannel();
                      },
                    ),
                  ),
                )),
          ],
        ),
      ),
    );
  }
}
