package com.example.domain.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * مدير إعلانات Google AdMob
 * AdMobManager: مسؤول عن تهيئة الـ SDK وتحميل وعرض الإعلانات البينية والمكافأة
 */
object AdMobManager {
    private const val TAG = "AdMobManager"

    // حالة تهيئة الـ SDK
    private var isInitialized = false

    // علم لتحديد ما إذا تم عرض إعلان الفتح مسبقاً في هذه الجلسة لمنع الإزعاج
    private var hasShownLaunchAd = false

    // الإعلان البيني لفتحة التطبيق
    private var launchInterstitialAd: InterstitialAd? = null
    private var isLaunchAdLoading = false

    // إعلان المكافأة (Rewarded Ad) المحمل في الذاكرة
    private var cachedRewardedAd: RewardedAd? = null
    private var isRewardedAdLoading = false

    /**
     * 1. تهيئة الـ SDK في نقطة بدء التطبيق (App Startup / MainActivity)
     */
    fun initialize(context: Context, onInitialized: (() -> Unit)? = null) {
        if (!AdMobConfig.IS_ADS_ENABLED) {
            Log.d(TAG, "AdMob is currently disabled via AdMobConfig.IS_ADS_ENABLED")
            return
        }

        if (isInitialized) {
            onInitialized?.invoke()
            return
        }

        try {
            MobileAds.initialize(context) { initializationStatus ->
                try {
                    isInitialized = true
                    Log.d(TAG, "Google Mobile Ads SDK initialized successfully: $initializationStatus")
                    onInitialized?.invoke()

                    // التحميل المسبق لإعلان الفتح وإعلان المكافأة
                    preloadLaunchAd(context)
                    preloadRewardedAd(context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error inside MobileAds initialization callback", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing MobileAds SDK", t)
        }
    }

    /**
     * التحميل المسبق لإعلان الفتح (Launch Interstitial Ad)
     */
    fun preloadLaunchAd(context: Context) {
        if (!AdMobConfig.IS_ADS_ENABLED || launchInterstitialAd != null || isLaunchAdLoading) return

        isLaunchAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdMobConfig.interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    launchInterstitialAd = interstitialAd
                    isLaunchAdLoading = false
                    Log.d(TAG, "Launch Interstitial Ad loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    launchInterstitialAd = null
                    isLaunchAdLoading = false
                    Log.w(TAG, "Launch Interstitial Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * 2. عرض إعلان عند فتح التطبيق لأول مرة فقط (App Open / Launch Ad)
     * يضمن عدم تكرار العرض أثناء التنقل أو الخروج المؤقت لمنع الإزعاج والامتثال لسياسات جوجل.
     */
    fun showAdOnFirstLaunchOnly(activity: Activity, onAdDismissedOrSkipped: () -> Unit) {
        // إذا كانت الإعلانات معطلة أو تم عرض الإعلان مسبقاً في هذه الجلسة
        if (!AdMobConfig.IS_ADS_ENABLED || hasShownLaunchAd) {
            onAdDismissedOrSkipped()
            return
        }

        val ad = launchInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Launch Ad dismissed by user")
                    launchInterstitialAd = null
                    hasShownLaunchAd = true
                    onAdDismissedOrSkipped()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Launch Ad failed to show: ${adError.message}")
                    launchInterstitialAd = null
                    hasShownLaunchAd = true
                    onAdDismissedOrSkipped()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Launch Ad showed full screen content")
                    hasShownLaunchAd = true
                }
            }
            ad.show(activity)
        } else {
            // إذا لم يكن الإعلان محملاً بعد، نتابع فوراً دون تعطيل تجربة المستخدم
            onAdDismissedOrSkipped()
        }
    }

    /**
     * التحميل المسبق لإعلان بمكافأة (Preload Rewarded Ad)
     */
    fun preloadRewardedAd(context: Context) {
        if (!AdMobConfig.IS_ADS_ENABLED || cachedRewardedAd != null || isRewardedAdLoading) return

        isRewardedAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdMobConfig.rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    cachedRewardedAd = rewardedAd
                    isRewardedAdLoading = false
                    Log.d(TAG, "Rewarded Ad loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    cachedRewardedAd = null
                    isRewardedAdLoading = false
                    Log.w(TAG, "Rewarded Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * 4. منطق دمج الإعلانات بمكافأة (Rewarded Ads Logic)
     * يتم استدعاؤه عند زر معين (مثل فتح إجابات، تفريغ ملف، توليد أسئلة إضافية).
     * @param activity النشاط الحالي لعرض الإعلان
     * @param onRewardEarned يتم استدعاؤها حصراً عند إكمال المستخدم لمشاهدة الإعلان وحصوله على المكافأة
     * @param onAdClosedOrUnavailable يتم استدعاؤها في حال إغلاق الإعلان أو عدم توفره
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (rewardAmount: Int, rewardType: String) -> Unit,
        onAdClosedOrUnavailable: () -> Unit = {}
    ) {
        if (!AdMobConfig.IS_ADS_ENABLED) {
            // إذا كانت الإعلانات معطلة، يتم فتح الميزة مباشرة تشجيعاً للمستخدم أثناء التطوير
            onRewardEarned(1, "free_access")
            return
        }

        val ad = cachedRewardedAd
        if (ad != null) {
            var userEarnedReward = false
            var earnedAmount = 1
            var earnedType = "reward"

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded Ad dismissed")
                    cachedRewardedAd = null
                    preloadRewardedAd(activity.applicationContext)

                    if (userEarnedReward) {
                        onRewardEarned(earnedAmount, earnedType)
                    } else {
                        onAdClosedOrUnavailable()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Rewarded Ad failed to show: ${adError.message}")
                    cachedRewardedAd = null
                    preloadRewardedAd(activity.applicationContext)
                    onAdClosedOrUnavailable()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded Ad shown")
                }
            }

            ad.show(activity) { rewardItem ->
                userEarnedReward = true
                earnedAmount = rewardItem.amount
                earnedType = rewardItem.type
                Log.d(TAG, "User earned reward: $earnedAmount of $earnedType")
            }
        } else {
            Log.d(TAG, "Rewarded ad is not ready yet, loading for next time")
            preloadRewardedAd(activity.applicationContext)
            // إذا لم يكن الإعلان جاهزاً، يمكنك إما تنبيه المستخدم أو إتاحة الميزة له مجاناً
            onAdClosedOrUnavailable()
        }
    }
}
