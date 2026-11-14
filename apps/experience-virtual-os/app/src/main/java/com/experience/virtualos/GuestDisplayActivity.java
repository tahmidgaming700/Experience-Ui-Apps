package com.experience.virtualos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shows the guest framebuffer served by QEMU's VNC server on 127.0.0.1 and
 * forwards touch (as an absolute pointer) and keyboard input. What is drawn is
 * exactly what the guest renders; nothing is simulated.
 */
public final class GuestDisplayActivity extends Activity implements RfbClient.Listener {
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_KEEP_SCREEN_ON = "keep_screen_on";

    private RfbClient client;
    private GuestSurface surface;
    private TextView status;

    public static Intent intent(Context context, int port, String title, boolean keepScreenOn) {
        return new Intent(context, GuestDisplayActivity.class)
                .putExtra(EXTRA_PORT, port)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_KEEP_SCREEN_ON, keepScreenOn);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int port = getIntent().getIntExtra(EXTRA_PORT, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (getIntent().getBooleanExtra(EXTRA_KEEP_SCREEN_ON, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff000000);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(pad * 2, pad, pad, pad);
        bar.setBackgroundColor(0xff16181d);
        TextView name = new TextView(this);
        name.setText(title == null ? "Guest display" : title);
        name.setTextColor(0xffe3e2e9);
        name.setTextSize(15);
        bar.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button keyboard = new Button(this);
        keyboard.setText("Keyboard");
        keyboard.setAllCaps(false);
        keyboard.setContentDescription("Show the on-screen keyboard for the guest");
        keyboard.setOnClickListener(v -> {
            surface.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(surface, InputMethodManager.SHOW_IMPLICIT);
        });
        bar.addView(keyboard);
        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setContentDescription("Close the display. The virtual device keeps running.");
        close.setOnClickListener(v -> finish());
        bar.addView(close);
        root.addView(bar);

        status = new TextView(this);
        status.setTextColor(0xffc5c6d0);
        status.setTextSize(13);
        status.setPadding(pad * 2, pad / 2, pad * 2, pad / 2);
        status.setText("Connecting to QEMU display on 127.0.0.1:" + port + "…");
        root.addView(status);

        surface = new GuestSurface(this);
        surface.setContentDescription("Guest framebuffer");
        root.addView(surface, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        client = new RfbClient(this, port);
        new Thread(client::run, "evos-vnc").start();
    }

    @Override public void connected(int w, int h) {
        runOnUiThread(() -> status.setText("Framebuffer " + w + " × " + h + " · touch acts as a pointer · closing leaves the device running"));
    }

    @Override public void frame(Bitmap b) {
        runOnUiThread(() -> surface.setFrame(b));
    }

    @Override public void error(String m) {
        runOnUiThread(() -> status.setText("Display unavailable: " + m + ". Check the device console for QEMU and guest messages."));
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        int k = keySym(e);
        if (client != null && k != 0) { client.key(k, true); return true; }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        int k = keySym(e);
        if (client != null && k != 0) { client.key(k, false); return true; }
        return super.onKeyUp(code, e);
    }

    private static int keySym(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_ENTER: return 0xff0d;
            case KeyEvent.KEYCODE_DEL: return 0xff08;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 0xffff;
            case KeyEvent.KEYCODE_TAB: return 0xff09;
            case KeyEvent.KEYCODE_ESCAPE: return 0xff1b;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 0xff51;
            case KeyEvent.KEYCODE_DPAD_UP: return 0xff52;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 0xff53;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 0xff54;
            case KeyEvent.KEYCODE_BACK: return 0;
            default: return e.getUnicodeChar();
        }
    }

    @Override protected void onDestroy() {
        if (client != null) client.close();
        super.onDestroy();
    }

    private final class GuestSurface extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dest = new RectF();
        private Bitmap image;
        private float scale, ox, oy;

        GuestSurface(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        @Override public boolean onCheckIsTextEditor() { return true; }

        @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
            attrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            attrs.imeOptions = EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            return new BaseInputConnection(this, false) {
                @Override public boolean commitText(CharSequence text, int cursor) {
                    if (client != null) for (int i = 0; i < text.length(); i++) {
                        int ch = text.charAt(i);
                        if (ch == '\n') ch = 0xff0d;
                        client.key(ch, true);
                        client.key(ch, false);
                    }
                    return true;
                }

                @Override public boolean deleteSurroundingText(int before, int after) {
                    if (client != null) for (int i = 0; i < Math.max(1, before); i++) {
                        client.key(0xff08, true);
                        client.key(0xff08, false);
                    }
                    return true;
                }

                @Override public boolean sendKeyEvent(KeyEvent event) {
                    int sym = keySym(event);
                    if (client != null && sym != 0) client.key(sym, event.getAction() == KeyEvent.ACTION_DOWN);
                    return true;
                }
            };
        }

        void setFrame(Bitmap b) {
            image = b;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xff000000);
            if (image == null) return;
            scale = Math.min(getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight());
            float w = image.getWidth() * scale, h = image.getHeight() * scale;
            ox = (getWidth() - w) / 2;
            oy = (getHeight() - h) / 2;
            dest.set(ox, oy, ox + w, oy + h);
            c.drawBitmap(image, null, dest, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (client == null || image == null || scale <= 0) return true;
            int x = Math.max(0, Math.min(image.getWidth() - 1, (int) ((e.getX() - ox) / scale)));
            int y = Math.max(0, Math.min(image.getHeight() - 1, (int) ((e.getY() - oy) / scale)));
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    client.pointer(x, y, true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    client.pointer(x, y, false);
                    performClick();
                    break;
                default:
                    break;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
