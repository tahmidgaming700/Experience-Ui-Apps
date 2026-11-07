package com.experience.virtualos;

import android.content.Context;
import java.io.*;

/** Owns the private Android Emulator/Ranchu process and guest AVD storage. */
public final class GuestVmManager {
    private static Process process;
    private static File log;
    private static boolean bundledRanchu;

    private GuestVmManager() {}

    public static synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public static synchronized File logFile() { return log; }

    public static synchronized void installEngine(Context context) throws Exception {
        if (!AlpineEnvironment.isInstalled(context)) AlpineEnvironment.installFromAssets(context);

        try {
            String existing = AlpineRuntime.run(context,
                    "test -x /opt/ranchu/emulator && LD_LIBRARY_PATH=/opt/ranchu/lib64:/opt/ranchu/lib /opt/ranchu/emulator -version");
            if (existing.toLowerCase().contains("emulator")) {
                bundledRanchu = true;
                return;
            }
        } catch (Exception notInstalled) {
            bundledRanchu = false;
        }

        try (InputStream input = context.getAssets().open("ranchu-engine.tar.gz")) {
            File archive = new File(context.getFilesDir(), "ranchu-engine.tar.gz");
            try (OutputStream output = new FileOutputStream(archive)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            String verify = "apk update && apk add --no-cache gcompat libstdc++ libgcc tar "
                    + "&& mkdir -p /opt/ranchu && tar -xzf /workspace/ranchu-engine.tar.gz -C /opt/ranchu "
                    + "&& chmod +x /opt/ranchu/emulator /opt/ranchu/qemu/linux-aarch64/qemu-system-aarch64 "
                    + "&& LD_LIBRARY_PATH=/opt/ranchu/lib64:/opt/ranchu/lib /opt/ranchu/emulator -version";
            String output = AlpineRuntime.run(context, verify);
            if (!output.toLowerCase().contains("emulator")) throw new IOException("Android Emulator launcher verification failed.");
            bundledRanchu = true;
            return;
        } catch (FileNotFoundException noBundledEngine) {
            bundledRanchu = false;
        } catch (Exception engineError) {
            bundledRanchu = false;
        }

        AlpineRuntime.run(context,
                "printf 'nameserver 1.1.1.1\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf "
                + "&& apk update && apk add --no-cache qemu-system-aarch64 qemu-img");
        String version = AlpineRuntime.run(context, "qemu-system-aarch64 --version");
        if (!version.contains("QEMU")) throw new IOException("QEMU executable verification failed.");
    }

    public static synchronized void start(Context context, int api, int memoryMb) throws Exception {
        if (isRunning()) return;
        installEngine(context);

        File imageDir = new File(SdkManagerBootstrap.sdkRoot(context),
                "system-images/android-" + api + "/google_apis/arm64-v8a");
        File kernel = pick(imageDir, "kernel-ranchu-64", "kernel-ranchu");
        File ramdisk = new File(imageDir, "ramdisk.img");
        File system = new File(imageDir, "system.img");
        if (!kernel.isFile() || !ramdisk.isFile() || !system.isFile()) {
            throw new IOException("The selected ARM64 SDK image is incomplete. Install its kernel, ramdisk and system image first.");
        }

        File data = new File(context.getFilesDir(), "guest-data/api-" + api);
        if (!data.exists() && !data.mkdirs()) throw new IOException("Cannot create guest data directory.");
        log = new File(data, "qemu.log");
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(context);

        if (bundledRanchu) {
            startWithAndroidEmulator(context, api, memoryMb, capabilities, data);
        } else {
            startWithGenericQemu(context, api, memoryMb, capabilities, imageDir, data);
        }

        if (capabilities.hasUsableKvmPermissions() && process != null) {
            Thread.sleep(1800);
            if (!process.isAlive() && bundledRanchu) {
                startEmulatorProcess(context, api, memoryMb, data, false);
            }
        }
    }

    private static void startWithAndroidEmulator(Context context, int api, int memoryMb,
            RuntimeCapabilities capabilities, File data) throws Exception {
        File avdHome = new File(context.getFilesDir(), "avd");
        File avdDir = new File(avdHome, "Experience_API_" + api + ".avd");
        File emulatorHome = new File(context.getFilesDir(), "emulator-home");
        if (!avdDir.exists() && !avdDir.mkdirs()) throw new IOException("Cannot create AVD directory.");
        if (!avdHome.exists() && !avdHome.mkdirs()) throw new IOException("Cannot create AVD home.");
        if (!emulatorHome.exists() && !emulatorHome.mkdirs()) throw new IOException("Cannot create emulator home.");

        String name = "Experience_API_" + api;
        String config = "AvdId=" + name + "\n"
                + "PlayStore.enabled=false\n"
                + "abi.type=arm64-v8a\n"
                + "avd.ini.displayname=Experience Virtual OS\n"
                + "avd.ini.encoding=UTF-8\n"
                + "disk.dataPartition.size=3G\n"
                + "fastboot.forceColdBoot=yes\n"
                + "hw.cpu.arch=arm64\n"
                + "hw.cpu.ncore=" + Math.max(1, Math.min(2, capabilities.processorCount)) + "\n"
                + "hw.ramSize=" + Math.max(512, memoryMb) + "\n"
                + "hw.gpu.enabled=yes\n"
                + "hw.gpu.mode=swiftshader_indirect\n"
                + "hw.keyboard=yes\n"
                + "hw.touchScreen=yes\n"
                + "hw.lcd.width=1080\n"
                + "hw.lcd.height=1920\n"
                + "hw.lcd.density=420\n"
                + "image.sysdir.1=system-images/android-" + api + "/google_apis/arm64-v8a/\n"
                + "tag.display=Google APIs\n"
                + "tag.id=google_apis\n"
                + "target=android-" + api + "\n";
        writeText(new File(avdDir, "config.ini"), config);
        writeText(new File(avdHome, name + ".ini"),
                "avd.ini.encoding=UTF-8\npath=/workspace/avd/" + name + ".avd\n"
                + "path.rel=avd/" + name + ".avd\ntarget=android-" + api + "\n");

        startEmulatorProcess(context, api, memoryMb, data, capabilities.hasUsableKvmPermissions());
    }

    private static void startEmulatorProcess(Context context, int api, int memoryMb, File data, boolean kvm) throws IOException {
        String home = "/workspace/emulator-home";
        String command = "export HOME=" + home
                + " ANDROID_HOME=/workspace/android-sdk"
                + " ANDROID_SDK_ROOT=/workspace/android-sdk"
                + " ANDROID_AVD_HOME=/workspace/avd"
                + " ANDROID_EMULATOR_HOME=" + home
                + " LD_LIBRARY_PATH=/opt/ranchu/lib64:/opt/ranchu/lib:$LD_LIBRARY_PATH; "
                + "exec /opt/ranchu/emulator -avd Experience_API_" + api
                + " -no-window -no-audio -no-snapshot -no-boot-anim"
                + " -gpu swiftshader_indirect -memory " + Math.max(512, memoryMb)
                + " -cores 2 -accel " + (kvm ? "on" : "off")
                + " -qemu -vnc 127.0.0.1:1"
                + " -serial file:/workspace/guest-data/api-" + api + "/serial.log";
        process = AlpineRuntime.start(context, command, log);
    }

    private static void startWithGenericQemu(Context context, int api, int memoryMb,
            RuntimeCapabilities capabilities, File imageDir, File data) throws Exception {
        File userdataSource = new File(imageDir, "userdata.img");
        File userdata = new File(data, "userdata.img");
        if (!userdata.exists()) {
            if (!userdataSource.isFile()) throw new IOException("The SDK image does not include userdata.img.");
            copy(userdataSource, userdata);
        }
        String qemu = "qemu-system-aarch64";
        String machineList = AlpineRuntime.run(context, qemu + " -machine help");
        String machine = machineList.toLowerCase().contains("ranchu") ? "ranchu" : "virt,gic-version=3";
        boolean kvm = capabilities.hasUsableKvmPermissions();
        String accel = kvm ? "kvm" : "tcg,thread=multi";
        String cpu = kvm ? "host" : "max";
        int cpus = Math.max(1, Math.min(2, capabilities.processorCount));
        String root = "/workspace/android-sdk/system-images/android-" + api + "/google_apis/arm64-v8a/";
        StringBuilder command = new StringBuilder("exec " + qemu + " -machine " + machine
                + " -cpu " + cpu + " -smp " + cpus + " -m " + Math.max(512, memoryMb)
                + " -accel '" + accel + "'");
        command.append(" -kernel '").append(root).append(pick(imageDir, "kernel-ranchu-64", "kernel-ranchu").getName()).append("'")
                .append(" -initrd '").append(root).append("ramdisk.img'")
                .append(" -append 'console=ttyAMA0 androidboot.hardware=ranchu androidboot.serialno=experience-virtual-os'")
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
                        command.toString().replace("-accel 'kvm'", "-accel 'tcg,thread=multi'"));
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
