package com.experience.virtualos;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.io.File;

/**
 * Read-only host capability checks. These checks never request root or change
 * host settings; they are deliberately conservative and do not claim that a
 * virtual machine can boot merely because an accelerator device exists.
 */
public final class RuntimeCapabilities {
    public final boolean arm64;
    public final boolean kvmDevicePresent;
    public final boolean kvmReadable;
    public final boolean kvmWritable;
    public final long availableMemoryMb;
    public final int processorCount;
    public final int hostApi;

    private RuntimeCapabilities(boolean arm64, boolean kvmDevicePresent,
            boolean kvmReadable, boolean kvmWritable, long availableMemoryMb,
            int processorCount, int hostApi) {
        this.arm64 = arm64;
        this.kvmDevicePresent = kvmDevicePresent;
        this.kvmReadable = kvmReadable;
        this.kvmWritable = kvmWritable;
        this.availableMemoryMb = availableMemoryMb;
        this.processorCount = processorCount;
        this.hostApi = hostApi;
    }

    public static RuntimeCapabilities inspect(Context context) {
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                arm64 = true;
                break;
            }
        }

        File kvm = new File("/dev/kvm");
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) manager.getMemoryInfo(memory);

        return new RuntimeCapabilities(arm64, kvm.exists(), kvm.canRead(),
                kvm.canWrite(), memory.availMem / (1024L * 1024L),
                Runtime.getRuntime().availableProcessors(), Build.VERSION.SDK_INT);
    }

    public boolean hasUsableKvmPermissions() {
        return kvmDevicePresent && kvmReadable && kvmWritable;
    }

    public String accelerationSummary() {
        if (!arm64) return "This device is not an ARM64 host.";
        if (hasUsableKvmPermissions()) {
            return "A KVM device is visible and accessible. The app still needs a compatible Android-host QEMU build and guest machine profile.";
        }
        if (kvmDevicePresent) {
            return "KVM exists but this app cannot access it. Hardware acceleration is not available to Experience Virtual OS.";
        }
        return "No accessible KVM device was detected. A software-emulation engine would be required and may be extremely slow.";
    }

    public String deviceSummary() {
        return (arm64 ? "ARM64 host" : "Non-ARM64 host")
                + " • Android API " + hostApi
                + " • " + processorCount + " CPU cores"
                + " • " + availableMemoryMb + " MB available RAM";
    }
}
