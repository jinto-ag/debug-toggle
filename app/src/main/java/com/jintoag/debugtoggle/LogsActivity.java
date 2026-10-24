package com.jintoag.debugtoggle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
    private Button btnFilterAll;
    private Button btnFilterInfo;
    private Button btnFilterWarn;
    private Button btnFilterError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initThemeColors();
        buildUi();
        refreshLogs();
    }

    private void initThemeColors() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (isNight) {
            colorSurface = Color.parseColor("#121316");
            colorSurfaceContainer = Color.parseColor("#1E1F24");
            colorSurfaceContainerHigh = Color.parseColor("#282A2F");
            colorPrimary = Color.parseColor("#81C784");
            colorOnPrimary = Color.parseColor("#00390E");
            colorTextPrimary = Color.parseColor("#E2E2E6");
            colorTextSecondary = Color.parseColor("#A0A4AB");
            colorOutline = Color.parseColor("#3D4046");
            colorError = Color.parseColor("#F2B8B5");
            colorWarn = Color.parseColor("#FFD54F");
        } else {
            colorSurface = Color.parseColor("#FDFCFF");
            colorSurfaceContainer = Color.parseColor("#F1F0F5");
            colorSurfaceContainerHigh = Color.parseColor("#E9E8EE");
            colorPrimary = Color.parseColor("#2E7D32");
            colorOnPrimary = Color.parseColor("#FFFFFF");
            colorTextPrimary = Color.parseColor("#1A1C1E");
            colorTextSecondary = Color.parseColor("#53565A");
            colorOutline = Color.parseColor("#D7D6DC");
            colorError = Color.parseColor("#B3261E");
            colorWarn = Color.parseColor("#F57F17");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorSurface);
        root.setPadding(36, 48, 36, 36);

        // Header with Back, Title, and Actions
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, 24);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.logs_title);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setTextColor(colorTextPrimary);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleParams);
        headerRow.addView(tvTitle);

        // Export button
        Button btnExport = createIconButton(R.drawable.ic_share, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLogs();
            }
        });
        headerRow.addView(btnExport);

        // Clear button
        Button btnClear = createIconButton(R.drawable.ic_delete, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearLogs();
            }
        });
        headerRow.addView(btnClear);

        root.addView(headerRow);

        // Search Input
        EditText etSearch = new EditText(this);
        etSearch.setHint(R.string.hint_search_logs);
        etSearch.setHintTextColor(colorTextSecondary);
        etSearch.setTextColor(colorTextPrimary);
        etSearch.setTextSize(14);
        etSearch.setPadding(32, 24, 32, 24);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(colorSurfaceContainer);
        searchBg.setCornerRadius(24);
        searchBg.setStroke(2, colorOutline);
        etSearch.setBackground(searchBg);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(0, 0, 0, 24);
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
        root.addView(etSearch);

        // Filter chips bar
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, 0, 0, 24);

        btnFilterAll = createFilterChip("ALL", AppLogger.LogLevel.ALL);
        btnFilterInfo = createFilterChip("INFO", AppLogger.LogLevel.INFO);
        btnFilterWarn = createFilterChip("WARN", AppLogger.LogLevel.WARN);
        btnFilterError = createFilterChip("ERROR", AppLogger.LogLevel.ERROR);

        chipRow.addView(btnFilterAll);
        chipRow.addView(btnFilterInfo);
        chipRow.addView(btnFilterWarn);
        chipRow.addView(btnFilterError);

        updateChipStates();
        root.addView(chipRow);

        // Empty state view
        emptyView = new TextView(this);
        emptyView.setText(R.string.empty_logs);
        emptyView.setTextSize(14);
        emptyView.setTextColor(colorTextSecondary);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, 80, 0, 80);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView);

        // Scrollable Log Entries list
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        GradientDrawable logBoxBg = new GradientDrawable();
        logBoxBg.setColor(colorSurfaceContainer);
        logBoxBg.setCornerRadius(24);
        logBoxBg.setStroke(2, colorOutline);
        scrollView.setBackground(logBoxBg);

        logsContainer = new LinearLayout(this);
        logsContainer.setOrientation(LinearLayout.VERTICAL);
        logsContainer.setPadding(24, 24, 24, 24);
        scrollView.addView(logsContainer);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scrollView.setLayoutParams(scrollParams);
        root.addView(scrollView);

        setContentView(root);
    }

    private Button createIconButton(int drawableRes, View.OnClickListener onClickListener) {
        Button btn = new Button(this);
        btn.setBackground(getDrawable(drawableRes));
        int sizePx = 72;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
        params.setMarginStart(16);
        btn.setLayoutParams(params);
        btn.setOnClickListener(onClickListener);
        return btn;
    }

    private Button createFilterChip(final String label, final AppLogger.LogLevel level) {
        final Button chip = new Button(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setAllCaps(false);
        chip.setPadding(28, 12, 28, 12);
        chip.setMinimumHeight(0);
        chip.setMinimumWidth(0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(16);
        chip.setLayoutParams(params);

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentLevel = level;
                updateChipStates();
                refreshLogs();
            }
        });
        return chip;
    }

    private void updateChipStates() {
        applyChipStyle(btnFilterAll, currentLevel == AppLogger.LogLevel.ALL);
        applyChipStyle(btnFilterInfo, currentLevel == AppLogger.LogLevel.INFO);
        applyChipStyle(btnFilterWarn, currentLevel == AppLogger.LogLevel.WARN);
        applyChipStyle(btnFilterError, currentLevel == AppLogger.LogLevel.ERROR);
    }

    private void applyChipStyle(Button chip, boolean isSelected) {
        if (chip == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20);
        if (isSelected) {
            bg.setColor(colorPrimary);
            chip.setTextColor(colorOnPrimary);
            bg.setStroke(0, Color.TRANSPARENT);
        } else {
            bg.setColor(colorSurfaceContainerHigh);
            chip.setTextColor(colorTextSecondary);
            bg.setStroke(2, colorOutline);
        }
        chip.setBackground(bg);
    }

    private void refreshLogs() {
        logsContainer.removeAllViews();
        List<AppLogger.LogEntry> entries = AppLogger.getEntries(currentLevel, currentQuery);

        if (entries.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        for (AppLogger.LogEntry entry : entries) {
            LinearLayout entryRow = new LinearLayout(this);
            entryRow.setOrientation(LinearLayout.VERTICAL);
            entryRow.setPadding(8, 10, 8, 10);

            TextView tvMeta = new TextView(this);
            tvMeta.setTypeface(Typeface.MONOSPACE);
            tvMeta.setTextSize(11);
            int metaColor = colorTextSecondary;
            if (entry.level == AppLogger.LogLevel.ERROR) metaColor = colorError;
            else if (entry.level == AppLogger.LogLevel.WARN) metaColor = colorWarn;

            tvMeta.setTextColor(metaColor);
            tvMeta.setText(String.format("[%s] %s • %s", entry.level.name(), entry.getFormattedTimestamp(), entry.tag));
            entryRow.addView(tvMeta);

            TextView tvMsg = new TextView(this);
            tvMsg.setTypeface(Typeface.MONOSPACE);
            tvMsg.setTextSize(12);
            tvMsg.setTextColor(colorTextPrimary);
            tvMsg.setText(entry.message);
            tvMsg.setTextIsSelectable(true);
            entryRow.addView(tvMsg);

            // Subtle divider line
            View divider = new View(this);
            divider.setBackgroundColor(colorOutline);
            LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
            );
            divParams.setMargins(0, 10, 0, 0);
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
            startActivity(Intent.createChooser(shareIntent, "Export Sanitized Logs"));
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
                    refreshLogs();
                    Toast.makeText(LogsActivity.this, "Audit logs cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }
}
