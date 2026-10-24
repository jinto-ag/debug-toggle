package com.jintoag.debugtoggle;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class WifiDebugTileService extends TileService {

    private DebuggingManager manager;

    @Override
    public void onCreate() {
        super.onCreate();
        manager = new DebuggingManager(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null || manager == null) return;

        try {
            boolean isWifiActive = manager.isWirelessDebuggingEnabled();
            tile.setState(isWifiActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_wifi_name));
            if (Build.VERSION.SDK_INT >= 29) {
                tile.setSubtitle(isWifiActive ? "Enabled" : "Disabled");
            }
            tile.updateTile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        try {
            boolean wasDevModeOff = !manager.isDeveloperOptionsEnabled();
            boolean currentlyActive = manager.isWirelessDebuggingEnabled();
            boolean newState = !currentlyActive;

            manager.setWirelessDebuggingEnabled(newState);

            Tile tile = getQsTile();
            if (tile != null) {
                tile.setState(newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                if (Build.VERSION.SDK_INT >= 29) {
                    tile.setSubtitle(newState ? "Enabled" : "Disabled");
                }
                tile.updateTile();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(newState ? "Wireless ADB enabled" : "Wireless ADB disabled");
            if (wasDevModeOff && newState) {
                sb.append(" (Developer Options auto-activated)");
            }

            final String msg = sb.toString();
            AppLogger.i("WifiTile", msg);
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show()
            );

        } catch (Exception e) {
            AppLogger.e("WifiTile", "Failed to toggle Wireless debugging: " + e.getMessage(), e);
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), "Permission required: WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show()
            );
        }
    }
}
