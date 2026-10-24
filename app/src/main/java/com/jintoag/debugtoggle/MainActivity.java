package com.jintoag.debugtoggle;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        manager = new DebuggingManager(this);

        initThemeColors();
        buildMaterialUi();
        registerSettingsObserver();
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
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(colorSurface);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 56, 40, 56);
        scrollView.addView(root);

        // App Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, 36);

        ImageView appIcon = new ImageView(this);
        appIcon.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(96, 96);
        iconParams.setMarginEnd(32);
        appIcon.setLayoutParams(iconParams);
        headerRow.addView(appIcon);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.app_name);
        tvTitle.setTextSize(24);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setTextColor(colorTextPrimary);
        titleCol.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(R.string.app_subtitle);
        tvSubtitle.setTextSize(13);
        tvSubtitle.setTextColor(colorTextSecondary);
        titleCol.addView(tvSubtitle);

        headerRow.addView(titleCol);
        root.addView(headerRow);

        // Status Card (Hero banner)
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(40, 32, 40, 32);
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(colorSurfaceContainerHigh);
        statusBg.setCornerRadius(32);
        statusCard.setBackground(statusBg);

        LinearLayout statusHeaderRow = new LinearLayout(this);
        statusHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        statusHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        statusIndicatorDot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(24, 24);
        dotParams.setMarginEnd(20);
        statusIndicatorDot.setLayoutParams(dotParams);
        statusHeaderRow.addView(statusIndicatorDot);

        tvStatusHeader = new TextView(this);
        tvStatusHeader.setTextSize(15);
        tvStatusHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvStatusHeader.setTextColor(colorTextPrimary);
        statusHeaderRow.addView(tvStatusHeader);

        statusCard.addView(statusHeaderRow);

        tvStatusNetwork = new TextView(this);
        tvStatusNetwork.setTextSize(13);
        tvStatusNetwork.setTextColor(colorTextSecondary);
        tvStatusNetwork.setPadding(44, 8, 0, 0);
        statusCard.addView(tvStatusNetwork);

        root.addView(statusCard);

        // Section Title: Toggles
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(R.string.section_switches);
        sectionTitle.setTextSize(14);
        sectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sectionTitle.setTextColor(colorPrimary);
        sectionTitle.setPadding(8, 48, 0, 16);
        root.addView(sectionTitle);

        // Card Container for Switches
        LinearLayout switchCard = new LinearLayout(this);
        switchCard.setOrientation(LinearLayout.VERTICAL);
        switchCard.setPadding(32, 16, 32, 16);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(colorSurfaceContainer);
        cardBg.setCornerRadius(32);
        cardBg.setStroke(2, colorOutline);
        switchCard.setBackground(cardBg);

        // 1. Developer Options
        swDevMode = addSwitchRow(
            switchCard,
            R.drawable.ic_developer_mode,
            getString(R.string.title_dev_options),
            getString(R.string.desc_dev_options),
            (v, isChecked) -> {
                if (isUpdatingFromObserver) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
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
            (v, isChecked) -> {
                if (isUpdatingFromObserver) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
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
            (v, isChecked) -> {
                if (isUpdatingFromObserver) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
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
            (v, isChecked) -> {
                if (isUpdatingFromObserver) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                try {
                    manager.setAuthTimeoutDisabled(isChecked);
                    AppLogger.i("MainActivity", "Auth Timeout Disabled set to: " + isChecked);
                    refreshUiState();
                } catch (Exception e) {
                    showError(e);
                }
            }
        );

        root.addView(switchCard);

        // Section Title: Quick Actions
        TextView actionsTitle = new TextView(this);
        actionsTitle.setText(R.string.section_actions);
        actionsTitle.setTextSize(14);
        actionsTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        actionsTitle.setTextColor(colorPrimary);
        actionsTitle.setPadding(8, 48, 0, 16);
        root.addView(actionsTitle);

        // Action 1: Enable All Debugging (Filled Material 3 Button)
        Button btnEnableAll = new Button(this);
        btnEnableAll.setText(R.string.btn_enable_all);
        btnEnableAll.setTextColor(colorOnPrimary);
        btnEnableAll.setTextSize(15);
        btnEnableAll.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnEnableAll.setGravity(Gravity.CENTER);
        btnEnableAll.setMinHeight(130);
        Drawable boltIcon = getDrawable(R.drawable.ic_bolt);
        if (boltIcon != null) {
            boltIcon.setTint(colorOnPrimary);
            btnEnableAll.setCompoundDrawablesWithIntrinsicBounds(boltIcon, null, null, null);
            btnEnableAll.setCompoundDrawablePadding(24);
            btnEnableAll.setPadding(40, 0, 40, 0);
        }
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(colorPrimary);
        btnBg.setCornerRadius(28);
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
        root.addView(btnEnableAll);

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
        revokeParams.setMargins(0, 20, 0, 0);
        btnRevokeAuth.setLayoutParams(revokeParams);
        root.addView(btnRevokeAuth);

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
        openParams.setMargins(0, 16, 0, 36);
        btnOpenDev.setLayoutParams(openParams);
        root.addView(btnOpenDev);

        // Section: Audit & Community
        TextView tvSectionDiag = new TextView(this);
        tvSectionDiag.setText(R.string.section_audit_community);
        tvSectionDiag.setTextSize(12);
        tvSectionDiag.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSectionDiag.setTextColor(colorTextSecondary);
        tvSectionDiag.setLetterSpacing(0.08f);
        tvSectionDiag.setPadding(8, 24, 0, 16);
        root.addView(tvSectionDiag);

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
        logsParams.setMargins(0, 0, 0, 16);
        btnViewLogs.setLayoutParams(logsParams);
        root.addView(btnViewLogs);

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
        bugParams.setMargins(0, 0, 0, 16);
        btnReportBug.setLayoutParams(bugParams);
        root.addView(btnReportBug);

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
        secParams.setMargins(0, 0, 0, 48);
        btnReportSecurity.setLayoutParams(secParams);
        root.addView(btnReportSecurity);

        setContentView(scrollView);
    }

    private Switch addSwitchRow(LinearLayout container, int iconRes, String title, String subtitle, Switch.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 24, 0, 24);
        row.setMinimumHeight(140);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(64, 64);
        iconParams.setMarginEnd(28);
        icon.setLayoutParams(iconParams);
        row.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(colParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setTextColor(colorTextPrimary);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(12);
        tvSub.setTextColor(colorTextSecondary);
        tvSub.setPadding(0, 4, 0, 0);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        divider.setLayoutParams(params);
        container.addView(divider);
    }

    private Button createTonalButton(int textResId, int iconResId, View.OnClickListener onClickListener) {
        Button btn = new Button(this);
        btn.setText(textResId);
        btn.setTextColor(colorTextPrimary);
        btn.setTextSize(14);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setMinHeight(120);
        if (iconResId != 0) {
            Drawable icon = getDrawable(iconResId);
            if (icon != null) {
                icon.setTint(colorTextPrimary);
                btn.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                btn.setCompoundDrawablePadding(24);
                btn.setPadding(40, 0, 40, 0);
            }
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorSurfaceContainerHigh);
        bg.setCornerRadius(28);
        bg.setStroke(2, colorOutline);
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
                tvStatusHeader.setText(usb && wifi ? R.string.status_adb_both : (usb ? R.string.status_adb_usb : R.string.status_adb_wifi));
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
