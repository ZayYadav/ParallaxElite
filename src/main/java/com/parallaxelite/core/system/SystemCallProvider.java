package com.parallaxelite.core.system;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.parallaxelite.utils.Slog;
import com.parallaxelite.compat.oauth.TwitterKitExternalAuthBroker;
import com.parallaxelite.utils.compat.BundleCompat;

/**
 * Created by @jagdish_via on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class SystemCallProvider extends ContentProvider {
    public static final String TAG = "SystemCallProvider";

    @Override
    public boolean onCreate() {
        // A transient startup failure must not crash/poison the provider process.
        // VM calls below will retry initialization when the installer context is ready.
        initSystemSafely();
        return true;
    }

    private boolean initSystemSafely() {
        try {
            VBoxSystem system = VBoxSystem.getSystem();
            system.startup();
            return system.isStarted();
        } catch (Throwable e) {
            Slog.e(TAG, "System startup failed; keeping provider retryable", e);
            return false;
        }
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Slog.d(TAG, "call: " + method + ", " + extras);
        if (TwitterKitExternalAuthBroker.METHOD_BEGIN.equals(method)
                || TwitterKitExternalAuthBroker.METHOD_CANCEL.equals(method)
                || TwitterKitExternalAuthBroker.METHOD_COMPLETE.equals(method)) {
            return TwitterKitExternalAuthBroker.handleSystemCall(method, extras);
        }

        if ("VM".equals(method)) {
            Bundle bundle = new Bundle();
            if (!VBoxSystem.getSystem().isStarted() && !initSystemSafely()) {
                return bundle;
            }
            if (extras != null) {
                String name = extras.getString("_G_|_server_name_");
                if (name != null) {
                    IBinder binder = ServiceManager.getService(name);
                    if (binder != null && binder.isBinderAlive()) {
                        BundleCompat.putBinder(bundle, "_G_|_server_", binder);
                    }
                }
            }
            return bundle;
        }
        return super.call(method, arg, extras);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
