import 'package:flutter/material.dart';
import '../models/user_profile.dart';
import '../services/telegram_service.dart';

class LoginSetupScreen extends StatefulWidget {
  final UserProfile userProfile;
  final Function(UserProfile) onCompleted;

  const LoginSetupScreen({
    Key? key,
    required this.userProfile,
    required this.onCompleted,
  }) : super(key: key);

  @override
  State<LoginSetupScreen> createState() => _LoginSetupScreenState();
}

class _LoginSetupScreenState extends State<LoginSetupScreen> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _nameController;
  late TextEditingController _telegramController;
  late TextEditingController _passwordController;
  late TextEditingController _gradeController;
  late TextEditingController _gmailController;

  String _stage = 'HIGH_SCHOOL';
  String _branch = 'SCIENCE';
  int _age = 17;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.userProfile.fullName);
    _telegramController = TextEditingController(text: widget.userProfile.telegramIdOrPhone);
    _passwordController = TextEditingController(text: widget.userProfile.passwordPin);
    _gradeController = TextEditingController(text: widget.userProfile.studentGradeYear);
    _gmailController = TextEditingController(text: widget.userProfile.gmail);
    _stage = widget.userProfile.gradeStage;
    _branch = widget.userProfile.highSchoolBranch;
    _age = widget.userProfile.studentAge;
  }

  @override
  void dispose() {
    _nameController.dispose();
    _telegramController.dispose();
    _passwordController.dispose();
    _gradeController.dispose();
    _gmailController.dispose();
    super.dispose();
  }

  void _submit() async {
    if (_formKey.currentState!.validate()) {
      setState(() => _isLoading = true);

      // محاكاة إرسال تأكيد الحساب عبر Telegram Bot API
      final updatedProfile = UserProfile(
        fullName: _nameController.text.trim(),
        telegramIdOrPhone: _telegramController.text.trim(),
        passwordPin: _passwordController.text.trim(),
        gradeStage: _stage,
        highSchoolBranch: _branch,
        studentGradeYear: _gradeController.text.trim(),
        studentAge: _age,
        gmail: _gmailController.text.trim(),
        isRegistered: true,
      );

      // إرسال ترحيب أو ربط
      TelegramService.sendMessageViaBotApi(
        chatIdOrUsername: updatedProfile.telegramIdOrPhone,
        message: "👋 أهلاً بك يا ${updatedProfile.fullName} في منظومة *رفيق الطالب*! تم تفعيل ربط الحساب وتأكيد استقبال الإشعارات بنجاح 🎓",
      );

      await Future.delayed(const Duration(milliseconds: 600));
      setState(() => _isLoading = false);

      widget.onCompleted(updatedProfile);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('تسجيل حساب رفيق الطالب 🎓', style: TextStyle(fontWeight: FontWeight.bold)),
        centerTitle: true,
        backgroundColor: Colors.white,
        elevation: 0,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20.0),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Hero Banner
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF0088CC).withOpacity(0.08),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFF0088CC).withOpacity(0.2)),
                  ),
                  child: Row(
                    children: [
                      const CircleAvatar(
                        radius: 24,
                        backgroundColor: Color(0xFF0088CC),
                        child: Icon(Icons.send, color: Colors.white, size: 24),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: const [
                            Text(
                              'ربط الحساب والتنبيهات عبر Telegram',
                              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Color(0xFF0088CC)),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'الاسم بالكامل ومعرف التليجرام إجباريان لتأكيد استلام تقارير الإنجاز والأذكار وجدول المذاكرة.',
                              style: TextStyle(fontSize: 12, color: Colors.black87),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // 1. الاسم بالكامل (إجباري - ثنائي أو ثلاثي)
                TextFormField(
                  controller: _nameController,
                  decoration: InputDecoration(
                    labelText: 'الاسم بالكامل (إجباري) *',
                    hintText: 'مثال: عبد الرحمن صفوت',
                    prefixIcon: const Icon(Icons.person),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    filled: true,
                    fillColor: Colors.white,
                  ),
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'يرجى إدخال الاسم بالكامل';
                    }
                    final parts = value.trim().split(RegExp(r'\s+'));
                    if (parts.length < 2) {
                      return 'يرجى كتابة الاسم الثنائي أو الثلاثي على الأقل';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),

                // 2. رقم أو معرف التليجرام (إجباري)
                TextFormField(
                  controller: _telegramController,
                  decoration: InputDecoration(
                    labelText: 'رقم أو معرّف التليجرام (إجباري) *',
                    hintText: 'مثال: @username أو 01007730570',
                    prefixIcon: const Icon(Icons.send, color: Color(0xFF0088CC)),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    filled: true,
                    fillColor: Colors.white,
                  ),
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'يرجى إدخال رقم أو معرف التليجرام لربط التنبيهات';
                    }
                    if (value.trim().length < 4) {
                      return 'معرف أو رقم التليجرام قصير جداً';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),

                // 3. الرقم السري للحساب (إجباري)
                TextFormField(
                  controller: _passwordController,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: 'الرقم السري للحساب (كلمة المرور) *',
                    hintText: 'لا يقل عن 4 خانات لاسترجاع البيانات',
                    prefixIcon: const Icon(Icons.lock),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    filled: true,
                    fillColor: Colors.white,
                  ),
                  validator: (value) {
                    if (value == null || value.trim().length < 4) {
                      return 'يرجى كتابة كلمة مرور من 4 خانات على الأقل';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),

                // 4. المرحلة الدراسية
                const Text('المرحلة الدراسية:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: ChoiceChip(
                        label: const Text('الثانوية العامة'),
                        selected: _stage == 'HIGH_SCHOOL',
                        onSelected: (val) => setState(() => _stage = 'HIGH_SCHOOL'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ChoiceChip(
                        label: const Text('الإعدادية'),
                        selected: _stage == 'MIDDLE',
                        onSelected: (val) => setState(() => _stage = 'MIDDLE'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: ChoiceChip(
                        label: const Text('الجامعة'),
                        selected: _stage == 'UNIVERSITY',
                        onSelected: (val) => setState(() => _stage = 'UNIVERSITY'),
                      ),
                    ),
                  ],
                ),
                if (_stage == 'HIGH_SCHOOL') ...[
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: ChoiceChip(
                          label: const Text('علمي علوم 🧬'),
                          selected: _branch == 'SCIENCE',
                          onSelected: (val) => setState(() => _branch = 'SCIENCE'),
                        ),
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: ChoiceChip(
                          label: const Text('علمي رياضة 📐'),
                          selected: _branch == 'MATH',
                          onSelected: (val) => setState(() => _branch = 'MATH'),
                        ),
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: ChoiceChip(
                          label: const Text('أدبي 📖'),
                          selected: _branch == 'LITERARY',
                          onSelected: (val) => setState(() => _branch = 'LITERARY'),
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 16),

                // 5. الصف الدراسي والعمر
                Row(
                  children: [
                    Expanded(
                      flex: 3,
                      child: TextFormField(
                        controller: _gradeController,
                        decoration: InputDecoration(
                          labelText: 'الصف الدراسي',
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                          filled: true,
                          fillColor: Colors.white,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 2,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: Colors.grey.shade400),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.remove, size: 18),
                              onPressed: () => setState(() { if (_age > 10) _age--; }),
                            ),
                            Text('$_age سنة', style: const TextStyle(fontWeight: FontWeight.bold)),
                            IconButton(
                              icon: const Icon(Icons.add, size: 18),
                              onPressed: () => setState(() { if (_age < 50) _age++; }),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // 6. البريد الإلكتروني (اختياري)
                TextFormField(
                  controller: _gmailController,
                  keyboardType: TextInputType.emailAddress,
                  decoration: InputDecoration(
                    labelText: 'البريد الإلكتروني Gmail (اختياري)',
                    hintText: 'student@gmail.com',
                    prefixIcon: const Icon(Icons.email),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    filled: true,
                    fillColor: Colors.white,
                  ),
                ),
                const SizedBox(height: 24),

                // زر تأكيد التسجيل
                ElevatedButton.icon(
                  onPressed: _isLoading ? null : _submit,
                  icon: _isLoading
                      ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                      : const Icon(Icons.check_circle),
                  label: const Text('تأكيد التسجيل وتفعيل التنبيهات عبر Telegram ✈️', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0088CC),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedCornerShape(14),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
