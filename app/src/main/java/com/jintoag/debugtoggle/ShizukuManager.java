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
        if (context == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", PackageManager.PackageInfoFlags.of(0));
            } else {
                context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isServiceAvailable(Context context) {
        return isShizukuInstalled(context) && isShizukuRunning();
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

    public static boolean hasPermission() {
        return hasShizukuPermission();
    }

    public static void requestShizukuPermission() {
        if (!isShizukuAvailable()) {
            return;
        }
        try {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
        } catch (Throwable t) {
            AppLogger.e("PermissionManager", "Failed to request permission: " + t.getMessage());
        }
    }

    public static void requestPermission() {
        requestShizukuPermission();
    }

    static java.util.concurrent.Executor backgroundExecutor = null;

    static void setExecutorForTest(java.util.concurrent.Executor executor) {
        backgroundExecutor = executor;
    }

    public static void grantPermission(Context context, GrantCallback callback) {
        grantSecureSettingsViaShizuku(context, callback);
    }

    public static void grantSecureSettingsViaShizuku(Context context, GrantCallback callback) {
        Runnable task = () -> {
            try {
                if (!isShizukuAvailable()) {
                    callback.onResult(false, "Automated service is not running");
                    return;
                }
                if (!hasShizukuPermission()) {
                    callback.onResult(false, "Permission not granted");
                    return;
                }

                String pkg = context != null ? context.getPackageName() : "com.jintoag.debugtoggle";
                IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
                if (service == null) {
                    callback.onResult(false, "System service is unavailable");
                    return;
                }

                String[] cmd = new String[]{"pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS"};
                IRemoteProcess remoteProcess = service.newProcess(cmd, null, null);
                int exitCode = remoteProcess.waitFor();
                if (exitCode == 0) {
                    AppLogger.i("PermissionManager", "WRITE_SECURE_SETTINGS granted successfully");
                    callback.onResult(true, "Permission granted successfully");
                } else {
                    AppLogger.w("PermissionManager", "pm grant returned exit code: " + exitCode);
                    callback.onResult(false, "Command failed with exit code: " + exitCode);
                }
            } catch (Throwable t) {
                AppLogger.e("PermissionManager", "Error granting permission: " + t.getMessage());
                callback.onResult(false, "Execution failed: " + t.getMessage());
            }
        };

        if (backgroundExecutor != null) {
            backgroundExecutor.execute(task);
        } else {
            new Thread(task).start();
        }
    }
}
