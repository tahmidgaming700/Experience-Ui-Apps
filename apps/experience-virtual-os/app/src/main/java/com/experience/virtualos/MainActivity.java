package com.experience.virtualos;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String PREFS = "experience_virtual_os";
    private static final int PICK_IMAGE = 41;
    private static final String[] API_LEVELS = {"Android 9 (API 28)", "Android 10 (API 29)",
            "Android 11 (API 30)", "Android 12 (API 31)", "Android 12L (API 32)", "Android 13 (API 33)"};
    private static final String[] IMAGE_URLS = {
            "https://dl.google.com/android/repository/sys-img/google_apis_playstore/arm64-v8a-28_r01.zip",
            "https://dl.google.com/android/repository/sys-img/google_apis_playstore/arm64-v8a-29_r09-darwin.zip",
            "https://dl.google.com/android/repository/sys-img/google_apis_playstore/arm64-v8a-30_r10-darwin.zip",
            "https://dl.google.com/android/repository/sys-img/google_apis_playstore/arm64-v8a-31_r06-darwin.zip",
            "https://dl.google.com/android/repository/sys-img/google_apis_playstore/arm64-v8a-32_r03-darwin.zip",
            "https://dl.google.com/android/repository/sys-img/google_apis_playstore/arm64-v8a-33_r06-darwin.zip"
    };

    private SharedPreferences prefs;
    private LinearLayout page;
    private int wizardStep;
    private String selectedLanguage = "English";
    private int selectedApi = 0;
    private boolean guestRoot = false;
    private int selectedMemory = 1024;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("setup_complete", false)) showWizard();
        else showHome();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void base(String eyebrow, String heading, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(30), dp(22), dp(28));
        page.setBackgroundColor(0xFF0D1420);
        page.addView(label("EXPERIENCE UI  /  VIRTUAL OS", 11, 0xFF9DBBFF, true));
        TextView title = label(heading, 30, 0xFFF5F7FC, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(14);
        page.addView(title, tp);
        TextView sub = label(subtitle, 15, 0xFFB8C4D8, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8);
        sp.bottomMargin = dp(22);
        page.addView(sub, sp);
        scroll.addView(page);
        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(17), dp(16), dp(17), dp(16));
        c.setBackground(shape(0xFF192536, 22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(13);
        page.addView(c, p);
        return c;
    }

    private void gap(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(0xFF101827);
        b.setBackground(shape(0xFFB8CCFF, 18));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
        p.topMargin = dp(9);
        page.addView(b, p);
        return b;
    }

    private Spinner spinner(String[] values, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(a);
        s.setSelection(selected);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
        p.topMargin = dp(8);
        page.addView(s, p);
        return s;
    }

    private void showWizard() {
        wizardStep = 0;
        renderWizard();
    }

    private void renderWizard() {
        if (wizardStep == 0) {
            base("WELCOME", "A second Android. Your way.", "Set up your private guest environment. Your Huawei system stays installed and untouched.");
            LinearLayout c = card();
            c.addView(label("01  LANGUAGE", 12, 0xFF9DBBFF, true));
            c.addView(label("Choose the language for Experience Virtual OS.", 15, 0xFFE8EEF8, false));
            Spinner languages = spinner(new String[]{"English", "বাংলা", "العربية", "Español", "Français"}, 0);
            button("Continue", () -> {
                selectedLanguage = languages.getSelectedItem().toString();
                wizardStep = 1;
                renderWizard();
            });
        } else if (wizardStep == 1) {
            base("YOUR ENVIRONMENT", "Configure your OS", "Choose an Android guest image and how you want the virtual system configured.");
            LinearLayout c = card();
            c.addView(label("02  ANDROID VERSION", 12, 0xFF9DBBFF, true));
            c.addView(label("Google ARM64 emulator images", 17, 0xFFF5F7FC, true));
            Spinner versions = spinner(API_LEVELS, selectedApi);
            LinearLayout memory = card();
            memory.addView(label("MEMORY ALLOCATION", 12, 0xFF9DBBFF, true));
            Spinner ram = spinner(new String[]{"512 MB", "1 GB", "1.5 GB", "2 GB"}, 1);
            Switch root = new Switch(this);
            root.setText("Enable root inside guest OS (image permitting)");
            root.setTextColor(0xFFE8EEF8);
            root.setChecked(guestRoot);
            LinearLayout rootCard = card();
            rootCard.addView(root);
            rootCard.addView(label("This setting is for the virtual Android only. It never grants root to Huawei/host Android. Google Play images may not support guest root.", 13, 0xFFB8C4D8, false));
            button("Continue", () -> {
                selectedApi = versions.getSelectedItemPosition();
                String r = ram.getSelectedItem().toString();
                selectedMemory = r.equals("512 MB") ? 512 : r.equals("1 GB") ? 1024 : r.equals("1.5 GB") ? 1536 : 2048;
                guestRoot = root.isChecked();
                wizardStep = 2;
                renderWizard();
            });
            button("Back", () -> { wizardStep = 0; renderWizard(); });
        } else {
            base("READY TO BEGIN", "Your space is ready.", "You can add an Android system image now, or change these settings later.");
            LinearLayout c = card();
            c.addView(label("DEVICE", 12, 0xFF9DBBFF, true));
            c.addView(label(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, 18, 0xFFF5F7FC, true));
            c.addView(label("Android " + android.os.Build.VERSION.RELEASE + " • API " + android.os.Build.VERSION.SDK_INT
                    + " • " + (isArm64() ? "ARM64" : android.os.Build.CPU_ABI), 13, 0xFFB8C4D8, false));
            LinearLayout cfg = card();
            cfg.addView(label("GUEST CONFIGURATION", 12, 0xFF9DBBFF, true));
            cfg.addView(label(API_LEVELS[selectedApi] + " • " + selectedMemory + " MB RAM", 16, 0xFFF5F7FC, true));
            cfg.addView(label("Guest root: " + (guestRoot ? "requested if supported by image" : "off"), 13, 0xFFB8C4D8, false));
            button("Finish setup", () -> {
                prefs.edit().putBoolean("setup_complete", true)
                        .putString("language", selectedLanguage).putInt("api", selectedApi)
                        .putInt("memory", selectedMemory).putBoolean("guest_root", guestRoot).apply();
                showHome();
            });
            button("Back", () -> { wizardStep = 1; renderWizard(); });
        }
    }

    private boolean isArm64() {
        for (String abi : android.os.Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) return true;
        return false;
    }

    private void showHome() {
        base("YOUR ENVIRONMENTS", "Virtual OS", "A separate Android workspace, managed from one place.");
        LinearLayout status = card();
        status.addView(label("ROOTLESS HOST", 12, 0xFF9DBBFF, true));
        status.addView(label("Huawei Android remains untouched", 18, 0xFFF5F7FC, true));
        status.addView(label("No host root, bootloader unlock or system partition access is requested.", 14, 0xFFB8C4D8, false));
        LinearLayout config = card();
        config.addView(label("CURRENT GUEST PROFILE", 12, 0xFF9DBBFF, true));
        config.addView(label(API_LEVELS[prefs.getInt("api", 0)] + " • " + prefs.getInt("memory", 1024) + " MB", 17, 0xFFF5F7FC, true));
        config.addView(label("Guest root: " + (prefs.getBoolean("guest_root", false) ? "requested (image-dependent)" : "off"), 13, 0xFFB8C4D8, false));
        LinearLayout sources = card();
        sources.addView(label("ANDROID SYSTEM LIBRARY", 12, 0xFF9DBBFF, true));
        sources.addView(label("Google ARM64 • API 28–33", 18, 0xFFF5F7FC, true));
        sources.addView(label("Download an official SDK system-image package. The package still needs a compatible emulator runtime and may not boot on this phone.", 13, 0xFFB8C4D8, false));
        for (int i = 0; i < API_LEVELS.length; i++) {
            final int index = i;
            Button download = new Button(this);
            download.setText("Download " + API_LEVELS[i]);
            download.setAllCaps(false);
            download.setTextColor(0xFFE8EEF8);
            download.setBackground(shape(0xFF26374D, 15));
            download.setOnClickListener(v -> downloadImage(index));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
            p.topMargin = dp(7);
            sources.addView(download, p);
        }
        button("Import another guest image", this::chooseImage);
        button("Change guest configuration", () -> {
            selectedApi = prefs.getInt("api", 0);
            selectedMemory = prefs.getInt("memory", 1024);
            guestRoot = prefs.getBoolean("guest_root", false);
            wizardStep = 1;
            renderWizard();
        });
        LinearLayout runtime = card();
        runtime.addView(label("VIRTUAL MACHINE STATUS", 12, 0xFF9DBBFF, true));
        runtime.addView(label("VM engine not integrated yet", 17, 0xFFFFD18A, true));
        runtime.addView(label("This build can configure the app and download/import images. It does not claim to boot Android guests until the QEMU runtime, Android machine profile, display, input and networking are integrated and tested.", 13, 0xFFB8C4D8, false));
        button("Restart setup wizard", () -> {
            prefs.edit().putBoolean("setup_complete", false).apply();
            showWizard();
        });
        page.addView(label("Experience Virtual OS • alpha", 12, 0xFF7F8CA2, false));
    }

    private void downloadImage(int index) {
        try {
            String filename = "experience-android-api-" + (28 + index) + "-arm64.zip";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(IMAGE_URLS[index]));
            request.setTitle("Experience Virtual OS — " + API_LEVELS[index]);
            request.setDescription("Downloading official Google ARM64 SDK image package");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager manager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
            long id = manager.enqueue(request);
            prefs.edit().putLong("download_" + index, id).apply();
            Toast.makeText(this, "Download queued. Check the notification shade for progress.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start download: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("imported_image", uri.toString()).apply();
                Toast.makeText(this, "Guest image reference saved.", Toast.LENGTH_SHORT).show();
            } catch (SecurityException e) {
                Toast.makeText(this, "Could not retain access to this file.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
