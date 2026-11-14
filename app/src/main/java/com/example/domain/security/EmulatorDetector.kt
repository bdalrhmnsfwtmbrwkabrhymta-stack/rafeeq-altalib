package com.example.domain.security

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 🛡️ Helper object that checks for common emulator build properties
 * (such as system properties like ro.kernel.qemu, build models, hardware, and brand fingerprints).
 */
object EmulatorDetector {

    private const val TAG = "EmulatorDetector"

    /**
     * Checks if the current runtime environment displays characteristics of an Android emulator.
     */
    fun isEmulator(): Boolean {
        return checkBuildProperties() || checkSystemProperties() || checkPipesOrDrivers()
    }

    /**
     * Inspects android.os.Build fields for known emulator indicators.
     */
    fun checkBuildProperties(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()

        val isGenericFingerprint = fingerprint.startsWith("generic") || fingerprint.startsWith("unknown")
        val isEmulatorModel = model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                model.contains("sdk_gphone")
        val isEmulatorManufacturer = manufacturer.contains("genymotion") || manufacturer.contains("nox")
        val isGenericBrand = brand.startsWith("generic") && device.startsWith("generic")
        val isEmulatorHardware = hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                hardware.contains("vbox86") ||
                hardware.contains("nox")
        val isEmulatorProduct = product.contains("sdk") ||
                product.contains("google_sdk") ||
                product.contains("sdk_x86") ||
                product.contains("vbox86p") ||
                product.contains("emulator") ||
                product.contains("simulator")
        val isEmulatorBoard = board.contains("vbox86")

        return isGenericFingerprint ||
                isEmulatorModel ||
                isEmulatorManufacturer ||
                isGenericBrand ||
                isEmulatorHardware ||
                isEmulatorProduct ||
                isEmulatorBoard
    }

    /**
     * Reads Android system properties (e.g. ro.kernel.qemu) through getprop.
     */
    fun checkSystemProperties(): Boolean {
        val qemuKernel = getSystemProperty("ro.kernel.qemu")
        if (qemuKernel == "1") return true

        val qemuHardware = getSystemProperty("ro.hardware")
        if (qemuHardware.contains("goldfish") || qemuHardware.contains("ranchu")) return true

        return false
    }

    /**
     * Checks for typical emulator driver pipes (/dev/socket/qemud, /dev/qemu_pipe).
     */
    fun checkPipesOrDrivers(): Boolean {
        val pipes = listOf("/dev/socket/qemud", "/dev/qemu_pipe")
        for (pipe in pipes) {
            try {
                if (java.io.File(pipe).exists()) {
                    return true
                }
            } catch (_: Exception) {
                // Ignore permission or file access errors
            }
        }
        return false
    }

    /**
     * Safely reads a system property using the getprop binary or reflection.
     */
    private fun getSystemProperty(propName: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", propName))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine() ?: ""
            }.trim().lowercase()
        } catch (_: Exception) {
            try {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java)
                (getMethod.invoke(null, propName) as? String)?.trim()?.lowercase() ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    }
}
