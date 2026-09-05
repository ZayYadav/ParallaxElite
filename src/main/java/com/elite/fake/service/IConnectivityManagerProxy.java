package com.elite.fake.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.elite.fake.hook.ScanClass;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import black.android.net.BRIConnectivityManagerStub;
import black.android.os.BRServiceManager;
import com.elite.fake.hook.BinderInvocationStub;
import com.elite.fake.hook.MethodHook;
import com.elite.fake.hook.ProxyMethod;
import com.elite.utils.Slog;

@ScanClass(VpnCommonProxy.class)
public class IConnectivityManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IConnectivityManagerProxy";

    public IConnectivityManagerProxy() {
        super(BRServiceManager.get().getService(Context.CONNECTIVITY_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIConnectivityManagerStub.get().asInterface(BRServiceManager.get().getService(Context.CONNECTIVITY_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
