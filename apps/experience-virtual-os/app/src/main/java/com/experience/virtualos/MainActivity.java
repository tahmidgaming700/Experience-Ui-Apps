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

public final class MainActivity extends Activity {
    private static final String PREFS = "experience_virtual_os";
    private static final int PICK_IMAGE = 41;
    private static final String[] API_LEVELS = {"Android 9 (API 28)", "Android 10 (API 29)",
            "Android 11 (API 30)", "Android 12 (API 31)", "Android 12L (API 32)", "Android 13 (API 33)",
            "Android 14 (API 34)", "Android 15 (API 35)", "Android 16 (API 36)", "Android 17 (API 37)"};

    private SharedPreferences prefs;
    private LinearLayout page;
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
            base("YOUR ENVIRONMENT", "Configure your OS", "Choose the Android release and resources for your private virtual device.");
            LinearLayout c = card();
            c.addView(label("02  ANDROID VERSION", 12, 0xFF9DBBFF, true));
            c.addView(label("Android guest releases", 17, 0xFFF5F7FC, true));
            c.addView(label("Android 17 is API 37. Experience Virtual OS uses Google's SDK package manager to resolve and install available platform and ARM64 guest-image packages.", 13, 0xFFB8C4D8, false));
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
            base("READY TO BEGIN", "Your virtual device is configured.", "Experience Virtual OS will now prepare its private system environment. Your phone Android remains unchanged.");
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
        base("YOUR ENVIRONMENTS", "Virtual OS", "A separate Android workspace, managed from one place.");
        LinearLayout status = card();
        status.addView(label("ROOTLESS HOST", 12, 0xFF9DBBFF, true));
        status.addView(label("Huawei Android remains untouched", 18, 0xFFF5F7FC, true));
        status.addView(label("No host root, bootloader unlock or system partition access is requested.", 14, 0xFFB8C4D8, false));
        LinearLayout foundation = card();
        foundation.addView(label("SYSTEM FOUNDATION", 12, 0xFF9DBBFF, true));
        foundation.addView(label(AlpineEnvironment.isInstalled(this) ? "Base environment prepared" : "Base environment not prepared", 19, 0xFFF5F7FC, true));
        foundation.addView(label("Private system components are stored inside Experience Virtual OS. They do not modify your phone or require root.", 13, 0xFFB8C4D8, false));
        if (!AlpineEnvironment.isInstalled(this)) {
            cardButton(foundation, "Prepare system environment", this::beginEnvironmentSetup);
        } else {
            foundation.addView(label("The base files are ready. Android virtual-machine runtime and guest image installation are still being integrated.", 12, 0xFFFFD18A, false));
        }

        LinearLayout updates = card();
        updates.addView(label("SOFTWARE UPDATES", 12, 0xFF9DBBFF, true));
        updates.addView(label("Experience Virtual OS", 19, 0xFFF5F7FC, true));
        String installedVersion = getVersionName();
        updates.addView(label("Installed version  •  " + installedVersion, 13, 0xFFB8C4D8, false));
        updateState = label("Check GitHub for the latest published release.", 14, 0xFFE8EEF8, false);
        updateNotes = label("", 12, 0xFFB8C4D8, false);
        updates.addView(updateState);
        updates.addView(updateNotes);
        cardButton(updates, "Check for updates", this::checkForUpdates);
        cardButton(updates, "Download available update", this::downloadUpdate);
        cardButton(updates, "Install downloaded update", this::installDownloadedUpdate);
        cardButton(updates, "View GitHub releases", this::openReleases);

        LinearLayout config = card();
        config.addView(label("CURRENT GUEST PROFILE", 12, 0xFF9DBBFF, true));
        config.addView(label(API_LEVELS[prefs.getInt("api", 0)] + " • " + prefs.getInt("memory", 1024) + " MB", 17, 0xFFF5F7FC, true));
        config.addView(label("Guest root: " + (prefs.getBoolean("guest_root", false) ? "requested (image-dependent)" : "off"), 13, 0xFFB8C4D8, false));
        LinearLayout sources = card();
        sources.addView(label("ANDROID SYSTEM LIBRARY", 12, 0xFF9DBBFF, true));
        sources.addView(label("Android guest releases • API 28–37", 18, 0xFFF5F7FC, true));
        sources.addView(label("The private SDK Manager can install selected Android platform and ARM64 guest-image packages. The virtual machine engine and interactive guest display are the next integration stages.", 13, 0xFFB8C4D8, false));
        cardButton(sources, "Install selected Android SDK + system image", this::installSdkManager);
        cardButton(sources, "Install virtual machine engine", this::installGuestEngine);
        cardButton(sources, "Start Android guest", this::startAndroidGuest);
        cardButton(sources, "Open live guest display", this::openGuestDisplay);
        cardButton(sources, "Stop Android guest", () -> {
            GuestVmManager.stop();
            Toast.makeText(this, "Android guest stopped.", Toast.LENGTH_SHORT).show();
        });
        cardButton(sources, "View guest runtime log", this::showGuestLog);
        sources.addView(label("The private SDK Manager installs the selected Android platform and ARM64 Google APIs system image. The guest engine, framebuffer and touch controls are managed separately.", 12, 0xFFB8C4D8, false));
        button("Import another guest image", this::chooseImage);
        button("Change guest configuration", () -> {
            selectedApi = prefs.getInt("api", 0);
            selectedMemory = prefs.getInt("memory", 1024);
            guestRoot = prefs.getBoolean("guest_root", false);
            wizardStep = 1;
            renderWizard();
        });
        LinearLayout runtime = card();
        runtime.addView(label("DEVICE COMPATIBILITY", 12, 0xFF9DBBFF, true));
        RuntimeCapabilities capabilities = RuntimeCapabilities.inspect(this);
        runtime.addView(label(capabilities.deviceSummary(), 15, 0xFFF5F7FC, true));
        runtime.addView(label(capabilities.accelerationSummary(), 13, 0xFFB8C4D8, false));
        runtime.addView(label("These are host checks only. A visible KVM node does not prove that a compatible guest machine can boot. Experience Virtual OS will not request root or change your bootloader.", 12, 0xFFFFD18A, false));
        runtime.addView(label("VIRTUAL MACHINE STATUS", 12, 0xFF9DBBFF, true));
        runtime.addView(label(GuestVmManager.isRunning() ? "Android guest process running" : "Android guest process stopped", 17, 0xFFFFD18A, true));
        runtime.addView(label("The guest launcher now starts QEMU and connects its localhost VNC framebuffer to the in-app touch display. Actual Android boot compatibility is reported by the guest serial and QEMU logs.", 13, 0xFFB8C4D8, false));
        button("Restart setup wizard", () -> {
            prefs.edit().putBoolean("setup_complete", false).apply();
            showWizard();
        });
        page.addView(label("Experience Virtual OS • alpha", 12, 0xFF7F8CA2, false));
    }


    private Button cardButton(LinearLayout parent, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(0xFFE8EEF8);
        b.setBackground(shape(0xFF26374D, 15));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46));
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
        showRuntimeProgress("Installing ARM64 virtual machine engine", "Preparing QEMU inside the private Alpine environment…", () -> {
            try {
                GuestVmManager.installEngine(this);
                mainHandler.post(() -> { Toast.makeText(this, "QEMU engine installed.", Toast.LENGTH_LONG).show(); showHome(); });
            } catch (Exception e) {
                mainHandler.post(() -> showRuntimeError("Engine installation failed", e));
            }
        });
    }

    private void startAndroidGuest() {
        showRuntimeProgress("Starting Android guest", "Preparing the selected SDK image, guest storage and virtual hardware…", () -> {
            try {
                GuestVmManager.start(this, 28 + prefs.getInt("api", 0), prefs.getInt("memory", 1024));
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
        TextView stage = label(initial, 16, 0xFFF5F7FC, true);
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
                .setMessage("Android 17 platform and system-image packages are distributed under Google's Android SDK licence. Review the terms before continuing. Experience Virtual OS will accept the SDK package licences only after you confirm here.")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Accept and install", (dialog, which) -> beginSdkManagerInstall())
                .show();
    }

    private void beginSdkManagerInstall() {
        base("ANDROID SDK INSTALLER", "Installing Android SDK", "Experience Virtual OS is preparing the selected Android platform and ARM64 system image. No terminal will be shown.");
        LinearLayout panel = card();
        panel.addView(label("INSTALLATION PROGRESS", 12, 0xFF9DBBFF, true));
        TextView stage = label("Preparing private environment…", 17, 0xFFF5F7FC, true);
        panel.addView(stage);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(9));
        pp.topMargin = dp(16);
        pp.bottomMargin = dp(12);
        panel.addView(progress, pp);
        TextView details = label("The SDK packages are installed into app-private storage.", 13, 0xFFB8C4D8, false);
        panel.addView(details);
        new Thread(() -> {
            try {
                SdkManagerBootstrap.installSelectedGuest(this, 28 + prefs.getInt("api", 0),
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
        panel.addView(label("INSTALLATION", 12, 0xFF9DBBFF, true));
        TextView stage = label("Preparing system components…", 17, 0xFFF5F7FC, true);
        panel.addView(stage);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(9));
        pp.topMargin = dp(18);
        pp.bottomMargin = dp(12);
        panel.addView(progress, pp);
        TextView detail = label("Verifying the private environment. No host system changes are made.", 13, 0xFFB8C4D8, false);
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
