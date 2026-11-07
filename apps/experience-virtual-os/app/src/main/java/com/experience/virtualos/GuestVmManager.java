package com.experience.virtualos;

import android.content.Context;
import java.io.*;

/** Owns the private QEMU process and its Android guest disk set. */
public final class GuestVmManager {
    private static Process process; private static File log;
    private static boolean bundledRanchu;
    private GuestVmManager(){}
    public static synchronized boolean isRunning(){return process!=null&&process.isAlive();}
    public static synchronized File logFile(){return log;}
    public static synchronized void installEngine(Context c)throws Exception{
        if(!AlpineEnvironment.isInstalled(c))AlpineEnvironment.installFromAssets(c);
        try {
            String existing=AlpineRuntime.run(c,"test -x /opt/ranchu/qemu/linux-aarch64/qemu-system-aarch64 && LD_LIBRARY_PATH=/opt/ranchu/lib64:/opt/ranchu/lib /opt/ranchu/qemu/linux-aarch64/qemu-system-aarch64 --version");
            if(existing.contains("QEMU")){bundledRanchu=true;return;}
        } catch(Exception notInstalled) { bundledRanchu=false; }
        try(InputStream in=c.getAssets().open("ranchu-engine.tar.gz")){
            File archive=new File(c.getFilesDir(),"ranchu-engine.tar.gz");
            try(OutputStream out=new FileOutputStream(archive)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b);}
            AlpineRuntime.run(c,"apk update && apk add --no-cache gcompat libstdc++ libgcc tar && mkdir -p /opt/ranchu && tar -xzf /workspace/ranchu-engine.tar.gz -C /opt/ranchu && chmod +x /opt/ranchu/qemu/linux-aarch64/qemu-system-aarch64 && LD_LIBRARY_PATH=/opt/ranchu/lib64:/opt/ranchu/lib /opt/ranchu/qemu/linux-aarch64/qemu-system-aarch64 --version");
            bundledRanchu=true;
            return;
        }catch(FileNotFoundException noBundledEngine){
            bundledRanchu=false;
        }catch(Exception engineError){
            bundledRanchu=false;
        }
        AlpineRuntime.run(c,"printf 'nameserver 1.1.1.1\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf && apk update && apk add --no-cache qemu-system-aarch64 qemu-img");
        String v=AlpineRuntime.run(c,"qemu-system-aarch64 --version");
        if(!v.contains("QEMU"))throw new IOException("QEMU executable verification failed.");
    }
    public static synchronized void start(Context c,int api,int ram)throws Exception{
        if(isRunning())return;
        installEngine(c);
        File sdk=new File(SdkManagerBootstrap.sdkRoot(c),"system-images/android-"+api+"/google_apis/arm64-v8a");
        File kernel=pick(sdk,"kernel-ranchu-64","kernel-ranchu");
        File ramdisk=new File(sdk,"ramdisk.img"),system=new File(sdk,"system.img"),vendor=new File(sdk,"vendor.img");
        File userdataSource=new File(sdk,"userdata.img");
        if(!kernel.isFile()||!ramdisk.isFile()||!system.isFile())throw new IOException("SDK image missing kernel, ramdisk or system.img. Install the ARM64 Google APIs image first.");
        File data=new File(c.getFilesDir(),"guest-data/api-"+api);if(!data.exists()&&!data.mkdirs())throw new IOException("Cannot create guest data directory.");
        File userdata=new File(data,"userdata.img");
        if(!userdata.exists()){if(!userdataSource.isFile())throw new IOException("SDK package does not include userdata.img.");copy(userdataSource,userdata);}
        RuntimeCapabilities caps=RuntimeCapabilities.inspect(c);
        String qemu=bundledRanchu?"/opt/ranchu/qemu/linux-aarch64/qemu-system-aarch64":"qemu-system-aarch64";
        String prefix=bundledRanchu?"export LD_LIBRARY_PATH=/opt/ranchu/lib64:/opt/ranchu/lib:$LD_LIBRARY_PATH; ":"";
        String machineList=AlpineRuntime.run(c,prefix+qemu+" -machine help");
        String machine=machineList.toLowerCase().contains("ranchu")?"ranchu":"virt,gic-version=3";
        String accel=caps.hasUsableKvmPermissions()?"kvm":"tcg,thread=multi";
        String cpu=caps.hasUsableKvmPermissions()?"host":"max";
        int cpus=Math.max(1,Math.min(2,caps.processorCount));
        String root="/workspace/android-sdk/system-images/android-"+api+"/google_apis/arm64-v8a/";
        StringBuilder q=new StringBuilder(prefix+"exec "+qemu+" -machine "+machine+" -cpu "+cpu+" -smp "+cpus+" -m "+Math.max(512,ram)+" -accel '"+accel+"'");
        q.append(" -kernel '").append(root).append(kernel.getName()).append("' -initrd '").append(root).append("ramdisk.img'");
        q.append(" -append 'console=ttyAMA0 androidboot.hardware=ranchu androidboot.serialno=experience-virtual-os'");
        q.append(" -device virtio-gpu-pci -device virtio-keyboard-pci -device virtio-tablet-pci");
        q.append(" -netdev user,id=net0 -device virtio-net-pci,netdev=net0");
        q.append(" -drive 'file=").append(root).append("system.img,if=none,format=raw,id=system' -device virtio-blk-device,drive=system");
        if(vendor.isFile())q.append(" -drive 'file=").append(root).append("vendor.img,if=none,format=raw,id=vendor' -device virtio-blk-device,drive=vendor");
        q.append(" -drive 'file=/workspace/guest-data/api-").append(api).append("/userdata.img,if=none,format=raw,id=userdata' -device virtio-blk-device,drive=userdata");
        q.append(" -vnc 127.0.0.1:1 -monitor none -serial 'file=/workspace/guest-data/api-").append(api).append("/serial.log' -no-reboot");
        log=new File(data,"qemu.log");
        process=AlpineRuntime.start(c,q.toString(),log);
        if("kvm".equals(accel)){
            Thread.sleep(1800);
            if(!process.isAlive()){
                process=AlpineRuntime.start(c,q.toString().replace("-accel 'kvm'","-accel 'tcg,thread=multi'"),log);
            }
        }
    }
    public static synchronized void stop(){if(process!=null){process.destroy();try{if(!process.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();process.destroyForcibly();}}process=null;}
    private static File pick(File dir,String...names){for(String n:names){File f=new File(dir,n);if(f.isFile())return f;}return new File(dir,names[0]);}
    private static void copy(File a,File b)throws IOException{try(InputStream i=new FileInputStream(a);OutputStream o=new FileOutputStream(b)){byte[] z=new byte[65536];int n;while((n=i.read(z))!=-1)o.write(z,0,n);o.flush();}}
}