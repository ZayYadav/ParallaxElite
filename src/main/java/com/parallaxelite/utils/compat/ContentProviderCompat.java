package com.parallaxelite.utils.compat;

import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

public class ContentProviderCompat {
    private static final String TAG = "ContentProviderCompat";
    private static final int MAX_BACKGROUND_RETRIES = 3;
    private static final long BASE_RETRY_DELAY_MS = 40L;

    public static Bundle call(Context context, String authority, String method, String arg, Bundle extras) {
        if (context == null || authority == null) {
            Log.e(TAG, "Provider call skipped: missing context/authority");
            return null;
        }
        try {
            Uri uri = Uri.parse("content://" + authority);
            return context.getContentResolver().call(uri, method, arg, extras);
        } catch (Exception e) {
            Log.e(TAG, "Provider call failed: " + authority + ", error: " + e.getMessage());
            return null;
        }
    }

    public static Bundle call(Context context, Uri uri, String method, String arg, Bundle extras, int retryCount) {
        if (context == null || uri == null) {
            Log.e(TAG, "Provider call skipped: missing context/uri");
            return null;
        }
        ContentProviderClient client = acquireContentProviderClientRetry(context, uri, retryCount);
        try {
            if (client == null) {
                Log.e(TAG, "Client is null for URI: " + uri);
                return null;
            }
            return client.call(method, arg, extras);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
            return null;
        } finally {
            releaseQuietly(client);
        }
    }

    private static ContentProviderClient acquireContentProviderClient(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        try {
            return context.getContentResolver().acquireUnstableContentProviderClient(uri);
        } catch (Exception e) {
            Log.e(TAG, "Acquire failed for URI: " + uri + ", error: " + e.getMessage());
            return null;
        }
    }

    public static ContentProviderClient acquireContentProviderClientRetry(Context context, Uri uri, int retryCount) {
        ContentProviderClient client = acquireContentProviderClient(context, uri);
        int retries = effectiveRetryCount(retryCount);
        for (int retry = 0; retry < retries && client == null; retry++) {
            SystemClock.sleep(retryDelayMs(retry));
            client = acquireContentProviderClient(context, uri);
        }
        return client;
    }

    public static ContentProviderClient acquireContentProviderClientRetry(Context context, String name, int retryCount) {
        ContentProviderClient client = acquireContentProviderClient(context, name);
        int retries = effectiveRetryCount(retryCount);
        for (int retry = 0; retry < retries && client == null; retry++) {
            SystemClock.sleep(retryDelayMs(retry));
            client = acquireContentProviderClient(context, name);
        }
        return client;
    }

    private static int effectiveRetryCount(int requested) {
        if (requested <= 0) {
            return 0;
        }
        // Never sleep the UI/game main thread. acquireUnstableContentProviderClient()
        // already performs the synchronous acquisition attempt; callers can retry on
        // a later frame instead of freezing gameplay for up to multiple seconds.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return 0;
        }
        return Math.min(requested, MAX_BACKGROUND_RETRIES);
    }

    private static long retryDelayMs(int retry) {
        return Math.min(BASE_RETRY_DELAY_MS << Math.min(retry, 2), 120L);
    }

    private static ContentProviderClient acquireContentProviderClient(Context context, String name) {
        if (context == null || name == null) {
            return null;
        }
        try {
            return context.getContentResolver().acquireUnstableContentProviderClient(name);
        } catch (Exception e) {
            Log.e(TAG, "Acquire failed for: " + name + ", error: " + e.getMessage());
            return null;
        }
    }

    private static void releaseQuietly(ContentProviderClient client) {
        if (client != null) {
            try {
                if (VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    client.close();
                } else {
                    client.release();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
