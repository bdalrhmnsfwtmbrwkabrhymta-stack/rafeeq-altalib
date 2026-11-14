package com.example.domain.prayer

object QiblaCalculator {
    // Kaaba Coordinates
    private const val KAABA_LATITUDE = 21.422487
    private const val KAABA_LONGITUDE = 39.826206

    /**
     * Calculates the Qibla angle (in degrees from True North) for given user latitude and longitude.
     */
    fun calculateQiblaDirection(latitude: Double, longitude: Double): Float {
        val userLatRad = Math.toRadians(latitude)
        val userLonRad = Math.toRadians(longitude)
        val kaabaLatRad = Math.toRadians(KAABA_LATITUDE)
        val kaabaLonRad = Math.toRadians(KAABA_LONGITUDE)

        val deltaLon = kaabaLonRad - userLonRad

        val y = Math.sin(deltaLon) * Math.cos(kaabaLatRad)
        val x = Math.cos(userLatRad) * Math.sin(kaabaLatRad) -
                Math.sin(userLatRad) * Math.cos(kaabaLatRad) * Math.cos(deltaLon)

        var qiblaBearing = Math.toDegrees(Math.atan2(y, x)).toFloat()
        if (qiblaBearing < 0) {
            qiblaBearing += 360f
        }
        return qiblaBearing % 360f
    }
}
