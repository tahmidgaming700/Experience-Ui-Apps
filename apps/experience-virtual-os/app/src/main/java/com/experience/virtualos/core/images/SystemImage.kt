package com.experience.virtualos.core.images

import kotlinx.serialization.Serializable

/**
 * How far an Android system image is known to get on this runtime
 * (Ubuntu QEMU, generic `virt` machine, no goldfish/ranchu devices).
 */
@Serializable
enum class SupportLevel(val label: String, val explanation: String) {
    EXPERIMENTAL_TESTED(
        "Experimental · console only",
        "Tested: the kernel, first-stage init and Android's native services start on QEMU's generic virt machine. " +
            "There is no display: the image's graphics stack (libEGL_emulation, hwcomposer.ranchu) needs the Android " +
            "Emulator's goldfish pipe, which upstream QEMU does not provide. Use the serial console to observe the boot.",
    ),
    EXPERIMENTAL_UNTESTED(
        "Experimental · untested",
        "Same dynamic-partition layout as the tested image, but not yet booted by the developers. Expect the same " +
            "console-only result at best.",
    ),
    UNSUPPORTED(
        "Not compatible",
        "This image depends on hardware or a disk layout that the runtime cannot provide (pre-Android 10 images expect " +
            "the emulator's goldfish devices; 16 KB-page images need a matching kernel). It is listed for reference only.",
    ),
}

/** An entry from Google's Android SDK system-image repository. */
@Serializable
data class CatalogImage(
    val id: String,
    val apiLevel: Int,
    val apiString: String,
    val tag: String,
    val tagDisplay: String,
    val revision: Int,
    val url: String,
    val sizeBytes: Long,
    val sha1: String,
    val obsolete: Boolean,
    val support: SupportLevel,
) {
    val title: String get() = "${androidName(apiLevel)} · API $apiString"
}

/** An image downloaded and extracted into app storage. */
@Serializable
data class InstalledImage(
    val id: String,
    val apiLevel: Int,
    val apiString: String,
    val tag: String,
    val revision: Int,
    val support: SupportLevel,
    val sha1: String,
    val sizeBytes: Long,
    val installedAtMillis: Long,
    val layout: ImageLayout,
) {
    val title: String get() = "${androidName(apiLevel)} · API $apiString · $tag"
}

/** The files a dynamic-partition (Android 10+) emulator image provides. */
@Serializable
data class ImageLayout(
    val kernel: String,
    val ramdisk: String,
    val system: String,
    val vendor: String,
    val metadataTemplate: String,
    /** androidboot.vbmeta.* parameters from VerifiedBootParams.textproto. */
    val bootParams: List<String>,
    val kernelCompressed: Boolean,
)

fun androidName(api: Int): String = when (api) {
    21, 22 -> "Android 5"
    23 -> "Android 6"
    24, 25 -> "Android 7"
    26, 27 -> "Android 8"
    28 -> "Android 9"
    29 -> "Android 10"
    30 -> "Android 11"
    31 -> "Android 12"
    32 -> "Android 12L"
    33 -> "Android 13"
    34 -> "Android 14"
    35 -> "Android 15"
    36 -> "Android 16"
    37 -> "Android 17"
    else -> "Android (API $api)"
}

object ImageSupport {
    /** Classification before download, from catalogue metadata only. */
    fun classify(apiLevel: Int, apiString: String, tag: String): SupportLevel = when {
        tag.contains("ps16k") -> SupportLevel.UNSUPPORTED
        apiLevel < 29 -> SupportLevel.UNSUPPORTED
        apiLevel == 30 && apiString == "30" && tag == "default" -> SupportLevel.EXPERIMENTAL_TESTED
        tag == "default" || tag == "google_apis" || tag == "google_apis_playstore" -> SupportLevel.EXPERIMENTAL_UNTESTED
        else -> SupportLevel.UNSUPPORTED
    }
}
