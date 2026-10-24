package com.jintoag.debugtoggle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.List;

public class LogsActivity extends Activity {

    private int colorSurface;
    private int colorSurfaceContainer;
    private int colorSurfaceContainerHigh;
    private int colorPrimary;
    private int colorOnPrimary;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorOutline;
    private int colorError;
    private int colorWarn;

    private AppLogger.LogLevel currentLevel = AppLogger.LogLevel.ALL;
    private String currentQuery = "";

    private LinearLayout logsContainer;
    private TextView emptyView;
    private TextView btnFilterAll;
    private TextView btnFilterInfo;
    private TextView btnFilterWarn;
    private TextView btnFilterError;

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        initThemeColors();
        buildUi();
        AppLogger.i("LogsActivity", "Opened audit log viewer");
        refreshLogs();
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
            colorError = Color.parseColor("#F2B8B5");
            colorWarn = Color.parseColor("#FFD54F");
        } else {
            colorSurface = getColor(R.color.md_theme_light_surface);
            colorSurfaceContainer = getColor(R.color.md_theme_light_surfaceContainer);
            colorSurfaceContainerHigh = getColor(R.color.md_theme_light_surfaceContainerHigh);
            colorPrimary = getColor(R.color.md_theme_light_primary);
            colorOnPrimary = getColor(R.color.md_theme_light_onPrimary);
            colorTextPrimary = getColor(R.color.md_theme_light_onSurface);
            colorTextSecondary = getColor(R.color.md_theme_light_textSecondary);
            colorOutline = getColor(R.color.md_theme_light_outlineVariant);
            colorError = Color.parseColor("#B3261E");
            colorWarn = Color.parseColor("#F57F17");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorSurface);

        // Fixed Top Area (Header + Search + Filter Chips)
        LinearLayout fixedTopArea = new LinearLayout(this);
        fixedTopArea.setOrientation(LinearLayout.VERTICAL);
        fixedTopArea.setBackgroundColor(colorSurface);

        // Header Row: Back, Title, Export, Clear
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(12));

        ImageView btnBack = createHeaderIconButton(
                R.drawable.ic_arrow_back,
                getString(R.string.btn_back),
                v -> finish()
        );
        headerRow.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.logs_title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleParams);
        headerRow.addView(tvTitle);

        ImageView btnExport = createHeaderIconButton(
                R.drawable.ic_share,
                getString(R.string.btn_export_logs),
                v -> exportLogs()
        );
        headerRow.addView(btnExport);

        ImageView btnClear = createHeaderIconButton(
                R.drawable.ic_delete,
                getString(R.string.btn_clear_logs),
                v -> confirmClearLogs()
        );
        LinearLayout.LayoutParams clearParams = (LinearLayout.LayoutParams) btnClear.getLayoutParams();
        clearParams.setMarginStart(dp(8));
        btnClear.setLayoutParams(clearParams);
        headerRow.addView(btnClear);

        fixedTopArea.addView(headerRow);

        // Search Input
        EditText etSearch = new EditText(this);
        etSearch.setHint(R.string.hint_search_logs);
        etSearch.setHintTextColor(colorTextSecondary);
        etSearch.setTextColor(colorTextPrimary);
        etSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etSearch.setPadding(dp(16), dp(8), dp(16), dp(8));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(colorSurfaceContainer);
        searchBg.setCornerRadius(dp(20));
        searchBg.setStroke(dp(1), colorOutline);
        etSearch.setBackground(searchBg);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(0, 0, 0, dp(10));
        etSearch.setLayoutParams(searchParams);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                refreshLogs();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        fixedTopArea.addView(etSearch);

        // Filter chips bar
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, 0, 0, dp(8));

        btnFilterAll = createFilterChip("ALL", AppLogger.LogLevel.ALL);
        btnFilterInfo = createFilterChip("INFO", AppLogger.LogLevel.INFO);
        btnFilterWarn = createFilterChip("WARN", AppLogger.LogLevel.WARN);
        btnFilterError = createFilterChip("ERROR", AppLogger.LogLevel.ERROR);

        chipRow.addView(btnFilterAll);
        chipRow.addView(btnFilterInfo);
        chipRow.addView(btnFilterWarn);
        chipRow.addView(btnFilterError);

        updateChipStates();
        fixedTopArea.addView(chipRow);

        // Subtle divider separating fixed top area from scrollable logs
        View divider = new View(this);
        divider.setBackgroundColor(colorOutline);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        fixedTopArea.addView(divider);

        root.addView(fixedTopArea);

        // Scrollable Log Entries list
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        GradientDrawable logBoxBg = new GradientDrawable();
        logBoxBg.setColor(colorSurfaceContainer);
        logBoxBg.setCornerRadius(dp(16));
        logBoxBg.setStroke(dp(1), colorOutline);
        scrollView.setBackground(logBoxBg);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scrollParams.setMargins(dp(16), dp(8), dp(16), 0);
        scrollView.setLayoutParams(scrollParams);

        logsContainer = new LinearLayout(this);
        logsContainer.setOrientation(LinearLayout.VERTICAL);
        logsContainer.setPadding(dp(12), dp(12), dp(12), dp(12));

        // Empty state view
        emptyView = new TextView(this);
        emptyView.setText(R.string.empty_logs);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        emptyView.setTextColor(colorTextSecondary);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dp(60), 0, dp(60));
        emptyView.setVisibility(View.GONE);
        logsContainer.addView(emptyView);

        scrollView.addView(logsContainer);
        root.addView(scrollView);

        // WindowInsets / Safe Area Listener
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets sb = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = sb.top;
                bottom = sb.bottom;
            }
            fixedTopArea.setPadding(dp(16), top + dp(10), dp(16), 0);
            scrollParams.setMargins(dp(16), dp(8), dp(16), bottom + dp(16));
            scrollView.setLayoutParams(scrollParams);
            return insets;
        });

        setContentView(root);
    }

    private ImageView createHeaderIconButton(int drawableRes, String contentDescription, View.OnClickListener onClickListener) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(drawableRes);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setColorFilter(colorTextPrimary);
        iv.setContentDescription(contentDescription);
        iv.setFocusable(true);
        iv.setClickable(true);

        int size = dp(40);
        int pad = dp(8);
        iv.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colorSurfaceContainerHigh);
        bg.setCornerRadius(dp(20));
        iv.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        iv.setLayoutParams(params);
        iv.setOnClickListener(onClickListener);
        return iv;
    }

    private TextView createFilterChip(final String label, final AppLogger.LogLevel level) {
        final TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setGravity(Gravity.CENTER);
        chip.setFocusable(true);
        chip.setClickable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        if (level != AppLogger.LogLevel.ERROR) {
            params.setMarginEnd(dp(8));
        }
        chip.setLayoutParams(params);

        chip.setOnClickListener(v -> {
            currentLevel = level;
            updateChipStates();
            refreshLogs();
        });
        return chip;
    }

    private void updateChipStates() {
        applyChipStyle(btnFilterAll, currentLevel == AppLogger.LogLevel.ALL);
        applyChipStyle(btnFilterInfo, currentLevel == AppLogger.LogLevel.INFO);
        applyChipStyle(btnFilterWarn, currentLevel == AppLogger.LogLevel.WARN);
        applyChipStyle(btnFilterError, currentLevel == AppLogger.LogLevel.ERROR);
    }

    private void applyChipStyle(TextView chip, boolean isSelected) {
        if (chip == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(17));
        if (isSelected) {
            bg.setColor(colorPrimary);
            chip.setTextColor(colorOnPrimary);
            bg.setStroke(0, Color.TRANSPARENT);
        } else {
            bg.setColor(colorSurfaceContainerHigh);
            chip.setTextColor(colorTextSecondary);
            bg.setStroke(dp(1), colorOutline);
        }
        chip.setBackground(bg);
    }

    private void refreshLogs() {
        logsContainer.removeAllViews();
        logsContainer.addView(emptyView);
        List<AppLogger.LogEntry> entries = AppLogger.getEntries(currentLevel, currentQuery);

        if (entries.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        for (AppLogger.LogEntry entry : entries) {
            LinearLayout entryRow = new LinearLayout(this);
            entryRow.setOrientation(LinearLayout.VERTICAL);
            entryRow.setPadding(dp(6), dp(8), dp(6), dp(8));

            TextView tvMeta = new TextView(this);
            tvMeta.setTypeface(Typeface.MONOSPACE);
            tvMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            int metaColor = colorTextSecondary;
            if (entry.level == AppLogger.LogLevel.ERROR) metaColor = colorError;
            else if (entry.level == AppLogger.LogLevel.WARN) metaColor = colorWarn;

            tvMeta.setTextColor(metaColor);
            tvMeta.setText(String.format("[%s] %s • %s", entry.level.name(), entry.getFormattedTimestamp(), entry.tag));
            entryRow.addView(tvMeta);

            TextView tvMsg = new TextView(this);
            tvMsg.setTypeface(Typeface.MONOSPACE);
            tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvMsg.setTextColor(colorTextPrimary);
            tvMsg.setText(entry.message);
            tvMsg.setTextIsSelectable(true);
            entryRow.addView(tvMsg);

            // Subtle divider line
            View divider = new View(this);
            divider.setBackgroundColor(colorOutline);
            LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            );
            divParams.setMargins(0, dp(8), 0, 0);
            divider.setLayoutParams(divParams);
            entryRow.addView(divider);

            logsContainer.addView(entryRow);
        }
    }

    private void exportLogs() {
        try {
            File exportedFile = AppLogger.exportSanitizedLogs(this);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, AuditLogProvider.CONTENT_URI);
            shareIntent.setClipData(ClipData.newRawUri("audit_log.txt", AuditLogProvider.CONTENT_URI));
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "DebugToggle Sanitized Audit Log");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_export_logs)));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearLogs() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_msg)
                .setPositiveButton(R.string.dialog_clear_confirm, (dialog, which) -> {
                    AppLogger.clear(LogsActivity.this);
                    AppLogger.i("LogsActivity", "Audit log entries cleared");
                    refreshLogs();
                    Toast.makeText(LogsActivity.this, "Audit logs cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
}
