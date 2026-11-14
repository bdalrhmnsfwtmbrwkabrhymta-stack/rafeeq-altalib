package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.ads.AdMobConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * 3. عنصر/ودجة مخصصة لإعلان البانر (Ad Banner Widget)
 * يتم إظهاره أو إخفاؤه بمرونة تامة عبر المتغير الشرطي المركزي AdMobConfig.IS_ADS_ENABLED
 * أو عبر متغير [isAdsEnabled] الممرر للودجة.
 */
@Composable
fun AdBannerWidget(
    modifier: Modifier = Modifier,
    isAdsEnabled: Boolean = AdMobConfig.IS_ADS_ENABLED,
    showPlaceholderInDev: Boolean = false
) {
    if (!isAdsEnabled) {
        if (showPlaceholderInDev) {
            // مساحة مخصصة توضيحية أثناء التطوير فقط إذا رغبت في معاينة مكان البانر
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "مساحة إعلان بانر AdMob (معطل حالياً)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    // عند تفعيل الإعلانات، يتم بناء وعرض الـ AdView المعتمد من Google AdMob
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdMobConfig.bannerAdUnitId
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
