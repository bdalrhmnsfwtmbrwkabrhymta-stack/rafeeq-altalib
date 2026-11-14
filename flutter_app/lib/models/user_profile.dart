class UserProfile {
  String fullName;
  String telegramIdOrPhone;
  String passwordPin;
  String gradeStage;
  String highSchoolBranch;
  String studentGradeYear;
  int studentAge;
  String gmail;
  bool isRegistered;
  int waterCups;
  int waterGoal;
  String dailyGoal;
  int points;
  bool fridayKahfRead;
  bool fridayMunafiqoonRead;

  UserProfile({
    this.fullName = '',
    this.telegramIdOrPhone = '',
    this.passwordPin = '',
    this.gradeStage = 'HIGH_SCHOOL',
    this.highSchoolBranch = 'SCIENCE',
    this.studentGradeYear = 'الصف الثالث الثانوي',
    this.studentAge = 17,
    this.gmail = '',
    this.isRegistered = false,
    this.waterCups = 0,
    this.waterGoal = 8,
    this.dailyGoal = 'إتمام مذاكرة وحدتين ومراجعة الكلمات',
    this.points = 120,
    this.fridayKahfRead = false,
    this.fridayMunafiqoonRead = false,
  });

  Map<String, dynamic> toJson() => {
        'fullName': fullName,
        'telegramIdOrPhone': telegramIdOrPhone,
        'passwordPin': passwordPin,
        'gradeStage': gradeStage,
        'highSchoolBranch': highSchoolBranch,
        'studentGradeYear': studentGradeYear,
        'studentAge': studentAge,
        'gmail': gmail,
        'isRegistered': isRegistered,
        'waterCups': waterCups,
        'waterGoal': waterGoal,
        'dailyGoal': dailyGoal,
        'points': points,
        'fridayKahfRead': fridayKahfRead,
        'fridayMunafiqoonRead': fridayMunafiqoonRead,
      };

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        fullName: json['fullName'] ?? '',
        telegramIdOrPhone: json['telegramIdOrPhone'] ?? '',
        passwordPin: json['passwordPin'] ?? '',
        gradeStage: json['gradeStage'] ?? 'HIGH_SCHOOL',
        highSchoolBranch: json['highSchoolBranch'] ?? 'SCIENCE',
        studentGradeYear: json['studentGradeYear'] ?? 'الصف الثالث الثانوي',
        studentAge: json['studentAge'] ?? 17,
        gmail: json['gmail'] ?? '',
        isRegistered: json['isRegistered'] ?? false,
        waterCups: json['waterCups'] ?? 0,
        waterGoal: json['waterGoal'] ?? 8,
        dailyGoal: json['dailyGoal'] ?? '',
        points: json['points'] ?? 0,
        fridayKahfRead: json['fridayKahfRead'] ?? false,
        fridayMunafiqoonRead: json['fridayMunafiqoonRead'] ?? false,
      );
}
