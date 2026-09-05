package com.parallaxelite.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import black.android.net.wifi.BRIWifiManagerStub;
import black.android.os.BRServiceManager;
import com.parallaxelite.fake.hook.BinderInvocationStub;
import com.parallaxelite.fake.hook.MethodHook;
import com.parallaxelite.fake.hook.ProxyMethod;

/**
 * Wi-Fi Binder bridge. Android's own permission/privacy policy is preserved;
 * virtualization does not fabricate SSID/BSSID/MAC values.
 */
public class IWifiManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IWifiManagerProxy";

    public IWifiManagerProxy() {
        super(BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIWifiManagerStub.get().asInterface(
                BRServiceManager.get().getService(Context.WIFI_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getConnectionInfo")
    public static class GetConnectionInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }
}
