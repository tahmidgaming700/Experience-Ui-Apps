package com.experience.virtualos

import com.experience.virtualos.core.images.ImageLayout
import com.experience.virtualos.core.images.ImageRepository
import com.experience.virtualos.core.images.ImageSupport
import com.experience.virtualos.core.images.SupportLevel
import com.experience.virtualos.core.vm.CpuModel
import com.experience.virtualos.core.vm.DeviceStore
import com.experience.virtualos.core.vm.GuestKind
import com.experience.virtualos.core.vm.QemuCommandBuilder
import com.experience.virtualos.core.vm.VirtualDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DeviceAndQemuTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun template(kind: GuestKind = GuestKind.LINUX_ARM64) = VirtualDevice(
        id = "", name = "Alpine", kind = kind, memoryMb = if (kind == GuestKind.ANDROID_AOSP) 2048 else 1024,
        systemImageId = if (kind == GuestKind.ANDROID_AOSP) "android-30-default-arm64-v8a-r2" else null,
        createdAtMillis = 0, updatedAtMillis = 0,
    )

    @Test fun deviceStorePersistsAcrossInstances() {
        val dir = tmp.newFolder()
        var t = 1000L
        val store = DeviceStore(dir, clock = { t++ })
        val created = store.create(template())
        store.update(created.copy(memoryMb = 1536, notes = "edited"))
        val reloaded = DeviceStore(dir)
        val d = reloaded.get(created.id)!!
        assertEquals(1536, d.memoryMb)
        assertEquals("edited", d.notes)
        assertTrue(File(dir, created.id).isDirectory)
    }

    @Test fun duplicateDeleteExportImport() {
        val store = DeviceStore(tmp.newFolder())
        val a = store.create(template())
        File(store.directory(a.id), "disk.qcow2").writeText("disk")
        val copy = store.duplicate(a.id, includeDisks = true)
        assertEquals("Alpine copy", copy.name)
        assertEquals("disk", File(store.directory(copy.id), "disk.qcow2").readText())
        val configOnly = store.duplicate(a.id, includeDisks = false)
        assertFalse(File(store.directory(configOnly.id), "disk.qcow2").exists())

        val exported = store.export(a.id)
        val imported = store.import(exported)
        assertNotEquals(a.id, imported.id)
        assertEquals("Alpine 2", imported.name)
        assertEquals(a.memoryMb, imported.memoryMb)

        store.delete(a.id)
        assertEquals(null, store.get(a.id))
        assertFalse(File(tmp.root, a.id).exists())
        assertEquals(3, DeviceStore(store.directory(copy.id).parentFile!!).devices.value.size)
    }

    @Test fun importRejectsForeignFiles() {
        val store = DeviceStore(tmp.newFolder())
        for (bad in listOf("not json", """{"format":"other","version":1,"device":{}}""")) {
            try { store.import(bad); fail("should reject $bad") } catch (_: IOException) {}
        }
    }

    @Test fun validationRejectsBadConfigurations() {
        val store = DeviceStore(tmp.newFolder())
        val bad = listOf(
            template().copy(name = " "),
            template().copy(memoryMb = 64),
            template().copy(cpuCores = 32),
            template(GuestKind.ANDROID_AOSP).copy(systemImageId = null),
            template(GuestKind.ANDROID_AOSP).copy(memoryMb = 1024),
        )
        for (d in bad) {
            try { store.create(d); fail("should reject $d") } catch (_: IllegalArgumentException) {}
        }
        assertTrue(store.devices.value.isEmpty())
    }

    @Test fun corruptDatabaseIsPreservedNotOverwritten() {
        val dir = tmp.newFolder()
        File(dir, "devices.json").writeText("{broken")
        val store = DeviceStore(dir, clock = { 42 })
        assertTrue(store.devices.value.isEmpty())
        assertTrue(File(dir, "devices.corrupt-42.json").isFile)
    }

    @Test fun virtioTransportAddressesMatchAndroidFstab() {
        // The API 30 fstab expects metadata at a003c00 (2nd device) and the
        // emulator's super disk is the 6th device.
        assertEquals("a003e00", QemuCommandBuilder.transportAddress(0))
        assertEquals("a003c00", QemuCommandBuilder.transportAddress(1))
        assertEquals("a003400", QemuCommandBuilder.transportAddress(5))
    }

    private val paths = QemuCommandBuilder.Paths(
        qemu = "/usr/bin/qemu-system-aarch64", firmwareCode = "/usr/share/qemu/edk2-aarch64-code.fd",
        firmwareVars = "/data/evos/devices/x/efivars.fd", systemDisk = "/data/evos/devices/x/disk.qcow2",
        installMedia = "/data/evos/devices/x/media.iso", serialLog = "/data/evos/devices/x/serial.log",
        androidImageDir = "/data/evos/images/a30", androidMetadata = "/m.img", androidSdcard = "/s.img", androidCache = "/c.img",
    )
    private val ports = QemuCommandBuilder.Ports(17, 44417)

    @Test fun linuxCommandUsesUefiTcgAndLocalhostOnlyServices() {
        val argv = QemuCommandBuilder.build(template().copy(id = "x"), paths, ports, useKvm = false, tcgThreads = 4)
        val s = argv.joinToString(" ")
        assertEquals("/usr/bin/qemu-system-aarch64", argv.first())
        assertTrue(s.contains("-accel tcg,thread=multi,tb-size=128"))
        assertTrue(s.contains("-cpu cortex-a57"))
        assertTrue(s.contains("if=pflash,format=raw,unit=0,readonly=on,file=/usr/share/qemu/edk2-aarch64-code.fd"))
        assertTrue(s.contains("-vnc 127.0.0.1:17"))
        assertTrue(s.contains("tcp:127.0.0.1:44417"))
        assertTrue(s.contains("scsi-cd,bus=scsi0.0,drive=cd0,bootindex=1"))
        assertFalse(s.contains("0.0.0.0"))
    }

    @Test fun kvmSwitchesCpuAndAccelerator() {
        val s = QemuCommandBuilder.build(template().copy(id = "x", cpuModel = CpuModel.MAX), paths, ports, useKvm = true, tcgThreads = 1).joinToString(" ")
        assertTrue(s.contains("-accel kvm"))
        assertTrue(s.contains("-cpu host"))
    }

    @Test fun androidCommandReproducesVerifiedTopology() {
        val layout = ImageLayout("kernel-ranchu", "ramdisk.img", "system.img", "vendor.img", "encryptionkey.img",
            listOf("androidboot.vbmeta.size=6784"), true)
        val argv = QemuCommandBuilder.build(template(GuestKind.ANDROID_AOSP).copy(id = "abcdef12-0000"), paths, ports, false, 4, layout)
        val s = argv.joinToString(" ")
        val append = argv[argv.indexOf("-append") + 1]
        assertTrue(append.contains("androidboot.boot_devices=a003c00.virtio_mmio,a003400.virtio_mmio"))
        assertTrue(append.contains("androidboot.vbmeta.size=6784"))
        assertTrue(append.contains("androidboot.hardware=ranchu"))
        assertTrue(s.contains("virt,gic-version=3"))
        val drives = argv.withIndex().filter { it.value == "-drive" }.map { argv[it.index + 1] }
        assertEquals(listOf("sdcard", "metadata", "cache", "userdata", "vendor", "system"), drives.map { it.substringAfter("id=").substringBefore(",") })
        assertTrue(drives.last().contains("readonly=on"))
    }

    @Test fun parsesGoogleCatalogXml() {
        val xml = """<?xml version="1.0"?>
            <sys-img:sdk-sys-img xmlns:sys-img="http://schemas.android.com/sdk/android/repo/sys-img2/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <remotePackage path="system-images;android-30;default;arm64-v8a">
                <type-details xsi:type="sys-img:sysImgDetailsType"><api-level>30</api-level>
                  <tag><id>default</id><display>Default Android System Image</display></tag><abi>arm64-v8a</abi></type-details>
                <revision><major>2</major></revision>
                <archives><archive><complete><size>548363615</size><checksum type="sha1">E96298145A5E0BFD6DA4816F51B06C520D8DBA72</checksum><url>arm64-v8a-30_r02.zip</url></complete></archive></archives>
              </remotePackage>
              <remotePackage path="system-images;android-30;default;x86_64">
                <type-details><api-level>30</api-level><tag><id>default</id></tag></type-details>
                <revision><major>1</major></revision>
                <archives><archive><complete><size>1</size><checksum>aa</checksum><url>x.zip</url></complete></archive></archives>
              </remotePackage>
              <remotePackage path="system-images;android-26;google_apis;arm64-v8a">
                <type-details><api-level>26</api-level><tag><id>google_apis</id></tag></type-details>
                <revision><major>3</major></revision>
                <archives><archive><host-os>macosx</host-os><complete><size>9</size><checksum>bb</checksum><url>mac.zip</url></complete></archive>
                  <archive><host-os>linux</host-os><complete><size>10</size><checksum>cc</checksum><url>linux.zip</url></complete></archive></archives>
              </remotePackage>
            </sys-img:sdk-sys-img>"""
        val images = ImageRepository.parseCatalog(xml)
        assertEquals(2, images.size)
        val a30 = images.first { it.apiLevel == 30 }
        assertEquals("https://dl.google.com/android/repository/sys-img/android/arm64-v8a-30_r02.zip", a30.url)
        assertEquals("e96298145a5e0bfd6da4816f51b06c520d8dba72", a30.sha1)
        assertEquals(SupportLevel.EXPERIMENTAL_TESTED, a30.support)
        val a26 = images.first { it.apiLevel == 26 }
        assertTrue(a26.url.endsWith("google_apis/linux.zip"))
        assertEquals(SupportLevel.UNSUPPORTED, a26.support)
    }

    @Test fun supportClassification() {
        assertEquals(SupportLevel.EXPERIMENTAL_TESTED, ImageSupport.classify(30, "30", "default"))
        assertEquals(SupportLevel.EXPERIMENTAL_UNTESTED, ImageSupport.classify(34, "34", "google_apis"))
        assertEquals(SupportLevel.UNSUPPORTED, ImageSupport.classify(28, "28", "default"))
        assertEquals(SupportLevel.UNSUPPORTED, ImageSupport.classify(36, "36", "google_apis_ps16k"))
    }

    @Test fun detectsGptPartitionNames() {
        val img = tmp.newFile("system.img")
        RandomAccessFile(img, "rw").use { raf ->
            raf.setLength(64 * 512)
            raf.seek(512)
            val header = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN)
            header.put("EFI PART".toByteArray()); header.position(72); header.putLong(2); header.putInt(4); header.putInt(128)
            raf.write(header.array())
            fun entry(i: Int, name: String) {
                raf.seek(2 * 512L + i * 128)
                val e = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
                e.put(ByteArray(16) { 1 }); e.position(56); e.put(name.toByteArray(Charsets.UTF_16LE))
                raf.write(e.array())
            }
            entry(0, "vbmeta"); entry(1, "super")
        }
        assertTrue(ImageRepository.gptHasPartition(img, "super"))
        assertFalse(ImageRepository.gptHasPartition(img, "userdata"))
        assertFalse(ImageRepository.gptHasPartition(tmp.newFile("empty.img"), "super"))
    }
}
