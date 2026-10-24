package com.jintoag.debugtoggle;

import android.content.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AppLoggerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        AppLogger.resetForTest();
    }

    @Test
    public void testConstructor() {
        AppLogger logger = new AppLogger();
        Assert.assertNotNull(logger);
    }

    @Test
    public void testPiiRedaction() {
        String inputIp = "Device connected to 192.168.1.155 port 5555";
        String redactedIp = AppLogger.sanitize(inputIp);
        Assert.assertFalse("Should not contain IPv4 address", redactedIp.contains("192.168.1.155"));
        Assert.assertTrue("Should contain redacted token", redactedIp.contains("[REDACTED_IP]"));

        String inputIpv6 = "Gateway fe80::1ff:fe23:4567:890a on wlan0";
        String redactedIpv6 = AppLogger.sanitize(inputIpv6);
        Assert.assertFalse("Should not contain IPv6 address", redactedIpv6.contains("fe80::1ff:fe23:4567:890a"));
        Assert.assertTrue("Should contain redacted IPv6 token", redactedIpv6.contains("[REDACTED_IPV6]"));

        String inputMac = "Hardware MAC: 00:1A:2B:3C:4D:5E on wlan0";
        String redactedMac = AppLogger.sanitize(inputMac);
        Assert.assertFalse("Should not contain MAC address", redactedMac.contains("00:1A:2B:3C:4D:5E"));
        Assert.assertTrue("Should contain redacted MAC token", redactedMac.contains("[REDACTED_MAC]"));

        String inputSsid = "Connected to SSID: \"Office_Secure_WiFi_5G\"";
        String redactedSsid = AppLogger.sanitize(inputSsid);
        Assert.assertFalse("Should not contain SSID", redactedSsid.contains("Office_Secure_WiFi_5G"));
        Assert.assertTrue("Should contain redacted SSID token", redactedSsid.contains("[REDACTED_SSID]"));

        String inputKey = "Auth key: AAAAB3NzaC1yc2EAAAADAQABAAABAQC3+9v1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890==";
        String redactedKey = AppLogger.sanitize(inputKey);
        Assert.assertFalse("Should not contain raw auth key", redactedKey.contains("AAAAB3NzaC1yc2EAAAADAQABAAABAQC3+9v1234567890"));
        Assert.assertTrue("Should contain redacted key token", redactedKey.contains("[REDACTED_ADB_KEY]"));

        Assert.assertEquals("", AppLogger.sanitize(null));
    }

    @Test
    public void testByteCapacityEviction() {
        byte[] payloadBytes = new byte[1000];
        java.util.Arrays.fill(payloadBytes, (byte) 'A');
        String chunk = new String(payloadBytes);

        for (int i = 0; i < 300; i++) {
            AppLogger.i("Tag", "Chunk " + i + ": " + chunk);
        }

        long totalBytes = AppLogger.getTotalBytes();
        Assert.assertTrue("Total buffer memory must stay under 256 KB (262144 bytes), actual: " + totalBytes,
                totalBytes <= AppLogger.MAX_BYTE_CAPACITY);
    }

    @Test
    public void testRingBufferCappedAtMaxCapacity() {
        for (int i = 0; i < 550; i++) {
            AppLogger.i("TestTag", "Log entry " + i);
        }

        List<AppLogger.LogEntry> entries = AppLogger.getEntries(AppLogger.LogLevel.ALL, "");
        Assert.assertEquals("Ring buffer must be strictly capped at MAX_CAPACITY (500)", 500, entries.size());
        Assert.assertEquals("Log entry 549", entries.get(0).message);
        Assert.assertEquals("Log entry 50", entries.get(entries.size() - 1).message);

        List<AppLogger.LogEntry> chronological = AppLogger.getEntries(AppLogger.LogLevel.ALL, "", false);
        Assert.assertEquals("Log entry 50", chronological.get(0).message);
        Assert.assertEquals("Log entry 549", chronological.get(chronological.size() - 1).message);
    }

    @Test
    public void testLogLevelFiltering() {
        AppLogger.i("Tag", "Info message");
        AppLogger.w("Tag", "Warning message");
        AppLogger.e("Tag", "Error message");
        AppLogger.e("Tag", "Exception message", new RuntimeException("Simulated error"));

        List<AppLogger.LogEntry> all = AppLogger.getEntries(AppLogger.LogLevel.ALL, "");
        Assert.assertEquals(4, all.size());

        List<AppLogger.LogEntry> infoAndAbove = AppLogger.getEntries(AppLogger.LogLevel.INFO, "");
        Assert.assertEquals(4, infoAndAbove.size());

        List<AppLogger.LogEntry> warnOnly = AppLogger.getEntries(AppLogger.LogLevel.WARN, "");
        Assert.assertEquals(3, warnOnly.size());

        List<AppLogger.LogEntry> errorOnly = AppLogger.getEntries(AppLogger.LogLevel.ERROR, "");
        Assert.assertEquals(2, errorOnly.size());
    }

    @Test
    public void testSearchFiltering() {
        AppLogger.i("USB", "USB debugging activated");
        AppLogger.i("WIFI", "Wireless debugging enabled");
        AppLogger.i("SETTINGS", "Developer options restored");

        List<AppLogger.LogEntry> results = AppLogger.getEntries(AppLogger.LogLevel.ALL, "wireless");
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("Wireless debugging enabled", results.get(0).message);

        List<AppLogger.LogEntry> tagResults = AppLogger.getEntries(AppLogger.LogLevel.ALL, "SETTINGS");
        Assert.assertEquals(1, tagResults.size());
        Assert.assertEquals("Developer options restored", tagResults.get(0).message);
    }

    @Test
    public void testClearLogs() {
        AppLogger.i("Tag", "Sample log");
        Assert.assertEquals(1, AppLogger.getEntries(AppLogger.LogLevel.ALL, "").size());

        AppLogger.clear();
        Assert.assertEquals(0, AppLogger.getEntries(AppLogger.LogLevel.ALL, "").size());
    }

    @Test
    public void testInitWithEmptyHistory() throws Exception {
        File filesDir = tempFolder.newFolder("files_empty");
        File cacheDir = tempFolder.newFolder("cache_empty");

        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getFilesDir()).thenReturn(filesDir);
        Mockito.when(appContext.getCacheDir()).thenReturn(cacheDir);

        AppLogger.init(context);
        List<AppLogger.LogEntry> entries = AppLogger.getEntries(AppLogger.LogLevel.ALL, "");
        Assert.assertFalse("Should log engine ready message when buffer was empty", entries.isEmpty());
        Assert.assertTrue(entries.get(0).message.contains("ready"));
    }

    @Test
    public void testInitAndFilePersistence() throws Exception {
        File filesDir = tempFolder.newFolder("files");
        File cacheDir = tempFolder.newFolder("cache");

        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getFilesDir()).thenReturn(filesDir);
        Mockito.when(appContext.getCacheDir()).thenReturn(cacheDir);

        // Prepopulate history file with valid and malformed lines
        File historyFile = new File(filesDir, "audit_history.log");
        try (FileOutputStream fos = new FileOutputStream(historyFile)) {
            String content = "1700000000000|INFO|App|Historical event 1\n"
                    + "invalid_line_without_pipes\n"
                    + "1700000000001|invalid_pipe\n"
                    + "1700000000002|WARN|Audit|Warning event 2\n";
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }

        AppLogger.init(context);

        List<AppLogger.LogEntry> loaded = AppLogger.getEntries(AppLogger.LogLevel.ALL, "", false);
        Assert.assertEquals(2, loaded.size());
        Assert.assertEquals("Historical event 1", loaded.get(0).message);
        Assert.assertEquals("Warning event 2", loaded.get(1).message);

        // Appending new log while initialized writes to history file
        AppLogger.i("Test", "Live logged event 3");
        Assert.assertTrue(historyFile.exists());
        Assert.assertTrue(historyFile.length() > 0);

        // Export sanitized logs
        File exported = AppLogger.exportSanitizedLogs(appContext);
        Assert.assertNotNull(exported);
        Assert.assertTrue(exported.exists());
        Assert.assertTrue(exported.length() > 0);

        // Clear with context deletes files
        AppLogger.clear(appContext);
        Assert.assertEquals(0, AppLogger.getEntries(AppLogger.LogLevel.ALL, "").size());
        Assert.assertFalse(historyFile.exists());
        Assert.assertFalse(exported.exists());
    }

    @Test
    public void testLoadHistoryWithOverflowEviction() throws Exception {
        File filesDir = tempFolder.newFolder("files_overflow");
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getFilesDir()).thenReturn(filesDir);

        File historyFile = new File(filesDir, "audit_history.log");
        try (FileOutputStream fos = new FileOutputStream(historyFile)) {
            for (int i = 0; i < 550; i++) {
                String line = (1700000000000L + i) + "|INFO|Tag|Overflow event " + i + "\n";
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }

        AppLogger.init(context);
        List<AppLogger.LogEntry> entries = AppLogger.getEntries(AppLogger.LogLevel.ALL, "");
        Assert.assertEquals(500, entries.size());
    }

    @Test
    public void testRewriteHistoryWhenFileExceedsMaxCapacity() throws Exception {
        File filesDir = tempFolder.newFolder("files_rewrite");
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getFilesDir()).thenReturn(filesDir);

        File historyFile = new File(filesDir, "audit_history.log");
        // Create file larger than MAX_BYTE_CAPACITY (262144 bytes)
        byte[] largeData = new byte[270000];
        try (FileOutputStream fos = new FileOutputStream(historyFile)) {
            fos.write(largeData);
        }

        AppLogger.init(context);
        // Appending log will trigger rewriteHistory because file length > MAX_BYTE_CAPACITY
        AppLogger.i("Test", "Triggering rewrite");
        Assert.assertTrue(historyFile.length() < 270000);
    }

    @Test
    public void testLogEntryToStringAndFormatting() {
        AppLogger.LogEntry entry = new AppLogger.LogEntry(1700000000000L, AppLogger.LogLevel.INFO, "TagTest", "MessageBody");
        Assert.assertNotNull(entry.getFormattedTimestamp());
        String str = entry.toString();
        Assert.assertTrue(str.contains("INFO"));
        Assert.assertTrue(str.contains("TagTest"));
        Assert.assertTrue(str.contains("MessageBody"));
    }
}
