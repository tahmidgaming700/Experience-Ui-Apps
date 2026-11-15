package com.experience.virtualos.core.runtime

import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.TarExtractor
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * On-disk layout of installed runtimes, all inside app-private storage:
 *
 *   <base>/current.json            pointer to the active version (atomic)
 *   <base>/versions/<version>/     extracted runtime (runtime-info.json, rootfs/)
 *   <base>/staging/                in-progress extraction, never used directly
 *   <base>/downloads/              partial and verified archives
 */
class RuntimeLayout(val base: File) {
    val versions = File(base, "versions")
    val staging = File(base, "staging")
    val downloads = File(base, "downloads")
    val pointer = File(base, "current.json")
    fun versionDir(version: String) = File(versions, version)
}

@Serializable
data class InstalledRuntime(
    val runtimeVersion: String,
    val releaseTag: String,
    val archiveSha256: String,
    val installedAtMillis: Long,
    val source: String,
    val manifest: RuntimeManifest,
)

sealed interface InstallStage {
    data object FetchingManifest : InstallStage
    data class Downloading(val bytes: Long, val total: Long) : InstallStage
    data object Verifying : InstallStage
    data class Extracting(val bytes: Long, val total: Long) : InstallStage
    data object Checking : InstallStage
    data object Activating : InstallStage
}

class RuntimeInstallException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Installs runtime archives atomically. A runtime only becomes active after it
 * has been downloaded, SHA-256 verified, fully extracted into a staging
 * directory, checked for every required path and smoke-tested. Until the final
 * pointer swap the previously active runtime keeps working; any failure leaves
 * it untouched and removes the partial installation.
 */
class RuntimeInstaller(
    private val layout: RuntimeLayout,
    private val downloader: Downloader,
    private val host: HostConstraints,
    private val freeSpace: (File) -> Long = { it.usableSpace },
    private val clock: () -> Long = System::currentTimeMillis,
    /** Extra check run on the staged runtime directory (e.g. PRoot smoke test). */
    private val smokeTest: (File, RuntimeManifest) -> Unit = { _, _ -> },
) {
    fun current(): InstalledRuntime? = try {
        if (!layout.pointer.isFile) null
        else RuntimeManifest.json.decodeFromString(InstalledRuntime.serializer(), layout.pointer.readText())
    } catch (_: Exception) {
        null
    }

    fun currentDirectory(): File? = current()?.let { layout.versionDir(it.runtimeVersion) }?.takeIf { it.isDirectory }

    fun fetchManifest(url: String): RuntimeManifest {
        val text = try {
            downloader.fetchText(url)
        } catch (e: IOException) {
            if (e.message?.contains("404") == true) {
                throw RuntimeInstallException("No runtime release has been published yet (manifest not found).", e)
            }
            throw RuntimeInstallException("Could not fetch the runtime manifest: ${e.message}", e)
        }
        return ManifestValidator.validate(RuntimeManifest.parse(text), host)
    }

    /** True when [manifest] describes something other than the active runtime. */
    fun isUpdate(manifest: RuntimeManifest): Boolean {
        val active = current() ?: return true
        return active.archiveSha256 != manifest.archive.sha256 || verify().isNotEmpty()
    }

    fun install(manifest: RuntimeManifest, onStage: (InstallStage) -> Unit, isCancelled: () -> Boolean = { false }): InstalledRuntime {
        ManifestValidator.validate(manifest, host)
        val archive = File(layout.downloads, "${manifest.archive.sha256}.tar.gz")
        if (!archive.isFile || FileUtils.sha256(archive) != manifest.archive.sha256) {
            val part = File(layout.downloads, "${manifest.archive.sha256}.part")
            requireSpace(manifest, includeDownload = true, partial = if (part.isFile) part.length() else 0)
            layout.downloads.mkdirs()
            onStage(InstallStage.Downloading(if (part.isFile) part.length() else 0, manifest.archive.sizeBytes))
            try {
                downloader.download(manifest.archive.url, part, { done, total ->
                    onStage(InstallStage.Downloading(done, if (total > 0) total else manifest.archive.sizeBytes))
                }, isCancelled)
            } catch (e: IOException) {
                throw RuntimeInstallException("Download failed: ${e.message}. Progress is kept; retry to resume.", e)
            }
            onStage(InstallStage.Verifying)
            verifyArchive(part, manifest)
            if (!part.renameTo(archive)) throw RuntimeInstallException("Could not finalise the downloaded archive.")
        } else {
            onStage(InstallStage.Verifying)
        }
        return installVerifiedArchive(archive, manifest, "github-release", onStage, isCancelled).also {
            archive.delete()
        }
    }

    /**
     * Installs an archive the user supplied (e.g. a CI artifact), checking it
     * against the accompanying manifest exactly like a downloaded one.
     */
    fun installFromFile(source: File, manifest: RuntimeManifest, onStage: (InstallStage) -> Unit): InstalledRuntime {
        ManifestValidator.validate(manifest, host.copy(requireGithubHost = false))
        onStage(InstallStage.Verifying)
        verifyArchive(source, manifest)
        return installVerifiedArchive(source, manifest, "local-file", onStage) { false }
    }

    private fun verifyArchive(file: File, manifest: RuntimeManifest) {
        if (file.length() != manifest.archive.sizeBytes) {
            file.delete()
            throw RuntimeInstallException("Archive size ${file.length()} does not match the manifest (${manifest.archive.sizeBytes}). The file was discarded.")
        }
        val actual = FileUtils.sha256(file)
        if (actual != manifest.archive.sha256) {
            file.delete()
            throw RuntimeInstallException("SHA-256 mismatch: expected ${manifest.archive.sha256}, got $actual. The corrupted download was discarded.")
        }
    }

    private fun installVerifiedArchive(
        archive: File,
        manifest: RuntimeManifest,
        source: String,
        onStage: (InstallStage) -> Unit,
        isCancelled: () -> Boolean,
    ): InstalledRuntime {
        requireSpace(manifest, includeDownload = false, partial = 0)
        layout.staging.mkdirs()
        val stage = File(layout.staging, "${manifest.runtimeVersion}-${clock()}")
        try {
            val total = manifest.archive.unpackedSizeBytes
            TarExtractor { _, bytes ->
                if (isCancelled()) throw RuntimeInstallException("Installation cancelled")
                onStage(InstallStage.Extracting(minOf(bytes, total), total))
            }.extractGzip(archive, stage)

            onStage(InstallStage.Checking)
            val missing = missingPaths(stage, manifest)
            if (missing.isNotEmpty()) throw RuntimeInstallException("Runtime archive is incomplete; missing: ${missing.joinToString()}")
            for (key in listOf(RuntimeManifest.ENTRY_QEMU, RuntimeManifest.ENTRY_QEMU_IMG)) {
                val f = File(stage, manifest.entrypoints.getValue(key))
                if (!f.canExecute() && !f.setExecutable(true, false)) throw RuntimeInstallException("Cannot mark $key executable.")
            }
            smokeTest(stage, manifest)

            onStage(InstallStage.Activating)
            layout.versions.mkdirs()
            val target = layout.versionDir(manifest.runtimeVersion)
            val previous = current()
            var displaced: File? = null
            if (target.exists()) {
                // Re-installing the same version (repair). Move the old copy
                // aside instead of deleting it, so a failed rename can roll back.
                displaced = File(layout.staging, "displaced-${clock()}")
                if (!target.renameTo(displaced)) throw RuntimeInstallException("Cannot replace the existing runtime directory.")
            }
            if (!stage.renameTo(target)) {
                displaced?.renameTo(target)
                throw RuntimeInstallException("Cannot move the verified runtime into place.")
            }
            val installed = InstalledRuntime(
                runtimeVersion = manifest.runtimeVersion,
                releaseTag = manifest.releaseTag,
                archiveSha256 = manifest.archive.sha256,
                installedAtMillis = clock(),
                source = source,
                manifest = manifest,
            )
            FileUtils.writeAtomically(layout.pointer, RuntimeManifest.json.encodeToString(InstalledRuntime.serializer(), installed))
            FileUtils.deleteRecursively(displaced)
            // Remove versions other than the new one. The previous one is no
            // longer referenced once the pointer has been swapped.
            layout.versions.listFiles()?.filter { it.name != manifest.runtimeVersion }?.forEach { FileUtils.deleteRecursively(it) }
            if (previous != null && previous.runtimeVersion != manifest.runtimeVersion) {
                FileUtils.deleteRecursively(layout.versionDir(previous.runtimeVersion))
            }
            return installed
        } catch (e: Exception) {
            FileUtils.deleteRecursively(stage)
            if (e is RuntimeInstallException) throw e
            throw RuntimeInstallException("Installation failed: ${e.message}", e)
        } finally {
            layout.staging.listFiles()?.filter { it.name.startsWith("displaced-") }?.forEach { FileUtils.deleteRecursively(it) }
        }
    }

    /** Re-checks the active runtime. Returns a list of problems (empty when healthy). */
    fun verify(): List<String> {
        val active = current() ?: return listOf("No runtime is installed.")
        val dir = layout.versionDir(active.runtimeVersion)
        if (!dir.isDirectory) return listOf("Runtime directory for ${active.runtimeVersion} is missing.")
        val problems = missingPaths(dir, active.manifest).map { "Missing $it" }.toMutableList()
        val qemu = File(dir, active.manifest.qemuEntrypoint)
        if (qemu.isFile && !qemu.canExecute()) problems += "QEMU is not executable"
        return problems
    }

    /** Removes the active runtime and all leftovers. Devices and images are kept. */
    fun uninstall() {
        layout.pointer.delete()
        FileUtils.deleteRecursively(layout.versions)
        FileUtils.deleteRecursively(layout.staging)
        FileUtils.deleteRecursively(layout.downloads)
    }

    /** Deletes abandoned staging directories left by a crash during extraction. */
    fun cleanupInterrupted() {
        FileUtils.deleteRecursively(layout.staging)
    }

    private fun missingPaths(dir: File, manifest: RuntimeManifest): List<String> =
        manifest.requiredPaths.filter { !Files.exists(File(dir, it).toPath(), LinkOption.NOFOLLOW_LINKS) }

    private fun requireSpace(manifest: RuntimeManifest, includeDownload: Boolean, partial: Long) {
        layout.base.mkdirs()
        val needed = manifest.archive.unpackedSizeBytes + (manifest.archive.unpackedSizeBytes / 10) +
            (if (includeDownload) manifest.archive.sizeBytes - partial else 0)
        val free = freeSpace(layout.base)
        if (free < needed) {
            throw RuntimeInstallException(
                "Not enough free storage: ${FileUtils.formatBytes(needed)} needed, ${FileUtils.formatBytes(free)} available.",
            )
        }
    }
}
