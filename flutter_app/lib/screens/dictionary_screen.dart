import 'package:flutter/material.dart';

class DictionaryScreen extends StatefulWidget {
  const DictionaryScreen({Key? key}) : super(key: key);

  @override
  State<DictionaryScreen> createState() => _DictionaryScreenState();
}

class _DictionaryScreenState extends State<DictionaryScreen> {
  final List<Map<String, String>> _words = [
    {"en": "Achievement", "ar": "إنجاز / تحقيق الهدف", "example": "His academic achievement was outstanding.", "pos": "Noun"},
    {"en": "Concentration", "ar": "تركيز ذهني", "example": "Pomodoro technique improves concentration.", "pos": "Noun"},
    {"en": "Consistent", "ar": "مستمر / ثابت على المبدأ", "example": "Consistent study leads to success.", "pos": "Adjective"},
    {"en": "Perseverance", "ar": "المثابرة والمواظبة", "example": "Success requires perseverance and patience.", "pos": "Noun"},
    {"en": "Comprehension", "ar": "الفهم والاستيعاب", "example": "Reading helps in deep comprehension.", "pos": "Noun"},
    {"en": "Efficient", "ar": "فعّال وذو كفاءة", "example": "She has an efficient study routine.", "pos": "Adjective"},
    {"en": "Motivation", "ar": "الدافعية والتحفيز", "example": "Daily goals boost your study motivation.", "pos": "Noun"},
    {"en": "Productivity", "ar": "الإنتاجية", "example": "Hydration enhances mental productivity.", "pos": "Noun"},
    {"en": "Hydration", "ar": "ترطيب الجسم وشرب الماء", "example": "Brain hydration prevents exam headaches.", "pos": "Noun"},
    {"en": "Excellence", "ar": "التفوق والامتياز", "example": "Strive for excellence in every subject.", "pos": "Noun"},
  ];

  String _search = "";

  @override
  Widget build(BuildContext context) {
    final filtered = _words.where((w) =>
        w["en"]!.toLowerCase().contains(_search.toLowerCase()) ||
        w["ar"]!.contains(_search)).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('القاموس والمفردات الإنجليزية 📚', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF673AB7),
        foregroundColor: Colors.white,
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: TextField(
              decoration: InputDecoration(
                hintText: 'ابحث عن كلمة بالإنجليزية أو العربية...',
                prefixIcon: const Icon(Icons.search),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                filled: true,
                fillColor: Colors.white,
              ),
              onChanged: (v) => setState(() => _search = v),
            ),
          ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              itemCount: filtered.length,
              itemBuilder: (context, index) {
                final item = filtered[index];
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  shape: RoundedCornerShape(12),
                  child: ListTile(
                    title: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(item["en"]!, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Color(0xFF673AB7))),
                        Chip(label: Text(item["pos"]!, style: const TextStyle(fontSize: 10))),
                      ],
                    ),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 4),
                        Text(item["ar"]!, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.black87)),
                        const SizedBox(height: 4),
                        Text('مثال: ${item["example"]!}', style: const TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: Colors.grey)),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
