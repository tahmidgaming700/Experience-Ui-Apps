package com.experience.virtualos;

import android.content.Context;
import android.system.Os;
import android.system.ErrnoException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Installs and validates the bundled Alpine ARM64 minirootfs in app-private storage. */
public final class AlpineEnvironment {
    private static final String ASSET = "alpine/alpine-minirootfs.tar.gz";
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
        deleteTree(base);
        File parent = base.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Cannot create private Alpine data directory at " + base);
        }
        if (!base.mkdirs() && !base.isDirectory()) {
            throw new IOException("Cannot create private Alpine root directory at " + base);
        }

        long entries = 0;
        try (InputStream raw = context.getAssets().open(ASSET);
             GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(raw, 65536))) {
            entries = extractTar(gzip, base.toPath());
        } catch (IOException e) {
            deleteTree(base);
            throw new IOException("Alpine archive extraction failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteTree(base);
            throw new IOException("Alpine filesystem setup failed: " + e.getMessage(), e);
        }

        File shell = new File(base, "bin/sh");
        if (!shell.exists()) {
            deleteTree(base);
            throw new IOException("Alpine archive was extracted (" + entries
                    + " entries), but bin/sh is missing. The bundled root filesystem is incomplete.");
        }
        if (!shell.canExecute() && !shell.setExecutable(true, true)) {
            deleteTree(base);
            throw new IOException("Alpine /bin/sh was extracted but Android could not mark it executable.");
        }

        File marker = new File(base, MARKER);
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write(("Alpine ARM64 minirootfs installed; entries=" + entries + "\n").getBytes("UTF-8"));
            out.getFD().sync();
        } catch (IOException e) {
            deleteTree(base);
            throw new IOException("Could not write Alpine installation marker: " + e.getMessage(), e);
        }
    }

    private static long extractTar(InputStream in, Path root) throws IOException {
        byte[] header = new byte[512];
        String longName = null;
        String longLink = null;
        Map<String, String> pax = new HashMap<>();
        long entries = 0;
        while (readBlock(in, header)) {
            boolean zero = true;
            for (byte b : header) if (b != 0) { zero = false; break; }
            if (zero) break;

            String name = field(header, 0, 100);
            String prefix = field(header, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            String link = field(header, 157, 100);
            long size = octal(header, 124, 12);
            int mode = (int) octal(header, 100, 8);
            int type = header[156] & 0xff;

            if (type == 'L' || type == 'K') {
                byte[] value = readPayload(in, size);
                String decoded = new String(value, "UTF-8").replace("\u0000", "");
                if (type == 'L') longName = decoded; else longLink = decoded;
                skipFully(in, padded(size) - size);
                continue;
            }
            if (type == 'x' || type == 'g') {
                byte[] value = readPayload(in, size);
                parsePax(value, pax);
                skipFully(in, padded(size) - size);
                continue;
            }

            if (longName != null) { name = longName; longName = null; }
            if (longLink != null) { link = longLink; longLink = null; }
            if (pax.containsKey("path")) name = pax.get("path");
            if (pax.containsKey("linkpath")) link = pax.get("linkpath");
            pax.clear();

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

            if (type == 0 || type == '0' || type == '7') {
                ensureParent(target);
                try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                    copyExactly(in, out, size);
                }
                skipFully(in, padded(size) - size);
                applyMode(target.toFile(), mode);
                entries++;
            } else if (type == '5') {
                skipFully(in, padded(size));
                if (!target.toFile().isDirectory() && !target.toFile().mkdirs()) {
                    throw new IOException("Cannot create Alpine directory " + relative);
                }
                // Do not chmod directories during extraction: a read-only directory
                // could prevent later archive entries from being created beneath it.
                entries++;
            } else if (type == '2') {
                skipFully(in, padded(size));
                ensureParent(target);
                try {
                    if (!Files.exists(target)) Os.symlink(link, target.toString());
                } catch (ErrnoException | UnsupportedOperationException e) {
                    throw new IOException("Cannot create Alpine symlink " + relative + " -> " + link
                            + ": " + e.getMessage(), e);
                }
                entries++;
            } else if (type == '1') {
                skipFully(in, padded(size));
                Path source = root.resolve(link).normalize();
                if (!source.startsWith(root)) throw new IOException("Unsafe Alpine hardlink target: " + link);
                ensureParent(target);
                if (!Files.exists(target)) {
                    if (!source.toFile().isFile()) {
                        throw new IOException("Alpine hardlink target is missing: " + link);
                    }
                    copyFile(source.toFile(), target.toFile());
                }
                entries++;
            } else {
                skipFully(in, padded(size));
            }
        }
        return entries;
    }

    private static void parsePax(byte[] data, Map<String, String> values) throws IOException {
        int offset = 0;
        while (offset < data.length) {
            int space = offset;
            while (space < data.length && data[space] != ' ') space++;
            if (space == data.length) throw new IOException("Malformed PAX header length");
            int length;
            try { length = Integer.parseInt(new String(data, offset, space - offset, "UTF-8")); }
            catch (NumberFormatException e) { throw new IOException("Malformed PAX header length", e); }
            if (length <= 0 || offset + length > data.length) throw new IOException("Invalid PAX record size");
            int keyStart = space + 1;
            int equals = keyStart;
            while (equals < offset + length && data[equals] != '=') equals++;
            if (equals < offset + length) {
                String key = new String(data, keyStart, equals - keyStart, "UTF-8");
                int valueEnd = offset + length;
                if (valueEnd > keyStart && data[valueEnd - 1] == '\n') valueEnd--;
                String value = new String(data, equals + 1, valueEnd - equals - 1, "UTF-8");
                values.put(key, value);
            }
            offset += length;
        }
    }

    private static byte[] readPayload(InputStream in, long size) throws IOException {
        if (size > Integer.MAX_VALUE) throw new IOException("Alpine tar metadata record is too large");
        byte[] data = new byte[(int) size];
        int offset = 0;
        while (offset < data.length) {
            int count = in.read(data, offset, data.length - offset);
            if (count < 0) throw new IOException("Truncated Alpine tar metadata");
            offset += count;
        }
        return data;
    }

    private static void ensureParent(Path target) throws IOException {
        File parent = target.getParent().toFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create Alpine directory " + parent);
    }

    private static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int offset = 0;
        while (offset < block.length) {
            int count = in.read(block, offset, block.length - offset);
            if (count < 0) {
                if (offset == 0) return false;
                throw new IOException("Truncated Alpine tar archive header");
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
        byte[] buffer = new byte[65536];
        long remaining = count;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
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
        file.setReadable((mode & 0444) != 0, true);
        file.setWritable((mode & 0222) != 0, true);
        file.setExecutable((mode & 0111) != 0, true);
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.getFD().sync();
        }
        destination.setReadable(true, true);
        destination.setWritable(true, true);
        destination.setExecutable(source.canExecute(), true);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
