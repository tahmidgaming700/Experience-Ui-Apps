package com.experience.virtualos;

import android.content.Context;
import java.io.*;

/** Owns the private ARM64 QEMU process and guest storage. */
public final class GuestVmManager {
    private static Process process;
    private static File log;

    private GuestVmManager() {}

    public static synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public static synchronized File logFile() { return log; }

    public static synchronized void installEngine(Context context) throws Exception {
        if (!AlpineEnvironment.isInstalled(context)) AlpineEnvironment.installFromAssets(context);
        AlpineRuntime.run(context,
                "printf 'nameserver 1.1.1.1\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf "
                + "&& apk update && apk add --no-cache qemu-system-aarch64 qemu-img");
        String version = AlpineRuntime.run(context, "qemu-system-aarch64 --version");
        if (!version.contains("QEMU")) throw new IOException("ARM64 QEMU executable verification failed.");
    }

    public static synchronized void start(Context context, int api, int memoryMb) throws Exception {
        if (isRunning()) return;
        installEngine(context);

        File imageDir = new File(SdkManagerBootstrap.sdkRoot(context),
                "system-images/android-" + api + "/google_apis/arm64-v8a");
        File kernel = bundledKernel(context);
        File ramdisk = new File(imageDir, "ramdisk.img");
        File system = new File(imageDir, "system.img");
        if (!kernel.isFile() || kernel.length() != 6157664L) {
            throw new IOException("The bundled Google ARM64 kernel-qemu is missing or has an unexpected size.");
        }
        if (!ramdisk.isFile() || !system.isFile()) {
            throw new IOException("The selected ARM64 SDK image is incomplete. Install its ramdisk and system image first.");
        }

        File data = new File(context.getFilesDir(), "guest-data/api-" + api);
        if (!data.exists() && !data.mkdirs()) throw new IOException("Cannot create guest data directory.");
        log = new File(data, "qemu.log");
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(context);

        startWithGenericQemu(context, api, memoryMb, capabilities, imageDir, kernel, data);
    }

    private static void startWithGenericQemu(Context context, int api, int memoryMb,
            RuntimeCapabilities capabilities, File imageDir, File kernel, File data) throws Exception {
        File userdataSource = new File(imageDir, "userdata.img");
        File userdata = new File(data, "userdata.img");
        if (!userdata.exists()) {
            if (!userdataSource.isFile()) throw new IOException("The SDK image does not include userdata.img.");
            copy(userdataSource, userdata);
        }
        String qemu = "qemu-system-aarch64";
        String machine = "virt,gic-version=3";
        boolean kvm = capabilities.hasUsableKvmPermissions();
        String accel = kvm ? "kvm" : "tcg,thread=multi";
        String cpu = kvm ? "host" : "max";
        int cpus = Math.max(1, Math.min(2, capabilities.processorCount));
        String root = "/workspace/android-sdk/system-images/android-" + api + "/google_apis/arm64-v8a/";
        String kernelPath = "/workspace/kernels/kernel-qemu";
        StringBuilder command = new StringBuilder("exec " + qemu + " -machine " + machine
                + " -cpu " + cpu + " -smp " + cpus + " -m " + Math.max(512, memoryMb)
                + " -accel '" + accel + "'");
        command.append(" -kernel '").append(kernelPath).append("'")
                .append(" -initrd '").append(root).append("ramdisk.img'")
                .append(" -append 'console=ttyAMA0 androidboot.hardware=virt androidboot.serialno=experience-virtual-os'")
                .append(" -device virtio-gpu-pci -device virtio-keyboard-pci -device virtio-tablet-pci")
                .append(" -netdev user,id=net0 -device virtio-net-pci,netdev=net0")
                .append(" -drive 'file=").append(root).append("system.img,if=none,format=raw,id=system' -device virtio-blk-device,drive=system");
        File vendor = new File(imageDir, "vendor.img");
        if (vendor.isFile()) command.append(" -drive 'file=").append(root).append("vendor.img,if=none,format=raw,id=vendor' -device virtio-blk-device,drive=vendor");
        command.append(" -drive 'file=/workspace/guest-data/api-").append(api)
                .append("/userdata.img,if=none,format=raw,id=userdata' -device virtio-blk-device,drive=userdata")
                .append(" -vnc 127.0.0.1:1 -monitor none -serial 'file=/workspace/guest-data/api-")
                .append(api).append("/serial.log' -no-reboot");
        process = AlpineRuntime.start(context, command.toString(), log);
        if (kvm) {
            Thread.sleep(1800);
            if (!process.isAlive()) {
                process = AlpineRuntime.start(context,
                        command.toString().replace("-accel 'kvm'", "-accel 'tcg,thread=multi'"), log);
            }
        }
    }

    public static synchronized void stop() {
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
    }

    private static File bundledKernel(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "kernels");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create private kernel directory.");
        File destination = new File(directory, "kernel-qemu");
        if (destination.isFile() && destination.length() == 6157664L) return destination;
        try (InputStream input = context.getAssets().open("kernel/kernel-qemu");
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        }
        if (destination.length() != 6157664L) {
            destination.delete();
            throw new IOException("Bundled kernel-qemu failed its size check.");
        }
        return destination;
    }

    private static File pick(File dir, String... names) {
        for (String name : names) {
            File candidate = new File(dir, name);
            if (candidate.isFile()) return candidate;
        }
        return new File(dir, names[0]);
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
        }
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.flush();
        }
    }
}
