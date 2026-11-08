package com.experience.virtualos;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads Google's Java-based command-line SDK tools and uses sdkmanager in Alpine. */
public final class SdkManagerBootstrap {
    private static final String TOOLS_URL =
            "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip";
    private static final String TOOLS_SHA256 =
            "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583";
    private static final int MIN_API_LEVEL = 24;
    private static final int MAX_API_LEVEL = 37;

    public interface Progress {
        void onStage(String stage);
    }

    private SdkManagerBootstrap() {}

    public static File sdkRoot(Context context) {
        return new File(context.getFilesDir(), "android-sdk");
    }

    public static void installSelectedGuest(Context context, int apiLevel, Progress progress) throws Exception {
        if (apiLevel < MIN_API_LEVEL || apiLevel > MAX_API_LEVEL) throw new IOException("Unsupported Android API level: " + apiLevel);
        if (!isArm64()) throw new IOException("The SDK runtime installer currently requires an ARM64 host.");
        if (!AlpineEnvironment.isInstalled(context)) AlpineEnvironment.installFromAssets(context);

        File sdk = sdkRoot(context);
        File tools = new File(sdk, "cmdline-tools/latest");
        if (!new File(tools, "bin/sdkmanager").isFile()) {
            progress.onStage("Downloading Android SDK command-line tools…");
            if (!sdk.exists() && !sdk.mkdirs()) throw new IOException("Cannot create private Android SDK directory.");
            File archive = new File(context.getCacheDir(), "android-commandlinetools.zip");
            download(TOOLS_URL, archive);
            String actual = sha256(archive);
            if (!TOOLS_SHA256.equals(actual)) {
                archive.delete();
                throw new IOException("Google SDK command-line tools checksum verification failed.");
            }
            progress.onStage("Installing SDK Manager…");
            File staging = new File(context.getCacheDir(), "sdk-tools-staging");
            deleteTree(staging);
            if (!staging.mkdirs()) throw new IOException("Cannot create SDK staging directory.");
            unzip(archive, staging);
            File extracted = new File(staging, "cmdline-tools");
            if (!extracted.isDirectory()) throw new IOException("Google SDK archive has an unexpected layout.");
            File parent = new File(sdk, "cmdline-tools");
            if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create SDK tools directory.");
            deleteTree(tools);
            if (!extracted.renameTo(tools)) {
                copyTree(extracted, tools);
            }
            archive.delete();
            deleteTree(staging);
        }

        progress.onStage("Preparing private Java runtime…");
        AlpineRuntime.run(context,
                "printf 'nameserver 1.1.1.1\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf && apk update && apk add --no-cache openjdk17-jre-headless ca-certificates bash unzip");

        String sdkPath = "/workspace/android-sdk";
        String manager = sdkPath + "/cmdline-tools/latest/bin/sdkmanager";
        progress.onStage("Accepting Android SDK licences…");
        AlpineRuntime.run(context,
                "chmod +x " + quote(manager) + " && yes | " + quote(manager)
                        + " --sdk_root=" + quote(sdkPath) + " --licenses");

        String imagePackage = "system-images;android-" + apiLevel + ";google_apis;arm64-v8a";
        progress.onStage("Checking available ARM64 images for Android API " + apiLevel + "…");
        String packageCatalog = AlpineRuntime.run(context,
                quote(manager) + " --sdk_root=" + quote(sdkPath) + " --list");
        if (!packageCatalog.contains(imagePackage)) {
            throw new IOException("Google's SDK repository does not currently list the ARM64 Google APIs image for API "
                    + apiLevel + ". Experience Virtual OS will not substitute an incompatible x86 or generic image.");
        }

        progress.onStage("Installing Android API " + apiLevel + " platform and ARM64 system image…");
        AlpineRuntime.run(context,
                quote(manager) + " --sdk_root=" + quote(sdkPath)
                        + " --channel=0 " + quote("platforms;android-" + apiLevel)
                        + " " + quote(imagePackage));
        new File(context.getFilesDir(), ".android-api-" + apiLevel + "-sdk-installed").createNewFile();
    }

    private static boolean isArm64() {
        for (String abi : android.os.Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) return true;
        return false;
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void download(String address, File destination) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Experience-Virtual-OS");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("Google returned HTTP " + code);
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format("%02x", b & 0xff));
        return result.toString();
    }

    private static void unzip(File archive, File destination) throws IOException {
        String base = destination.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(destination, entry.getName());
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(base)) throw new IOException("Unsafe path in SDK tools archive.");
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Cannot create SDK directory.");
                } else {
                    File parent = out.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create SDK tools folder.");
                    try (FileOutputStream stream = new FileOutputStream(out)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) stream.write(buffer, 0, n);
                    }
                    out.setReadable(true, false);
                    out.setWritable(true, true);
                    if (entry.getName().contains("/bin/")) out.setExecutable(true, false);
                }
                zip.closeEntry();
            }
        }
    }

    private static void copyTree(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) throw new IOException("Cannot create " + destination);
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(destination, child.getName()));
        } else {
            try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
                byte[] b = new byte[32768]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
            destination.setExecutable(source.canExecute(), false);
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
