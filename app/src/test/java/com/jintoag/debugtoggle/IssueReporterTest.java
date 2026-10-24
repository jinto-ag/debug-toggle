package com.jintoag.debugtoggle;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.widget.Toast;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class IssueReporterTest {

    @Test
    public void testConstructor() {
        IssueReporter reporter = new IssueReporter();
        Assert.assertNotNull(reporter);
    }

    @Test
    public void testDiagnosticsSanitization() {
        String rawDiagnostic = "Device IP: 192.168.1.100, MAC: 00:1A:2B:3C:4D:5E, SSID: 'HomeWiFi'";
        String sanitized = AppLogger.sanitize(rawDiagnostic);

        Assert.assertFalse(sanitized.contains("192.168.1.100"));
        Assert.assertFalse(sanitized.contains("00:1A:2B:3C:4D:5E"));
        Assert.assertFalse(sanitized.contains("HomeWiFi"));
        Assert.assertTrue(sanitized.contains("[REDACTED_IP]"));
        Assert.assertTrue(sanitized.contains("[REDACTED_MAC]"));
        Assert.assertTrue(sanitized.contains("[REDACTED_SSID]"));
    }

    @Test
    public void testIsOnline() {
        Assert.assertFalse("Null context must return false", IssueReporter.isOnline(null));

        Context context = Mockito.mock(Context.class);
        ConnectivityManager cm = Mockito.mock(ConnectivityManager.class);
        Network net = Mockito.mock(Network.class);
        NetworkCapabilities caps = Mockito.mock(NetworkCapabilities.class);

        Mockito.when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm);
        Mockito.when(cm.getActiveNetwork()).thenReturn(net);
        Mockito.when(cm.getNetworkCapabilities(net)).thenReturn(caps);

        Mockito.when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);
        Assert.assertTrue("Should be online when internet capability present", IssueReporter.isOnline(context));

        Mockito.when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(false);
        Assert.assertFalse("Should be offline when internet capability absent", IssueReporter.isOnline(context));

        Mockito.when(cm.getActiveNetwork()).thenReturn(null);
        Assert.assertFalse("Should be offline when no active network", IssueReporter.isOnline(context));

        // When connectivity manager throws exception
        Mockito.when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenThrow(new RuntimeException("Crash"));
        Assert.assertFalse("Should handle exception gracefully and return false", IssueReporter.isOnline(context));
    }

    @Test
    public void testBuildDiagnosticReport() throws Exception {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        PackageManager pm = Mockito.mock(PackageManager.class);
        PackageInfo info = new PackageInfo();
        info.versionName = "1.1.0";

        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(context.getPackageManager()).thenReturn(pm);
        Mockito.when(context.getPackageName()).thenReturn("com.jintoag.debugtoggle");
        Mockito.when(appContext.getContentResolver()).thenReturn(Mockito.mock(android.content.ContentResolver.class));
        Mockito.when(pm.getPackageInfo(Mockito.eq("com.jintoag.debugtoggle"), Mockito.anyInt())).thenReturn(info);

        try (MockedStatic<Settings.Global> mocked = Mockito.mockStatic(Settings.Global.class)) {
            mocked.when(() -> Settings.Global.getInt(Mockito.any(), Mockito.eq(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), Mockito.anyInt()))
                    .thenReturn(1);
            mocked.when(() -> Settings.Global.getInt(Mockito.any(), Mockito.eq(Settings.Global.ADB_ENABLED), Mockito.anyInt()))
                    .thenReturn(1);
            mocked.when(() -> Settings.Global.getInt(Mockito.any(), Mockito.eq(DebuggingManager.ADB_WIFI_ENABLED), Mockito.anyInt()))
                    .thenReturn(1);
            mocked.when(() -> Settings.Global.getLong(Mockito.any(), Mockito.eq(DebuggingManager.ADB_ALLOWED_CONNECTION_TIME), Mockito.anyLong()))
                    .thenReturn(0L);

            String report = IssueReporter.buildDiagnosticReport(context);
            Assert.assertNotNull(report);
            Assert.assertTrue(report.contains("### Diagnostic Information"));
            Assert.assertTrue(report.contains("Developer Options Enabled"));
            Assert.assertTrue(report.contains("USB Debugging Enabled"));
            Assert.assertTrue(report.contains("Wireless Debugging Enabled"));
            Assert.assertTrue(report.contains("Auth Timeout Disabled"));
        }

        // Test settings exception handling
        try (MockedStatic<Settings.Global> mocked = Mockito.mockStatic(Settings.Global.class)) {
            mocked.when(() -> Settings.Global.getInt(Mockito.any(), Mockito.eq(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), Mockito.anyInt()))
                    .thenThrow(new SecurityException("Settings access denied"));

            String errReport = IssueReporter.buildDiagnosticReport(context);
            Assert.assertNotNull(errReport);
            Assert.assertTrue(errReport.contains("Error querying settings"));
        }
    }

    @Test
    public void testReportBugOnlineAndOffline() {
        Activity activity = Mockito.mock(Activity.class);
        Context appContext = Mockito.mock(Context.class);
        ClipboardManager clipboard = Mockito.mock(ClipboardManager.class);
        ConnectivityManager cm = Mockito.mock(ConnectivityManager.class);
        Network net = Mockito.mock(Network.class);
        NetworkCapabilities caps = Mockito.mock(NetworkCapabilities.class);
        Toast mockToast = Mockito.mock(Toast.class);

        Mockito.when(activity.getApplicationContext()).thenReturn(appContext);
        Mockito.when(activity.getSystemService(Context.CLIPBOARD_SERVICE)).thenReturn(clipboard);
        Mockito.when(activity.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm);
        Mockito.when(activity.getPackageName()).thenReturn("com.jintoag.debugtoggle");
        Mockito.when(appContext.getContentResolver()).thenReturn(Mockito.mock(android.content.ContentResolver.class));

        try (MockedStatic<Toast> toastStatic = Mockito.mockStatic(Toast.class)) {
            toastStatic.when(() -> Toast.makeText(Mockito.any(), Mockito.anyString(), Mockito.anyInt()))
                    .thenReturn(mockToast);

            // Test Offline -> Copies to clipboard and shows toast
            Mockito.when(cm.getActiveNetwork()).thenReturn(null);
            IssueReporter.reportBug(activity);
            Mockito.verify(clipboard, Mockito.atLeastOnce()).setPrimaryClip(Mockito.any());
            Mockito.verify(mockToast, Mockito.atLeastOnce()).show();

            // Test Online -> Launches Intent
            Mockito.when(cm.getActiveNetwork()).thenReturn(net);
            Mockito.when(cm.getNetworkCapabilities(net)).thenReturn(caps);
            Mockito.when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);

            IssueReporter.reportBug(activity);
            Mockito.verify(activity, Mockito.atLeastOnce()).startActivity(Mockito.any(Intent.class));

            // Test Online but startActivity throws (e.g. no browser) -> falls back to clipboard
            Mockito.doThrow(new RuntimeException("No browser")).when(activity).startActivity(Mockito.any(Intent.class));
            IssueReporter.reportBug(activity);
            Mockito.verify(clipboard, Mockito.atLeast(2)).setPrimaryClip(Mockito.any());
        }
    }

    @Test
    public void testReportSecurityVulnerabilityOnlineAndOffline() {
        Activity activity = Mockito.mock(Activity.class);
        Context appContext = Mockito.mock(Context.class);
        ClipboardManager clipboard = Mockito.mock(ClipboardManager.class);
        ConnectivityManager cm = Mockito.mock(ConnectivityManager.class);
        Network net = Mockito.mock(Network.class);
        NetworkCapabilities caps = Mockito.mock(NetworkCapabilities.class);

        Mockito.when(activity.getApplicationContext()).thenReturn(appContext);
        Mockito.when(activity.getSystemService(Context.CLIPBOARD_SERVICE)).thenReturn(clipboard);
        Mockito.when(activity.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm);
        Mockito.when(activity.getPackageName()).thenReturn("com.jintoag.debugtoggle");
        Mockito.when(appContext.getContentResolver()).thenReturn(Mockito.mock(android.content.ContentResolver.class));

        // Test Offline -> Copies to clipboard
        Mockito.when(cm.getActiveNetwork()).thenReturn(null);
        IssueReporter.reportSecurityVulnerability(activity);
        Mockito.verify(clipboard, Mockito.atLeastOnce()).setPrimaryClip(Mockito.any());

        // Test Online -> Launches Intent
        Mockito.when(cm.getActiveNetwork()).thenReturn(net);
        Mockito.when(cm.getNetworkCapabilities(net)).thenReturn(caps);
        Mockito.when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);

        IssueReporter.reportSecurityVulnerability(activity);
        Mockito.verify(activity, Mockito.atLeastOnce()).startActivity(Mockito.any(Intent.class));
    }
}
