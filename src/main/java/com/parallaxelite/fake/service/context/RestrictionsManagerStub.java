package com.parallaxelite.fake.service.context;

import android.content.Context;
import black.android.content.BRIRestrictionsManagerStub;
import black.android.os.BRServiceManager;
import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.fake.hook.BinderInvocationStub;
import com.parallaxelite.fake.hook.MethodHook;
import com.parallaxelite.fake.hook.ProxyMethod;
import java.lang.reflect.Method;

public class RestrictionsManagerStub extends BinderInvocationStub {

    public RestrictionsManagerStub() {
        super(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIRestrictionsManagerStub.get().asInterface(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.RESTRICTIONS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getApplicationRestrictions")
    public static class GetApplicationRestrictions extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                args[0] = ParallaxELiteInstaller.getHostPkg();
            }
            return method.invoke(who, args);
        }
    }
}