package com.parallaxelite.fake.frameworks;

import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.ParameterizedType;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.utils.Reflector;

/**
 * Created by BlackBox on 2022/3/23.
 */
public abstract class BlackManager<Service extends IInterface> {
    public static final String TAG = "BlackManager";

    private final Object mServiceLock = new Object();
    private volatile Service mService;

    protected abstract String getServiceName();

    public Service getService() {
        Service service = mService;
        if (isServiceAlive(service)) {
            return service;
        }

        synchronized (mServiceLock) {
            service = mService;
            if (isServiceAlive(service)) {
                return service;
            }

            try {
                final IBinder remoteBinder = ParallaxELiteInstaller.get().getService(getServiceName());
                if (remoteBinder == null || !remoteBinder.pingBinder() || !remoteBinder.isBinderAlive()) {
                    mService = null;
                    return null;
                }

                final Service resolvedService = Reflector
                        .on(getTClass().getName() + "$Stub")
                        .method("asInterface", IBinder.class)
                        .call(remoteBinder);
                if (!isServiceAlive(resolvedService)) {
                    mService = null;
                    return null;
                }

                final IBinder resolvedBinder = resolvedService.asBinder();
                resolvedBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        try {
                            resolvedBinder.unlinkToDeath(this, 0);
                        } catch (Throwable ignored) {
                        }
                        synchronized (mServiceLock) {
                            // A delayed death callback from an old connection must not
                            // clear a newer healthy service that has already reconnected.
                            Service current = mService;
                            if (current == resolvedService
                                    || (current != null && current.asBinder() == resolvedBinder)) {
                                mService = null;
                            }
                        }
                    }
                }, 0);

                mService = resolvedService;
                return resolvedService;
            } catch (Throwable e) {
                mService = null;
                e.printStackTrace();
                return null;
            }
        }
    }

    private boolean isServiceAlive(Service service) {
        if (service == null) {
            return false;
        }
        try {
            IBinder binder = service.asBinder();
            return binder != null && binder.pingBinder() && binder.isBinderAlive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Class<Service> getTClass() {
        return (Class<Service>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
}
