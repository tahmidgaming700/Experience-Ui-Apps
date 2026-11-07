package com.experience.virtualos;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.widget.*;

public final class GuestDisplayActivity extends Activity implements RfbClient.Listener {
    private RfbClient client; private GuestSurface surface; private TextView status;
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff080d14);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);title.setText("EXPERIENCE VIRTUAL OS");title.setTextColor(0xffe8eef8);title.setTextSize(12);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button kb=new Button(this);kb.setText("Keyboard");kb.setAllCaps(false);kb.setOnClickListener(v->{surface.requestFocus();((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(surface,InputMethodManager.SHOW_IMPLICIT);});bar.addView(kb);
        Button close=new Button(this);close.setText("Close");close.setAllCaps(false);close.setOnClickListener(v->finish());bar.addView(close);root.addView(bar);
        status=new TextView(this);status.setText("Connecting to guest display on localhost…");status.setTextColor(0xffb8c4d8);status.setPadding(12,8,12,8);root.addView(status);
        surface=new GuestSurface(this);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        client=new RfbClient(this);new Thread(client::run,"experience-vnc").start();
    }
    @Override public void connected(int w,int h){runOnUiThread(()->status.setText("Guest framebuffer "+w+" × "+h+" • Touch active"));}
    @Override public void frame(Bitmap b){runOnUiThread(()->surface.setFrame(b));}
    @Override public void error(String m){runOnUiThread(()->status.setText(m+" • Guest log is available in app-private storage."));}
    @Override public boolean onKeyDown(int code,KeyEvent e){int k=keySym(e);if(client!=null&&k!=0){client.key(k,true);return true;}return super.onKeyDown(code,e);}
    @Override public boolean onKeyUp(int code,KeyEvent e){int k=keySym(e);if(client!=null&&k!=0){client.key(k,false);return true;}return super.onKeyUp(code,e);}
    private int keySym(KeyEvent e){switch(e.getKeyCode()){case KeyEvent.KEYCODE_ENTER:return 0xff0d;case KeyEvent.KEYCODE_DEL:return 0xff08;case KeyEvent.KEYCODE_TAB:return 0xff09;case KeyEvent.KEYCODE_BACK:case KeyEvent.KEYCODE_ESCAPE:return 0xff1b;case KeyEvent.KEYCODE_DPAD_LEFT:return 0xff51;case KeyEvent.KEYCODE_DPAD_UP:return 0xff52;case KeyEvent.KEYCODE_DPAD_RIGHT:return 0xff53;case KeyEvent.KEYCODE_DPAD_DOWN:return 0xff54;default:return e.getUnicodeChar();}}
    @Override protected void onDestroy(){if(client!=null)client.close();super.onDestroy();}
    private final class GuestSurface extends View{
        private final Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);private Bitmap image;private float scale,ox,oy;private boolean down;
        GuestSurface(Context c){super(c);setFocusable(true);setFocusableInTouchMode(true);}
        @Override public boolean onCheckIsTextEditor() { return true; }
        @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
            attrs.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            attrs.imeOptions=EditorInfo.IME_ACTION_NONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            return new BaseInputConnection(this,false) {
                @Override public boolean commitText(CharSequence text,int cursor) {
                    if(client!=null) for(int i=0;i<text.length();i++){int ch=text.charAt(i);client.key(ch,true);client.key(ch,false);}
                    return true;
                }
                @Override public boolean deleteSurroundingText(int before,int after) {
                    if(client!=null) for(int i=0;i<Math.max(1,before);i++){client.key(0xff08,true);client.key(0xff08,false);}
                    return true;
                }
                @Override public boolean sendKeyEvent(KeyEvent event) {
                    int sym=keySym(event);if(client!=null&&sym!=0)client.key(sym,event.getAction()==KeyEvent.ACTION_DOWN);
                    return true;
                }
            };
        }
        void setFrame(Bitmap b){image=b;invalidate();}
        @Override protected void onDraw(Canvas c){c.drawColor(0xff000000);if(image==null)return;scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;ox=(getWidth()-w)/2;oy=(getHeight()-h)/2;c.drawBitmap(image,null,new RectF(ox,oy,ox+w,oy+h),p);}
        @Override public boolean onTouchEvent(MotionEvent e){if(client==null||image==null||scale<=0)return true;int x=(int)((e.getX()-ox)/scale),y=(int)((e.getY()-oy)/scale);x=Math.max(0,Math.min(image.getWidth()-1,x));y=Math.max(0,Math.min(image.getHeight()-1,y));
            if(e.getAction()==MotionEvent.ACTION_DOWN){down=true;client.pointer(x,y,true);return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&down){client.pointer(x,y,true);return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){down=false;client.pointer(x,y,false);performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}