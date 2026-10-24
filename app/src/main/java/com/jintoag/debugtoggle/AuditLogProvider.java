package com.jintoag.debugtoggle;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public class AuditLogProvider extends ContentProvider {

    public static final String AUTHORITY = "com.jintoag.debugtoggle.logs";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/audit_log.txt");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null) {
            throw new FileNotFoundException("Context unavailable");
        }
        File file = new File(getContext().getCacheDir(), "debugtoggle_audit_log.txt");
        if (!file.exists()) {
            throw new FileNotFoundException("Audit log file does not exist: " + file.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (getContext() == null) return null;
        File file = new File(getContext().getCacheDir(), "debugtoggle_audit_log.txt");
        String[] columns = (projection != null) ? projection : new String[] {
                android.provider.OpenableColumns.DISPLAY_NAME,
                android.provider.OpenableColumns.SIZE
        };
        android.database.MatrixCursor cursor = new android.database.MatrixCursor(columns, 1);
        android.database.MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : columns) {
            if (android.provider.OpenableColumns.DISPLAY_NAME.equals(col)) {
                row.add("debugtoggle_audit_log.txt");
            } else if (android.provider.OpenableColumns.SIZE.equals(col)) {
                row.add(file.exists() ? file.length() : 0L);
            } else {
                row.add(null);
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
