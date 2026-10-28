package com.example.data.flasher

import android.content.Context
import com.example.data.model.DDFlasherStatus
import com.example.data.model.PartitionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object DDFlasher {

    private val PARTITION_CANDIDATES = listOf(
        "/dev/block/bootdevice/by-name/system",
        "/dev/block/by-name/system",
        "/dev/block/mapper/system",
        "/dev/block/bootdevice/by-name/system_a",
        "/dev/block/by-name/system_a",
        "/dev/block/bootdevice/by-name/system_b",
        "/dev/block/by-name/system_b"
    )

    fun checkRootAccess(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/system/sd/xbin/su",
            "/data/local/bin/su"
        )
        for (p in paths) {
            if (File(p).exists()) return true
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val completed = process.waitFor(1500, TimeUnit.MILLISECONDS)
            completed && process.exitValue() == 0 && (line?.contains("uid=0") == true)
        } catch (_: Exception) {
            false
        }
    }

    fun detectTargetPartition(): String {
        for (candidate in PARTITION_CANDIDATES) {
            val file = File(candidate)
            if (file.exists()) {
                return candidate
            }
        }
        return "/dev/block/by-name/system"
    }

    fun scanAvailablePartitions(): List<PartitionInfo> {
        val partitions = mutableListOf<PartitionInfo>()
        val seenPaths = mutableSetOf<String>()

        val directoriesToScan = listOf(
            "/dev/block/by-name",
            "/dev/block/bootdevice/by-name",
            "/dev/block/mapper"
        )

        for (dirPath in directoriesToScan) {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (!seenPaths.contains(file.absolutePath)) {
                            seenPaths.add(file.absolutePath)
                            val name = file.name
                            val size = try {
                                if (file.canRead()) file.length() else null
                            } catch (_: Exception) {
                                null
                            }
                            partitions.add(
                                PartitionInfo(
                                    name = name,
                                    path = file.absolutePath,
                                    sizeBytes = size,
                                    isSystemPartition = name.contains("system", ignoreCase = true),
                                    isWritable = file.canWrite()
                                )
                            )
                        }
                    }
                }
            }
        }

        // If directory listing is blocked by Android SELinux, provide standard candidates
        if (partitions.isEmpty()) {
            for (candidate in PARTITION_CANDIDATES) {
                val file = File(candidate)
                val exists = file.exists()
                val name = file.name
                partitions.add(
                    PartitionInfo(
                        name = name,
                        path = candidate,
                        sizeBytes = if (exists && file.canRead()) file.length() else null,
                        isSystemPartition = true,
                        isWritable = exists && file.canWrite()
                    )
                )
            }
        }

        return partitions.sortedByDescending { it.isSystemPartition }
    }

    fun inspectEnvironment(context: Context, imageFile: File?): DDFlasherStatus.PrivilegeReport {
        val hasRoot = checkRootAccess()
        val partition = detectTargetPartition()
        val partitionFile = File(partition)
        val partitionSize = if (partitionFile.exists() && partitionFile.canRead()) partitionFile.length() else null

        val selinuxEnforcing = try {
            val process = Runtime.getRuntime().exec("getenforce")
            val output = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            output?.trim()?.equals("Enforcing", ignoreCase = true) == true
        } catch (_: Exception) {
            true
        }

        val imageName = imageFile?.name ?: "system.img"
        val imagePath = imageFile?.absolutePath ?: "/sdcard/$imageName"

        val warning = if (!hasRoot) {
            "Privilege Restriction: Android's unprivileged application sandbox prohibits writing to block devices. DD Flasher requires an elevated root daemon (/system/xbin/su or Magisk) to write directly to $partition. Without root, use Fastboot or DSU."
        } else {
            "Caution: Root access detected. Writing raw disk images via DD directly modifies the physical flash storage. Ensure your battery is above 50% and your device has an unlocked bootloader."
        }

        val exactDdCommand = "su -c dd if='$imagePath' of='$partition' bs=4M status=progress"
        val fastbootCommand = "fastboot flash system $imageName"

        return DDFlasherStatus.PrivilegeReport(
            hasRoot = hasRoot,
            targetPartition = partition,
            partitionSize = partitionSize,
            selinuxEnforcing = selinuxEnforcing,
            warning = warning,
            exactDdCommand = exactDdCommand,
            fastbootCommand = fastbootCommand
        )
    }

    suspend fun executeDdFlash(
        imageFile: File,
        targetPartition: String,
        blockSize: String = "4M",
        onLog: (line: String) -> Unit = {},
        onProgress: (percent: Int, bytesWritten: Long, speed: String) -> Unit = { _, _, _ -> }
    ): DDFlasherStatus = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()

        fun log(msg: String) {
            logs.add(msg)
            onLog(msg)
        }

        log("[INIT] Preparing DD Flasher operation...")
        log("[SOURCE] ${imageFile.name} (${"%.1f".format(imageFile.length() / (1024.0 * 1024.0))} MB)")
        log("[TARGET] $targetPartition")
        log("[BLOCK SIZE] $blockSize")

        if (!imageFile.exists() || imageFile.length() == 0L) {
            val err = "Image file missing or empty"
            log("[ERROR] $err at ${imageFile.absolutePath}")
            return@withContext DDFlasherStatus.Failed(
                error = err,
                reason = "Target GSI image was not found at ${imageFile.absolutePath}.",
                manualDdCommand = "su -c dd if='system.img' of='$targetPartition' bs=$blockSize",
                executionLogs = logs
            )
        }

        val hasRoot = checkRootAccess()
        if (!hasRoot) {
            val err = "Root access denied"
            log("[FAIL] Privilege check failed: UID is not root, no 'su' binary accessible.")
            return@withContext DDFlasherStatus.Failed(
                error = "Insufficient Permissions (Root Required)",
                reason = "DD Flasher cannot access raw block partition '$targetPartition' without root privileges (su). Ordinary APKs cannot perform privileged flashing.",
                manualDdCommand = "su -c dd if='${imageFile.absolutePath}' of='$targetPartition' bs=$blockSize",
                executionLogs = logs
            )
        }

        try {
            val command = "dd if='${imageFile.absolutePath}' of='$targetPartition' bs=$blockSize status=progress"
            log("[EXEC] su -c \"$command\"")

            val cmd = arrayOf("su", "-c", command)
            val process = Runtime.getRuntime().exec(cmd)

            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorOutput = StringBuilder()

            var line: String?
            while (errorReader.readLine().also { line = it } != null) {
                line?.let { l ->
                    errorOutput.append(l).append("\n")
                    log("[DD] $l")
                    if (l.contains("bytes", ignoreCase = true)) {
                        onProgress(75, imageFile.length(), l)
                    }
                }
            }

            while (outReader.readLine().also { line = it } != null) {
                line?.let { l ->
                    log("[DD-OUT] $l")
                }
            }

            val exitCode = process.waitFor()
            log("[EXIT CODE] $exitCode")

            if (exitCode == 0) {
                onProgress(100, imageFile.length(), "Completed")
                log("[SUCCESS] Image block flashing confirmed by DD.")
                return@withContext DDFlasherStatus.Success(
                    message = "Image successfully written to $targetPartition via DD Flasher.",
                    targetPartition = targetPartition,
                    imagePath = imageFile.absolutePath,
                    executionLogs = logs
                )
            } else {
                val reason = errorOutput.toString().ifBlank { "DD process terminated with exit code $exitCode." }
                log("[FAILED] $reason")
                return@withContext DDFlasherStatus.Failed(
                    error = "DD Flasher exited with code $exitCode",
                    reason = reason,
                    manualDdCommand = "su -c dd if='${imageFile.absolutePath}' of='$targetPartition' bs=$blockSize",
                    executionLogs = logs
                )
            }
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Process execution failed"
            log("[EXCEPTION] $err")
            return@withContext DDFlasherStatus.Failed(
                error = "DD Execution Error",
                reason = err,
                manualDdCommand = "su -c dd if='${imageFile.absolutePath}' of='$targetPartition' bs=$blockSize",
                executionLogs = logs
            )
        }
    }
}
