package com.elite.fake.service;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.app.IServiceConnection;
import android.app.Notification;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import black.android.app.BRActivityManagerNative;
import black.android.app.BRActivityManagerOreo;
import black.android.app.BRLoadedApkReceiverDispatcher;
import black.android.app.BRLoadedApkReceiverDispatcherInnerReceiver;
import black.android.app.BRLoadedApkServiceDispatcher;
import black.android.app.BRLoadedApkServiceDispatcherInnerConnection;
import black.android.content.BRContentProviderNative;
import black.android.content.pm.BRUserInfo;
import black.android.util.BRSingleton;
import com.elite.EliteInstaller;
import com.elite.app.BActivityThread;
import com.elite.core.env.AppSystemEnv;
import com.elite.core.system.DaemonService;
import com.elite.core.system.user.BUserHandle;
import com.elite.entity.AppConfig;
import com.elite.entity.am.RunningAppProcessInfo;
import com.elite.entity.am.RunningServiceInfo;
import com.elite.fake.delegate.ContentProviderDelegate;
import com.elite.fake.delegate.InnerReceiverDelegate;
import com.elite.fake.delegate.ServiceConnectionDelegate;
import com.elite.fake.frameworks.BActivityManager;
import com.elite.fake.frameworks.BPackageManager;
import com.elite.fake.hook.ClassInvocationStub;
import com.elite.fake.hook.MethodHook;
import com.elite.fake.hook.ProxyMethod;
import com.elite.fake.hook.ProxyMethods;
import com.elite.fake.hook.ScanClass;
import com.elite.fake.service.base.PkgMethodProxy;
import com.elite.fake.service.context.providers.ContentProviderStub;
import com.elite.fake.service.context.providers.SystemProviderStub;
import com.elite.proxy.ProxyManifest;
import com.elite.proxy.record.ProxyBroadcastRecord;
import com.elite.proxy.record.ProxyPendingRecord;
import com.elite.utils.ArrayUtils;
import com.elite.utils.ComponentUtils;
import com.elite.utils.FileUtils;
import com.elite.utils.MethodParameterUtils;
import com.elite.utils.Reflector;
import com.elite.utils.Slog;
import com.elite.utils.compat.ActivityManagerCompat;
import com.elite.utils.compat.BuildCompat;
import com.elite.utils.compat.ParceledListSliceCompat;
import com.elite.utils.compat.ReceiverCompat;
import com.elite.utils.compat.TaskDescriptionCompat;
import com.elite.utils.compat.VirtualPermissionCompat;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import org.lsposed.lsparanoid.Obfuscate;
/**
 * Created by @jagdish_vip on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
@Obfuscate
@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityManagerProxy extends ClassInvocationStub {
    public static final String TAG = "ActivityManagerStub";

    @Override
    protected Object getWho() {
        Object iActivityManager = null;
        if (BuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (BuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        return BRSingleton.get(iActivityManager).get();
    }

    @Override
    protected void inject(Object base, Object proxy) {
        Object iActivityManager = null;
        if (BuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (BuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        BRSingleton.get(iActivityManager)._set_mInstance(proxy);
    }

    @Override
    public boolean isBadEnv() {
        return getProxyInvocation() != getWho();
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new PkgMethodProxy("getAppStartMode"));
        addMethodHook(new PkgMethodProxy("setAppLockedVerifying"));
        addMethodHook(new PkgMethodProxy("reportJunkFromApp"));
    }

    @ProxyMethod("getContentProvider")
	public static class GetContentProvider extends MethodHook {

		@Override
		protected Object hook(Object who, Method method, Object[] args) throws Exception {

			int authIndex = getAuthIndex();
			Object auth = args[authIndex];

			if (!(auth instanceof String)) {
				return method.invoke(who, args);
			}

			String authority = (String) auth;

			if (ProxyManifest.isProxy(authority)) {
				return method.invoke(who, args);
			}

			if (BuildCompat.isQ() || BuildCompat.isR()) {
				if (args.length > 1 && args[1] instanceof String) {
					args[1] = EliteInstaller.getHostPkg();
				}
			}

			if ("settings".equals(authority) || "media".equals(authority) || "telephony".equals(authority)) {
				Object content = method.invoke(who, args);
				if (content != null) {
					ContentProviderDelegate.update(content, authority);
				}
				return content;
			}

			ProviderInfo providerInfo = EliteInstaller.getBPackageManager().resolveContentProvider(authority, FileUtils.FileMode.MODE_IWUSR, BActivityThread.getUserId());

			if (providerInfo == null) {
				return method.invoke(who, args);
			}

			IBinder providerBinder = null;

			if (BActivityThread.getAppPid() != -1) {

				AppConfig appConfig = EliteInstaller.getBActivityManager().initProcess(providerInfo.packageName, providerInfo.processName, BActivityThread.getUserId());

				if (appConfig == null) {
					return method.invoke(who, args);
				}

				if (appConfig.bpid != BActivityThread.getAppPid()) {
					providerBinder = EliteInstaller.getBActivityManager().acquireContentProviderClient(providerInfo);
				}

				if (providerBinder == null) {
					return method.invoke(who, args);
				}

				args[authIndex] = ProxyManifest.getProxyAuthorities(appConfig.bpid);

				int userIndex = getUserIndex();
				if (args.length > userIndex) {
					args[userIndex] = EliteInstaller.getHostUserId();
				}
			}

			Object content = method.invoke(who, args);

			if (content == null) {
				return null;
			}

			try {
				Reflector.with(content).field("info").set(providerInfo);
			} catch (Throwable ignored) { }

			try {
				Reflector.with(content).field("provider").set(new ContentProviderStub().wrapper(BRContentProviderNative.get().asInterface(providerBinder),BActivityThread.getAppPackageName()));
			} catch (Throwable ignored) { }
			return content;
		}

		private int getAuthIndex() {
			if (BuildCompat.isQ() || BuildCompat.isR()) {
				return 2;
			}
			return 1;
		}

		private int getUserIndex() {
			return getAuthIndex() + 1;
		}
	}
    
    @ProxyMethod("startService")
    public static class StartService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int intentIndex = findIntentIndex(args);
            if (intentIndex < 0) {
                return method.invoke(who, args);
            }

            Intent intent = (Intent) args[intentIndex];
            String resolvedType = findStringAfter(args, intentIndex);
            ResolveInfo resolveInfo = EliteInstaller.getBPackageManager().resolveService(
                    intent, 0, resolvedType, BActivityThread.getUserId());
            if (resolveInfo == null) {
                return method.invoke(who, args);
            }

            boolean requireForeground = findBooleanAfter(args, intentIndex);
            return EliteInstaller.getBActivityManager().startService(
                    intent, resolvedType, requireForeground, BActivityThread.getUserId());
        }
    }

    @ProxyMethod("stopService")
    public static class StopService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int intentIndex = findIntentIndex(args);
            if (intentIndex < 0) {
                return method.invoke(who, args);
            }
            Intent intent = (Intent) args[intentIndex];
            String resolvedType = findStringAfter(args, intentIndex);
            return EliteInstaller.getBActivityManager().stopService(
                    intent, resolvedType, BActivityThread.getUserId());
        }
    }

    private static int findIntentIndex(Object[] args) {
        if (args == null) return -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Intent) {
                return i;
            }
        }
        return -1;
    }

    private static String findStringAfter(Object[] args, int afterIndex) {
        if (args == null) return null;
        for (int i = Math.max(0, afterIndex + 1); i < args.length; i++) {
            if (args[i] instanceof String) {
                return (String) args[i];
            }
        }
        return null;
    }

    private static boolean findBooleanAfter(Object[] args, int afterIndex) {
        if (args == null) return false;
        for (int i = Math.max(0, afterIndex + 1); i < args.length; i++) {
            if (args[i] instanceof Boolean) {
                return (Boolean) args[i];
            }
        }
        return false;
    }

    @ProxyMethod("stopServiceToken")
    public static class StopServiceToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            IBinder token = (IBinder) args[1];
            EliteInstaller.getBActivityManager().stopServiceToken(componentName, token, BActivityThread.getUserId());
            return true;
        }
    }
    
    @ProxyMethod("bindService")
    public static class BindService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int intentIndex = findIntentIndex(args);
            int connectionIndex = MethodParameterUtils.getIndex(args, IServiceConnection.class);
            if (intentIndex < 0) {
                return method.invoke(who, args);
            }

            Intent intent = (Intent) args[intentIndex];
            String resolvedType = findStringAfter(args, intentIndex);
            IServiceConnection connection = connectionIndex >= 0
                    ? (IServiceConnection) args[connectionIndex] : null;

            int userId = intent.getIntExtra("_G_|_UserId", -1);
            userId = userId == -1 ? BActivityThread.getUserId() : userId;

            ResolveInfo resolveInfo = EliteInstaller.getBPackageManager().resolveService(
                    intent, 0, resolvedType, userId);
            ComponentName component = intent.getComponent();
            boolean openSystemComponent = component != null
                    && AppSystemEnv.isOpenPackage(component);
            if (resolveInfo == null && !openSystemComponent) {
                return method.invoke(who, args);
            }

            Intent bindService = EliteInstaller.getBActivityManager().bindService(
                    intent,
                    connection == null ? null : connection.asBinder(),
                    resolvedType,
                    userId);

            if (connection != null && connectionIndex >= 0) {
                if (intent.getComponent() == null
                        && resolveInfo != null
                        && resolveInfo.serviceInfo != null) {
                    intent.setComponent(new ComponentName(
                            resolveInfo.serviceInfo.packageName,
                            resolveInfo.serviceInfo.name));
                }

                IServiceConnection proxy =
                        ServiceConnectionDelegate.createProxy(connection, intent);
                args[connectionIndex] = proxy;

                try {
                    WeakReference<?> weakReference =
                            BRLoadedApkServiceDispatcherInnerConnection
                                    .get(connection).mDispatcher();
                    if (weakReference != null && weakReference.get() != null) {
                        BRLoadedApkServiceDispatcher.get(weakReference.get())
                                ._set_mConnection(proxy);
                    }
                } catch (Throwable ignored) {
                    // OEM LoadedApk internals can differ. The Binder call still
                    // receives the proxy connection even if local dispatcher
                    // reflection is unavailable.
                }
            }

            if (bindService != null) {
                args[intentIndex] = bindService;
                return method.invoke(who, args);
            }
            return 0;
        }

        @Override
        protected boolean isEnable() {
            return EliteInstaller.get().isBlackProcess()
                    || EliteInstaller.get().isServerProcess();
        }
    }

    @ProxyMethod("bindServiceInstance")
    public static class BindServiceInstance extends BindService {
    }

    @ProxyMethod("bindIsolatedService")
    public static class BindIsolatedService extends BindService {
    }

    @ProxyMethod("unbindService")
    public static class UnbindService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IServiceConnection iServiceConnection = (IServiceConnection) args[0];
            if (iServiceConnection == null) {
                return method.invoke(who, args);
            }
            EliteInstaller.getBActivityManager().unbindService(iServiceConnection.asBinder(), BActivityThread.getUserId());
            ServiceConnectionDelegate delegate = ServiceConnectionDelegate.getDelegate(iServiceConnection.asBinder());
            if (delegate != null) {
                args[0] = delegate;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getRunningAppProcesses")
    public static class GetRunningAppProcesses extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            RunningAppProcessInfo runningAppProcesses = BActivityManager.get().getRunningAppProcesses(BActivityThread.getAppPackageName(), BActivityThread.getUserId());
            if (runningAppProcesses == null) {
                return new ArrayList<>();
            }
            return runningAppProcesses.mAppProcessInfoList;
        }
    }

    @ProxyMethod("getServices")
    public static class GetServices extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            RunningServiceInfo runningServices = BActivityManager.get().getRunningServices(BActivityThread.getAppPackageName(), BActivityThread.getUserId());
            if (runningServices == null) {
                return new ArrayList<>();
            }
            return runningServices.mRunningServiceInfoList;
        }
    }

    @ProxyMethod("getIntentSender")
    public static class GetIntentSender extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int type = (int) args[0];
            Intent[] intents = (Intent[]) args[getIntentsIndex(args)];
            MethodParameterUtils.replaceFirstAppPkg(args);

            for (int i = 0; i < intents.length; i++) {
                Intent intent = intents[i];
                switch (type) {
                    case ActivityManagerCompat.INTENT_SENDER_ACTIVITY:
                        Intent shadow = new Intent();
                        shadow.setComponent(new ComponentName(EliteInstaller.getHostPkg(), ProxyManifest.getProxyPendingActivity(BActivityThread.getAppPid())));
                        ProxyPendingRecord.saveStub(shadow, intent, BActivityThread.getUserId());
                        intents[i] = shadow;
                        break;
                }
            }

            // Android 12 (API 31) compat: PendingIntent requires FLAG_IMMUTABLE or FLAG_MUTABLE
            if (BuildCompat.isS()) {
                int flagsIndex = getIntentsIndex(args) + 2;
                if (flagsIndex < args.length && args[flagsIndex] instanceof Integer) {
                    int flags = (int) args[flagsIndex];
                    if ((flags & (0x4000000 | 0x2000000)) == 0) {
                        flags |= 0x4000000; // FLAG_IMMUTABLE
                        args[flagsIndex] = flags;
                    }
                }
            }

            IInterface invoke = (IInterface) method.invoke(who, args);
            if (invoke != null) {
                String[] packagesForUid = BPackageManager.get().getPackagesForUid(BActivityThread.getCallingBUid());
                if (packagesForUid.length < 1) {
                    packagesForUid = new String[]{EliteInstaller.getHostPkg()};
                }
                EliteInstaller.getBActivityManager().getIntentSender(invoke.asBinder(), packagesForUid[0], BActivityThread.getCallingBUid());
            }
            return invoke;
        }

        private int getIntentsIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent[]) {
                    return i;
                }
            }
            if (BuildCompat.isR()) {
                return 6;
            } else {
                return 5;
            }
        }
    }

    @ProxyMethod("getPackageForIntentSender")
    public static class getPackageForIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return EliteInstaller.getBActivityManager().getPackageForIntentSender(invoke.asBinder());
        }
    }

    @ProxyMethod("getUidForIntentSender")
    public static class getUidForIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return EliteInstaller.getBActivityManager().getUidForIntentSender(invoke.asBinder());
        }
    }

    @ProxyMethod("getIntentSenderWithSourceToken")
    public static class GetIntentSenderWithSourceToken extends GetIntentSender {
    }

    @ProxyMethod("getIntentSenderWithFeature")
    public static class GetIntentSenderWithFeature extends GetIntentSender {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("broadcastIntent")
    public static class BroadcastIntent extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int intentIndex = getIntentIndex(args);
            Intent intent = (Intent) args[intentIndex];
            String resolvedType = (String) args[intentIndex + 1];

            Intent proxyIntent = EliteInstaller.getBActivityManager().sendBroadcast(intent, resolvedType, BActivityThread.getUserId());
            if (proxyIntent != null) {
                proxyIntent.setExtrasClassLoader(BActivityThread.getApplication().getClassLoader());

                ProxyBroadcastRecord.saveStub(proxyIntent, intent, BActivityThread.getUserId());
                args[intentIndex] = proxyIntent;
            }
            // ignore permission
            for (int i = 0; i < args.length; i++) {
                Object o = args[i];
                if (o instanceof String[]) {
                    args[i] = null;
                }
            }
            return method.invoke(who, args);
        }

        int getIntentIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Intent) {
                    return i;
                }
            }
            return 1;
        }
    }

    @ProxyMethod("unregisterReceiver")
    public static class unregisterReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishReceiver")
    public static class finishReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("publishService")
    public static class PublishService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("peekService")
    public static class PeekService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            return EliteInstaller.getBActivityManager().peekService(intent, resolvedType, BActivityThread.getUserId());
        }
    }

    // todo
    @ProxyMethod("sendIntentSender")
    public static class SendIntentSender extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    /**
     * registerReceiver signatures changed repeatedly from Android 10 through 16.
     * Locate receiver/user/flags by runtime type instead of fixed array offsets.
     */
    @ProxyMethods({"registerReceiver", "registerReceiverWithFeature"})
    public static class RegisterReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args == null || method == null) {
                return method == null ? null : method.invoke(who, args);
            }

            MethodParameterUtils.replaceFirstAppPkg(args);

            int receiverIndex = MethodParameterUtils.getIndex(args, IIntentReceiver.class);
            if (receiverIndex >= 0 && args[receiverIndex] instanceof IIntentReceiver) {
                IIntentReceiver intentReceiver = (IIntentReceiver) args[receiverIndex];
                IIntentReceiver proxy = InnerReceiverDelegate.createProxy(intentReceiver);

                try {
                    WeakReference<?> weakReference =
                            BRLoadedApkReceiverDispatcherInnerReceiver.get(intentReceiver).mDispatcher();
                    if (weakReference != null && weakReference.get() != null) {
                        BRLoadedApkReceiverDispatcher.get(weakReference.get())
                                ._set_mIIntentReceiver(proxy);
                    }
                } catch (Throwable ignored) {
                    // OEM ActivityThread internals vary. The system call still
                    // receives the proxy even when the local dispatcher differs.
                }

                args[receiverIndex] = proxy;
            }

            normalizeReceiverUserAndFlags(method, args, receiverIndex);
            // Keep the guest's requiredPermission intact. Clearing it broadens
            // who can send matching broadcasts and is not needed for virtualization.
            return method.invoke(who, args);
        }

        private static void normalizeReceiverUserAndFlags(
                Method method, Object[] args, int receiverIndex) {
            Class<?>[] types = method.getParameterTypes();
            int count = Math.min(types.length, args.length);
            int[] intIndexes = new int[count];
            int intCount = 0;

            for (int i = Math.max(0, receiverIndex + 1); i < count; i++) {
                if (types[i] == int.class || types[i] == Integer.class) {
                    intIndexes[intCount++] = i;
                }
            }

            if (intCount == 0) {
                return;
            }

            // Modern registerReceiver(..., userId, flags): userId is the
            // penultimate int and receiver flags are last.
            if (intCount >= 2) {
                int userIdIndex = intIndexes[intCount - 2];
                if (args[userIdIndex] instanceof Integer) {
                    args[userIdIndex] = EliteInstaller.getHostUserId();
                }

                int flagsIndex = intIndexes[intCount - 1];
                if (args[flagsIndex] instanceof Integer) {
                    args[flagsIndex] = ReceiverCompat.ensureExplicitExportFlag(
                            (Integer) args[flagsIndex]);
                }
                return;
            }

            // Older layouts expose only userId after the receiver.
            int userIdIndex = intIndexes[0];
            if (args[userIdIndex] instanceof Integer) {
                args[userIdIndex] = EliteInstaller.getHostUserId();
            }
        }
    }

    //这里需要修复
    @ProxyMethod("grantUriPermission")
    public static class GrantUriPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastUid(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setServiceForeground")
    public static class setServiceForeground extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("getHistoricalProcessExitReasons")
    public static class getHistoricalProcessExitReasons extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ProxyMethod("getCurrentUser")
    public static class getCurrentUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BRUserInfo.get()._new(BActivityThread.getUserId(), "EliteCore", BRUserInfo.get().FLAG_PRIMARY());
        }
    }

    @ProxyMethods({"checkPermission", "checkPermissionForDevice"})
    public static class checkPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : null;
            if (VirtualPermissionCompat.shouldGrantDeclaredNetworkPermission(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            replacePermissionCheckUid(args);
            if (Manifest.permission.ACCOUNT_MANAGER.equals(permission)
                    || Manifest.permission.SEND_SMS.equals(permission)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }

        private static void replacePermissionCheckUid(Object[] args) {
            // Android 16 checkPermissionForDevice has uid at index 2; the final int
            // is deviceId and must not be rewritten as a uid.
            if (args == null || args.length <= 2 || !(args[2] instanceof Integer)) {
                return;
            }
            int uid = (Integer) args[2];
            if (uid == BActivityThread.getBUid()) {
                args[2] = EliteInstaller.getHostUid();
            }
        }
    }

    @ProxyMethod("checkUriPermission")
    public static class checkUriPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return PackageManager.PERMISSION_GRANTED;
        }
    }

    // for < Android 10 and OEM variants that still route through AMS
    @ProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof ActivityManager.TaskDescription) {
                        args[i] = TaskDescriptionCompat.fix(
                                (ActivityManager.TaskDescription) args[i]);
                        break;
                    }
                }
            }
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("setRequestedOrientation")
    public static class setRequestedOrientation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    @ProxyMethod("registerUidObserver")
    public static class registerUidObserver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("unregisterUidObserver")
    public static class unregisterUidObserver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("updateConfiguration")
    public static class updateConfiguration extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

}
