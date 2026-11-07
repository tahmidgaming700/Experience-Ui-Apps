package com.experience.virtualos;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/** Runs private Linux commands without exposing a shell UI to the user. */
public final class AlpineRuntime {
    private AlpineRuntime() {}

    public static String run(Context context, String command) throws IOException, InterruptedException {
        if (!AlpineEnvironment.isInstalled(context)) {
            throw new IOException("The private Linux environment has not been installed.");
        }
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File proot = new File(nativeDir, "libproot.so");
        File loader = new File(nativeDir, "libproot-loader.so");
        if (!proot.isFile() || !loader.isFile()) {
            throw new IOException("The ARM64 private execution engine is not bundled in this build.");
        }
        File root = AlpineEnvironment.root(context);
        File workspace = context.getFilesDir();
        File tmp = new File(context.getCacheDir(), "proot-tmp");
        if (!tmp.exists() && !tmp.mkdirs()) throw new IOException("Cannot create private runtime cache.");

        ProcessBuilder builder = new ProcessBuilder(
                proot.getAbsolutePath(),
                "--kill-on-exit",
                "--link2symlink",
                "-0",
                "-r", root.getAbsolutePath(),
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", workspace.getAbsolutePath() + ":/workspace",
                "-w", "/root",
                "/bin/sh", "-c", command
        );
        Map<String, String> env = builder.environment();
        env.put("PROOT_LOADER", loader.getAbsolutePath());
        env.put("PROOT_TMP_DIR", tmp.getAbsolutePath());
        env.put("HOME", "/root");
        env.put("LANG", "C.UTF-8");
        env.put("TERM", "dumb");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 24000) output.append(line).append('\n');
            }
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("Private setup command failed (exit " + exit + "): "
                    + output.toString().trim());
        }
        return output.toString();
    }
}
