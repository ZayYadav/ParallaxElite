package com.parallaxelite.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.Toast;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.core.system.am.IRequestPermissionsResult;
import com.parallaxelite.utils.compat.BundleCompat;

/**
 * Host-side runtime permission bridge for legacy-target virtual applications.
 */
@TargetApi(Build.VERSION_CODES.M)
public class RequestPermissionsActivity extends Activity {
    private static final int REQUEST_PERMISSION_CODE = 996;

    public static void request(
            Context context,
            String[] permissions,
            IRequestPermissionsResult callback) {
        if (context == null || permissions == null || permissions.length == 0) {
            notifyFailure(callback, permissions);
            return;
        }

        Intent intent = new Intent();
        intent.setClassName(
                ParallaxELiteInstaller.getContext(),
                RequestPermissionsActivity.class.getName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("permissions", permissions);
        if (callback != null) {
            BundleCompat.putBinder(intent, "callback", callback.asBinder());
        }

        try {
            context.startActivity(intent);
        } catch (Throwable error) {
            notifyFailure(callback, permissions);
        }
    }

    private static void notifyFailure(
            IRequestPermissionsResult callback, String[] permissions) {
        if (callback == null) {
            return;
        }
        String[] safePermissions =
                permissions == null ? new String[0] : permissions;
        int[] denied = new int[safePermissions.length];
        for (int i = 0; i < denied.length; i++) {
            denied[i] = PackageManager.PERMISSION_DENIED;
        }
        try {
            callback.onResult(REQUEST_PERMISSION_CODE, safePermissions, denied);
        } catch (RemoteException ignored) {
        }
    }

    private IRequestPermissionsResult mCallBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        final String[] permissions = intent.getStringArrayExtra("permissions");
        IBinder binder = BundleCompat.getBinder(intent, "callback");
        if (binder == null || permissions == null || permissions.length == 0) {
            finish();
            return;
        }

        mCallBack = IRequestPermissionsResult.Stub.asInterface(binder);
        if (mCallBack == null) {
            finish();
            return;
        }

        try {
            requestPermissions(permissions, REQUEST_PERMISSION_CODE);
        } catch (Throwable error) {
            notifyFailure(mCallBack, permissions);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            final String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_PERMISSION_CODE) {
            return;
        }

        if (mCallBack != null) {
            try {
                boolean success =
                        mCallBack.onResult(requestCode, permissions, grantResults);
                if (!success) {
                    Toast.makeText(
                            this,
                            "Request permission failed.",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) {
            }
        }
        finish();
    }
}
