package com.jintoag.debugtoggle;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class AuditLogProviderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testBasicCrudOperations() {
        AuditLogProvider provider = new AuditLogProvider();
        Assert.assertTrue(provider.onCreate());
        Assert.assertNull(provider.insert(AuditLogProvider.CONTENT_URI, null));
        Assert.assertEquals(0, provider.delete(AuditLogProvider.CONTENT_URI, null, null));
        Assert.assertEquals(0, provider.update(AuditLogProvider.CONTENT_URI, null, null, null));
    }

    @Test
    public void testGetType() {
        AuditLogProvider provider = new AuditLogProvider();

        Uri apkUri = Mockito.mock(Uri.class);
        Mockito.when(apkUri.getPath()).thenReturn("/DebugToggle.apk");
        Assert.assertEquals("application/vnd.android.package-archive", provider.getType(apkUri));

        Uri textUri = Mockito.mock(Uri.class);
        Mockito.when(textUri.getPath()).thenReturn("/audit_log.txt");
        Assert.assertEquals("text/plain", provider.getType(textUri));

        Assert.assertEquals("text/plain", provider.getType(null));
    }

    @Test(expected = FileNotFoundException.class)
    public void testOpenFileWithoutContextThrows() throws Exception {
        AuditLogProvider provider = new AuditLogProvider();
        provider.openFile(AuditLogProvider.CONTENT_URI, "r");
    }

    @Test
    public void testOpenFileAndQueryWithMockContext() throws Exception {
        File cacheDir = tempFolder.newFolder("cache");
        File logFile = new File(cacheDir, "debugtoggle_audit_log.txt");
        try (FileOutputStream fos = new FileOutputStream(logFile)) {
            fos.write("sample log data".getBytes());
        }

        File apkDir = tempFolder.newFolder("source");
        File sourceApk = new File(apkDir, "base.apk");
        try (FileOutputStream fos = new FileOutputStream(sourceApk)) {
            fos.write("source apk data".getBytes());
        }

        Context context = Mockito.mock(Context.class);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.sourceDir = sourceApk.getAbsolutePath();

        Mockito.when(context.getCacheDir()).thenReturn(cacheDir);
        Mockito.when(context.getApplicationInfo()).thenReturn(appInfo);

        AuditLogProvider provider = new AuditLogProvider();
        provider.setContextForTest(context);

        Uri apkUri = Mockito.mock(Uri.class);
        Mockito.when(apkUri.getPath()).thenReturn("/DebugToggle.apk");

        Uri textUri = Mockito.mock(Uri.class);
        Mockito.when(textUri.getPath()).thenReturn("/debugtoggle_audit_log.txt");

        // Test openFile with APK falling back to sourceDir
        ParcelFileDescriptor mockPfd = Mockito.mock(ParcelFileDescriptor.class);
        try (MockedStatic<ParcelFileDescriptor> pfdStatic = Mockito.mockStatic(ParcelFileDescriptor.class)) {
            pfdStatic.when(() -> ParcelFileDescriptor.open(Mockito.any(File.class), Mockito.eq(ParcelFileDescriptor.MODE_READ_ONLY)))
                    .thenReturn(mockPfd);

            ParcelFileDescriptor pfdLog = provider.openFile(textUri, "r");
            Assert.assertNotNull(pfdLog);

            ParcelFileDescriptor pfdApk = provider.openFile(apkUri, "r");
            Assert.assertNotNull(pfdApk);
        }

        // Test query with mocked MatrixCursor to execute row iteration
        MatrixCursor.RowBuilder mockRow = Mockito.mock(MatrixCursor.RowBuilder.class);
        try (MockedConstruction<MatrixCursor> mc = Mockito.mockConstruction(MatrixCursor.class, (mock, ctx) -> {
            Mockito.when(mock.newRow()).thenReturn(mockRow);
        })) {
            String[] projection = new String[] {
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    "other_column"
            };

            Cursor logCursor = provider.query(textUri, projection, null, null, null);
            Assert.assertNotNull(logCursor);
            Mockito.verify(mockRow, Mockito.atLeastOnce()).add("debugtoggle_audit_log.txt");

            Cursor apkCursor = provider.query(apkUri, projection, null, null, null);
            Assert.assertNotNull(apkCursor);
            Mockito.verify(mockRow, Mockito.atLeastOnce()).add("DebugToggle.apk");
        }

        // Test query with null context
        AuditLogProvider nullCtxProvider = new AuditLogProvider();
        Assert.assertNull(nullCtxProvider.query(textUri, null, null, null, null));

        // Test missing file throws FileNotFoundException
        File emptyCacheDir = tempFolder.newFolder("emptyCache");
        Mockito.when(context.getCacheDir()).thenReturn(emptyCacheDir);
        try {
            provider.openFile(textUri, "r");
            Assert.fail("Expected FileNotFoundException");
        } catch (FileNotFoundException expected) {
            // expected
        }
    }
}
