package com.experience.virtualos.core.vm

import com.experience.virtualos.core.images.ImageLayout

/**
 * Pure construction of QEMU argument vectors. Every path given here is a
 * *guest* path (as seen inside the Alpine root under PRoot).
 *
 * The Android profile reproduces the disk topology the AOSP emulator images
 * expect, verified by booting the API 30 image on upstream QEMU:
 *
 *   transport  probe name  contents
 *   a003e00    vdf         sdcard placeholder (vold-managed)
 *   a003c00    vde         metadata (GPT, partition "metadata")
 *   a003a00    vdd         cache placeholder
 *   a003800    vdc         userdata (ext4, mounted at /data)
 *   a003600    vdb         vendor
 *   a003400    vda         system (GPT: vbmeta, super)
 *
 * QEMU's virt machine attaches virtio-mmio devices from the highest transport
 * downwards, while the kernel probes them in ascending address order, hence
 * the reversed names.
 */
object QemuCommandBuilder {

    data class Paths(
        val qemu: String,
        val firmwareCode: String,
        val firmwareVars: String?,
        val systemDisk: String,
        val installMedia: String?,
        val serialLog: String,
        // Android only
        val androidImageDir: String? = null,
        val androidMetadata: String? = null,
        val androidSdcard: String? = null,
        val androidCache: String? = null,
    )

    data class Ports(val vncDisplay: Int, val qmpPort: Int)

    fun build(
        device: VirtualDevice,
        paths: Paths,
        ports: Ports,
        useKvm: Boolean,
        tcgThreads: Int,
        androidLayout: ImageLayout? = null,
    ): List<String> = when (device.kind) {
        GuestKind.LINUX_ARM64 -> linux(device, paths, ports, useKvm, tcgThreads)
        GuestKind.ANDROID_AOSP -> android(device, paths, ports, useKvm, tcgThreads, requireNotNull(androidLayout) { "Android image layout required" })
    }

    private fun common(device: VirtualDevice, ports: Ports, useKvm: Boolean, tcgThreads: Int, gic3: Boolean): MutableList<String> {
        val cpu = if (useKvm) "host" else device.cpuModel.qemuName
        val accel = if (useKvm) "kvm" else "tcg,thread=${if (tcgThreads > 1) "multi" else "single"},tb-size=$TB_CACHE_MB"
        return mutableListOf(
            "-name", sanitize(device.name),
            "-machine", if (gic3) "virt,gic-version=3" else "virt",
            "-cpu", cpu,
            "-smp", device.cpuCores.toString(),
            "-m", device.memoryMb.toString(),
            "-accel", accel,
            "-nodefaults",
            "-no-user-config",
            "-vnc", "127.0.0.1:${ports.vncDisplay}",
            "-qmp", "tcp:127.0.0.1:${ports.qmpPort},server=on,wait=off",
            "-monitor", "none",
        )
    }

    private fun linux(device: VirtualDevice, p: Paths, ports: Ports, useKvm: Boolean, tcgThreads: Int): List<String> {
        val args = mutableListOf(p.qemu)
        args += common(device, ports, useKvm, tcgThreads, gic3 = false)
        args += listOf(
            "-drive", "if=pflash,format=raw,unit=0,readonly=on,file=${p.firmwareCode}",
        )
        p.firmwareVars?.let { args += listOf("-drive", "if=pflash,format=raw,unit=1,file=$it") }
        args += listOf(
            "-device", "virtio-gpu-pci,xres=${device.displayWidth},yres=${device.displayHeight}",
            "-device", "qemu-xhci,id=xhci",
            "-device", "usb-kbd,bus=xhci.0",
            "-device", "usb-tablet,bus=xhci.0",
            "-device", "virtio-rng-pci",
            "-drive", "file=${p.systemDisk},if=none,id=disk0,format=qcow2,discard=unmap",
            "-device", "virtio-blk-pci,drive=disk0,bootindex=${if (p.installMedia != null && device.bootFromMedia) 2 else 1}",
        )
        if (p.installMedia != null) {
            args += listOf(
                "-device", "virtio-scsi-pci,id=scsi0",
                "-drive", "file=${p.installMedia},if=none,id=cd0,media=cdrom,readonly=on",
                "-device", "scsi-cd,bus=scsi0.0,drive=cd0,bootindex=${if (device.bootFromMedia) 1 else 2}",
            )
        }
        args += network(device)
        args += listOf("-serial", "file:${p.serialLog}")
        return args
    }

    private fun android(device: VirtualDevice, p: Paths, ports: Ports, useKvm: Boolean, tcgThreads: Int, layout: ImageLayout): List<String> {
        val dir = requireNotNull(p.androidImageDir).trimEnd('/')
        val disks = listOf(
            Triple("sdcard", requireNotNull(p.androidSdcard), false),
            Triple("metadata", requireNotNull(p.androidMetadata), false),
            Triple("cache", requireNotNull(p.androidCache), false),
            Triple("userdata", p.systemDisk, false),
            Triple("vendor", "$dir/${layout.vendor}", true),
            Triple("system", "$dir/${layout.system}", true),
        )
        val metadataTransport = transportAddress(disks.indexOfFirst { it.first == "metadata" })
        val systemTransport = transportAddress(disks.indexOfFirst { it.first == "system" })
        val cmdline = buildList {
            add("console=ttyAMA0,38400")
            add("keep_bootcon")
            add("earlycon=pl011,0x09000000")
            add("androidboot.hardware=ranchu")
            add("qemu=1")
            add("androidboot.qemu=1")
            add("androidboot.boot_devices=$metadataTransport.virtio_mmio,$systemTransport.virtio_mmio")
            add("androidboot.verifiedbootstate=orange")
            add("androidboot.serialno=EVOS${device.id.take(8).uppercase()}")
            addAll(layout.bootParams)
            add("printk.devkmsg=on")
        }.joinToString(" ")

        val args = mutableListOf(p.qemu)
        args += common(device, ports, useKvm, tcgThreads, gic3 = true)
        args += listOf(
            "-kernel", "$dir/${layout.kernel}",
            "-initrd", "$dir/${layout.ramdisk}",
            "-append", cmdline,
        )
        for ((id, path, readOnly) in disks) {
            args += listOf("-drive", "file=$path,if=none,format=raw,id=$id${if (readOnly) ",readonly=on" else ""}")
            args += listOf("-device", "virtio-blk-device,drive=$id")
        }
        args += listOf(
            "-device", "virtio-gpu-pci,xres=${device.displayWidth},yres=${device.displayHeight}",
            "-device", "virtio-keyboard-pci",
            "-device", "virtio-tablet-pci",
        )
        args += network(device)
        args += listOf("-serial", "file:${p.serialLog}")
        return args
    }

    private fun network(device: VirtualDevice): List<String> =
        if (device.networkEnabled) listOf("-netdev", "user,id=net0", "-device", "virtio-net-pci,netdev=net0")
        else listOf("-nic", "none")

    /** virtio-mmio transport address of the [index]-th (0-based) attached virtio-blk-device. */
    fun transportAddress(index: Int): String {
        require(index in 0 until VIRT_MMIO_TRANSPORTS)
        val address = VIRT_MMIO_BASE + VIRT_MMIO_SIZE * (VIRT_MMIO_TRANSPORTS - 1 - index)
        return address.toString(16)
    }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9 _.-]"), "_").replace(",", "_").ifBlank { "guest" }

    private const val VIRT_MMIO_BASE = 0x0a000000L
    private const val VIRT_MMIO_SIZE = 0x200L
    private const val VIRT_MMIO_TRANSPORTS = 32
    /** TCG translation cache. QEMU defaults to 1 GiB on 64-bit hosts, far too much for a phone. */
    const val TB_CACHE_MB = 128
}
