package com.jintoag.debugtoggle;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {

    private DebuggingManager manager;
    private Switch swDevMode;
    private Switch swUsbDebug;
    private Switch swWifiDebug;
    private Switch swAuthTimeout;
    private TextView tvStatusHeader;
    private TextView tvStatusNetwork;
    private View statusIndicatorDot;

    private boolean isUpdatingFromObserver = false;
    private ContentObserver settingsObserver;

    private int colorSurface;
    private int colorSurfaceContainer;
    private int colorSurfaceContainerHigh;
    private int colorPrimary;
    private int colorOnPrimary;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorOutline;

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        manager = new DebuggingManager(this);

        initThemeColors();
        buildMaterialUi();
        registerSettingsObserver();

        AppLogger.i("MainActivity", "DebugToggle v1.0.0 initialized");
        logSystemStates();
    }

    private void logSystemStates() {
        if (manager == null) return;
        try {
            boolean dev = manager.isDeveloperOptionsEnabled();
            boolean usb = manager.isUsbDebuggingEnabled();
            boolean wifi = manager.isWirelessDebuggingEnabled();
            boolean auth = manager.isAuthTimeoutDisabled();
            AppLogger.i("DebuggingManager", "States: DeveloperOptions=" + dev + ", USB=" + usb + ", Wifi=" + wifi + ", AuthTimeoutDisabled=" + auth);
        } catch (Exception ignored) {
        }
    }

    private void initThemeColors() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        if (isNight) {
            colorSurface = getColor(R.color.md_theme_dark_surface);
            colorSurfaceContainer = getColor(R.color.md_theme_dark_surfaceContainer);
            colorSurfaceContainerHigh = getColor(R.color.md_theme_dark_surfaceContainerHigh);
            colorPrimary = getColor(R.color.md_theme_dark_primary);
            colorOnPrimary = getColor(R.color.md_theme_dark_onPrimary);
            colorTextPrimary = getColor(R.color.md_theme_dark_onSurface);
            colorTextSecondary = getColor(R.color.md_theme_dark_textSecondary);
            colorOutline = getColor(R.color.md_theme_dark_outlineVariant);
        } else {
            colorSurface = getColor(R.color.md_theme_light_surface);
            colorSurfaceContainer = getColor(R.color.md_theme_light_surfaceContainer);
            colorSurfaceContainerHigh = getColor(R.color.md_theme_light_surfaceContainerHigh);
            colorPrimary = getColor(R.color.md_theme_light_primary);
            colorOnPrimary = getColor(R.color.md_theme_light_onPrimary);
            colorTextPrimary = getColor(R.color.md_theme_light_onSurface);
            colorTextSecondary = getColor(R.color.md_theme_light_textSecondary);
            colorOutline = getColor(R.color.md_theme_light_outlineVariant);
        }
    }

    private void buildMaterialUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorSurface);

        // Fixed Top Header (Never scrolls away)
        LinearLayout fixedHeader = new LinearLayout(this);
        fixedHeader.setOrientation(LinearLayout.HORIZONTAL);
        fixedHeader.setGravity(Gravity.CENTER_VERTICAL);
        fixedHeader.setBackgroundColor(colorSurface);

        ImageView appIcon = new ImageView(this);
        appIcon.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        appIcon.setLayoutParams(iconParams);
        fixedHeader.addView(appIcon);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.app_name);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setTextColor(colorTextPrimary);
        titleCol.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(R.string.app_subtitle);
        tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSubtitle.setTextColor(colorTextSecondary);
        titleCol.addView(tvSubtitle);

        fixedHeader.addView(titleCol);
        root.addView(fixedHeader);

        // Header divider
        View headerDivider = new View(this);
        headerDivider.setBackgroundColor(colorOutline);
        headerDivider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        root.addView(headerDivider);

        // ScrollView for all remaining content
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scrollView.setLayoutParams(scrollParams);

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(scrollContent);
        root.addView(scrollView);

        // Status Card (Hero banner)
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(colorSurfaceContainerHigh);
        statusBg.setCornerRadius(dp(16));
        statusCard.setBackground(statusBg);

        LinearLayout statusHeaderRow = new LinearLayout(this);
        statusHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        statusHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        statusIndicatorDot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.setMarginEnd(dp(10));
        statusIndicatorDot.setLayoutParams(dotParams);
        statusHeaderRow.addView(statusIndicatorDot);

        tvStatusHeader = new TextView(this);
        tvStatusHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvStatusHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvStatusHeader.setTextColor(colorTextPrimary);
        statusHeaderRow.addView(tvStatusHeader);

        statusCard.addView(statusHeaderRow);

        tvStatusNetwork = new TextView(this);
        tvStatusNetwork.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatusNetwork.setTextColor(colorTextSecondary);
        tvStatusNetwork.setPadding(dp(20), dp(4), 0, 0);
        statusCard.addView(tvStatusNetwork);

        scrollContent.addView(statusCard);

        // Section Title: Toggles
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(R.string.section_switches);
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sectionTitle.setTextColor(colorPrimary);
        sectionTitle.setLetterSpacing(0.06f);
        sectionTitle.setPadding(dp(4), dp(20), 0, dp(8));
        scrollContent.addView(sectionTitle);

        // Card Container for Switches
        LinearLayout switchCard = new LinearLayout(this);
        switchCard.setOrientation(LinearLayout.VERTICAL);
        switchCard.setPadding(dp(14), dp(4), dp(14), dp(4));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(colorSurfaceContainer);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), colorOutline);
        switchCard.setBackground(cardBg);

        // 1. Developer Options
        swDevMode = addSwitchRow(
                switchCard,
                R.drawable.ic_developer_mode,
                getString(R.string.title_dev_options),
                getString(R.string.desc_dev_options),
                (buttonView, isChecked) -> {
                    if (isUpdatingFromObserver) return;
                    try {
                        manager.setDeveloperOptionsEnabled(isChecked);
                        AppLogger.i("MainActivity", "Developer Options set to: " + isChecked);
                        refreshUiState();
                    } catch (Exception e) {
                        showError(e);
                    }
                }
        );

        addDivider(switchCard);

        // 2. USB Debugging
        swUsbDebug = addSwitchRow(
                switchCard,
                R.drawable.ic_usb,
                getString(R.string.title_usb_debug),
                getString(R.string.desc_usb_debug),
                (buttonView, isChecked) -> {
                    if (isUpdatingFromObserver) return;
                    try {
                        manager.setUsbDebuggingEnabled(isChecked);
                        AppLogger.i("MainActivity", "USB Debugging set to: " + isChecked);
                        refreshUiState();
                    } catch (Exception e) {
                        showError(e);
                    }
                }
        );

        addDivider(switchCard);

        // 3. Wireless Debugging
        swWifiDebug = addSwitchRow(
                switchCard,
                R.drawable.ic_wifi,
                getString(R.string.title_wifi_debug),
                getString(R.string.desc_wifi_debug),
                (buttonView, isChecked) -> {
                    if (isUpdatingFromObserver) return;
                    try {
                        manager.setWirelessDebuggingEnabled(isChecked);
                        AppLogger.i("MainActivity", "Wireless Debugging set to: " + isChecked);
                        refreshUiState();
                    } catch (Exception e) {
                        showError(e);
                    }
                }
        );

        addDivider(switchCard);

        // 4. Disable Auth Timeout
        swAuthTimeout = addSwitchRow(
                switchCard,
                R.drawable.ic_shield_clock,
                getString(R.string.title_auth_timeout),
                getString(R.string.desc_auth_timeout),
                (buttonView, isChecked) -> {
                    if (isUpdatingFromObserver) return;
                    try {
                        manager.setAuthTimeoutDisabled(isChecked);
                        AppLogger.i("MainActivity", "Auth Timeout Disabled set to: " + isChecked);
                        refreshUiState();
                    } catch (Exception e) {
                        showError(e);
                    }
                }
        );

        scrollContent.addView(switchCard);

        // Section Title: Quick Actions
        TextView actionsTitle = new TextView(this);
        actionsTitle.setText(R.string.section_actions);
        actionsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        actionsTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        actionsTitle.setTextColor(colorPrimary);
        actionsTitle.setLetterSpacing(0.06f);
        actionsTitle.setPadding(dp(4), dp(20), 0, dp(8));
        scrollContent.addView(actionsTitle);

        // Action 1: Enable All Debugging (Filled Material 3 Button)
        Button btnEnableAll = new Button(this);
        btnEnableAll.setText(R.string.btn_enable_all);
        btnEnableAll.setTextColor(colorOnPrimary);
        btnEnableAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnEnableAll.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnEnableAll.setGravity(Gravity.CENTER);
        btnEnableAll.setMinHeight(dp(48));
        btnEnableAll.setAllCaps(false);

        Drawable boltIcon = getDrawable(R.drawable.ic_bolt);
        if (boltIcon != null) {
            boltIcon.setTint(colorOnPrimary);
            boltIcon.setBounds(0, 0, dp(20), dp(20));
            btnEnableAll.setCompoundDrawables(boltIcon, null, null, null);
            btnEnableAll.setCompoundDrawablePadding(dp(10));
            btnEnableAll.setPadding(dp(20), 0, dp(20), 0);
        }

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(colorPrimary);
        btnBg.setCornerRadius(dp(24));
        btnEnableAll.setBackground(btnBg);
        btnEnableAll.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            try {
                manager.enableAllDebugging();
                AppLogger.i("MainActivity", "Enable All Debugging triggered");
                refreshUiState();
                Toast.makeText(this, R.string.action_enable_all_toast, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                showError(e);
            }
        });
        scrollContent.addView(btnEnableAll);

        // Action 2: Revoke Authorizations (Tonal Button)
        Button btnRevokeAuth = createTonalButton(
                R.string.btn_revoke_auth,
                R.drawable.ic_key_off,
                v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    try {
                        manager.ensureDeveloperOptionsActive();
                        AppLogger.i("MainActivity", "Opened Revoke Authorizations in Settings");
                        Intent intent = manager.getDeveloperOptionsIntent();
                        startActivity(intent);
                        Toast.makeText(this, R.string.action_revoke_auth_toast, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        showError(e);
                    }
                }
        );
        LinearLayout.LayoutParams revokeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        revokeParams.setMargins(0, dp(10), 0, 0);
        btnRevokeAuth.setLayoutParams(revokeParams);
        scrollContent.addView(btnRevokeAuth);

        // Action 3: Open Full Developer Options (Tonal Button)
        Button btnOpenDev = createTonalButton(
                R.string.btn_open_dev,
                R.drawable.ic_settings,
                v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    try {
                        manager.ensureDeveloperOptionsActive();
                        AppLogger.i("MainActivity", "Opened Developer Options Settings");
                        startActivity(manager.getDeveloperOptionsIntent());
                    } catch (Exception e) {
                        showError(e);
                    }
                }
        );
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        openParams.setMargins(0, dp(10), 0, 0);
        btnOpenDev.setLayoutParams(openParams);
        scrollContent.addView(btnOpenDev);

        // Section: Audit & Community
        TextView tvSectionDiag = new TextView(this);
        tvSectionDiag.setText(R.string.section_audit_community);
        tvSectionDiag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSectionDiag.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSectionDiag.setTextColor(colorTextSecondary);
        tvSectionDiag.setLetterSpacing(0.06f);
        tvSectionDiag.setPadding(dp(4), dp(20), 0, dp(8));
        scrollContent.addView(tvSectionDiag);

        // View Audit Logs
        Button btnViewLogs = createTonalButton(
                R.string.btn_view_logs,
                R.drawable.ic_log,
                v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    startActivity(new Intent(MainActivity.this, LogsActivity.class));
                }
        );
        LinearLayout.LayoutParams logsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        logsParams.setMargins(0, 0, 0, dp(10));
        btnViewLogs.setLayoutParams(logsParams);
        scrollContent.addView(btnViewLogs);

        // Report Bug (GitHub)
        Button btnReportBug = createTonalButton(
                R.string.btn_report_bug,
                R.drawable.ic_bug,
                v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    IssueReporter.reportBug(MainActivity.this);
                }
        );
        LinearLayout.LayoutParams bugParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bugParams.setMargins(0, 0, 0, dp(10));
        btnReportBug.setLayoutParams(bugParams);
        scrollContent.addView(btnReportBug);

        // Report Security Vulnerability
        Button btnReportSecurity = createTonalButton(
                R.string.btn_report_security,
                R.drawable.ic_shield_clock,
                v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    IssueReporter.reportSecurityVulnerability(MainActivity.this);
                }
        );
        LinearLayout.LayoutParams secParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        secParams.setMargins(0, 0, 0, 0);
        btnReportSecurity.setLayoutParams(secParams);
        scrollContent.addView(btnReportSecurity);

        // Section: About & Share
        TextView tvSectionAbout = new TextView(this);
        tvSectionAbout.setText(R.string.section_about_share);
        tvSectionAbout.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSectionAbout.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSectionAbout.setTextColor(colorTextSecondary);
        tvSectionAbout.setLetterSpacing(0.06f);
        tvSectionAbout.setPadding(dp(4), dp(20), 0, dp(8));
        scrollContent.addView(tvSectionAbout);

        // Share App (APK) Button
        Button btnShareApp = createTonalButton(
                R.string.btn_share_app,
                R.drawable.ic_share,
                v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    shareAppApk();
                }
        );
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        shareParams.setMargins(0, 0, 0, dp(16));
        btnShareApp.setLayoutParams(shareParams);
        scrollContent.addView(btnShareApp);

        // Footer: Version & Author
        TextView tvVersion = new TextView(this);
        tvVersion.setText(getString(R.string.app_version_fmt, "1.0.0", 1));
        tvVersion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvVersion.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tvVersion.setTextColor(colorTextSecondary);
        tvVersion.setGravity(Gravity.CENTER);
        scrollContent.addView(tvVersion);

        TextView tvAuthor = new TextView(this);
        tvAuthor.setText(R.string.app_author);
        tvAuthor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvAuthor.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvAuthor.setTextColor(colorPrimary);
        tvAuthor.setGravity(Gravity.CENTER);
        tvAuthor.setPadding(0, dp(4), 0, dp(8));
        tvAuthor.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jinto-ag/debug-toggle"));
                startActivity(browserIntent);
            } catch (Exception ignored) {
            }
        });
        scrollContent.addView(tvAuthor);

        // WindowInsets / Safe Area Listener
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            }
            fixedHeader.setPadding(dp(16), top + dp(12), dp(16), dp(12));
            scrollContent.setPadding(dp(16), dp(12), dp(16), bottom + dp(28));
            return insets;
        });

        setContentView(root);
    }

    private void shareAppApk() {
        try {
            String sourceDir = getApplicationInfo().sourceDir;
            File srcApk = new File(sourceDir);
            File cacheApk = new File(getCacheDir(), "DebugToggle.apk");

            if (!cacheApk.exists() || cacheApk.length() != srcApk.length()) {
                copyFile(srcApk, cacheApk);
            }

            Uri apkUri = AuditLogProvider.APK_CONTENT_URI;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.android.package-archive");
            shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "DebugToggle APK");
            shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setClipData(ClipData.newRawUri("DebugToggle APK", apkUri));

            AppLogger.i("MainActivity", "Sharing app APK via native options");
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app_chooser)));
        } catch (Exception e) {
            AppLogger.e("MainActivity", "Failed to share APK: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.share_error_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private Switch addSwitchRow(LinearLayout container, int iconRes, String title, String subtitle, Switch.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setMinimumHeight(dp(52));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMarginEnd(dp(12));
        icon.setLayoutParams(iconParams);
        row.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(colParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setTextColor(colorTextPrimary);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSub.setTextColor(colorTextSecondary);
        tvSub.setPadding(0, dp(2), 0, 0);
        textCol.addView(tvSub);

        row.addView(textCol);

        Switch sw = new Switch(this);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);

        container.addView(row);
        return sw;
    }

    private void addDivider(LinearLayout container) {
        View divider = new View(this);
        divider.setBackgroundColor(colorOutline);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divider.setLayoutParams(params);
        container.addView(divider);
    }

    private Button createTonalButton(int textResId, int iconResId, View.OnClickListener onClickListener) {
        Button btn = new Button(this);
        btn.setText(textResId);
        btn.setTextColor(colorTextPrimary);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setMinHeight(dp(44));
        btn.setGravity(Gravity.CENTER);
        btn.setAllCaps(false);

        if (iconResId != 0) {
            Drawable icon = getDrawable(iconResId);
            if (icon != null) {
                icon.setTint(colorTextPrimary);
                icon.setBounds(0, 0, dp(20), dp(20));
                btn.setCompoundDrawables(icon, null, null, null);
                btn.setCompoundDrawablePadding(dp(10));
                btn.setPadding(dp(20), 0, dp(20), 0);
            }
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorSurfaceContainerHigh);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), colorOutline);
        btn.setBackground(bg);
        btn.setOnClickListener(onClickListener);
        return btn;
    }

    private void registerSettingsObserver() {
        settingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                refreshUiState();
            }
        };

        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), false, settingsObserver
        );
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, settingsObserver
        );
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("adb_wifi_enabled"), false, settingsObserver
        );
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor("adb_allowed_connection_time"), false, settingsObserver
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (manager != null) {
            try {
                manager.ensureDeveloperOptionsActive();
            } catch (Exception ignored) {
            }
        }
        refreshUiState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (settingsObserver != null) {
            getContentResolver().unregisterContentObserver(settingsObserver);
        }
    }

    private void refreshUiState() {
        if (manager == null) return;
        isUpdatingFromObserver = true;

        try {
            boolean dev = manager.isDeveloperOptionsEnabled();
            boolean usb = manager.isUsbDebuggingEnabled();
            boolean wifi = manager.isWirelessDebuggingEnabled();
            boolean authTimeoutDisabled = manager.isAuthTimeoutDisabled();

            if (swDevMode != null) swDevMode.setChecked(dev);
            if (swUsbDebug != null) swUsbDebug.setChecked(usb);
            if (swWifiDebug != null) swWifiDebug.setChecked(wifi);
            if (swAuthTimeout != null) swAuthTimeout.setChecked(authTimeoutDisabled);

            // Update status indicator
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);

            if (usb || wifi) {
                dot.setColor(Color.parseColor("#4CAF50")); // Green
                int statusRes = (usb && wifi) ? R.string.status_adb_both : (usb ? R.string.status_adb_usb : R.string.status_adb_wifi);
                tvStatusHeader.setText(statusRes);
            } else if (dev) {
                dot.setColor(Color.parseColor("#FF9800")); // Orange
                tvStatusHeader.setText(R.string.status_dev_active_adb_off);
            } else {
                dot.setColor(Color.parseColor("#757575")); // Gray
                tvStatusHeader.setText(R.string.status_all_debugging_off);
            }
            if (statusIndicatorDot != null) {
                statusIndicatorDot.setBackground(dot);
            }

            // Network info
            String ip = manager.getWifiIpAddress();
            if (ip != null) {
                tvStatusNetwork.setText(getString(R.string.status_wifi_ip, ip));
            } else {
                tvStatusNetwork.setText(R.string.status_wifi_disconnected);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isUpdatingFromObserver = false;
        }
    }

    private void showError(Exception e) {
        AppLogger.e("MainActivity", "Operation failed: " + (e != null ? e.getMessage() : "Unknown error"), e);
        refreshUiState();
        Toast.makeText(this, R.string.perm_error_msg, Toast.LENGTH_LONG).show();
    }
}
