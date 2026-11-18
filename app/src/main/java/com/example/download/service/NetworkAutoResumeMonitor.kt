package com.example.download.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.download.SmartDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkAutoResumeMonitor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Volatile
    private var registered = false
    private var pendingResumeJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleResumeAfterStabilization()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (hasInternet) {
                scheduleResumeAfterStabilization()
            }
        }

        override fun onLost(network: Network) {
            if (!isNetworkConnected()) {
                pendingResumeJob?.cancel()
                SmartDownloadManager.getInstance(context).onNetworkLost()
            }
        }
    }

    fun register() {
        val cm = connectivityManager ?: return
        if (registered) return
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            registered = true
        }
    }

    fun unregister() {
        val cm = connectivityManager ?: return
        if (!registered) return
        pendingResumeJob?.cancel()
        runCatching {
            cm.unregisterNetworkCallback(callback)
            registered = false
        }
    }

    fun isNetworkConnected(): Boolean {
        val cm = connectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifiConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun scheduleResumeAfterStabilization() {
        pendingResumeJob?.cancel()
        pendingResumeJob = scope.launch {
            delay(2000L)
            if (isNetworkConnected()) {
                SmartDownloadManager.getInstance(context)
                    .onNetworkRestored(isWifi = isWifiConnected())
            }
        }
    }
}
