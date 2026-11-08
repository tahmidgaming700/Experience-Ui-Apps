package com.experience.virtualos;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

/**
 * Installs the bundled, checksum-verified Alpine ARM64 minirootfs.
 *
 * Android's own tar implementation is used for extraction so that tar handles
 * symlinks, hardlinks, long names and file metadata. This is the same Android
 * extraction strategy used by established rootless VM environments.
 */
public final class AlpineEnvironment {
    // AAPT2 strips .gz from packaged asset names; the bytes remain gzip-compressed.
    private static final String ASSET = "alpine/alpine-minirootfs.bin";
    private static final String MARKER = ".experience-alpine-installed";

    private AlpineEnvironment() {}

    public static File root(Context context) {
        return new File(context.getFilesDir(), "alpine/rootfs");
    }

    public static boolean isInstalled(Context context) {
        File base = root(context);
        return new File(base, MARKER).isFile()
                && new File(base, "bin/sh").exists()
                && new File(base, "usr").isDirectory();
    }

    public static synchronized void installFromAssets(Context context) throws IOException {
        File base = root(context);
        if (isInstalled(context)) return;

        File parent = base.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Cannot create private Alpine data directory.");
        }

        File staging = new File(parent, "rootfs-staging");
        File archive = new File(parent, "alpine-minirootfs.tar");
        deleteTree(staging);
        deleteTree(base);
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new IOException("Cannot create private Alpine staging directory.");
        }

        try {
            try (InputStream asset = context.getAssets().open(ASSET);
                 GZIPInputStream gzip = new GZIPInputStream(asset);
                 FileOutputStream out = new FileOutputStream(archive)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = gzip.read(buffer)) != -1) out.write(buffer, 0, count);
                out.getFD().sync();
            } catch (IOException e) {
                throw new IOException("Cannot read or decompress the bundled Alpine archive: " + e.getMessage(), e);
            }

            validateArchivePaths(archive);
            TarResult first = runTar(new String[]{"tar", "xf", archive.getAbsolutePath(),
                    "-C", staging.getAbsolutePath()});
            if (first.exitCode != 0) {
                // Android tar may need ownership preservation disabled on app-private storage.
                deleteTree(staging);
                if (!staging.mkdirs()) throw new IOException("Cannot recreate Alpine staging directory.");
                TarResult retry = runTar(new String[]{"tar", "xf", archive.getAbsolutePath(),
                        "-C", staging.getAbsolutePath(), "--no-same-owner"});
                if (retry.exitCode != 0) {
                    throw new IOException("Android tar could not extract Alpine. First attempt: "
                            + first.output + " Retry: " + retry.output);
                }
            }

            File shell = new File(staging, "bin/sh");
            if (!shell.exists()) {
                throw new IOException("Alpine archive extracted, but /bin/sh is missing.");
            }
            if (!shell.canExecute() && !shell.setExecutable(true, true)) {
                throw new IOException("Android could not mark Alpine /bin/sh executable.");
            }
            File marker = new File(staging, MARKER);
            try (FileOutputStream out = new FileOutputStream(marker)) {
                out.write("Alpine ARM64 minirootfs installed using Android tar.\n".getBytes("UTF-8"));
                out.getFD().sync();
            }

            if (!staging.renameTo(base)) {
                throw new IOException("Could not move the prepared Alpine filesystem into place.");
            }
            if (!isInstalled(context)) {
                throw new IOException("Alpine filesystem verification failed after installation.");
            }
        } catch (IOException | RuntimeException e) {
            deleteTree(staging);
            deleteTree(base);
            throw e instanceof IOException ? (IOException) e
                    : new IOException("Alpine setup failed: " + e.getMessage(), e);
        } finally {
            deleteTree(archive);
        }
    }

    private static void validateArchivePaths(File archive) throws IOException {
        TarResult listing = runTar(new String[]{"tar", "tf", archive.getAbsolutePath()});
        if (listing.exitCode != 0) {
            throw new IOException("Android tar could not inspect the Alpine archive: " + listing.output);
        }
        String[] names = listing.output.split("\\r?\\n");
        for (String name : names) {
            if (name.isEmpty()) continue;
            Path entry;
            try {
                entry = Paths.get(name).normalize();
            } catch (RuntimeException e) {
                throw new IOException("Invalid path in Alpine archive: " + name, e);
            }
            if (entry.isAbsolute() || entry.startsWith("..")) {
                throw new IOException("Unsafe path in Alpine archive: " + name);
            }
        }
    }

    private static final class TarResult {
        final int exitCode;
        final String output;
        TarResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static TarResult runTar(String[] command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 12000) output.append(line).append('\n');
            }
        }
        try {
            return new TarResult(process.waitFor(), output.toString().trim());
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException("Alpine extraction was interrupted.", e);
        }
    }

    /** Delete without following symlinks, including dangling symlinks. */
    private static void deleteTree(File file) {
        if (file == null) return;
        Path path = file.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            file.delete();
        }
    }
}
