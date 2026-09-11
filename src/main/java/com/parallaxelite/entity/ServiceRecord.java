package com.parallaxelite.entity;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Milk on 4/7/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ServiceRecord {
    private volatile Service mService;
    // Binder death callbacks can arrive on Binder threads while service bind/unbind
    // runs on the main thread. Never mutate a plain HashMap from both paths.
    private final Map<Intent.FilterComparison, BoundInfo> mBounds = new ConcurrentHashMap<>();
    private volatile boolean rebind;
    private volatile int mStartId;

    public class BoundInfo {
        private volatile IBinder mIBinder;
        private final AtomicInteger mBindCount = new AtomicInteger(0);

        public int incrementAndGetBindCount() {
            return mBindCount.incrementAndGet();
        }

        public int decrementAndGetBindCount() {
            while (true) {
                int current = mBindCount.get();
                if (current <= 0) {
                    return 0;
                }
                if (mBindCount.compareAndSet(current, current - 1)) {
                    return current - 1;
                }
            }
        }

        public IBinder getIBinder() {
            return mIBinder;
        }

        public void setIBinder(IBinder IBinder) {
            mIBinder = IBinder;
        }
    }

    public int getStartId() {
        return mStartId;
    }

    public void setStartId(int startId) {
        mStartId = startId;
    }

    public Service getService() {
        return mService;
    }

    public void setService(Service service) {
        mService = service;
    }

    public IBinder getBinder(Intent intent) {
        BoundInfo boundInfo = getOrCreateBoundInfo(intent);
        return boundInfo.getIBinder();
    }

    public boolean hasBinder(Intent intent) {
        BoundInfo boundInfo = getOrCreateBoundInfo(intent);
        return boundInfo.getIBinder() != null;
    }

    public void addBinder(Intent intent, final IBinder iBinder) {
        final Intent.FilterComparison filterComparison = new Intent.FilterComparison(intent);
        BoundInfo boundInfo = getOrCreateBoundInfo(intent);
        boundInfo.setIBinder(iBinder);
        if (iBinder == null) {
            return;
        }
        try {
            iBinder.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    try {
                        iBinder.unlinkToDeath(this, 0);
                    } catch (Throwable ignored) {
                    }
                    mBounds.remove(filterComparison);
                }
            }, 0);
        } catch (RemoteException e) {
            // The binder died before registration completed. Do not keep a stale
            // binder in the bound-service cache.
            mBounds.remove(filterComparison);
        }
    }

    public int incrementAndGetBindCount(Intent intent) {
        BoundInfo boundInfo = getOrCreateBoundInfo(intent);
        return boundInfo.incrementAndGetBindCount();
    }

    public boolean decreaseConnectionCount(Intent intent) {
        Intent.FilterComparison filterComparison = new Intent.FilterComparison(intent);
        BoundInfo boundInfo = mBounds.get(filterComparison);
        if (boundInfo == null)
            return true;
        int i = boundInfo.decrementAndGetBindCount();
        return i <= 0;
    }

    public BoundInfo getOrCreateBoundInfo(Intent intent) {
        Intent.FilterComparison filterComparison = new Intent.FilterComparison(intent);
        BoundInfo boundInfo = mBounds.get(filterComparison);
        if (boundInfo != null) {
            return boundInfo;
        }
        BoundInfo created = new BoundInfo();
        BoundInfo existing = mBounds.putIfAbsent(filterComparison, created);
        return existing != null ? existing : created;
    }

    public boolean isRebind() {
        return rebind;
    }

    public void setRebind(boolean rebind) {
        this.rebind = rebind;
    }
}
