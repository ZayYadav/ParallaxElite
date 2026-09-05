package com.parallaxelite.fake.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.lang.reflect.Method;
import java.util.List;

import black.android.app.BRNotificationManager;
import black.android.content.pm.BRParceledListSlice;
import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.fake.frameworks.BNotificationManager;
import com.parallaxelite.fake.hook.BinderInvocationStub;
import com.parallaxelite.fake.hook.MethodHook;
import com.parallaxelite.fake.hook.ProxyMethod;
import com.parallaxelite.utils.MethodParameterUtils;
import com.parallaxelite.utils.compat.BuildCompat;
import com.parallaxelite.utils.compat.ParceledListSliceCompat;

/**
 * Created by @jagdish_vip on 4/2/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class INotificationManagerProxy extends BinderInvocationStub {
    public static final String TAG = "INotificationManagerProxy";

    public INotificationManagerProxy() {
        super(BRNotificationManager.get().getService().asBinder());
    }

    @Override
    protected Object getWho() {
        return BRNotificationManager.get().getService();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRNotificationManager.get()._set_sService(getProxyInvocation());
        replaceSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//        Slog.d(TAG, "call: " + method.getName());
        MethodParameterUtils.replaceAllAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private static int findFirstIntParameter(Method method, Object[] args) {
        if (method == null || args == null) return -1;
        Class<?>[] types = method.getParameterTypes();
        int count = Math.min(types.length, args.length);
        for (int i = 0; i < count; i++) {
            if ((types[i] == int.class || types[i] == Integer.class)
                    && args[i] instanceof Number) {
                return i;
            }
        }
        return -1;
    }

    private static int findNearestIntBefore(
            Method method, Object[] args, int beforeIndex) {
        if (method == null || args == null || beforeIndex < 0) return -1;
        Class<?>[] types = method.getParameterTypes();
        int start = Math.min(Math.min(beforeIndex - 1, args.length - 1),
                types.length - 1);
        for (int i = start; i >= 0; i--) {
            if ((types[i] == int.class || types[i] == Integer.class)
                    && args[i] instanceof Number) {
                return i;
            }
        }
        return -1;
    }

    private static String findNearestStringBefore(
            Method method, Object[] args, int beforeIndex) {
        if (method == null || args == null || beforeIndex < 0) return null;
        Class<?>[] types = method.getParameterTypes();
        int start = Math.min(Math.min(beforeIndex - 1, args.length - 1),
                types.length - 1);
        for (int i = start; i >= 0; i--) {
            if (types[i] == String.class) {
                return args[i] instanceof String ? (String) args[i] : null;
            }
        }
        return null;
    }

    private static String findLastString(Object[] args) {
        if (args == null) return null;
        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i] instanceof String) {
                return (String) args[i];
            }
        }
        return null;
    }

    private static List<?> findParceledList(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null || arg instanceof String) continue;
            try {
                List<?> list = BRParceledListSlice.get(arg).getList();
                if (list != null) {
                    return list;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @ProxyMethod("getNotificationChannel")
    public static class GetNotificationChannel extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            NotificationChannel notificationChannel = BNotificationManager.get().getNotificationChannel((String) args[args.length - 1]);
            return notificationChannel;
        }
    }

    @ProxyMethod("getNotificationChannels")
    public static class GetNotificationChannels extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<NotificationChannel> notificationChannels = BNotificationManager.get().getNotificationChannels(BActivityThread.getAppPackageName());
            return ParceledListSliceCompat.create(notificationChannels);
        }
    }

    @ProxyMethod("cancelNotificationWithTag")
    public static class CancelNotificationWithTag extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int idIndex = findFirstIntParameter(method, args);
            if (idIndex < 0) {
                return method.invoke(who, args);
            }
            int id = ((Number) args[idIndex]).intValue();
            String tag = findNearestStringBefore(method, args, idIndex);
            BNotificationManager.get().cancelNotificationWithTag(id, tag);
            return null;
        }
    }

    @ProxyMethod("enqueueNotificationWithTag")
    public static class EnqueueNotificationWithTag extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Notification notification =
                    MethodParameterUtils.getFirstParam(args, Notification.class);
            int notificationIndex =
                    MethodParameterUtils.getIndex(args, Notification.class);
            int idIndex = findNearestIntBefore(method, args, notificationIndex);
            if (notification == null || idIndex < 0) {
                return method.invoke(who, args);
            }
            int id = ((Number) args[idIndex]).intValue();
            String tag = findNearestStringBefore(method, args, idIndex);
            BNotificationManager.get().enqueueNotificationWithTag(id, tag, notification);
            return null;
        }
    }

    @ProxyMethod("createNotificationChannels")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static class CreateNotificationChannels extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<?> list = findParceledList(args);
            if (list == null) {
                return method.invoke(who, args);
            }
            for (Object o : list) {
                if (o instanceof NotificationChannel) {
                    BNotificationManager.get().createNotificationChannel(
                            (NotificationChannel) o);
                }
            }
            return null;
        }
    }

    @ProxyMethod("deleteNotificationChannel")
    public static class DeleteNotificationChannel extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String id = findLastString(args);
            if (id == null) {
                return method.invoke(who, args);
            }
            BNotificationManager.get().deleteNotificationChannel(id);
            return null;
        }
    }

    @ProxyMethod("createNotificationChannelGroups")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static class CreateNotificationChannelGroups extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<?> list = findParceledList(args);
            if (list == null) {
                return method.invoke(who, args);
            }
            for (Object o : list) {
                if (o instanceof NotificationChannelGroup) {
                    BNotificationManager.get().createNotificationChannelGroup(
                            (NotificationChannelGroup) o);
                }
            }
            return null;
        }
    }

    @ProxyMethod("deleteNotificationChannelGroup")
    public static class DeleteNotificationChannelGroup extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String id = findLastString(args);
            if (id == null) {
                return method.invoke(who, args);
            }
            BNotificationManager.get().deleteNotificationChannelGroup(id);
            return null;
        }
    }

    @ProxyMethod("getNotificationChannelGroups")
    public static class GetNotificationChannelGroups extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<NotificationChannelGroup> notificationChannelGroups = BNotificationManager.get().getNotificationChannelGroups(BActivityThread.getAppPackageName());
            return ParceledListSliceCompat.create(notificationChannelGroups);
        }
    }
}
