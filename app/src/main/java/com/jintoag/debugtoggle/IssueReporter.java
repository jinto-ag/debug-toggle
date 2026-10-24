package com.jintoag.debugtoggle;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class IssueReporter {

    private static final String GITHUB_REPO_URL = "https://github.com/jinto-ag/debug-toggle";
    private static final String GITHUB_ISSUES_NEW = GITHUB_REPO_URL + "/issues/new";

    public static boolean isOnline(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network net = cm.getActiveNetwork();
                if (net != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                    return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String buildDiagnosticReport(Context context) {
        DebuggingManager manager = new DebuggingManager(context);
        StringBuilder sb = new StringBuilder();
        sb.append("### Diagnostic Information\n\n");

        String versionName = "Unknown";
        long versionCode = 0;
        try {
            android.content.pm.PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = context.getPackageManager().getPackageInfo(context.getPackageName(), android.content.pm.PackageManager.PackageInfoFlags.of(0));
            } else {
                info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            }
            if (info.versionName != null) {
                versionName = info.versionName;
            }
            versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info);
        } catch (Exception ignored) {
        }
        sb.append("- **App Version:** ").append(versionName).append(" (Build ").append(versionCode).append(")\n");
        sb.append("- **Device Model:** ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("- **Android Version:** Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("- **Security Patch:** ").append(Build.VERSION.SECURITY_PATCH).append("\n");

        try {
            boolean dev = manager.isDeveloperOptionsEnabled();
            boolean usb = manager.isUsbDebuggingEnabled();
            boolean wifi = manager.isWirelessDebuggingEnabled();
            boolean authTimeoutDisabled = manager.isAuthTimeoutDisabled();
            sb.append("- **Developer Options Enabled:** ").append(dev).append("\n");
            sb.append("- **USB Debugging Enabled:** ").append(usb).append("\n");
            sb.append("- **Wireless Debugging Enabled:** ").append(wifi).append("\n");
            sb.append("- **Auth Timeout Disabled:** ").append(authTimeoutDisabled).append("\n");
        } catch (Exception e) {
            sb.append("- **Settings State:** Error querying settings: ").append(e.getMessage()).append("\n");
        }

        return AppLogger.sanitize(sb.toString());
    }

    public static void reportBug(Activity activity) {
        String diagnosticInfo = buildDiagnosticReport(activity);
        String issueBody = "### Bug Description\n\n"
                + "[Describe what happened and how to reproduce]\n\n"
                + "### Expected Behavior\n\n"
                + "[What did you expect to happen?]\n\n"
                + diagnosticInfo + "\n"
                + "### Attached Logs\n\n"
                + "_Please attach exported sanitized logs from 'Audit & Diagnostics Logs' if relevant._\n";

        openGitHubIssue(activity, "[Bug]: ", issueBody, "bug");
    }

    public static void reportSecurityVulnerability(Activity activity) {
        String diagnosticInfo = buildDiagnosticReport(activity);
        String issueBody = "### Security Advisory Details\n\n"
                + "**Severity / Impact:** [Low / Moderate / High / Critical]\n\n"
                + "**Vulnerability Description:**\n"
                + "[Provide detailed technical description of the security issue]\n\n"
                + diagnosticInfo + "\n"
                + "**Remediation Proposal:**\n"
                + "[Optional suggested fix]\n";

        openGitHubIssue(activity, "[Security]: ", issueBody, "security");
    }

    private static void openGitHubIssue(Activity activity, String title, String body, String label) {
        if (!isOnline(activity)) {
            AppLogger.i("IssueReporter", "Device is offline. Copying diagnostics to clipboard.");
            copyToClipboard(activity, body);
            return;
        }

        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name());
            String encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8.name());
            String encodedLabels = URLEncoder.encode(label, StandardCharsets.UTF_8.name());

            String url = GITHUB_ISSUES_NEW + "?title=" + encodedTitle + "&body=" + encodedBody + "&labels=" + encodedLabels;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
            AppLogger.i("IssueReporter", "Opened GitHub issue URL for label: " + label);
        } catch (Exception e) {
            AppLogger.w("IssueReporter", "Failed to open browser, copying to clipboard: " + e.getMessage());
            copyToClipboard(activity, body);
        }
    }

    private static void copyToClipboard(Context context, String body) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("DebugToggle Diagnostics", body));
        }
        Toast.makeText(context, "Offline: Diagnostics copied to clipboard. Submit on GitHub when connected.", Toast.LENGTH_LONG).show();
    }
}
