package com.experience.virtualos;

import android.content.Context;
import java.io.*;
import java.util.Map;

/** Runs private Alpine commands without presenting a terminal to the user. */
public final class AlpineRuntime {
    private AlpineRuntime() {}
    private static ProcessBuilder builder(Context context, String command) throws IOException {
        if (!AlpineEnvironment.isInstalled(context)) throw new IOException("Private Linux environment is not installed.");
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File proot = new File(nativeDir, "libproot.so"), loader = new File(nativeDir, "libproot-loader.so");
        if (!proot.isFile() || !loader.isFile()) throw new IOException("ARM64 PRoot runtime is missing from this build.");
        File tmp = new File(context.getCacheDir(), "proot-tmp");
        if (!tmp.exists() && !tmp.mkdirs()) throw new IOException("Cannot create private runtime cache.");
        ProcessBuilder b = new ProcessBuilder(proot.getAbsolutePath(), "--kill-on-exit", "--link2symlink", "-0",
                "-r", AlpineEnvironment.root(context).getAbsolutePath(), "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-b", context.getFilesDir().getAbsolutePath() + ":/workspace", "-w", "/root", "/bin/sh", "-c", command);
        Map<String,String> e=b.environment();
        e.put("PROOT_LOADER",loader.getAbsolutePath()); e.put("PROOT_TMP_DIR",tmp.getAbsolutePath());
        e.put("HOME","/root"); e.put("LANG","C.UTF-8"); e.put("TERM","dumb"); e.put("TMPDIR","/tmp"); e.put("QEMU_AUDIO_DRV","none");
        b.redirectErrorStream(true);
        return b;
    }
    public static String run(Context context, String command) throws IOException, InterruptedException {
        Process p=builder(context,command).start(); StringBuilder out=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while((line=r.readLine())!=null) if(out.length()<24000) out.append(line).append('\n');
        }
        int exit=p.waitFor();
        if(exit!=0) throw new IOException("Private setup command failed (exit "+exit+"): "+out.toString().trim());
        return out.toString();
    }
    public static Process start(Context context,String command,File log) throws IOException {
        File parent=log.getParentFile(); if(parent!=null&&!parent.exists()&&!parent.mkdirs()) throw new IOException("Cannot create runtime log directory.");
        ProcessBuilder b=builder(context,command); b.redirectOutput(log); return b.start();
    }
}