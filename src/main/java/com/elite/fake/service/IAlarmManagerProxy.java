package com.elite.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import black.android.app.BRIAlarmManagerStub;
import black.android.os.BRServiceManager;
import com.elite.fake.hook.BinderInvocationStub;
import com.elite.fake.hook.MethodHook;
import com.elite.fake.hook.ProxyMethod;
import com.elite.utils.MethodParameterUtils;

/**
 * AlarmManager bridge.
 *
 * PendingIntents are virtualized at IActivityManager.getIntentSender, so alarms
 * can be scheduled by the real system instead of being silently discarded.
 */
public class IAlarmManagerProxy extends BinderInvocationStub {

    public IAlarmManagerProxy() {
        super(BRServiceManager.get().getService(Context.ALARM_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAlarmManagerStub.get().asInterface(
                BRServiceManager.get().getService(Context.ALARM_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstAppPkg(args);
        MethodParameterUtils.replaceAllVirtualUids(args);
        return super.invoke(proxy, method, args);
    }

    @ProxyMethod("set")
    public static class Set extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
