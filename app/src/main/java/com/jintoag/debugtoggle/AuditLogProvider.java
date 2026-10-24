package com.jintoag.debugtoggle;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public class AuditLogProvider extends ContentProvider {

    public static final String AUTHORITY = "com.jintoag.debugtoggle.logs";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/audit_log.txt");
    public static final Uri APK_CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/DebugToggle.apk");

    private Context contextOverride;

    void setContextForTest(Context context) {
        this.contextOverride = context;
    }

    private Context getSafeContext() {
        return contextOverride != null ? contextOverride : getContext();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context ctx = getSafeContext();
        if (ctx == null) {
            throw new FileNotFoundException("Context unavailable");
        }
        boolean isApk = uri != null && uri.getPath() != null && uri.getPath().endsWith(".apk");
        File file;
        if (isApk) {
            file = new File(ctx.getCacheDir(), "DebugToggle.apk");
            if (!file.exists()) {
                file = new File(ctx.getApplicationInfo().sourceDir);
            }
        } else {
            file = new File(ctx.getCacheDir(), "debugtoggle_audit_log.txt");
        }

        if (!file.exists()) {
            throw new FileNotFoundException("File does not exist: " + file.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        if (uri != null && uri.getPath() != null && uri.getPath().endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        return "text/plain";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getSafeContext();
        if (ctx == null) return null;
        boolean isApk = uri != null && uri.getPath() != null && uri.getPath().endsWith(".apk");
        File file;
        String defaultName;
        if (isApk) {
            file = new File(ctx.getCacheDir(), "DebugToggle.apk");
            if (!file.exists()) {
                file = new File(ctx.getApplicationInfo().sourceDir);
            }
            defaultName = "DebugToggle.apk";
        } else {
            file = new File(ctx.getCacheDir(), "debugtoggle_audit_log.txt");
            defaultName = "debugtoggle_audit_log.txt";
        }

        String[] columns = (projection != null) ? projection : new String[] {
                android.provider.OpenableColumns.DISPLAY_NAME,
                android.provider.OpenableColumns.SIZE
        };
        android.database.MatrixCursor cursor = new android.database.MatrixCursor(columns, 1);
        android.database.MatrixCursor.RowBuilder row = cursor.newRow();
        if (row != null) {
            for (String col : columns) {
                if (android.provider.OpenableColumns.DISPLAY_NAME.equals(col)) {
                    row.add(defaultName);
                } else if (android.provider.OpenableColumns.SIZE.equals(col)) {
                    row.add(file.exists() ? file.length() : 0L);
                } else {
                    row.add(null);
                }
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
