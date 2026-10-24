package com.jintoag.debugtoggle;

import android.content.Context;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ShizukuManagerTest {

    @Before
    public void setUp() {
        ShizukuManager.setExecutorForTest(Runnable::run);
    }

    @After
    public void tearDown() {
        ShizukuManager.setExecutorForTest(null);
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
        PackageManager pm = Mockito.mock(PackageManager.class);
        Mockito.when(context.getPackageManager()).thenReturn(pm);
        Mockito.when(pm.getPackageInfo(Mockito.eq("moe.shizuku.privileged.api"), Mockito.anyInt()))
                .thenReturn(new PackageInfo());

        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(true);
            Assert.assertTrue(ShizukuManager.isServiceAvailable(context));

            mockedShizuku.when(Shizuku::pingBinder).thenReturn(false);
            Assert.assertFalse(ShizukuManager.isServiceAvailable(context));
        }
    }

    @Test
    public void testGrantPermissionFailures() throws Exception {
        Context context = Mockito.mock(Context.class);

        // Failure 1: Service not available
        try (MockedStatic<Shizuku> mockedShizuku = Mockito.mockStatic(Shizuku.class)) {
            mockedShizuku.when(Shizuku::pingBinder).thenReturn(false);

            AtomicBoolean resSuccess = new AtomicBoolean(true);
            AtomicReference<String> resMsg = new AtomicReference<>();

            ShizukuManager.grantPermission(context, (success, message) -> {
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

            ShizukuManager.grantPermission(context, (success, message) -> {
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

            ShizukuManager.grantPermission(context, (success, message) -> {
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

            ShizukuManager.grantPermission(context, (success, message) -> {
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

            ShizukuManager.grantPermission(context, (success, message) -> {
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

            ShizukuManager.grantPermission(context, (success, message) -> {
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
            ShizukuManager.grantPermission(context, (success, message) -> latch.countDown());

            Assert.assertTrue(latch.await(3, TimeUnit.SECONDS));
        }
    }
}
