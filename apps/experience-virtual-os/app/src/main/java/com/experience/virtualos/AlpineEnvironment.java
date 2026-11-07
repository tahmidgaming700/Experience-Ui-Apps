package com.experience.virtualos;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

/** Installs the official Alpine ARM64 minirootfs bundled by the CI build. */
public final class AlpineEnvironment {
    private static final String ASSET = "alpine/alpine-minirootfs.tar.gz";
    private static final String MARKER = ".experience-alpine-installed";

    private AlpineEnvironment() {}

    public static File root(Context context) {
        return new File(context.getFilesDir(), "alpine/rootfs");
    }

    public static boolean isInstalled(Context context) {
        return new File(root(context), MARKER).isFile();
    }

    public static void installFromAssets(Context context) throws IOException {
        File root = root(context);
        if (isInstalled(context)) return;
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create Alpine root directory");
        File parent = root.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Cannot create Alpine data directory");
        }

        try (InputStream raw = context.getAssets().open(ASSET);
             GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(raw))) {
            extractTar(gzip, root.toPath());
        } catch (IOException e) {
            deleteTree(root);
            throw e;
        }

        File marker = new File(root, MARKER);
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write(("Alpine ARM64 minirootfs installed\n").getBytes("UTF-8"));
            out.flush();
        }
    }

    private static void extractTar(InputStream in, Path root) throws IOException {
        byte[] header = new byte[512];
        while (readBlock(in, header)) {
            boolean zero = true;
            for (byte b : header) if (b != 0) { zero = false; break; }
            if (zero) return;

            String name = field(header, 0, 100);
            String prefix = field(header, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            String link = field(header, 157, 100);
            long size = octal(header, 124, 12);
            int mode = (int) octal(header, 100, 8);
            int type = header[156] & 0xff;

            Path relative = Paths.get(name).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                skipFully(in, padded(size));
                continue;
            }
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root)) {
                skipFully(in, padded(size));
                continue;
            }

            if (type == 0 || type == '0') {
                File parent = target.getParent().toFile();
                if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
                try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                    copyExactly(in, out, size);
                }
                applyMode(target.toFile(), mode);
                skipFully(in, padded(size) - size);
            } else if (type == '5') {
                if (!target.toFile().exists() && !target.toFile().mkdirs()) {
                    throw new IOException("Cannot create directory " + target);
                }
                skipFully(in, padded(size));
            } else if (type == '2') {
                skipFully(in, padded(size));
                File parent = target.getParent().toFile();
                if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
                try {
                    if (!Files.exists(target)) Files.createSymbolicLink(target, Paths.get(link));
                } catch (UnsupportedOperationException ignored) {
                    // Symlinks are required by Alpine; report this rather than silently faking them.
                    throw new IOException("This Android filesystem does not support Alpine symlinks");
                }
            } else if (type == '1') {
                skipFully(in, padded(size));
                Path source = root.resolve(link).normalize();
                if (source.startsWith(root) && Files.exists(source) && !Files.exists(target)) {
                    Files.createLink(target, source);
                }
            } else {
                // PAX/global metadata and other non-file records are not needed by the minirootfs.
                skipFully(in, padded(size));
            }
        }
    }

    private static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int offset = 0;
        while (offset < block.length) {
            int count = in.read(block, offset, block.length - offset);
            if (count < 0) {
                if (offset == 0) return false;
                throw new IOException("Truncated Alpine tar archive");
            }
            offset += count;
        }
        return true;
    }

    private static String field(byte[] data, int offset, int length) throws IOException {
        int end = offset;
        while (end < offset + length && data[end] != 0) end++;
        return new String(data, offset, end - offset, "UTF-8").trim();
    }

    private static long octal(byte[] data, int offset, int length) throws IOException {
        String value = field(data, offset, length).trim();
        if (value.isEmpty()) return 0;
        try { return Long.parseLong(value.replace("\u0000", "").trim(), 8); }
        catch (NumberFormatException e) { throw new IOException("Invalid tar header number", e); }
    }

    private static long padded(long size) {
        return ((size + 511L) / 512L) * 512L;
    }

    private static void copyExactly(InputStream in, FileOutputStream out, long count) throws IOException {
        byte[] buffer = new byte[32768];
        long remaining = count;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
            if (read < 0) throw new IOException("Truncated Alpine file entry");
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) throw new IOException("Truncated Alpine archive");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static void applyMode(File file, int mode) {
        file.setReadable((mode & 0444) != 0, false);
        file.setWritable((mode & 0222) != 0, true);
        file.setExecutable((mode & 0111) != 0, false);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
