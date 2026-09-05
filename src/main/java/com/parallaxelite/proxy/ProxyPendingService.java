package com.parallaxelite.proxy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.proxy.record.ProxyPendingRecord;

/**
 * Internal bridge for PendingIntent.getService().
 *
 * The real PendingIntent belongs to the host package, while the original target
 * remains inside the virtual namespace. When Android fires the host PendingIntent
 * this service forwards the original Intent to the virtual ActivityManager.
 */
public class ProxyPendingService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ProxyPendingRecord record = ProxyPendingRecord.create(intent);
        if (record.mTarget != null) {
            try {
                record.mTarget.setExtrasClassLoader(getClassLoader());
                String resolvedType = record.mTarget.resolveTypeIfNeeded(
                        getContentResolver());
                ParallaxELiteInstaller.getBActivityManager().startService(
                        record.mTarget,
                        resolvedType,
                        false,
                        record.mUserId);
            } catch (Throwable ignored) {
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
