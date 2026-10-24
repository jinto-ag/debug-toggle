package com.jintoag.debugtoggle;

import org.junit.Assert;
import org.junit.Test;

public class IssueReporterTest {

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
    public void testOfflineDetectionWithNullContext() {
        boolean online = IssueReporter.isOnline(null);
        Assert.assertFalse("Null context must return false without throwing", online);
    }
}
