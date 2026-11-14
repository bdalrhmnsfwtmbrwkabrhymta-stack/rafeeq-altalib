package com.example.domain.ads

/**
 * ملف الإعدادات المركزي لإعلانات Google AdMob
 * Centralized Configuration for Google Mobile Ads
 *
 * يحتوي هذا الملف على معرفات الوحدات الإعلانية (Ad Unit IDs) المعتمدة رسمياً من جوجل للاختبار والتطوير،
 * بالإضافة إلى مفتاح التحكم العام [IS_ADS_ENABLED] لتفعيل أو تعطيل الإعلانات فوراً بضغطة واحدة.
 */
object AdMobConfig {

    /**
     * مفتاح التحكم المركزي في الإعلانات:
     * - عند ضبطه على false: تكون جميع الإعلانات معطلة تماماً ولن تظهر أو تطلب أي بيانات.
     * - عند ضبطه على true: يتم تفعيل الإعلانات وظهورها في الأماكن المحددة لها.
     */
    const val IS_ADS_ENABLED: Boolean = false

    /**
     * وضع الاختبار (Test Mode):
     * - true: استخدام معرفات اختبار جوجل الرسمية (آمن 100% ولا يسبب حظر الحساب).
     * - false: استخدام معرفات الإنتاج الحقيقية الخاصة بحسابك في AdMob.
     */
    const val IS_TEST_MODE: Boolean = true

    // =========================================================================================
    // 1. معرفات اختبار جوجل الرسمية (Google Official Sample Test Ad Unit IDs)
    // =========================================================================================
    private const val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917"

    // =========================================================================================
    // 2. معرفات إعلاناتك الحقيقية من لوحة تحكم AdMob (Production Ad Unit IDs)
    // ⚠️ ضع هنا معرفاتك الحقيقية عند جهوزية التطبيق للنشر على متجر Google Play
    // =========================================================================================
    private const val PROD_BANNER_AD_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"
    private const val PROD_INTERSTITIAL_AD_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"
    private const val PROD_APP_OPEN_AD_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"
    private const val PROD_REWARDED_AD_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY"

    // =========================================================================================
    // الدوال المساعدة لجلب المعرف النشط حسب وضع الاختبار أو الإنتاج
    // =========================================================================================
    val bannerAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_BANNER_AD_ID else PROD_BANNER_AD_ID

    val interstitialAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_INTERSTITIAL_AD_ID else PROD_INTERSTITIAL_AD_ID

    val appOpenAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_APP_OPEN_AD_ID else PROD_APP_OPEN_AD_ID

    val rewardedAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_REWARDED_AD_ID else PROD_REWARDED_AD_ID
}
