package com.experience.virtualos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class MainActivity extends Activity {
    private static final String PREFS = "experience_virtual_os";
    private static final int PICK_IMAGE = 41;
    private static final int[] API_CODES = {24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
    private static final String[] API_LEVELS = {
            "Android 7.0 (API 24)", "Android 7.1 (API 25)", "Android 8.0 (API 26)", "Android 8.1 (API 27)",
            "Android 9 (API 28)", "Android 10 (API 29)", "Android 11 (API 30)", "Android 12 (API 31)",
            "Android 12L (API 32)", "Android 13 (API 33)", "Android 14 (API 34)", "Android 15 (API 35)",
            "Android 16 (API 36)", "Android 17 (API 37)"};

    private static int apiCode(int selection) {
        return API_CODES[Math.max(0, Math.min(API_CODES.length - 1, selection))];
    }

    private SharedPreferences prefs;
    private LinearLayout page;
    private LinearLayout root;
    private int wizardStep;
    private String selectedLanguage = "English";
    private int selectedApi = 0;
    private boolean guestRoot = false;
    private int selectedMemory = 1024;
    private TextView updateState;
    private TextView updateNotes;
    private String latestReleaseTag = "";
    private String latestApkUrl = "";
    private long updateDownloadId = -1L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(34));
        page.setBackgroundColor(0xFFF7F2FA);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Experience Virtual OS");
        toolbar.setTitleTextColor(0xFF292431);
        toolbar.setSubtitle(eyebrow);
        toolbar.setSubtitleTextColor(0xFF655B70);
        toolbar.setBackgroundColor(0xFFF7F2FA);
        toolbar.setElevation(0);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));

        TextView title = label(heading, 29, 0xFF211B2B, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(18);
        page.addView(title, tp);
        TextView sub = label(subtitle, 15, 0xFF625B71, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(7);
        sp.bottomMargin = dp(20);
        page.addView(sub, sp);
        scroll.addView(page);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF7F2FA);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private LinearLayout card() {
        MaterialCardView shell = new MaterialCardView(this);
        shell.setCardBackgroundColor(0xFFFFFBFF);
        shell.setRadius(dp(26));
        shell.setCardElevation(0);
        shell.setStrokeWidth(dp(1));
        shell.setStrokeColor(0xFFE9E1EE);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(17), dp(18), dp(17));
        shell.addView(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(13);
        page.addView(shell, p);
        return c;
    }
    private void gap(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private Button button(String text, Runnable action) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setTextSize(15);
        b.setCornerRadius(dp(18));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF6750A4));
        b.setTextColor(0xFFFFFFFF);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54));
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
            c.addView(label("01  LANGUAGE", 12, 0xFF6750A4, true));
            c.addView(label("Choose the language for Experience Virtual OS.", 15, 0xFF514568, false));
            Spinner languages = spinner(new String[]{"English", "বাংলা", "العربية", "Español", "Français"}, 0);
            button("Continue", () -> {
                selectedLanguage = languages.getSelectedItem().toString();
                wizardStep = 1;
                renderWizard();
            });
        } else if (wizardStep == 1) {
            base("YOUR ENVIRONMENT", "Configure your OS", "Choose the Android release and resources for your private virtual device.");
            LinearLayout c = card();
            c.addView(label("02  ANDROID VERSION", 12, 0xFF6750A4, true));
            c.addView(label("Android guest releases", 17, 0xFF211B2B, true));
            c.addView(label("Android 17 is API 37. Experience Virtual OS uses Google's SDK package manager to resolve and install available platform and ARM64 guest-image packages.", 13, 0xFF625B71, false));
            Spinner versions = spinner(API_LEVELS, selectedApi);
            LinearLayout memory = card();
            memory.addView(label("MEMORY ALLOCATION", 12, 0xFF6750A4, true));
            Spinner ram = spinner(new String[]{"512 MB", "1 GB", "1.5 GB", "2 GB"}, 1);
            Switch root = new Switch(this);
            root.setText("Enable root inside guest OS (image permitting)");
            root.setTextColor(0xFF514568);
            root.setChecked(guestRoot);
            LinearLayout rootCard = card();
            rootCard.addView(root);
            rootCard.addView(label("This setting is for the virtual Android only. It never grants root to Huawei/host Android. Google Play images may not support guest root.", 13, 0xFF625B71, false));
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
            base("READY TO BEGIN", "Your virtual device is configured.", "Experience Virtual OS will now prepare its private system environment. Your phone Android remains unchanged.");
            LinearLayout c = card();
            c.addView(label("DEVICE", 12, 0xFF6750A4, true));
            c.addView(label(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, 18, 0xFF211B2B, true));
            c.addView(label("Android " + android.os.Build.VERSION.RELEASE + " • API " + android.os.Build.VERSION.SDK_INT
                    + " • " + (isArm64() ? "ARM64" : android.os.Build.CPU_ABI), 13, 0xFF625B71, false));
            LinearLayout cfg = card();
            cfg.addView(label("GUEST CONFIGURATION", 12, 0xFF6750A4, true));
            cfg.addView(label(API_LEVELS[selectedApi] + " • " + selectedMemory + " MB RAM", 16, 0xFF211B2B, true));
            cfg.addView(label("Guest root: " + (guestRoot ? "requested if supported by image" : "off"), 13, 0xFF625B71, false));
            button("Finish setup", () -> {
                prefs.edit().putBoolean("setup_complete", true)
                        .putString("language", selectedLanguage).putInt("api", selectedApi)
                        .putInt("memory", selectedMemory).putBoolean("guest_root", guestRoot).apply();
                beginEnvironmentSetup();
            });
            button("Back", () -> { wizardStep = 1; renderWizard(); });
        }
    }

    private boolean isArm64() {
        for (String abi : android.os.Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) return true;
        return false;
    }

    private void showHome() {
        base("VIRTUAL MACHINE LIBRARY", "Your virtual machines", "Create, organise and run guest systems from your phone.");
        LinearLayout overview = card();
        overview.addView(label("DEVICE STATUS", 12, 0xFF6750A4, true));
        overview.addView(label(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, 20, 0xFF211B2B, true));
        overview.addView(label("Android " + android.os.Build.VERSION.RELEASE + "  •  " +
                (isArm64() ? "ARM64 host" : android.os.Build.CPU_ABI) + "  •  Rootless mode", 13, 0xFF625B71, false));

        LinearLayout vm = card();
        vm.addView(label("ANDROID VIRTUAL DEVICE", 12, 0xFF6750A4, true));
        vm.addView(label(API_LEVELS[Math.max(0, Math.min(API_LEVELS.length - 1, prefs.getInt("api", 0)))]
                + "  •  " + prefs.getInt("memory", 1024) + " MB RAM", 18, 0xFF211B2B, true));
        vm.addView(label(GuestVmManager.isRunning() ? "Running • guest process active" : "Stopped • ready when its runtime and image are installed",
                13, 0xFF625B71, false));
        cardButton(vm, "Start virtual machine", this::startAndroidGuest);
        cardButton(vm, "Open guest display", this::openGuestDisplay);
        cardButton(vm, "Configure Android guest", () -> {
            selectedApi = prefs.getInt("api", 0);
            selectedMemory = prefs.getInt("memory", 1024);
            guestRoot = prefs.getBoolean("guest_root", false);
            wizardStep = 1;
            renderWizard();
        });

        LinearLayout alpine = card();
        alpine.addView(label("ALPINE LINUX RUNTIME", 12, 0xFF6750A4, true));
        alpine.addView(label(AlpineEnvironment.isInstalled(this) ? "Alpine base system installed" : "Alpine setup required",
                18, 0xFF211B2B, true));
        alpine.addView(label("Private Alpine ARM64 userspace • PRoot • QEMU • no host root", 13, 0xFF625B71, false));
        cardButton(alpine, "Set up Alpine + QEMU engines", this::installGuestEngine);
        cardButton(alpine, "Alpine package maintenance", this::updateAlpinePackages);

        LinearLayout library = card();
        library.addView(label("IMAGE LIBRARY", 12, 0xFF6750A4, true));
        String selected = prefs.getString("imported_image", "");
        library.addView(label(selected.isEmpty() ? "No external image selected" : "An image reference is saved",
                17, 0xFF211B2B, true));
        library.addView(label("Import disk images and installation media from Android Files.", 13, 0xFF625B71, false));
        cardButton(library, "Open image library", () -> showLibrary());

        addNavigation(1);
    }

    private void addNavigation(int selectedId) {
        if (root == null) return;
        BottomNavigationView navigation = new BottomNavigationView(this);
        navigation.setBackgroundColor(0xFFFFFBFF);
        navigation.getMenu().add(0, 1, 0, "Home").setIcon(android.R.drawable.ic_menu_view);
        navigation.getMenu().add(0, 2, 1, "Library").setIcon(android.R.drawable.ic_menu_agenda);
        navigation.getMenu().add(0, 3, 2, "Tools").setIcon(android.R.drawable.ic_menu_manage);
        navigation.getMenu().add(0, 4, 3, "Settings").setIcon(android.R.drawable.ic_menu_preferences);
        navigation.setSelectedItemId(selectedId);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == 1) showHome();
            else if (item.getItemId() == 2) showLibrary();
            else if (item.getItemId() == 3) showTools();
            else if (item.getItemId() == 4) showSettings();
            return true;
        });
        root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showLibrary() {
        base("IMAGE AND SYSTEM LIBRARY", "Your library", "Manage imported media and Android guest packages.");
        LinearLayout images = card();
        images.addView(label("IMPORTED IMAGES", 12, 0xFF6750A4, true));
        String uri = prefs.getString("imported_image", "");
        images.addView(label(uri.isEmpty() ? "No image imported yet" : uri, 14, 0xFF211B2B, false));
        cardButton(images, "Import disk image or ISO", this::chooseImage);
        if (!uri.isEmpty()) cardButton(images, "Forget imported image reference", () -> {
            prefs.edit().remove("imported_image").apply();
            showLibrary();
        });

        LinearLayout android = card();
        android.addView(label("ANDROID SYSTEM IMAGES", 12, 0xFF6750A4, true));
        int api = apiCode(prefs.getInt("api", 0));
        File imageDir = new File(SdkManagerBootstrap.sdkRoot(this),
                "system-images/android-" + api + "/google_apis/arm64-v8a");
        android.addView(label("Android API " + api, 18, 0xFF211B2B, true));
        android.addView(label(imageDir.isDirectory() ? "System-image directory found" : "System image not installed",
                13, 0xFF625B71, false));
        cardButton(android, "Install selected Android system image", this::installSdkManager);
        addNavigation(2);
    }

    private void showTools() {
        base("RUNTIME AND UTILITIES", "Tools", "Manage the private Linux foundation and virtual-machine engine.");
        LinearLayout alpine = card();
        alpine.addView(label("ALPINE LINUX", 12, 0xFF6750A4, true));
        alpine.addView(label(AlpineEnvironment.isInstalled(this) ? "Installed in app-private storage" : "Not installed",
                18, 0xFF211B2B, true));
        cardButton(alpine, "Install / repair Alpine and QEMU", this::installGuestEngine);
        cardButton(alpine, "Update Alpine packages", this::updateAlpinePackages);

        LinearLayout sdk = card();
        sdk.addView(label("ANDROID SDK MANAGER", 12, 0xFF6750A4, true));
        sdk.addView(label("Install platform tools and the selected ARM64 system image.", 14, 0xFF625B71, false));
        cardButton(sdk, "Install Android SDK + system image", this::installSdkManager);

        LinearLayout diagnostics = card();
        diagnostics.addView(label("DIAGNOSTICS", 12, 0xFF6750A4, true));
        cardButton(diagnostics, "View QEMU runtime log", this::showGuestLog);
        cardButton(diagnostics, "Stop virtual machine", () -> {
            GuestVmManager.stop();
            Toast.makeText(this, "Virtual machine stopped.", Toast.LENGTH_SHORT).show();
            showTools();
        });
        addNavigation(3);
    }

    private void showSettings() {
        base("PREFERENCES AND DEVICE INFORMATION", "Settings", "Configure the guest and inspect host compatibility.");
        LinearLayout profile = card();
        profile.addView(label("GUEST PROFILE", 12, 0xFF6750A4, true));
        profile.addView(label(API_LEVELS[Math.max(0, Math.min(API_LEVELS.length - 1, prefs.getInt("api", 0)))]
                + "  •  " + prefs.getInt("memory", 1024) + " MB RAM", 17, 0xFF211B2B, true));
        profile.addView(label("Guest root: " + (prefs.getBoolean("guest_root", false) ? "requested if supported" : "off"),
                13, 0xFF625B71, false));
        cardButton(profile, "Change guest configuration", () -> {
            selectedApi = prefs.getInt("api", 0);
            selectedMemory = prefs.getInt("memory", 1024);
            guestRoot = prefs.getBoolean("guest_root", false);
            wizardStep = 1;
            renderWizard();
        });

        LinearLayout host = card();
        host.addView(label("HOST COMPATIBILITY", 12, 0xFF6750A4, true));
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(this);
        host.addView(label(capabilities.deviceSummary(), 15, 0xFF211B2B, true));
        host.addView(label(capabilities.accelerationSummary(), 13, 0xFF625B71, false));
        host.addView(label("KVM visibility is diagnostic only. No host root or bootloader modification is requested.",
                12, 0xFF8C4A00, false));

        LinearLayout updates = card();
        updates.addView(label("EXPERIENCE VIRTUAL OS", 12, 0xFF6750A4, true));
        updates.addView(label("Version " + getVersionName(), 14, 0xFF625B71, false));
        cardButton(updates, "Check for updates", this::checkForUpdates);
        cardButton(updates, "View GitHub releases", this::openReleases);
        cardButton(updates, "Restart setup wizard", () -> {
            prefs.edit().putBoolean("setup_complete", false).apply();
            showWizard();
        });
        addNavigation(4);
    }

    private Button cardButton(LinearLayout parent, String text, Runnable action) {
        MaterialButton b = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setTextSize(14);
        b.setCornerRadius(dp(16));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFB8AFC4));
        b.setTextColor(0xFF514568);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(8);
        parent.addView(b, p);
        return b;
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void installGuestEngine() {
        showRuntimeProgress("Installing ARM64 virtual machine engine", "Preparing the Android Emulator engine inside the private environment…", () -> {
            try {
                GuestVmManager.installEngine(this);
                mainHandler.post(() -> { Toast.makeText(this, "Virtual machine engine installed.", Toast.LENGTH_LONG).show(); showHome(); });
            } catch (Exception e) {
                mainHandler.post(() -> showRuntimeError("Engine installation failed", e));
            }
        });
    }

    private void updateAlpinePackages() {
        showRuntimeProgress("Updating Alpine packages",
                "Refreshing Alpine package indexes and applying available updates…", () -> {
            try {
                String release = AlpineRuntime.run(this,
                        "apk update && apk upgrade --available && cat /etc/alpine-release");
                mainHandler.post(() -> {
                    new AlertDialog.Builder(this).setTitle("Alpine is up to date")
                            .setMessage("Installed Alpine release: " + release.trim())
                            .setPositiveButton("Done", (d, w) -> showHome()).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> showRuntimeError("Alpine update failed", e));
            }
        });
    }

    private void startAndroidGuest() {
        showRuntimeProgress("Starting Android guest", "Preparing the selected SDK image, guest storage and virtual hardware…", () -> {
            try {
                GuestVmManager.start(this, apiCode(prefs.getInt("api", 0)), prefs.getInt("memory", 1024));
                Thread.sleep(2500);
                if (!GuestVmManager.isRunning()) {
                    File log = GuestVmManager.logFile();
                    String tail = readTail(log, 5000);
                    throw new java.io.IOException("QEMU exited before the guest display became available. " + tail);
                }
                mainHandler.post(() -> startActivity(new Intent(this, GuestDisplayActivity.class)));
            } catch (Exception e) {
                mainHandler.post(() -> showRuntimeError("Android guest could not start", e));
            }
        });
    }

    private void openGuestDisplay() {
        if (!GuestVmManager.isRunning()) {
            Toast.makeText(this, "Start the Android guest first.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, GuestDisplayActivity.class));
    }

    private void showGuestLog() {
        File log = GuestVmManager.logFile();
        String output = readTail(log, 12000);
        if (output.isEmpty()) output = "No QEMU log is available yet. Start the Android guest first.";
        new AlertDialog.Builder(this).setTitle("Guest runtime log").setMessage(output)
                .setPositiveButton("Close", null).show();
    }

    private String readTail(File file, int maxChars) {
        if (file == null || !file.isFile()) return "";
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(file, "r")) {
            long length = input.length();
            long start = Math.max(0, length - maxChars);
            input.seek(start);
            byte[] data = new byte[(int) (length - start)];
            input.readFully(data);
            return new String(data, "UTF-8");
        } catch (Exception e) { return e.getMessage() == null ? "" : e.getMessage(); }
    }

    private void showRuntimeProgress(String heading, String initial, Runnable task) {
        base("VIRTUAL DEVICE RUNTIME", heading, "The runtime is managed privately. No shell or terminal is exposed.");
        LinearLayout panel = card();
        TextView stage = label(initial, 16, 0xFF211B2B, true);
        panel.addView(stage);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        panel.addView(progress);
        button("Return to dashboard", this::showHome);
        new Thread(task, "experience-guest-runtime").start();
    }

    private void showRuntimeError(String heading, Exception error) {
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        new AlertDialog.Builder(this).setTitle(heading).setMessage(message)
                .setPositiveButton("OK", (d, w) -> showHome()).show();
    }

    private void installSdkManager() {
        new AlertDialog.Builder(this)
                .setTitle("Android SDK licence")
                .setMessage("The selected Android API platform and system-image packages are distributed under Google's Android SDK licence. Review the terms before continuing. Experience Virtual OS will accept the SDK package licences only after you confirm here.")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Accept and install", (dialog, which) -> beginSdkManagerInstall())
                .show();
    }

    private void beginSdkManagerInstall() {
        base("ANDROID SDK INSTALLER", "Installing Android SDK", "Experience Virtual OS is preparing the selected Android platform and ARM64 system image. No terminal will be shown.");
        LinearLayout panel = card();
        panel.addView(label("INSTALLATION PROGRESS", 12, 0xFF6750A4, true));
        TextView stage = label("Preparing private environment…", 17, 0xFF211B2B, true);
        panel.addView(stage);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(9));
        pp.topMargin = dp(16);
        pp.bottomMargin = dp(12);
        panel.addView(progress, pp);
        TextView details = label("The SDK packages are installed into app-private storage.", 13, 0xFF625B71, false);
        panel.addView(details);
        new Thread(() -> {
            try {
                SdkManagerBootstrap.installSelectedGuest(this, apiCode(prefs.getInt("api", 0)),
                        message -> mainHandler.post(() -> stage.setText(message)));
                mainHandler.post(() -> {
                    progress.setIndeterminate(false);
                    progress.setProgress(100);
                    stage.setText("Android SDK installation completed");
                    details.setText("The selected Android platform and ARM64 Google APIs system-image packages were accepted by SDK Manager.");
                    button("Return to Experience Virtual OS", this::showHome);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progress.setIndeterminate(false);
                    stage.setText("SDK installation needs attention");
                    String message = e.getMessage() == null ? "Unknown installation error" : e.getMessage();
                    details.setText(message.length() > 900 ? message.substring(0, 900) : message);
                    button("Retry SDK installation", this::installSdkManager);
                    button("Return to dashboard", this::showHome);
                });
            }
        }, "experience-sdk-manager").start();
    }

    private void beginEnvironmentSetup() {
        base("EXPERIENCE VIRTUAL OS SETUP", "Preparing your virtual device", "Please keep Experience Virtual OS open while the private system foundation is prepared.");
        LinearLayout panel = card();
        panel.addView(label("INSTALLATION", 12, 0xFF6750A4, true));
        TextView stage = label("Preparing system components…", 17, 0xFF211B2B, true);
        panel.addView(stage);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(9));
        pp.topMargin = dp(18);
        pp.bottomMargin = dp(12);
        panel.addView(progress, pp);
        TextView detail = label("Verifying the private environment. No host system changes are made.", 13, 0xFF625B71, false);
        panel.addView(detail);
        new Thread(() -> {
            try {
                if (!AlpineEnvironment.isInstalled(this)) {
                    mainHandler.post(() -> stage.setText("Installing system foundation…"));
                    AlpineEnvironment.installFromAssets(this);
                }
                if (!AlpineEnvironment.isInstalled(this)) throw new java.io.IOException("System foundation verification failed");
                mainHandler.post(() -> {
                    progress.setIndeterminate(false);
                    progress.setProgress(100);
                    stage.setText("System foundation prepared");
                    detail.setText("Your private base environment is ready. Android guest runtime components will be installed when their device-compatible build is available.");
                    button("Continue to Experience Virtual OS", this::showHome);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progress.setIndeterminate(false);
                    stage.setText("Setup could not be completed");
                    detail.setText("Please try again. " + e.getMessage());
                    button("Retry setup", this::beginEnvironmentSetup);
                });
            }
        }, "experience-system-setup").start();
    }

    private void installAlpine() {
        Toast.makeText(this, "Preparing private system components…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                AlpineEnvironment.installFromAssets(this);
                mainHandler.post(() -> {
                    Toast.makeText(this, "System foundation prepared.", Toast.LENGTH_LONG).show();
                    showHome();
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this,
                        "System preparation failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "alpine-rootfs-installer").start();
    }

    private void checkForUpdates() {
        if (updateState != null) updateState.setText("Checking GitHub Releases…");
        if (updateNotes != null) updateNotes.setText("");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.github.com/repos/tahmidgaming700/Experience-Ui-Apps/releases?per_page=10");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Experience-Virtual-OS-Android");
                int code = connection.getResponseCode();
                if (code == 404) {
                    mainHandler.post(() -> {
                        if (updateState != null) updateState.setText("No published stable release was found yet.");
                        if (updateNotes != null) updateNotes.setText("Publish a GitHub Release with an APK asset to enable in-app updates.");
                    });
                    return;
                }
                if (code < 200 || code >= 300) throw new java.io.IOException("GitHub returned HTTP " + code);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
                reader.close();
                JSONArray releases = new JSONArray(json.toString());
                if (releases.length() == 0) {
                    mainHandler.post(() -> {
                        if (updateState != null) updateState.setText("No published releases were found yet.");
                        if (updateNotes != null) updateNotes.setText("Publish a GitHub Release with an APK asset to enable in-app updates.");
                    });
                    return;
                }
                JSONObject release = releases.getJSONObject(0);
                String tag = release.optString("tag_name", "");
                String name = release.optString("name", tag);
                String body = release.optString("body", "");
                JSONArray assets = release.optJSONArray("assets");
                String apk = "";
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.toLowerCase().endsWith(".apk")) {
                            apk = asset.optString("browser_download_url", "");
                            if (!apk.isEmpty()) break;
                        }
                    }
                }
                final String foundApk = apk;
                final String foundTag = tag;
                final String foundName = name;
                final String notes = body.length() > 700 ? body.substring(0, 700) + "…" : body;
                latestReleaseTag = tag;
                latestApkUrl = apk;
                boolean newer = isVersionNewer(tag, getVersionName());
                mainHandler.post(() -> {
                    if (updateState != null) {
                        if (newer && !foundApk.isEmpty()) updateState.setText("Update available: " + foundName + " (" + foundTag + ")");
                        else if (newer) updateState.setText("Release " + foundTag + " exists, but it has no APK asset yet.");
                        else updateState.setText("You're up to date. Latest release: " + foundTag);
                    }
                    if (updateNotes != null) updateNotes.setText(notes.isEmpty() ? "No release notes supplied." : notes);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (updateState != null) updateState.setText("Could not check updates: " + e.getMessage());
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "experience-update-check").start();
    }

    private boolean isVersionNewer(String remote, String local) {
        String[] a = remote.replaceFirst("^[vV]", "").split("[-+]", 2);
        String[] b = local.replaceFirst("^[vV]", "").split("[-+]", 2);
        String[] av = a[0].split("\\.");
        String[] bv = b[0].split("\\.");
        for (int i = 0; i < Math.max(av.length, bv.length); i++) {
            int x = i < av.length ? leadingNumber(av[i]) : 0;
            int y = i < bv.length ? leadingNumber(bv[i]) : 0;
            if (x != y) return x > y;
        }
        boolean remotePre = a.length > 1;
        boolean localPre = b.length > 1;
        if (remotePre != localPre) return !remotePre;
        return remotePre && a[1].compareToIgnoreCase(b[1]) > 0;
    }

    private int leadingNumber(String value) {
        String digits = value.replaceFirst("^([0-9]+).*$", "$1");
        try { return Integer.parseInt(digits); } catch (Exception e) { return 0; }
    }

    private void downloadUpdate() {
        if (latestApkUrl == null || latestApkUrl.isEmpty()) {
            Toast.makeText(this, "Check for updates first. A release APK must be published.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(latestApkUrl));
            request.setTitle("Experience Virtual OS " + latestReleaseTag);
            request.setDescription("Downloading update from GitHub Releases");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "experience-virtual-os-" + latestReleaseTag + ".apk");
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            updateDownloadId = manager.enqueue(request);
            prefs.edit().putLong("update_download_id", updateDownloadId).apply();
            Toast.makeText(this, "Update download started.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not download update: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void installDownloadedUpdate() {
        long id = prefs.getLong("update_download_id", -1L);
        if (id < 0) {
            Toast.makeText(this, "No downloaded update is available.", Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Toast.makeText(this, "Update download was not found.", Toast.LENGTH_SHORT).show();
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(this, "The update has not finished downloading.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            Toast.makeText(this, "Allow installs from Experience Virtual OS, then tap Install downloaded update again.", Toast.LENGTH_LONG).show();
            return;
        }
        Uri apk = manager.getUriForDownloadedFile(id);
        if (apk == null) {
            Toast.makeText(this, "Could not open the downloaded APK.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apk, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(install); }
        catch (Exception e) { Toast.makeText(this, "Installer could not open the APK: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void openReleases() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/tahmidgaming700/Experience-Ui-Apps/releases")));
        } catch (Exception e) {
            Toast.makeText(this, "No browser is available.", Toast.LENGTH_SHORT).show();
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
