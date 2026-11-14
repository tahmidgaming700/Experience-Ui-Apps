package com.experience.virtualos.core.images

import com.experience.virtualos.core.FileUtils
import com.experience.virtualos.core.TarExtractor
import com.experience.virtualos.core.runtime.Downloader
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lists ARM64 system images from Google's public SDK repository and installs
 * them into `<base>/<id>/`. Nothing here substitutes x86 or generic images:
 * only `arm64-v8a` packages are considered.
 */
class ImageRepository(
    private val base: File,
    private val downloader: Downloader,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun fetchCatalog(): List<CatalogImage> {
        val all = ArrayList<CatalogImage>()
        val errors = ArrayList<String>()
        for (tag in CATALOG_TAGS) {
            try {
                all += parseCatalog(downloader.fetchText("$REPO/sys-img/$tag/sys-img2-3.xml", 8 shl 20))
            } catch (e: Exception) {
                errors += "$tag: ${e.message}"
            }
        }
        if (all.isEmpty() && errors.isNotEmpty()) throw IOException("Could not load Google's image catalogue (${errors.joinToString("; ")})")
        return all.sortedWith(compareByDescending<CatalogImage> { it.support == SupportLevel.EXPERIMENTAL_TESTED }
            .thenByDescending { it.apiLevel }.thenBy { it.tag })
    }

    fun installed(): List<InstalledImage> =
        base.listFiles()?.mapNotNull { dir ->
            val meta = File(dir, META)
            if (!meta.isFile) null else try { json.decodeFromString(InstalledImage.serializer(), meta.readText()) } catch (_: Exception) { null }
        }?.sortedByDescending { it.apiLevel } ?: emptyList()

    fun installedImage(id: String): InstalledImage? = installed().firstOrNull { it.id == id }

    fun directory(id: String): File {
        require(id.matches(Regex("^[A-Za-z0-9._-]{1,120}$"))) { "invalid image id" }
        return File(base, id)
    }

    fun delete(id: String) = FileUtils.deleteRecursively(directory(id))

    fun install(
        image: CatalogImage,
        onProgress: (stage: String, done: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): InstalledImage {
        if (image.support == SupportLevel.UNSUPPORTED) throw IOException("${image.title} is not compatible with this runtime and will not be installed.")
        val needed = image.sizeBytes * 3
        base.mkdirs()
        if (base.usableSpace < needed) {
            throw IOException("Not enough free storage: about ${FileUtils.formatBytes(needed)} needed, ${FileUtils.formatBytes(base.usableSpace)} available.")
        }
        val downloads = File(base, ".downloads").apply { mkdirs() }
        val zip = File(downloads, "${image.id}.zip.part")
        onProgress("Downloading", if (zip.isFile) zip.length() else 0, image.sizeBytes)
        downloader.download(image.url, zip, { d, t -> onProgress("Downloading", d, if (t > 0) t else image.sizeBytes) }, isCancelled)
        onProgress("Verifying checksum", 0, 0)
        if (zip.length() != image.sizeBytes) {
            zip.delete()
            throw IOException("Downloaded size ${zip.length()} does not match Google's catalogue (${image.sizeBytes}). The file was discarded.")
        }
        val sha1 = FileUtils.sha1(zip)
        if (!sha1.equals(image.sha1, ignoreCase = true)) {
            zip.delete()
            throw IOException("SHA-1 mismatch (expected ${image.sha1}, got $sha1). The corrupted download was discarded.")
        }
        val staging = File(base, ".staging-${image.id}")
        FileUtils.deleteRecursively(staging)
        try {
            onProgress("Extracting", 0, image.sizeBytes)
            unzip(zip, staging) { done -> if (isCancelled()) throw IOException("Cancelled"); onProgress("Extracting", done, image.sizeBytes * 3) }
            val root = staging.listFiles()?.singleOrNull { it.isDirectory } ?: staging
            val layout = detectLayout(root)
            val target = directory(image.id)
            FileUtils.deleteRecursively(target)
            if (!root.renameTo(target)) throw IOException("Could not move the image into place.")
            val installed = InstalledImage(
                id = image.id, apiLevel = image.apiLevel, apiString = image.apiString, tag = image.tag,
                revision = image.revision, support = image.support, sha1 = sha1,
                sizeBytes = FileUtils.sizeOf(target), installedAtMillis = clock(), layout = layout,
            )
            FileUtils.writeAtomically(File(target, META), json.encodeToString(InstalledImage.serializer(), installed))
            return installed
        } finally {
            FileUtils.deleteRecursively(staging)
            zip.delete()
        }
    }

    companion object {
        const val REPO = "https://dl.google.com/android/repository"
        val CATALOG_TAGS = listOf("android", "google_apis", "google_apis_playstore")
        private const val META = "image.json"

        fun parseCatalog(xml: String): List<CatalogImage> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                // Harden the parser: the catalogue never needs DTDs or entities.
                try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            val out = ArrayList<CatalogImage>()
            val packages = doc.getElementsByTagName("remotePackage")
            for (i in 0 until packages.length) {
                val pkg = packages.item(i) as Element
                val path = pkg.getAttribute("path")
                val parts = path.split(';')
                if (parts.size != 4 || parts[0] != "system-images" || parts[3] != "arm64-v8a") continue
                val details = child(pkg, "type-details") ?: continue
                val apiLevel = text(details, "api-level")?.toIntOrNull() ?: continue
                val tagEl = child(details, "tag")
                val tag = tagEl?.let { text(it, "id") } ?: parts[2]
                val tagDisplay = tagEl?.let { text(it, "display") } ?: tag
                val revision = child(pkg, "revision")?.let { text(it, "major") }?.toIntOrNull() ?: 0
                val apiString = parts[1].removePrefix("android-")
                val archives = child(pkg, "archives") ?: continue
                val archiveNodes = archives.getElementsByTagName("archive")
                var chosen: Element? = null
                for (j in 0 until archiveNodes.length) {
                    val a = archiveNodes.item(j) as Element
                    val hostOs = text(a, "host-os")
                    if (hostOs == null || hostOs == "linux") { chosen = a; break }
                }
                val complete = chosen?.let { child(it, "complete") } ?: continue
                val url = text(complete, "url") ?: continue
                val size = text(complete, "size")?.toLongOrNull() ?: continue
                val sha1 = text(complete, "checksum") ?: continue
                val obsolete = pkg.getAttribute("obsolete") == "true"
                val id = "android-$apiString-$tag-arm64-v8a-r$revision".replace(Regex("[^A-Za-z0-9._-]"), "_")
                out += CatalogImage(
                    id = id, apiLevel = apiLevel, apiString = apiString, tag = tag, tagDisplay = tagDisplay,
                    revision = revision,
                    url = if (url.startsWith("https://")) url else "$REPO/sys-img/${catalogDir(tag)}/$url",
                    sizeBytes = size, sha1 = sha1.lowercase(), obsolete = obsolete,
                    support = ImageSupport.classify(apiLevel, apiString, tag),
                )
            }
            return out
        }

        private fun catalogDir(tag: String) = when {
            tag == "default" -> "android"
            tag.startsWith("google_apis_playstore") -> "google_apis_playstore"
            tag.startsWith("google_apis") -> "google_apis"
            else -> tag
        }

        private fun child(parent: Element, name: String): Element? {
            val nodes = parent.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n is Element && (n.tagName == name || n.tagName.endsWith(":$name"))) return n
            }
            return null
        }

        private fun text(parent: Element, name: String): String? = child(parent, name)?.textContent?.trim()

        /** Confirms the extracted image has the dynamic-partition layout the runtime knows how to boot. */
        fun detectLayout(dir: File): ImageLayout {
            fun need(name: String): String {
                if (!File(dir, name).isFile) throw IOException("Image is missing $name; its layout is not supported by this runtime.")
                return name
            }
            val kernel = need("kernel-ranchu")
            val ramdisk = need("ramdisk.img")
            val system = need("system.img")
            val vendor = need("vendor.img")
            val metadata = need("encryptionkey.img")
            if (!gptHasPartition(File(dir, system), "super")) {
                throw IOException("system.img has no 'super' partition; only Android 10+ dynamic-partition images are supported.")
            }
            if (!gptHasPartition(File(dir, metadata), "metadata")) {
                throw IOException("encryptionkey.img has no 'metadata' partition.")
            }
            val params = File(dir, "VerifiedBootParams.textproto").takeIf { it.isFile }?.readLines()
                ?.mapNotNull { Regex("^param:\\s*\"(androidboot\\.vbmeta\\.[a-z_]+=[0-9A-Za-z]+)\"").find(it.trim())?.groupValues?.get(1) }
                ?: emptyList()
            val compressed = FileInputStream(File(dir, kernel)).use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte() }
            return ImageLayout(kernel, ramdisk, system, vendor, metadata, params, compressed)
        }

        fun gptHasPartition(image: File, name: String): Boolean {
            java.io.RandomAccessFile(image, "r").use { raf ->
                if (raf.length() < 34 * 512) return false
                val header = ByteArray(92)
                raf.seek(512); raf.readFully(header)
                if (String(header, 0, 8, Charsets.US_ASCII) != "EFI PART") return false
                val bb = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val entriesLba = bb.getLong(72)
                val count = bb.getInt(80)
                val size = bb.getInt(84)
                if (count !in 1..256 || size !in 128..1024) return false
                val entry = ByteArray(size)
                for (i in 0 until count) {
                    raf.seek(entriesLba * 512 + i.toLong() * size)
                    raf.readFully(entry)
                    val partName = String(entry, 56, 72, Charsets.UTF_16LE).trimEnd('\u0000')
                    if (partName == name) return true
                }
            }
            return false
        }

        /** Zip extraction with path-traversal protection. */
        fun unzip(zip: File, destination: File, onProgress: (Long) -> Unit) {
            destination.mkdirs()
            val root = destination.canonicalFile
            var total = 0L
            ZipInputStream(FileInputStream(zip).buffered(1 shl 16)).use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val entry = input.nextEntry ?: break
                    val rel = TarExtractor.safeRelative(entry.name) ?: continue
                    val out = File(root, rel)
                    if (!out.canonicalPath.startsWith(root.path + File.separator)) throw IOException("Unsafe zip entry ${entry.name}")
                    if (entry.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { o ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            o.write(buffer, 0, n)
                            total += n
                            if (total and 0xFFFFFF == 0L || n < buffer.size) onProgress(total)
                        }
                    }
                }
            }
        }
    }
}
