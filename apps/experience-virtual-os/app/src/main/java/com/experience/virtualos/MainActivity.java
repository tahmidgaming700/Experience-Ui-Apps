package com.experience.virtualos;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int PICK_GUEST_IMAGE = 41;
    private static final String PREFS = "guest_storage";
    private static final String KEY_URI = "guest_uri";
    private TextView imageStatus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackgroundColor(0xFF202938);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        box.setLayoutParams(params);
        return box;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(28), dp(20), dp(24));
        page.setBackgroundColor(0xFF101722);

        page.addView(text("EXPERIENCE UI", 12, 0xFF9DBBFF, true));
        TextView title = text("Virtual OS", 32, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(6);
        titleParams.bottomMargin = dp(6);
        page.addView(title, titleParams);
        page.addView(text("Explore guest operating systems without unlocking your bootloader or rooting your phone.",
                15, 0xFFCAD3E0, false));

        LinearLayout status = card();
        status.addView(text("ROOTLESS MODE", 12, 0xFF9DBBFF, true));
        status.addView(text("No root access requested", 18, 0xFFFFFFFF, true));
        status.addView(text("The app runs as a normal Android application. It will not replace Android, stop system services or modify system partitions.",
                14, 0xFFCAD3E0, false));
        addGap(status, 10);
        status.addView(text("Runtime: not connected yet", 13, 0xFFFFD18A, true));
        page.addView(status);

        LinearLayout guest = card();
        guest.addView(text("GUEST IMAGE", 12, 0xFF9DBBFF, true));
        imageStatus = text(readSavedImage(), 14, 0xFFCAD3E0, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(8);
        statusParams.bottomMargin = dp(12);
        guest.addView(imageStatus, statusParams);
        Button importButton = new Button(this);
        importButton.setText("Import OS image");
        importButton.setOnClickListener(v -> chooseImage());
        guest.addView(importButton);
        page.addView(guest);

        LinearLayout compatibility = card();
        compatibility.addView(text("DEVICE COMPATIBILITY", 12, 0xFF9DBBFF, true));
        compatibility.addView(text("Android 9+ • ARM64 preferred • Huawei research target",
                16, 0xFFFFFFFF, true));
        compatibility.addView(text("Huawei and Samsung support must be validated per model. Importing an image does not mean it can boot on this device.",
                14, 0xFFCAD3E0, false));
        page.addView(compatibility);

        LinearLayout safety = card();
        safety.addView(text("WHAT THIS VERSION CAN DO", 12, 0xFF9DBBFF, true));
        safety.addView(text("• Run without root or bootloader access\n• Import and remember a guest image\n• Keep guest files separate from Android",
                14, 0xFFCAD3E0, false));
        addGap(safety, 8);
        safety.addView(text("What it cannot do yet: boot a second Android system. A compatible virtual-machine runtime and guest boot pipeline are still required.",
                14, 0xFFFFD18A, false));
        page.addView(safety);

        page.addView(text("Experience Virtual OS • 0.1.0-alpha01",
                12, 0xFF8794A7, false));
        scroll.addView(page);
        setContentView(scroll);
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private String readSavedImage() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URI, null);
        if (value == null) return "No image imported. Select a guest image stored on your device.";
        Uri uri = Uri.parse(value);
        String name = uri.getLastPathSegment();
        return "Selected: " + (name == null ? uri.toString() : name)
                + "\nStored as a document reference. Boot support is not available in this alpha.";
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/octet-stream", "application/x-gzip", "application/zip"
        });
        startActivityForResult(intent, PICK_GUEST_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GUEST_IMAGE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit().putString(KEY_URI, uri.toString()).apply();
            if (imageStatus != null) imageStatus.setText(readSavedImage());
            Toast.makeText(this, "Image reference saved", Toast.LENGTH_SHORT).show();
        } catch (SecurityException exception) {
            Toast.makeText(this, "Could not retain access to this file", Toast.LENGTH_LONG).show();
        }
    }
}
