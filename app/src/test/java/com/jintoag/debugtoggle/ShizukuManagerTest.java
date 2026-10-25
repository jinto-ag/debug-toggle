package com.jintoag.debugtoggle;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import rikka.shizuku.Shizuku;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ShizukuManagerTest {

    private String[] originalRootPaths;

    @Before
    public void setUp() {
        originalRootPaths = ShizukuManager.rootPaths.clone();
        ShizukuManager.setExecutorForTest(Runnable::run);
        ShizukuManager.setProcessRunnerForTest(cmd -> 0);
    }

    @After
    public void tearDown() {
        ShizukuManager.rootPaths = originalRootPaths;
        ShizukuManager.setExecutorForTest(null);
        ShizukuManager.setProcessRunnerForTest(cmd -> {
            Process process = Runtime.getRuntime().exec(cmd);
            return process.waitFor();
        });
    }

    @Test
    public void testConstructor() {
        ShizukuManager manager = new ShizukuManager();
        Assert.assertNotNull(manager);
    }

    @Test
    public void testIsShizukuInstalled() throws Exception {
        Assert.assertFalse(ShizukuManager.isShizukuInstalled(null));

        Context context = Mockito.mock(Context.class);
        PackageManager pm = Mockito.mock(PackageManager.class);
        Mockito.when(context.getPackageManager()).thenReturn(pm);

        // Package present
        Mockito.when(pm.getPackageInfo(Mockito.eq("moe.shizuku.privileged.api"), Mockito.anyInt()))
                .thenReturn(new PackageInfo());
        Assert.assertTrue(ShizukuManager.isShizukuInstalled(context));

        // Package missing
        Mockito.when(pm.getPackageInfo(Mockito.eq("moe.shizuku.privileged.api"), Mockito.anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());
        Assert.assertFalse(ShizukuManager.isShizukuInstalled(context));

        // Throwable
        Mockito.when(pm.getPackageInfo(Mockito.eq("moe.shizuku.privileged.api"), Mockito.anyInt()))
                .thenThrow(new RuntimeException("Crash"));
        Assert.assertFalse(ShizukuManager.isShizukuInstalled(context));
    }

    @Test
    public void testGetServiceLaunchIntent() {
        Assert.assertNull(ShizukuManager.getServiceLaunchIntent(null));

        Context context = Mockito.mock(Context.class);
        PackageManager pm = Mockito.mock(PackageManager.class);
        Mockito.when(context.getPackageManager()).thenReturn(pm);

        Intent mockIntent = new Intent();
        Mockito.when(pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")).thenReturn(mockIntent);
        Assert.assertEquals(mockIntent, ShizukuManager.getServiceLaunchIntent(context));

        Mockito.when(pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")).thenThrow(new RuntimeException("PM error"));
        Assert.assertNull(ShizukuManager.getServiceLaunchIntent(context));
    }

    @Test
    public void testIsRootAvailable() throws Exception {
        // When none of the paths exist
        ShizukuManager.rootPaths = new String[]{"/nonexistent/su", "/fake/path/su"};
        Assert.assertFalse(ShizukuManager.isRootAvailable());

        // When a su file exists
        File tempSu = File.createTempFile("su_test", ".tmp");
        tempSu.deleteOnExit();
        ShizukuManager.rootPaths = new String[]{"/nonexistent/su", tempSu.getAbsolutePath()};
        Assert.assertTrue(ShizukuManager.isRootAvailable());

        // When rootPaths is null (causes NullPointerException, caught by catch block)
        ShizukuManager.rootPaths = null;
        Assert.assertFalse(ShizukuManager.isRootAvailable());
    }

    @Test
    public void testShizukuAvailabilityAndPermission() {
        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            Assert.assertTrue(ShizukuManager.isShizukuAvailable());
            Assert.assertTrue(ShizukuManager.isShizukuRunning());

            mockedShizuku.when(Shizuku::checkSelfPermission).thenReturn(PackageManager.PERMISSION_GRANTED);
            Assert.assertTrue(ShizukuManager.hasShizukuPermission());
            Assert.assertTrue(ShizukuManager.hasPermission());

            mockedShizuku.when(Shizuku::checkSelfPermission).thenReturn(PackageManager.PERMISSION_DENIED);
            Assert.assertFalse(ShizukuManager.hasShizukuPermission());

            // Test request permission
            ShizukuManager.requestPermission();
            mockedShizuku.verify(() -> Shizuku.requestPermission(ShizukuManager.REQUEST_CODE_SHIZUKU_PERMISSION));

            // Test request permission throwing exception
            mockedShizuku.when(() -> Shizuku.requestPermission(Mockito.anyInt())).thenThrow(new RuntimeException("IPC failed"));
            ShizukuManager.requestPermission(); // Should not throw

            // When checkSelfPermission throws exception
            mockedShizuku.when(Shizuku::checkSelfPermission).thenThrow(new RuntimeException("Binder died"));
            Assert.assertFalse(ShizukuManager.hasPermission());

            // When unavailable
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(false);
            Assert.assertFalse(ShizukuManager.isShizukuAvailable());
            Assert.assertFalse(ShizukuManager.hasPermission());
            ShizukuManager.requestPermission(); // Does nothing when unavailable

            // When pingBinder throws
            mockedShizuku.when(Shizuku::pingBinder).thenThrow(new RuntimeException("Native error"));
            Assert.assertFalse(ShizukuManager.isShizukuAvailable());
        }
    }

    @Test
    public void testIsServiceAvailable() throws Exception {
        Context context = Mockito.mock(Context.class);
        ShizukuManager.rootPaths = new String[]{"/nonexistent/su"};

        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            // Case 1: Shizuku is running
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            Assert.assertTrue(ShizukuManager.isServiceAvailable(context));

            // Case 2: Shizuku not running, but Root is available
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(false);
            File tempSu = File.createTempFile("su_test2", ".tmp");
            tempSu.deleteOnExit();
            ShizukuManager.rootPaths = new String[]{tempSu.getAbsolutePath()};
            Assert.assertTrue(ShizukuManager.isServiceAvailable(context));

            // Case 3: Neither Shizuku nor Root available
            ShizukuManager.rootPaths = new String[]{"/nonexistent/su"};
            Assert.assertFalse(ShizukuManager.isServiceAvailable(context));
        }
    }

    @Test
    public void testGrantPermissionRouting() {
        Context context = Mockito.mock(Context.class);
        ShizukuManager.rootPaths = new String[]{"/nonexistent/su"};

        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            // Neither Shizuku nor Root
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(false);
            AtomicBoolean resSuccess = new AtomicBoolean(true);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantPermission(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });

            Assert.assertFalse(resSuccess.get());
            Assert.assertEquals("Automated service is not running", resMsg.get());

            // When Root is available
            File tempSu = File.createTempFile("su_test3", ".tmp");
            tempSu.deleteOnExit();
            ShizukuManager.rootPaths = new String[]{tempSu.getAbsolutePath()};
            ShizukuManager.setProcessRunnerForTest(cmd -> 0);

            ShizukuManager.grantPermission(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });
            Assert.assertTrue(resSuccess.get());
            Assert.assertEquals("Permission granted successfully", resMsg.get());
        } catch (Exception e) {
            Assert.fail("Exception should not be thrown: " + e.getMessage());
        }
    }

    @Test
    public void testGrantSecureSettingsViaRoot() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getPackageName()).thenReturn("com.jintoag.debugtoggle");

        // Success (exitCode 0)
        ShizukuManager.setProcessRunnerForTest(cmd -> 0);
        AtomicBoolean resSuccess = new AtomicBoolean(false);
        AtomicReference<String> resMsg = new AtomicReference<>();

        ShizukuManager.grantSecureSettingsViaRoot(context, (success, message) -> {
            resSuccess.set(success);
            resMsg.set(message);
        });
        Assert.assertTrue(resSuccess.get());
        Assert.assertEquals("Permission granted successfully", resMsg.get());

        // Failure (exitCode 1)
        ShizukuManager.setProcessRunnerForTest(cmd -> 1);
        ShizukuManager.grantSecureSettingsViaRoot(context, (success, message) -> {
            resSuccess.set(success);
            resMsg.set(message);
        });
        Assert.assertFalse(resSuccess.get());
        Assert.assertEquals("Command failed with exit code: 1", resMsg.get());

        // Exception
        ShizukuManager.setProcessRunnerForTest(cmd -> {
            throw new RuntimeException("Root execution crashed");
        });
        ShizukuManager.grantSecureSettingsViaRoot(context, (success, message) -> {
            resSuccess.set(success);
            resMsg.set(message);
        });
        Assert.assertFalse(resSuccess.get());
        Assert.assertTrue(resMsg.get().contains("Execution failed"));

        // Context null fallback package name
        ShizukuManager.setProcessRunnerForTest(cmd -> 0);
        ShizukuManager.grantSecureSettingsViaRoot(null, (success, message) -> {
            resSuccess.set(success);
        });
        Assert.assertTrue(resSuccess.get());
    }

    @Test
    public void testGrantPermissionFailures() throws Exception {
        Context context = Mockito.mock(Context.class);

        // Failure 1: Service not available
        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(false);

            AtomicBoolean resSuccess = new AtomicBoolean(true);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantSecureSettingsViaShizuku(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });

            Assert.assertFalse(resSuccess.get());
            Assert.assertEquals("Automated service is not running", resMsg.get());
        }

        // Failure 2: Permission not granted
        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            mockedShizuku.when(Shizuku::checkSelfPermission).thenReturn(PackageManager.PERMISSION_DENIED);

            AtomicBoolean resSuccess = new AtomicBoolean(true);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantSecureSettingsViaShizuku(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });

            Assert.assertFalse(resSuccess.get());
            Assert.assertEquals("Permission not granted", resMsg.get());
        }

        // Failure 3: Binder service returns null
        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class);
             MockedStatic<IShizukuService.Stub> mockedStub = Mockito.mockStatic(IShizukuService.Stub.class)) {

            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            mockedShizuku.when(Shizuku::checkSelfPermission).thenReturn(PackageManager.PERMISSION_GRANTED);
            mockedStub.when(() -> IShizukuService.Stub.asInterface(Mockito.any())).thenReturn(null);

            AtomicBoolean resSuccess = new AtomicBoolean(true);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantSecureSettingsViaShizuku(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });

            Assert.assertFalse(resSuccess.get());
            Assert.assertEquals("System service is unavailable", resMsg.get());
        }

        // Failure 4: Process returns exit code 1
        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class);
             MockedStatic<IShizukuService.Stub> mockedStub = Mockito.mockStatic(IShizukuService.Stub.class)) {

            IBinder mockBinder = Mockito.mock(IBinder.class);
            IShizukuService mockService = Mockito.mock(IShizukuService.class);
            IRemoteProcess mockProcess = Mockito.mock(IRemoteProcess.class);

            Mockito.when(mockProcess.waitFor()).thenReturn(1);
            Mockito.when(mockService.newProcess(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(mockProcess);

            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            mockedShizuku.when(Shizuku::checkSelfPermission).thenReturn(PackageManager.PERMISSION_GRANTED);
            mockedShizuku.when(Shizuku::getBinder).thenReturn(mockBinder);
            mockedStub.when(() -> IShizukuService.Stub.asInterface(mockBinder)).thenReturn(mockService);

            AtomicBoolean resSuccess = new AtomicBoolean(true);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantSecureSettingsViaShizuku(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });

            Assert.assertFalse(resSuccess.get());
            Assert.assertEquals("Command failed with exit code: 1", resMsg.get());
        }

        // Failure 5: Exception thrown during IPC execution
        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class);
             MockedStatic<IShizukuService.Stub> mockedStub = Mockito.mockStatic(IShizukuService.Stub.class)) {

            IBinder mockBinder = Mockito.mock(IBinder.class);
            IShizukuService mockService = Mockito.mock(IShizukuService.class);

            Mockito.when(mockService.newProcess(Mockito.any(), Mockito.any(), Mockito.any())).thenThrow(new RuntimeException("Crash in remote IPC"));

            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            mockedShizuku.when(Shizuku::checkSelfPermission).thenReturn(PackageManager.PERMISSION_GRANTED);
            mockedShizuku.when(Shizuku::getBinder).thenReturn(mockBinder);
            mockedStub.when(() -> IShizukuService.Stub.asInterface(mockBinder)).thenReturn(mockService);

            AtomicBoolean resSuccess = new AtomicBoolean(true);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantSecureSettingsViaShizuku(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });

            Assert.assertFalse(resSuccess.get());
            Assert.assertTrue(resMsg.get().contains("Execution failed"));
        }
    }

    @Test
    public void testGrantPermissionSuccess() throws Exception {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getPackageName()).thenReturn("com.jintoag.debugtoggle");

        IBinder mockBinder = Mockito.mock(IBinder.class);
        IShizukuService mockService = Mockito.mock(IShizukuService.class);
        IRemoteProcess mockProcess = Mockito.mock(IRemoteProcess.class);

        Mockito.when(mockProcess.waitFor()).thenReturn(0);
        Mockito.when(mockService.newProcess(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(mockProcess);

        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class);
             MockedStatic<IShizukuService.Stub> mockedStub = Mockito.mockStatic(IShizukuService.Stub.class)) {

            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            mockedShizuku.when(Shizuku::checkSelfPermission).thenReturn(PackageManager.PERMISSION_GRANTED);
            mockedShizuku.when(Shizuku::getBinder).thenReturn(mockBinder);
            mockedStub.when(() -> IShizukuService.Stub.asInterface(mockBinder)).thenReturn(mockService);

            AtomicBoolean resSuccess = new AtomicBoolean(false);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantSecureSettingsViaShizuku(context, (success, message) -> {
                resSuccess.set(success);
                resMsg.set(message);
            });

            Assert.assertTrue(resSuccess.get());
            Assert.assertEquals("Permission granted successfully", resMsg.get());
        }
    }

    @Test
    public void testDefaultAsyncExecutionWithoutCustomExecutor() throws Exception {
        ShizukuManager.setExecutorForTest(null);
        Context context = Mockito.mock(Context.class);

        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(false);

            CountDownLatch latch = new CountDownLatch(1);
            ShizukuManager.grantSecureSettingsViaShizuku(context, (success, message) -> latch.countDown());

            Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
        }

        // Test root async execution without executor
        ShizukuManager.setExecutorForTest(null);
        CountDownLatch rootLatch = new CountDownLatch(1);
        ShizukuManager.grantSecureSettingsViaRoot(context, (success, message) -> rootLatch.countDown());
        Assert.assertTrue(rootLatch.await(3, TimeUnit.SECONDS));
    }
}
