package com.experience.virtualos.core.vm

import kotlinx.serialization.Serializable

/** What a virtual device boots and how its hardware is assembled. */
@Serializable
enum class GuestKind(val label: String) {
    /** Generic ARM64 Linux (or other UEFI OS) on QEMU's virt machine. */
    LINUX_ARM64("Linux ARM64 (UEFI)"),

    /** Google AOSP emulator system image on QEMU's virt machine. Experimental, console only. */
    ANDROID_AOSP("Android system image (experimental)"),

    /**
     * The runtime's Ubuntu userspace run natively on the host CPU under the
     * gVisor native engine (no CPU emulation). Experimental.
     */
    LINUX_CONTAINER("Linux container (native engine, experimental)"),
}

@Serializable
enum class CpuModel(val qemuName: String, val label: String) {
    CORTEX_A57("cortex-a57", "Cortex-A57 (most compatible)"),
    CORTEX_A72("cortex-a72", "Cortex-A72"),
    MAX("max", "QEMU max (newest features; some Android kernels hang)"),
}

@Serializable
data class VirtualDevice(
    val id: String,
    val name: String,
    val kind: GuestKind,
    val memoryMb: Int = 1024,
    val cpuCores: Int = 2,
    val cpuModel: CpuModel = CpuModel.CORTEX_A57,
    /** Size of the writable disk (Linux system disk or Android /data). */
    val diskSizeGb: Int = 8,
    /** Android: id of an installed system image. */
    val systemImageId: String? = null,
    /** Linux: installer/live ISO imported into app storage (file name in the device dir). */
    val installMedia: String? = null,
    val bootFromMedia: Boolean = true,
    val networkEnabled: Boolean = true,
    val displayWidth: Int = 1280,
    val displayHeight: Int = 720,
    val notes: String = "",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastStartedAtMillis: Long? = null,
) {
    fun validate(): List<String> = buildList {
        if (name.isBlank()) add("Name is required.")
        if (name.length > 60) add("Name must be 60 characters or fewer.")
        if (memoryMb !in MIN_MEMORY_MB..MAX_MEMORY_MB) add("Memory must be between $MIN_MEMORY_MB and $MAX_MEMORY_MB MB.")
        if (cpuCores !in 1..MAX_CPUS) add("CPU cores must be between 1 and $MAX_CPUS.")
        if (diskSizeGb !in 1..MAX_DISK_GB) add("Disk size must be between 1 and $MAX_DISK_GB GB.")
        if (displayWidth !in 640..2560 || displayHeight !in 480..2560) add("Display must be between 640×480 and 2560×2560.")
        if (kind == GuestKind.ANDROID_AOSP && systemImageId.isNullOrBlank()) add("Choose an installed Android system image.")
        if (kind == GuestKind.ANDROID_AOSP && memoryMb < 2048) add("Android images need at least 2048 MB of guest memory.")
    }

    companion object {
        const val MIN_MEMORY_MB = 256
        const val MAX_MEMORY_MB = 8192
        const val MAX_CPUS = 8
        const val MAX_DISK_GB = 64
    }
}

/** Portable export format (configuration only, never disk contents). */
@Serializable
data class DeviceExport(
    val format: String = FORMAT,
    val version: Int = 1,
    val device: VirtualDevice,
) {
    companion object {
        const val FORMAT = "experience-virtual-os-device"
    }
}
