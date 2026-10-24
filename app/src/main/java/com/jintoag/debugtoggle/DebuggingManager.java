package com.jintoag.debugtoggle;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import java.net.Inet4Address;
import java.net.InetAddress;

public class DebuggingManager {

    public static final String ADB_WIFI_ENABLED = "adb_wifi_enabled";
    public static final String ADB_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time";
    public static final long DEFAULT_CONNECTION_TIME = 604800000L; // 7 days in ms

    private final Context context;
    private final ContentResolver resolver;

    public DebuggingManager(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
    }

    public boolean hasSecureSettingsPermission() {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public boolean isDeveloperOptionsEnabled() {
        return Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
    }

    public void setDeveloperOptionsEnabled(boolean enabled) {
        Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, enabled ? 1 : 0);
    }

    public void ensureDeveloperOptionsActive() {
        if (!isDeveloperOptionsEnabled()) {
            setDeveloperOptionsEnabled(true);
        }
    }

    public boolean isUsbDebuggingEnabled() {
        return Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1;
    }

    public void setUsbDebuggingEnabled(boolean enabled) {
        if (enabled) {
            ensureDeveloperOptionsActive();
        }
        Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, enabled ? 1 : 0);
    }

    public boolean isWirelessDebuggingEnabled() {
        return Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0) == 1;
    }

    public void setWirelessDebuggingEnabled(boolean enabled) {
        if (enabled) {
            ensureDeveloperOptionsActive();
        }
        Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, enabled ? 1 : 0);
    }

    public boolean isAuthTimeoutDisabled() {
        long time = Settings.Global.getLong(resolver, ADB_ALLOWED_CONNECTION_TIME, DEFAULT_CONNECTION_TIME);
        return time == 0L;
    }

    public void setAuthTimeoutDisabled(boolean disabled) {
        if (disabled) {
            ensureDeveloperOptionsActive();
        }
        long value = disabled ? 0L : DEFAULT_CONNECTION_TIME;
        Settings.Global.putLong(resolver, ADB_ALLOWED_CONNECTION_TIME, value);
    }

    public void enableAllDebugging() {
        setDeveloperOptionsEnabled(true);
        setUsbDebuggingEnabled(true);
        setWirelessDebuggingEnabled(true);
        setAuthTimeoutDisabled(true);
    }

    public String getWifiIpAddress() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        LinkProperties lp = cm.getLinkProperties(network);
                        if (lp != null) {
                            for (LinkAddress la : lp.getLinkAddresses()) {
                                InetAddress addr = la.getAddress();
                                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                                    return addr.getHostAddress();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Intent getDeveloperOptionsIntent() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
