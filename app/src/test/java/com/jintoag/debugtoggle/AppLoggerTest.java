package com.jintoag.debugtoggle;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.util.List;

public class AppLoggerTest {

    @Before
    public void setUp() {
        AppLogger.clear();
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
    }

    @Test
    public void testByteCapacityEviction() {
        // Enqueue entries that exceed MAX_BYTE_CAPACITY (256 KB)
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
        // Verify FIFO eviction: earliest entries (0-49) were dropped, entry 549 is present
        Assert.assertEquals("Log entry 549", entries.get(entries.size() - 1).message);
    }

    @Test
    public void testLogLevelFiltering() {
        AppLogger.i("Tag", "Info message");
        AppLogger.w("Tag", "Warning message");
        AppLogger.e("Tag", "Error message");

        List<AppLogger.LogEntry> all = AppLogger.getEntries(AppLogger.LogLevel.ALL, "");
        Assert.assertEquals(3, all.size());

        List<AppLogger.LogEntry> warnOnly = AppLogger.getEntries(AppLogger.LogLevel.WARN, "");
        Assert.assertEquals(2, warnOnly.size()); // WARN and ERROR

        List<AppLogger.LogEntry> errorOnly = AppLogger.getEntries(AppLogger.LogLevel.ERROR, "");
        Assert.assertEquals(1, errorOnly.size()); // Only ERROR
        Assert.assertEquals("Error message", errorOnly.get(0).message);
    }

    @Test
    public void testSearchFiltering() {
        AppLogger.i("USB", "USB debugging activated");
        AppLogger.i("WIFI", "Wireless debugging enabled");
        AppLogger.i("SETTINGS", "Developer options restored");

        List<AppLogger.LogEntry> results = AppLogger.getEntries(AppLogger.LogLevel.ALL, "wireless");
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("Wireless debugging enabled", results.get(0).message);
    }

    @Test
    public void testClearLogs() {
        AppLogger.i("Tag", "Sample log");
        Assert.assertEquals(1, AppLogger.getEntries(AppLogger.LogLevel.ALL, "").size());

        AppLogger.clear();
        Assert.assertEquals(0, AppLogger.getEntries(AppLogger.LogLevel.ALL, "").size());
    }
}
