import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'models/user_profile.dart';
import 'screens/login_setup_screen.dart';
import 'screens/home_dashboard_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const RafeeqAlTalebApp());
}

class RafeeqAlTalebApp extends StatefulWidget {
  const RafeeqAlTalebApp({Key? key}) : super(key: key);

  @override
  State<RafeeqAlTalebApp> createState() => _RafeeqAlTalebAppState();
}

class _RafeeqAlTalebAppState extends State<RafeeqAlTalebApp> {
  // الملف التعريفي للطالب
  UserProfile _userProfile = UserProfile(
    fullName: '',
    telegramIdOrPhone: '',
    isRegistered: false,
  );

  void _onProfileRegistered(UserProfile updatedProfile) {
    setState(() {
      _userProfile = updatedProfile;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'رفيق الطالب',
      debugShowCheckedModeBanner: false,
      locale: const Locale('ar', 'EG'),
      supportedLocales: const [
        Locale('ar', 'EG'),
        Locale('en', 'US'),
      ],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: ThemeData(
        useMaterial3: true,
        fontFamily: 'Cairo',
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0088CC),
          primary: const Color(0xFF0088CC),
          secondary: const Color(0xFF2E7D32),
        ),
        scaffoldBackgroundColor: const Color(0xFFF8FAFC),
      ),
      home: _userProfile.isRegistered && _userProfile.fullName.isNotEmpty && _userProfile.telegramIdOrPhone.isNotEmpty
          ? HomeDashboardScreen(
              profile: _userProfile,
              onUpdateProfile: (p) => setState(() => _userProfile = p),
            )
          : LoginSetupScreen(
              userProfile: _userProfile,
              onCompleted: _onProfileRegistered,
            ),
    );
  }
}
