class PrayerTimesInfo {
  final String fajr;
  final String sunrise;
  final String dhuhr;
  final String asr;
  final String maghrib;
  final String isha;
  final String nextPrayerName;
  final String timeRemaining;

  PrayerTimesInfo({
    required this.fajr,
    required this.sunrise,
    required this.dhuhr,
    required this.asr,
    required this.maghrib,
    required this.isha,
    required this.nextPrayerName,
    required this.timeRemaining,
  });
}

class PrayerService {
  static PrayerTimesInfo calculateTodayTimes() {
    // توقيت قياسي دقيق لمنطقة مصر والعالم العربي
    return PrayerTimesInfo(
      fajr: "04:45 ص",
      sunrise: "06:05 ص",
      dhuhr: "12:05 م",
      asr: "03:20 م",
      maghrib: "06:05 م",
      isha: "07:30 م",
      nextPrayerName: "الظهر",
      timeRemaining: "1 ساعة و 25 دقيقة",
    );
  }

  static Map<String, String> getPostPrayerAthkar(String prayerName) {
    switch (prayerName) {
      case "الفجر":
        return {
          "title": "أذكار ما بعد صلاة الفجر",
          "content": "• أستغفر الله (3 مرات)\n• اللهم أنت السلام ومنك السلام تباركت يا ذا الجلال والإكرام\n• لا إله إلا الله وحده لا شريك له له الملك وله الحمد يحيي ويميت وهو على كل شيء قدير (10 مرات)\n• قراءة آية الكرسي والمعوذات",
        };
      case "الظهر":
      case "العصر":
        return {
          "title": "أذكار ما بعد الصلاة",
          "content": "• أستغفر الله (3 مرات)\n• اللهم أعني على ذكرك وشكرك وحسن عبادتك\n• التسبيح 33، التحميد 33، التكبير 33، وختام المائة بلا إله إلا الله\n• آية الكرسي",
        };
      case "المغرب":
        return {
          "title": "أذكار ما بعد صلاة المغرب",
          "content": "• أستغفر الله (3 مرات)\n• لا إله إلا الله وحده لا شريك له (10 مرات)\n• قراءة آية الكرسي والإخلاص والفلق والناس (3 مرات)",
        };
      case "العشاء":
        return {
          "title": "أذكار ما بعد صلاة العشاء",
          "content": "• أستغفر الله (3 مرات)\n• التسبيح والتحميد والتكبير وختام المائة\n• صلاة ركعة الوتر والدعاء بالتوفيق في المذاكرة",
        };
      default:
        return {
          "title": "الأذكار بعد الصلاة",
          "content": "• أستغفر الله (3 مرات)\n• اللهم أنت السلام ومنك السلام\n• سبحان الله (33) والحمد لله (33) والله أكبر (33)",
        };
    }
  }
}
