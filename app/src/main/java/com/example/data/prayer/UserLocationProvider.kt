package com.example.data.prayer

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class UserLocationData(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val countryName: String,
    val isGpsAccurate: Boolean = true
)

object UserLocationProvider {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Result<UserLocationData> = withContext(Dispatchers.IO) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext Result.failure(Exception("Location service unavailable"))

            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetworkEnabled) {
                return@withContext Result.failure(Exception("GPS and Network location providers are disabled"))
            }

            var bestLocation: Location? = null

            // Check last known location first for rapid response
            if (isGpsEnabled) {
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastGps != null) {
                    bestLocation = lastGps
                }
            }

            if (bestLocation == null && isNetworkEnabled) {
                val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (lastNet != null) {
                    bestLocation = lastNet
                }
            }

            // If no fresh last known location, request a single location update
            if (bestLocation == null || (System.currentTimeMillis() - bestLocation.time > 10 * 60 * 1000)) {
                val freshLocation = requestSingleLocationUpdate(locationManager, isGpsEnabled, isNetworkEnabled)
                if (freshLocation != null) {
                    bestLocation = freshLocation
                }
            }

            if (bestLocation == null) {
                return@withContext Result.failure(Exception("Could not obtain location. Please ensure GPS is active."))
            }

            val lat = bestLocation.latitude
            val lng = bestLocation.longitude

            val (city, country) = resolveCityAndCountry(context, lat, lng)

            Result.success(
                UserLocationData(
                    latitude = lat,
                    longitude = lng,
                    cityName = city,
                    countryName = country,
                    isGpsAccurate = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocationUpdate(
        locationManager: LocationManager,
        isGpsEnabled: Boolean,
        isNetworkEnabled: Boolean
    ): Location? = suspendCancellableCoroutine { cont ->
        val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (cont.isActive) {
                    cont.resume(location)
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            cont.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        } catch (e: Exception) {
            if (cont.isActive) {
                cont.resume(null)
            }
        }
    }

    private fun resolveCityAndCountry(context: Context, latitude: Double, longitude: Double): Pair<String, String> {
        try {
            val geocoder = Geocoder(context, Locale("ar"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "موقعي الحالي"
                    val country = addr.countryName ?: "مصر"
                    return Pair(city, country)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "موقعي الحالي"
                    val country = addr.countryName ?: "مصر"
                    return Pair(city, country)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: estimate closest Egyptian city or return coordinates
        var closestCity = "القاهرة"
        var minDistance = Double.MAX_VALUE
        for ((name, coords) in PrayerTimeCalculator.EGYPT_CITY_COORDINATES) {
            val dist = Math.hypot(coords.first - latitude, coords.second - longitude)
            if (dist < minDistance) {
                minDistance = dist
                closestCity = name
            }
        }

        return if (minDistance < 1.0) {
            Pair(closestCity, "مصر")
        } else {
            Pair("موقعي (${String.format(Locale.US, "%.2f", latitude)}, ${String.format(Locale.US, "%.2f", longitude)})", "موقع جغرافي")
        }
    }
}
