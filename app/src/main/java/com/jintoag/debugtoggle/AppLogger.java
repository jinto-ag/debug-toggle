package com.jintoag.debugtoggle;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class AppLogger {

    public static final int MAX_CAPACITY = 500;
    public static final long MAX_BYTE_CAPACITY = 262144L; // 256 KB memory cap
    private static final String TAG = "DebugToggle";

    // Privacy & ZDR redaction patterns
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b");
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern IPV6_PATTERN = Pattern.compile("(?i)(?:\\b(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}\\b|\\b[0-9a-f]{1,4}::[0-9a-f:.]*\\b|\\bfe80::[0-9a-f:.]*\\b|\\b::[0-9a-f:.]+\\b)");
    private static final Pattern SSID_PATTERN = Pattern.compile("(?i)\\b(?:ssid[:=\\s]+[\"']?)([^\"'\\r\\n,;]+)[\"']?");
    private static final Pattern ADB_KEY_PATTERN = Pattern.compile("(?i)\\b[A-Za-z0-9+/]{40,}={0,2}\\b|(?i)ssh-rsa\\s+[A-Za-z0-9+/=]+");

    public enum LogLevel {
        ALL(0),
        INFO(1),
        WARN(2),
        ERROR(3);

        final int severity;
        LogLevel(int severity) {
            this.severity = severity;
        }

        public boolean satisfies(LogLevel minLevel) {
            return this.severity >= minLevel.severity;
        }
    }

    public static class LogEntry {
        public final long timestamp;
        public final LogLevel level;
        public final String tag;
        public final String message;

        public LogEntry(long timestamp, LogLevel level, String tag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        public String getFormattedTimestamp() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            return sdf.format(new Date(timestamp));
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "[%s] [%s] [%s] %s",
                    getFormattedTimestamp(), level.name(), tag, message);
        }
    }

    private static final Deque<LogEntry> ringBuffer = new ArrayDeque<>(MAX_CAPACITY);
    private static long totalBytes = 0L;
    private static Context appContext = null;
    private static final String HISTORY_FILE = "audit_history.log";

    public static synchronized void init(Context context) {
        if (context == null) return;
        if (appContext == null) {
            appContext = context.getApplicationContext();
            loadHistory(appContext);
        }
        if (ringBuffer.isEmpty()) {
            i("System", "Audit logging engine ready (ZDR privacy filter active)");
        }
    }

    private static synchronized void loadHistory(Context context) {
        File file = new File(context.getFilesDir(), HISTORY_FILE);
        if (!file.exists()) return;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int firstPipe = line.indexOf('|');
                if (firstPipe == -1) continue;
                int secondPipe = line.indexOf('|', firstPipe + 1);
                if (secondPipe == -1) continue;
                int thirdPipe = line.indexOf('|', secondPipe + 1);
                if (thirdPipe == -1) continue;

                long ts = Long.parseLong(line.substring(0, firstPipe));
                LogLevel level = LogLevel.valueOf(line.substring(firstPipe + 1, secondPipe));
                String tag = line.substring(secondPipe + 1, thirdPipe);
                String msg = line.substring(thirdPipe + 1);

                int entryBytes = msg.length() + tag.length() + 32;
                while (!ringBuffer.isEmpty() && (ringBuffer.size() >= MAX_CAPACITY || (totalBytes + entryBytes > MAX_BYTE_CAPACITY))) {
                    LogEntry evicted = ringBuffer.pollFirst();
                    if (evicted != null) {
                        totalBytes = Math.max(0L, totalBytes - (evicted.message.length() + evicted.tag.length() + 32));
                    }
                }
                ringBuffer.addLast(new LogEntry(ts, level, tag, msg));
                totalBytes += entryBytes;
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void appendToHistory(LogEntry entry) {
        if (appContext == null) return;
        try {
            File file = new File(appContext.getFilesDir(), HISTORY_FILE);
            if (file.length() > MAX_BYTE_CAPACITY) {
                rewriteHistory(file);
                return;
            }
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                String line = entry.timestamp + "|" + entry.level.name() + "|" + entry.tag + "|" + entry.message + "\n";
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static synchronized void rewriteHistory(File file) {
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            for (LogEntry entry : ringBuffer) {
                String line = entry.timestamp + "|" + entry.level.name() + "|" + entry.tag + "|" + entry.message + "\n";
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized long getTotalBytes() {
        return totalBytes;
    }

    public static synchronized void i(String tag, String message) {
        logInternal(LogLevel.INFO, tag, message);
        try {
            Log.i(tag, message);
        } catch (Throwable ignored) {
            // Standalone JVM test runner fallback
        }
    }

    public static synchronized void w(String tag, String message) {
        logInternal(LogLevel.WARN, tag, message);
        try {
            Log.w(tag, message);
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void e(String tag, String message) {
        logInternal(LogLevel.ERROR, tag, message);
        try {
            Log.e(tag, message);
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void e(String tag, String message, Throwable t) {
        String fullMsg = message + (t != null ? " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")" : "");
        logInternal(LogLevel.ERROR, tag, fullMsg);
        try {
            Log.e(tag, fullMsg, t);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void logInternal(LogLevel level, String tag, String rawMessage) {
        String cleanMessage = sanitize(rawMessage);
        int entryBytes = cleanMessage.length() + tag.length() + 32;

        while (!ringBuffer.isEmpty() && (ringBuffer.size() >= MAX_CAPACITY || (totalBytes + entryBytes > MAX_BYTE_CAPACITY))) {
            LogEntry evicted = ringBuffer.pollFirst();
            if (evicted != null) {
                totalBytes = Math.max(0L, totalBytes - (evicted.message.length() + evicted.tag.length() + 32));
            }
        }
        LogEntry entry = new LogEntry(System.currentTimeMillis(), level, tag, cleanMessage);
        ringBuffer.addLast(entry);
        totalBytes += entryBytes;
        appendToHistory(entry);
    }

    public static String sanitize(String input) {
        if (input == null) return "";
        String sanitized = MAC_PATTERN.matcher(input).replaceAll("[REDACTED_MAC]");
        sanitized = IPV4_PATTERN.matcher(sanitized).replaceAll("[REDACTED_IP]");
        sanitized = IPV6_PATTERN.matcher(sanitized).replaceAll("[REDACTED_IPV6]");
        sanitized = SSID_PATTERN.matcher(sanitized).replaceAll("SSID: \"[REDACTED_SSID]\"");
        sanitized = ADB_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_ADB_KEY]");
        return sanitized;
    }

    public static synchronized List<LogEntry> getEntries(LogLevel minLevel, String searchQuery) {
        return getEntries(minLevel, searchQuery, true);
    }

    public static synchronized List<LogEntry> getEntries(LogLevel minLevel, String searchQuery, boolean newestFirst) {
        List<LogEntry> results = new ArrayList<>();
        String queryLower = (searchQuery != null) ? searchQuery.toLowerCase(Locale.US).trim() : "";

        if (newestFirst) {
            java.util.Iterator<LogEntry> it = ringBuffer.descendingIterator();
            while (it.hasNext()) {
                LogEntry entry = it.next();
                if (entry.level.satisfies(minLevel)) {
                    if (queryLower.isEmpty() ||
                            entry.message.toLowerCase(Locale.US).contains(queryLower) ||
                            entry.tag.toLowerCase(Locale.US).contains(queryLower)) {
                        results.add(entry);
                    }
                }
            }
        } else {
            for (LogEntry entry : ringBuffer) {
                if (entry.level.satisfies(minLevel)) {
                    if (queryLower.isEmpty() ||
                            entry.message.toLowerCase(Locale.US).contains(queryLower) ||
                            entry.tag.toLowerCase(Locale.US).contains(queryLower)) {
                        results.add(entry);
                    }
                }
            }
        }
        return results;
    }

    public static synchronized void clear() {
        clear(null);
    }

    public static synchronized void clear(Context context) {
        ringBuffer.clear();
        totalBytes = 0L;
        if (context != null) {
            try {
                File logFile = new File(context.getCacheDir(), "debugtoggle_audit_log.txt");
                if (logFile.exists()) {
                    logFile.delete();
                }
                File historyFile = new File(context.getFilesDir(), HISTORY_FILE);
                if (historyFile.exists()) {
                    historyFile.delete();
                }
            } catch (Exception ignored) {
            }
        }
    }

    static synchronized void resetForTest() {
        appContext = null;
        ringBuffer.clear();
        totalBytes = 0L;
    }

    public static synchronized File exportSanitizedLogs(Context context) throws IOException {
        File cacheDir = (context != null) ? context.getCacheDir() : new File("/tmp");
        File logFile = new File(cacheDir, "debugtoggle_audit_log.txt");

        StringBuilder sb = new StringBuilder();
        sb.append("=== DEBUG TOGGLE SANITIZED AUDIT LOGS ===\n");
        sb.append("Export Time: ").append(new Date().toString()).append("\n");
        sb.append("Total Entries: ").append(ringBuffer.size()).append("\n\n");

        for (LogEntry entry : ringBuffer) {
            sb.append(entry.toString()).append("\n");
        }

        try (FileOutputStream fos = new FileOutputStream(logFile)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return logFile;
    }
}
