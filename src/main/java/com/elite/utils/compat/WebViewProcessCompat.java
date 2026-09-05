package com.elite.utils.compat;

import android.os.Build;
import android.webkit.WebView;

import com.elite.utils.Slog;

import java.util.Locale;

/**
 * Prepares a unique, filesystem-safe WebView data directory for each virtual
 * process. Android P+ forbids multiple processes from sharing one WebView data
 * directory.
 */
public final class WebViewProcessCompat {
    private static final String TAG = "WebViewProcessCompat";

    private WebViewProcessCompat() {
    }

    public static void prepare(int userId, String packageName, String processName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }

        String suffix = buildSuffix(userId, packageName, processName);
        try {
            WebView.setDataDirectorySuffix(suffix);
        } catch (IllegalStateException alreadyInitialized) {
            // A library/OEM may initialize WebView earlier than expected. Do not
            // crash application binding merely because the suffix can no longer
            // be changed in this process.
            Slog.w(TAG, "WebView already initialized before suffix setup");
        } catch (Throwable error) {
            Slog.w(TAG, "WebView suffix setup failed: "
                    + error.getClass().getSimpleName());
        }
    }

    static String buildSuffix(int userId, String packageName, String processName) {
        String raw = userId + "_" + safe(packageName) + "_" + safe(processName);
        // Keep path names reasonably small on OEM filesystems.
        return raw.length() <= 120 ? raw : raw.substring(0, 120);
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "default";
        }
        String lower = value.trim().toLowerCase(Locale.US);
        return lower.replaceAll("[^a-z0-9._-]", "_");
    }
}
