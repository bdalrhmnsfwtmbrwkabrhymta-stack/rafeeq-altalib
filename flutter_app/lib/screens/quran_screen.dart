import 'package:flutter/material.dart';

class QuranScreen extends StatefulWidget {
  const QuranScreen({Key? key}) : super(key: key);

  @override
  State<QuranScreen> createState() => _QuranScreenState();
}

class _QuranScreenState extends State<QuranScreen> {
  final List<Map<String, dynamic>> _surahs = [
    {"number": 1, "name": "الفاتحة", "englishName": "Al-Fatiha", "ayahs": 7, "type": "مكية"},
    {"number": 2, "name": "البقرة", "englishName": "Al-Baqarah", "ayahs": 286, "type": "مدنية"},
    {"number": 3, "name": "آل عمران", "englishName": "Aal-Imran", "ayahs": 200, "type": "مدنية"},
    {"number": 4, "name": "النساء", "englishName": "An-Nisa", "ayahs": 176, "type": "مدنية"},
    {"number": 18, "name": "الكهف (سورة الجمعة)", "englishName": "Al-Kahf", "ayahs": 110, "type": "مكية"},
    {"number": 36, "name": "يس", "englishName": "Ya-Sin", "ayahs": 83, "type": "مكية"},
    {"number": 62, "name": "الجمعة", "englishName": "Al-Jumu'ah", "ayahs": 11, "type": "مدنية"},
    {"number": 63, "name": "المنافقون", "englishName": "Al-Munafiqoon", "ayahs": 11, "type": "مدنية"},
    {"number": 67, "name": "الملك", "englishName": "Al-Mulk", "ayahs": 30, "type": "مكية"},
    {"number": 112, "name": "الإخلاص", "englishName": "Al-Ikhlas", "ayahs": 4, "type": "مكية"},
    {"number": 113, "name": "الفلق", "englishName": "Al-Falaq", "ayahs": 5, "type": "مكية"},
    {"number": 114, "name": "الناس", "englishName": "An-Nas", "ayahs": 6, "type": "مكية"},
  ];

  String _searchQuery = "";

  @override
  Widget build(BuildContext context) {
    final filtered = _surahs.where((s) => s["name"].contains(_searchQuery) || s["englishName"].toLowerCase().contains(_searchQuery.toLowerCase())).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('المصحف الشريف 📖', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF2E7D32),
        foregroundColor: Colors.white,
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: TextField(
              decoration: InputDecoration(
                hintText: 'ابحث عن سورة (مثل: الكهف، المنافقون، البقرة)...',
                prefixIcon: const Icon(Icons.search),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                filled: true,
                fillColor: Colors.white,
              ),
              onChanged: (val) => setState(() => _searchQuery = val),
            ),
          ),
          Expanded(
            child: ListView.separated(
              itemCount: filtered.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (context, index) {
                final s = filtered[index];
                final isSpecial = s["number"] == 18 || s["number"] == 63;
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: isSpecial ? const Color(0xFF2E7D32) : Colors.grey.shade200,
                    foregroundColor: isSpecial ? Colors.white : Colors.black87,
                    child: Text('${s["number"]}'),
                  ),
                  title: Text(
                    s["name"],
                    style: TextStyle(fontWeight: FontWeight.bold, color: isSpecial ? const Color(0xFF2E7D32) : Colors.black87),
                  ),
                  subtitle: Text('${s["englishName"]} • ${s["ayahs"]} آية • ${s["type"]}'),
                  trailing: const Icon(Icons.arrow_forward_ios, size: 16),
                  onTap: () {
                    showModalBottomSheet(
                      context: context,
                      builder: (_) => Container(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text('سورة ${s["name"]}', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                            const SizedBox(height: 10),
                            const Text(
                              'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ\n\n«الحمد لله الذي أنزل على عبده الكتاب ولم يجعل له عوجا»',
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 16, height: 1.8),
                            ),
                            const SizedBox(height: 16),
                            ElevatedButton.icon(
                              onPressed: () => Navigator.pop(context),
                              icon: const Icon(Icons.check),
                              label: const Text('تمت القراءة وتثبيت الورد'),
                              style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF2E7D32), foregroundColor: Colors.white),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
