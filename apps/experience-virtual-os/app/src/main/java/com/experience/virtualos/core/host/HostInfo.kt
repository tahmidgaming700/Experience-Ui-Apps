package com.experience.virtualos.core.host

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

/**
 * Read-only facts about the host device. Nothing here requests root or
 * changes host settings, and /dev/kvm is only *probed*, never assumed.
 */
data class HostInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val cpuCores: Int,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val lowMemory: Boolean,
    val kvm: KvmStatus,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val socHint: String?,
) {
    val isArm64: Boolean get() = "arm64-v8a" in supportedAbis

    enum class KvmStatus(val label: String) {
        ABSENT("No /dev/kvm"),
        PRESENT_NO_ACCESS("/dev/kvm present, not accessible to apps"),
        ACCESSIBLE("/dev/kvm accessible"),
    }

    val accelerationLabel: String
        get() = when (kvm) {
            KvmStatus.ACCESSIBLE -> "KVM available"
            else -> "Software emulation (TCG)"
        }

    val accelerationDetail: String
        get() = when (kvm) {
            KvmStatus.ACCESSIBLE ->
                "/dev/kvm can be opened. QEMU will try KVM first and fall back to TCG if it is refused."
            KvmStatus.PRESENT_NO_ACCESS ->
                "/dev/kvm exists but SELinux or permissions block apps from it. Guests run with TCG software emulation, typically 10–50× slower than native."
            KvmStatus.ABSENT ->
                "This kernel exposes no KVM device to apps. Guests run with TCG software emulation, typically 10–50× slower than native."
        }

    companion object {
        fun probe(context: Context): HostInfo {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val stat = StatFs(context.filesDir.absolutePath)
            val kvm = File("/dev/kvm")
            val kvmStatus = when {
                !kvm.exists() -> KvmStatus.ABSENT
                kvm.canRead() && kvm.canWrite() -> KvmStatus.ACCESSIBLE
                else -> KvmStatus.PRESENT_NO_ACCESS
            }
            val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else Build.HARDWARE
            return HostInfo(
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                model = Build.MODEL,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                cpuCores = Runtime.getRuntime().availableProcessors(),
                totalMemoryBytes = mem.totalMem,
                availableMemoryBytes = mem.availMem,
                lowMemory = mem.lowMemory,
                kvm = kvmStatus,
                storageFreeBytes = stat.availableBytes,
                storageTotalBytes = stat.totalBytes,
                socHint = soc.ifBlank { null },
            )
        }
    }
}
