package com.example.data.device

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.data.model.DeviceCompatibility
import com.example.data.model.DeviceInfo
import com.example.data.model.ManifestRelease
import java.io.File

object DeviceInspector {

    private fun getSystemProperty(key: String, defaultValue: String = ""): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, defaultValue) as? String) ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    fun inspectDevice(context: Context): DeviceInfo {
        val path = context.filesDir
        val stat = try {
            StatFs(path.path)
        } catch (_: Exception) {
            null
        }

        val freeBytes = stat?.availableBytes ?: (500L * 1024 * 1024)
        val totalBytes = stat?.totalBytes ?: (4L * 1024 * 1024 * 1024)

        val trebleProp = getSystemProperty("ro.treble.enabled", "")
        val isTreble = when {
            trebleProp.equals("true", ignoreCase = true) -> true
            trebleProp.equals("false", ignoreCase = true) -> false
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        }

        val dynamicPartitionsProp = getSystemProperty("ro.boot.dynamic_partitions", "")
        val hasDynamicPartitions = dynamicPartitionsProp.equals("true", ignoreCase = true) ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val vndkVersion = getSystemProperty("ro.vndk.version", "Default/System")

        val abis = Build.SUPPORTED_ABIS.toList().ifEmpty {
            listOf(Build.CPU_ABI, Build.CPU_ABI2).filter { it.isNotBlank() }
        }

        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            "Unknown"
        }

        return DeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            deviceCode = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = securityPatch,
            supportedAbis = abis,
            primaryAbi = abis.firstOrNull() ?: "unknown",
            isTrebleEnabled = isTreble,
            hasDynamicPartitions = hasDynamicPartitions,
            vndkVersion = vndkVersion,
            freeInternalBytes = freeBytes,
            totalInternalBytes = totalBytes
        )
    }

    fun checkCompatibility(device: DeviceInfo, release: ManifestRelease): DeviceCompatibility {
        val issues = mutableListOf<String>()

        // 1. ABI Check
        val matchingAbis = device.supportedAbis.intersect(release.supportedAbis.toSet())
        val abiSupported = matchingAbis.isNotEmpty()
        if (!abiSupported) {
            issues.add(
                "Incompatible CPU Architecture: Device supports [${device.supportedAbis.joinToString()}], but release requires [${release.supportedAbis.joinToString()}]."
            )
        }

        // 2. Minimum Android SDK Check
        val sdkSupported = device.sdkInt >= release.minSdk
        if (!sdkSupported) {
            issues.add(
                "Unsupported Base Android Version: Device API level is ${device.sdkInt}, but this build requires at least API ${release.minSdk}."
            )
        }

        // 3. Treble Check
        val trebleSupported = !release.requiresTreble || device.isTrebleEnabled
        if (!trebleSupported) {
            issues.add(
                "Treble Unsupported: This Generic System Image requires Project Treble support (ro.treble.enabled=true), which is not detected on this hardware."
            )
        }

        val isCompatible = abiSupported && sdkSupported && trebleSupported
        return DeviceCompatibility(
            isCompatible = isCompatible,
            abiSupported = abiSupported,
            sdkSupported = sdkSupported,
            trebleSupported = trebleSupported,
            issues = issues
        )
    }

    fun getAvailableStorageBytes(targetDir: File): Long {
        return try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val stat = StatFs(targetDir.path)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }
}
