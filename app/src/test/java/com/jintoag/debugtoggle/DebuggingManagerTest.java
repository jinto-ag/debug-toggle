package com.jintoag.debugtoggle;

import android.provider.Settings;
import org.junit.Assert;
import org.junit.Test;

public class DebuggingManagerTest {

    @Test
    public void testSettingKeysMatchAndroidFramework() {
        Assert.assertEquals("development_settings_enabled", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        Assert.assertEquals("adb_enabled", Settings.Global.ADB_ENABLED);
        Assert.assertEquals("adb_wifi_enabled", DebuggingManager.ADB_WIFI_ENABLED);
        Assert.assertEquals("adb_allowed_connection_time", DebuggingManager.ADB_ALLOWED_CONNECTION_TIME);
        Assert.assertEquals(604800000L, DebuggingManager.DEFAULT_CONNECTION_TIME);
    }
}
