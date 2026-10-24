package com.jintoag.debugtoggle;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collections;

public class DebuggingManagerTest {

    @Test
    public void testConstants() {
        Assert.assertEquals("development_settings_enabled", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        Assert.assertEquals("adb_enabled", Settings.Global.ADB_ENABLED);
        Assert.assertEquals("adb_wifi_enabled", DebuggingManager.ADB_WIFI_ENABLED);
        Assert.assertEquals("adb_allowed_connection_time", DebuggingManager.ADB_ALLOWED_CONNECTION_TIME);
        Assert.assertEquals(604800000L, DebuggingManager.DEFAULT_CONNECTION_TIME);
    }

    @Test
    public void testSecureSettingsPermissionCheck() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getContentResolver()).thenReturn(resolver);

        Mockito.when(appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        DebuggingManager manager = new DebuggingManager(context);
        Assert.assertTrue(manager.hasSecureSettingsPermission());

        Mockito.when(appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        Assert.assertFalse(manager.hasSecureSettingsPermission());
    }

    @Test
    public void testDeveloperOptionsToggle() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getContentResolver()).thenReturn(resolver);

        DebuggingManager manager = new DebuggingManager(context);

        try (MockedStatic<Settings.Global> mocked = Mockito.mockStatic(Settings.Global.class)) {
            mocked.when(() -> Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                    .thenReturn(1);
            Assert.assertTrue(manager.isDeveloperOptionsEnabled());

            mocked.when(() -> Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                    .thenReturn(0);
            Assert.assertFalse(manager.isDeveloperOptionsEnabled());

            manager.setDeveloperOptionsEnabled(true);
            mocked.verify(() -> Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1));

            manager.setDeveloperOptionsEnabled(false);
            mocked.verify(() -> Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0));

            // ensureDeveloperOptionsActive when already true -> no putInt
            mocked.when(() -> Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                    .thenReturn(1);
            manager.ensureDeveloperOptionsActive();

            // ensureDeveloperOptionsActive when false -> calls putInt 1
            mocked.when(() -> Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                    .thenReturn(0);
            manager.ensureDeveloperOptionsActive();
            mocked.verify(() -> Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1), Mockito.atLeast(2));
        }
    }

    @Test
    public void testUsbDebuggingToggle() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getContentResolver()).thenReturn(resolver);

        DebuggingManager manager = new DebuggingManager(context);

        try (MockedStatic<Settings.Global> mocked = Mockito.mockStatic(Settings.Global.class)) {
            mocked.when(() -> Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0))
                    .thenReturn(1);
            Assert.assertTrue(manager.isUsbDebuggingEnabled());

            mocked.when(() -> Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0))
                    .thenReturn(0);
            Assert.assertFalse(manager.isUsbDebuggingEnabled());

            manager.setUsbDebuggingEnabled(true);
            mocked.verify(() -> Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 1));

            manager.setUsbDebuggingEnabled(false);
            mocked.verify(() -> Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 0));
        }
    }

    @Test
    public void testWirelessDebuggingToggle() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getContentResolver()).thenReturn(resolver);

        DebuggingManager manager = new DebuggingManager(context);

        try (MockedStatic<Settings.Global> mocked = Mockito.mockStatic(Settings.Global.class)) {
            mocked.when(() -> Settings.Global.getInt(resolver, DebuggingManager.ADB_WIFI_ENABLED, 0))
                    .thenReturn(1);
            Assert.assertTrue(manager.isWirelessDebuggingEnabled());

            mocked.when(() -> Settings.Global.getInt(resolver, DebuggingManager.ADB_WIFI_ENABLED, 0))
                    .thenReturn(0);
            Assert.assertFalse(manager.isWirelessDebuggingEnabled());

            manager.setWirelessDebuggingEnabled(true);
            mocked.verify(() -> Settings.Global.putInt(resolver, DebuggingManager.ADB_WIFI_ENABLED, 1));

            manager.setWirelessDebuggingEnabled(false);
            mocked.verify(() -> Settings.Global.putInt(resolver, DebuggingManager.ADB_WIFI_ENABLED, 0));
        }
    }

    @Test
    public void testAuthTimeoutToggle() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getContentResolver()).thenReturn(resolver);

        DebuggingManager manager = new DebuggingManager(context);

        try (MockedStatic<Settings.Global> mocked = Mockito.mockStatic(Settings.Global.class)) {
            mocked.when(() -> Settings.Global.getLong(resolver, DebuggingManager.ADB_ALLOWED_CONNECTION_TIME, DebuggingManager.DEFAULT_CONNECTION_TIME))
                    .thenReturn(0L);
            Assert.assertTrue(manager.isAuthTimeoutDisabled());

            mocked.when(() -> Settings.Global.getLong(resolver, DebuggingManager.ADB_ALLOWED_CONNECTION_TIME, DebuggingManager.DEFAULT_CONNECTION_TIME))
                    .thenReturn(DebuggingManager.DEFAULT_CONNECTION_TIME);
            Assert.assertFalse(manager.isAuthTimeoutDisabled());

            manager.setAuthTimeoutDisabled(true);
            mocked.verify(() -> Settings.Global.putLong(resolver, DebuggingManager.ADB_ALLOWED_CONNECTION_TIME, 0L));

            manager.setAuthTimeoutDisabled(false);
            mocked.verify(() -> Settings.Global.putLong(resolver, DebuggingManager.ADB_ALLOWED_CONNECTION_TIME, DebuggingManager.DEFAULT_CONNECTION_TIME));
        }
    }

    @Test
    public void testEnableAllDebugging() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        ContentResolver resolver = Mockito.mock(ContentResolver.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getContentResolver()).thenReturn(resolver);

        DebuggingManager manager = new DebuggingManager(context);

        try (MockedStatic<Settings.Global> mocked = Mockito.mockStatic(Settings.Global.class)) {
            manager.enableAllDebugging();

            mocked.verify(() -> Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1), Mockito.atLeastOnce());
            mocked.verify(() -> Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 1));
            mocked.verify(() -> Settings.Global.putInt(resolver, DebuggingManager.ADB_WIFI_ENABLED, 1));
            mocked.verify(() -> Settings.Global.putLong(resolver, DebuggingManager.ADB_ALLOWED_CONNECTION_TIME, 0L));
        }
    }

    @Test
    public void testGetDeveloperOptionsIntent() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);

        DebuggingManager manager = new DebuggingManager(context);
        Intent intent = manager.getDeveloperOptionsIntent();

        Assert.assertNotNull(intent);
    }

    @Test
    public void testGetWifiIpAddress() {
        Context context = Mockito.mock(Context.class);
        Context appContext = Mockito.mock(Context.class);
        ConnectivityManager cm = Mockito.mock(ConnectivityManager.class);
        Network network = Mockito.mock(Network.class);
        NetworkCapabilities caps = Mockito.mock(NetworkCapabilities.class);
        LinkProperties lp = Mockito.mock(LinkProperties.class);
        LinkAddress linkAddress = Mockito.mock(LinkAddress.class);
        Inet4Address inet4 = Mockito.mock(Inet4Address.class);

        Mockito.when(context.getApplicationContext()).thenReturn(appContext);
        Mockito.when(appContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm);
        Mockito.when(cm.getActiveNetwork()).thenReturn(network);
        Mockito.when(cm.getNetworkCapabilities(network)).thenReturn(caps);
        Mockito.when(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
        Mockito.when(cm.getLinkProperties(network)).thenReturn(lp);
        Mockito.when(lp.getLinkAddresses()).thenReturn(Collections.singletonList(linkAddress));
        Mockito.when(linkAddress.getAddress()).thenReturn(inet4);
        Mockito.when(inet4.isLoopbackAddress()).thenReturn(false);
        Mockito.when(inet4.getHostAddress()).thenReturn("192.168.1.150");

        DebuggingManager manager = new DebuggingManager(context);
        Assert.assertEquals("192.168.1.150", manager.getWifiIpAddress());

        // Test with non-wifi or null
        Mockito.when(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(false);
        Assert.assertNull(manager.getWifiIpAddress());

        Mockito.when(cm.getActiveNetwork()).thenReturn(null);
        Assert.assertNull(manager.getWifiIpAddress());
    }
}
