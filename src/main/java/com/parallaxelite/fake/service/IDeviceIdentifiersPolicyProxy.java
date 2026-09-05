package com.parallaxelite.fake.service;


import java.lang.reflect.Method;

import black.android.os.BRIDeviceIdentifiersPolicyServiceStub;
import black.android.os.BRServiceManager;
import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.fake.hook.BinderInvocationStub;
import com.parallaxelite.fake.hook.MethodHook;
import com.parallaxelite.fake.hook.ProxyMethod;
import com.parallaxelite.utils.Md5Utils;
import com.parallaxelite.utils.MethodParameterUtils;

/**
 * Created by @jagdish_vip on 4/3/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IDeviceIdentifiersPolicyProxy extends BinderInvocationStub {

    public IDeviceIdentifiersPolicyProxy() {
        super(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected Object getWho() {
        return BRIDeviceIdentifiersPolicyServiceStub.get().asInterface(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("device_identifiers");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getSerialForPackage")
    public static class x extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return Md5Utils.md5(ParallaxELiteInstaller.getHostPkg());
        }
    }
}
