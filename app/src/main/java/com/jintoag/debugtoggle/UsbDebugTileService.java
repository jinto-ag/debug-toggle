package com.jintoag.debugtoggle;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class UsbDebugTileService extends TileService {

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
            boolean isUsbActive = manager.isUsbDebuggingEnabled();
            tile.setState(isUsbActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_usb_name));
            if (Build.VERSION.SDK_INT >= 29) {
                tile.setSubtitle(isUsbActive ? "Enabled" : "Disabled");
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
            boolean currentlyActive = manager.isUsbDebuggingEnabled();
            boolean newState = !currentlyActive;

            manager.setUsbDebuggingEnabled(newState);

            Tile tile = getQsTile();
            if (tile != null) {
                tile.setState(newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                if (Build.VERSION.SDK_INT >= 29) {
                    tile.setSubtitle(newState ? "Enabled" : "Disabled");
                }
                tile.updateTile();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(newState ? "USB Debugging enabled" : "USB Debugging disabled");
            if (wasDevModeOff && newState) {
                sb.append(" (Developer Options auto-activated)");
            }

            final String msg = sb.toString();
            AppLogger.i("UsbTile", msg);
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show()
            );

        } catch (Exception e) {
            AppLogger.e("UsbTile", "Failed to toggle USB debugging: " + e.getMessage(), e);
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), "Permission required: WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show()
            );
        }
    }
}
