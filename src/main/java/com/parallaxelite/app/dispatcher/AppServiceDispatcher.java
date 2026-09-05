package com.parallaxelite.app.dispatcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.entity.ServiceRecord;
import com.parallaxelite.entity.UnbindRecord;
import com.parallaxelite.proxy.record.ProxyServiceRecord;
import com.parallaxelite.utils.compat.ScopedClassLoader;


/**
 * Created by @jagdish_vip on 4/1/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class AppServiceDispatcher {
    private static final AppServiceDispatcher sServiceDispatcher = new AppServiceDispatcher();
    private final Map<Intent.FilterComparison, ServiceRecord> mService = new HashMap<>();
    private final Handler mHandler = ParallaxELiteInstaller.get().getHandler();

    public static AppServiceDispatcher get() {
        return sServiceDispatcher;
    }

    public IBinder onBind(Intent proxyIntent) {
        ProxyServiceRecord serviceRecord = ProxyServiceRecord.create(proxyIntent);
        Intent intent = serviceRecord.mServiceIntent;
        ServiceInfo serviceInfo = serviceRecord.mServiceInfo;

        if (intent == null || serviceInfo == null) {
            return null;
        }

        Service service = getOrCreateService(serviceRecord);
        if (service == null) {
            return null;
        }
        intent.setExtrasClassLoader(service.getClassLoader());

        ServiceRecord record = findRecord(intent);
        record.incrementAndGetBindCount(intent);

        if (record.hasBinder(intent)) {
            if (record.isRebind()) {
                try (ScopedClassLoader ignored =
                             ScopedClassLoader.enter(service.getClassLoader())) {
                    service.onRebind(intent);
                }
                record.setRebind(false);
            }
            return record.getBinder(intent);
        }

        try (ScopedClassLoader ignored = ScopedClassLoader.enter(service.getClassLoader())) {
            IBinder iBinder = service.onBind(intent);
            record.addBinder(intent, iBinder);
            return iBinder;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public int onStartCommand(Intent proxyIntent, int flags) {
        if (proxyIntent == null) {
            return Service.START_NOT_STICKY;
        }

        ProxyServiceRecord stubRecord = ProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return Service.START_NOT_STICKY;
        }

        Service service = getOrCreateService(stubRecord);
        if (service == null) {
            return Service.START_NOT_STICKY;
        }

        Intent guestIntent = stubRecord.mServiceIntent;
        guestIntent.setExtrasClassLoader(service.getClassLoader());

        ServiceRecord record = findRecord(guestIntent);
        if (record == null) {
            return Service.START_NOT_STICKY;
        }
        record.setStartId(stubRecord.mStartId);

        try (ScopedClassLoader ignored = ScopedClassLoader.enter(service.getClassLoader())) {
            int result = service.onStartCommand(guestIntent, flags, stubRecord.mStartId);
            // A sticky host proxy can be recreated with a null Intent, at which
            // point the guest routing record is unavailable. Redeliver the last
            // proxy Intent instead so the guest Service can be reconstructed.
            if (result == Service.START_STICKY
                    || result == Service.START_STICKY_COMPATIBILITY) {
                return Service.START_REDELIVER_INTENT;
            }
            return result;
        } catch (Throwable error) {
            error.printStackTrace();
            return Service.START_NOT_STICKY;
        }
    }

    public void onDestroy() {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onDestroy();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        mService.clear();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onConfigurationChanged(newConfig);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onLowMemory() {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onLowMemory();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onTrimMemory(int level) {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onTrimMemory(level);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onUnbind(Intent proxyIntent) {
        ProxyServiceRecord stubRecord = ProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return;
        }

        Intent intent = stubRecord.mServiceIntent;
        try {
            UnbindRecord unbindRecord = ParallaxELiteInstaller.getBActivityManager().onServiceUnbind(proxyIntent, BActivityThread.getUserId());
            if (unbindRecord == null) {
                return;
            }

            ServiceRecord record = findRecord(intent);
            if (record == null || record.getService() == null) {
                return;
            }

            Service service = record.getService();
            intent.setExtrasClassLoader(service.getClassLoader());

            // Android calls Service.onUnbind only when the final connection for
            // this Intent is gone. Its boolean return controls a later onRebind.
            boolean lastConnection = record.decreaseConnectionCount(intent);
            if (!lastConnection) {
                return;
            }

            boolean wantsRebind = false;
            try (ScopedClassLoader ignored = ScopedClassLoader.enter(service.getClassLoader())) {
                wantsRebind = service.onUnbind(intent);
            }
            record.setRebind(wantsRebind);

            if (unbindRecord.getStartId() == 0) {
                try (ScopedClassLoader ignored = ScopedClassLoader.enter(service.getClassLoader())) {
                    service.onDestroy();
                }
                ParallaxELiteInstaller.getBActivityManager().onServiceDestroy(
                        proxyIntent, BActivityThread.getUserId());
                mService.remove(new Intent.FilterComparison(intent));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public IBinder peekService(Intent intent) {
        ServiceRecord record = findRecord(intent);
        if (record == null) {
            return null;
        }
        return record.getBinder(intent);
    }

    public void stopService(Intent intent) {
        if (intent == null) {
            return;
        }

        ServiceRecord record = findRecord(intent);
        if (record == null) {
            return;
        }

        if (record.getService() != null) {
            boolean destroy = record.getStartId() > 0;
            try {
                if (destroy) {
                    mHandler.post(() -> record.getService().onDestroy());
                    ParallaxELiteInstaller.getBActivityManager().onServiceDestroy(intent, BActivityThread.getUserId());
                    mService.remove(new Intent.FilterComparison(intent));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private ServiceRecord findRecord(Intent intent) {
        return mService.get(new Intent.FilterComparison(intent));
    }

    private Service getOrCreateService(ProxyServiceRecord proxyServiceRecord) {
        Intent intent = proxyServiceRecord.mServiceIntent;
        ServiceInfo serviceInfo = proxyServiceRecord.mServiceInfo;
        IBinder token = proxyServiceRecord.mToken;

        ServiceRecord record = findRecord(intent);
        if (record != null && record.getService() != null) {
            return record.getService();
        }

        Service service = BActivityThread.currentActivityThread().createService(serviceInfo, token);
        if (service == null) {
            return null;
        }

        record = new ServiceRecord();
        record.setService(service);
        mService.put(new Intent.FilterComparison(intent), record);
        return service;
    }
}
