package com.experience.virtualos;

import android.graphics.Bitmap;
import java.io.*;
import java.net.*;

/** RFB 3.8 framebuffer client. QEMU VNC is bound to localhost only. */
public final class RfbClient implements AutoCloseable {
    public interface Listener { void connected(int w,int h); void frame(Bitmap b); void error(String m); }
    private final Listener listener; private final Object lock=new Object();
    private volatile boolean running=true; private Socket socket; private DataInputStream in; private DataOutputStream out;
    private Bitmap bitmap; private int width,height;
    public RfbClient(Listener l){listener=l;}
    public void run(){
        Exception last=null;
        long deadline=System.currentTimeMillis()+30000L;
        while(running && System.currentTimeMillis()<deadline){
            try {
                socket=new Socket(); socket.connect(new InetSocketAddress("127.0.0.1",5901),2500); socket.setTcpNoDelay(true);
                in=new DataInputStream(new BufferedInputStream(socket.getInputStream())); out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                handshake(); readLoop(); return;
            } catch(Exception e){
                last=e; closeSocket();
                if(!running) return;
                try{Thread.sleep(500);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
            }
        }
        if(running) listener.error(last==null?"Timed out waiting for QEMU display":last.getMessage());
        closeSocket();
    }
    private void handshake() throws IOException {
        byte[] version=new byte[12]; in.readFully(version); write("RFB 003.008\\n".getBytes("US-ASCII"));
        int count=in.readUnsignedByte(); if(count==0){int n=in.readInt();byte[] why=new byte[Math.max(0,Math.min(n,4096))];in.readFully(why);throw new IOException(new String(why,"UTF-8"));}
        boolean none=false; for(int i=0;i<count;i++) if(in.readUnsignedByte()==1) none=true;
        if(!none) throw new IOException("VNC server does not offer local no-auth mode.");
        sendByte(1); if(in.readInt()!=0) throw new IOException("VNC handshake failed."); sendByte(1);
        width=in.readUnsignedShort();height=in.readUnsignedShort();byte[] pf=new byte[16];in.readFully(pf);
        int nameLen=in.readInt();if(nameLen<0||nameLen>65536)throw new IOException("Invalid VNC server name.");byte[] name=new byte[nameLen];in.readFully(name);
        if(width<1||height<1||(long)width*height>16000000)throw new IOException("Invalid framebuffer dimensions.");
        bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        synchronized(lock){out.writeByte(0);out.write(new byte[3]);out.writeByte(32);out.writeByte(24);out.writeByte(0);out.writeByte(1);
            out.writeShort(255);out.writeShort(255);out.writeShort(255);out.writeByte(16);out.writeByte(8);out.writeByte(0);out.write(new byte[3]);
            out.writeByte(2);out.writeByte(0);out.writeShort(2);out.writeInt(0);out.writeInt(-223);out.flush();}
        listener.connected(width,height); request(false);
    }
    private void readLoop() throws IOException {
        while(running){
            int type=in.readUnsignedByte();
            if(type==0){
                in.readUnsignedByte();
                int count=in.readUnsignedShort();
                for(int i=0;i<count;i++) rectangle();
                listener.frame(bitmap);
                request(true);
            }else if(type==2){
                // Server bell; no audio is required for framebuffer updates.
            }else if(type==3){
                in.readUnsignedByte();
                int length=in.readInt();
                if(length<0||length>65536)throw new IOException("Invalid VNC clipboard payload.");
                byte[] ignored=new byte[length];in.readFully(ignored);
            }else{
                throw new IOException("Unsupported VNC server message "+type);
            }
        }
    }
    private void rectangle() throws IOException {
        int x=in.readUnsignedShort(),y=in.readUnsignedShort(),w=in.readUnsignedShort(),h=in.readUnsignedShort(),enc=in.readInt();
        if(enc==-223){if(w<1||h<1||(long)w*h>16000000)throw new IOException("Invalid framebuffer resize");width=w;height=h;bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);return;}
        if(enc!=0)throw new IOException("Unsupported framebuffer encoding "+enc);
        if(w<1||h<1||x+w>width||y+h>height)throw new IOException("Invalid framebuffer rectangle");
        byte[] bytes=new byte[w*h*4];in.readFully(bytes);int[] pixels=new int[w*h];
        for(int i=0,p=0;i<pixels.length;i++,p+=4)pixels[i]=0xff000000|((bytes[p+2]&255)<<16)|((bytes[p+1]&255)<<8)|(bytes[p]&255);
        bitmap.setPixels(pixels,0,w,x,y,w,h);
    }
    private void request(boolean incremental)throws IOException{synchronized(lock){out.writeByte(3);out.writeByte(incremental?1:0);out.writeShort(0);out.writeShort(0);out.writeShort(width);out.writeShort(height);out.flush();}}
    public void pointer(int x,int y,boolean down){if(!running||out==null)return;try{synchronized(lock){out.writeByte(5);out.writeByte(down?1:0);out.writeShort(Math.max(0,Math.min(width-1,x)));out.writeShort(Math.max(0,Math.min(height-1,y)));out.flush();}}catch(IOException e){listener.error(e.getMessage());}}
    public void key(int sym,boolean down){if(!running||out==null)return;try{synchronized(lock){out.writeByte(4);out.writeByte(down?1:0);out.write(new byte[2]);out.writeInt(sym);out.flush();}}catch(IOException e){listener.error(e.getMessage());}}
    private void sendByte(int b)throws IOException{synchronized(lock){out.writeByte(b);out.flush();}}
    private void write(byte[] b)throws IOException{synchronized(lock){out.write(b);out.flush();}}
    private void closeSocket(){try{if(socket!=null)socket.close();}catch(Exception ignored){}}
    @Override public void close(){running=false;closeSocket();}
}