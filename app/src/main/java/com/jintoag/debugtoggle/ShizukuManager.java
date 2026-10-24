package com.jintoag.debugtoggle;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class ShizukuManager {

    public static final int REQUEST_CODE_SHIZUKU_PERMISSION = 7001;

    public interface GrantCallback {
        void onResult(boolean success, String message);
    }

    public static boolean isShizukuInstalled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", PackageManager.PackageInfoFlags.of(0));
            } else {
                context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShizukuRunning() {
        return isShizukuAvailable();
    }

    public static boolean hasShizukuPermission() {
        if (!isShizukuAvailable()) {
            return false;
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestShizukuPermission() {
        if (!isShizukuAvailable()) {
            return;
        }
        try {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
        } catch (Throwable t) {
            AppLogger.e("Shizuku", "Failed to request Shizuku permission: " + t.getMessage());
        }
    }

    public static void grantSecureSettingsViaShizuku(Context context, GrantCallback callback) {
        new Thread(() -> {
            try {
                if (!isShizukuAvailable()) {
                    callback.onResult(false, "Shizuku service is not running");
                    return;
                }
                if (!hasShizukuPermission()) {
                    callback.onResult(false, "Shizuku permission not granted");
                    return;
                }

                String pkg = context.getPackageName();
                IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
                if (service == null) {
                    callback.onResult(false, "IShizukuService is unavailable");
                    return;
                }

                String[] cmd = new String[]{"pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS"};
                IRemoteProcess remoteProcess = service.newProcess(cmd, null, null);
                int exitCode = remoteProcess.waitFor();
                if (exitCode == 0) {
                    AppLogger.i("Shizuku", "WRITE_SECURE_SETTINGS granted via Shizuku successfully");
                    callback.onResult(true, "Permission granted successfully via Shizuku");
                } else {
                    AppLogger.w("Shizuku", "pm grant returned exit code: " + exitCode);
                    callback.onResult(false, "pm grant failed with exit code: " + exitCode);
                }
            } catch (Throwable t) {
                AppLogger.e("Shizuku", "Error granting permission via Shizuku: " + t.getMessage());
                callback.onResult(false, "Shizuku execution failed: " + t.getMessage());
            }
        }).start();
    }
}
