package com.parallaxelite.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.VersionedPackage;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.parallaxelite.fake.frameworks.BPackageManager;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.core.GmsCore;
import com.parallaxelite.core.env.AppSystemEnv;

import com.parallaxelite.fake.hook.BinderInvocationStub;
import com.parallaxelite.fake.hook.MethodHook;
import com.parallaxelite.fake.hook.ProxyMethod;
import com.parallaxelite.fake.service.base.PkgMethodProxy;
import com.parallaxelite.fake.service.base.ValueMethodProxy;
import com.parallaxelite.utils.MethodParameterUtils;
import com.parallaxelite.utils.Reflector;
import com.parallaxelite.utils.Slog;
import com.parallaxelite.utils.compat.BuildCompat;
import com.parallaxelite.utils.compat.ParceledListSliceCompat;
import com.parallaxelite.utils.compat.VirtualPermissionCompat;

/**
 * Created by Milk on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class IPackageManagerProxy extends BinderInvocationStub {
    public static final String TAG = "PackageManagerStub";

    public IPackageManagerProxy() {
        super(BRActivityThread.get().sPackageManager().asBinder());
    }

    @Override
    protected Object getWho() {
        return BRActivityThread.get().sPackageManager();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRActivityThread.get()._set_sPackageManager(proxyInvocation);
        replaceSystemService("package");
        Object systemContext = BRActivityThread.get(ParallaxELiteInstaller.mainThread()).getSystemContext();
        BRContextImpl.get(systemContext).getPackageManager();
        PackageManager mPackageManager = BRContextImpl.get(systemContext).mPackageManager();
        if (mPackageManager != null) {
            try {
                Reflector.on("android.app.ApplicationPackageManager").field("mPM").set(mPackageManager, proxyInvocation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        if (BuildCompat.isT()) {
            return;
        }
    }
    
    @ProxyMethod("resolveIntent")
    public static class ResolveIntent extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            if (intent == null) {
                return method.invoke(who, args);
            }
            int intentIndex = MethodParameterUtils.getIndex(args, Intent.class);
            String resolvedType = null;
            for (int i = intentIndex + 1; i < args.length; i++) {
                if (args[i] instanceof String) {
                    resolvedType = (String) args[i];
                    break;
                }
            }
            int flags = MethodParameterUtils.getNumberAsIntAfter(args, intentIndex, 0);
            ResolveInfo resolveInfo = ParallaxELiteInstaller.getBPackageManager().resolveIntent(
                    intent, resolvedType, flags, BActivityThread.getUserId());
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("checkPermission")
    public static class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String permission = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : null;
            String requestedPackage = args != null && args.length > 1
                    && args[1] instanceof String ? (String) args[1] : null;
            if (requestedPackage != null
                    && VirtualPermissionCompat.shouldGrantDeclaredNetworkPermission(
                    permission, requestedPackage)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("resolveService")
    public static class ResolveService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            if (intent == null) {
                return method.invoke(who, args);
            }
            int intentIndex = MethodParameterUtils.getIndex(args, Intent.class);
            String resolvedType = null;
            for (int i = intentIndex + 1; i < args.length; i++) {
                if (args[i] instanceof String) {
                    resolvedType = (String) args[i];
                    break;
                }
            }
            int flags = MethodParameterUtils.getNumberAsIntAfter(args, intentIndex, 0);
            ResolveInfo resolveInfo = ParallaxELiteInstaller.getBPackageManager().resolveService(
                    intent, flags, resolvedType, BActivityThread.getUserId());
            if (resolveInfo != null) {
                return resolveInfo;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setComponentEnabledSetting")
    public static class SetComponentEnabledSetting extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("getPackageInfo")
    public static class GetPackageInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = (String) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
			if (GmsCore.isGoogleAppOrService(packageName)) {
				return method.invoke(who, args);
			}
            PackageInfo packageInfo = ParallaxELiteInstaller.getBPackageManager().getPackageInfo(packageName, flags, BActivityThread.getUserId());
            if (packageInfo != null) {
                return packageInfo;
            }
            
            if (AppSystemEnv.isOpenPackage(packageName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getPackageUid")
    public static class GetPackageUid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("setApplicationBlockedSettingAsUser")
    public static class setApplicationBlockedSettingAsUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            MethodParameterUtils.replaceLastUserId(args);
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("getPackageUidEtc")
    public static class getPackageUidEtc extends GetPackageUid {

    }

    @ProxyMethod("getProviderInfo")
    public static class GetProviderInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ProviderInfo providerInfo = ParallaxELiteInstaller.getBPackageManager().getProviderInfo(componentName, flags, BActivityThread.getUserId());
            if (providerInfo != null) return providerInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getReceiverInfo")
    public static class GetReceiverInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ActivityInfo receiverInfo = ParallaxELiteInstaller.getBPackageManager().getReceiverInfo(componentName, flags, BActivityThread.getUserId());
            if (receiverInfo != null) return receiverInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getActivityInfo")
    public static class GetActivityInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ActivityInfo activityInfo = ParallaxELiteInstaller.getBPackageManager().getActivityInfo(componentName, flags, BActivityThread.getUserId());
            if (activityInfo != null) return activityInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getServiceInfo")
    public static class GetServiceInfo extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ServiceInfo serviceInfo = ParallaxELiteInstaller.getBPackageManager().getServiceInfo(componentName, flags, BActivityThread.getUserId());
            if (serviceInfo != null) return serviceInfo;
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("getInstalledApplications")
    public static class GetInstalledApplications extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = MethodParameterUtils.toInt(args[0]);
            List<ApplicationInfo> installedApplications = ParallaxELiteInstaller.getBPackageManager().getInstalledApplications(flags, BActivityThread.getUserId());
            return ParceledListSliceCompat.create(installedApplications);
        }
    }

    @ProxyMethod("getInstalledPackages")
    public static class GetInstalledPackages extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int flags = MethodParameterUtils.toInt(args[0]);
            List<PackageInfo> installedPackages = ParallaxELiteInstaller.getBPackageManager().getInstalledPackages(flags, BActivityThread.getUserId());
            return ParceledListSliceCompat.create(installedPackages);
        }
    }

    @ProxyMethod("getApplicationInfo")
    public static class GetApplicationInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = (String) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ApplicationInfo applicationInfo = ParallaxELiteInstaller.getBPackageManager().getApplicationInfo(packageName, flags, BActivityThread.getUserId());
            if (GmsCore.isGoogleAppOrService(packageName)) {
				return method.invoke(who, args);
			}
            if (applicationInfo != null) {
				return applicationInfo;
			}
            if (AppSystemEnv.isOpenPackage(packageName)) {
                return method.invoke(who, args);
            }
            return null;
        }
    }

    @ProxyMethod("queryContentProviders")
    public static class QueryContentProviders extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String processName = args != null && args.length > 0 && args[0] instanceof String
                    ? (String) args[0] : null;
            int uid = args != null && args.length > 1 && args[1] instanceof Number
                    ? ((Number) args[1]).intValue() : BActivityThread.getBUid();
            int flags = args != null && args.length > 2 && args[2] instanceof Number
                    ? ((Number) args[2]).intValue() : 0;

            // Preserve the process requested by the guest. Passing null has the
            // Android PackageManager meaning of "all matching providers".
            List<ProviderInfo> providers = ParallaxELiteInstaller.getBPackageManager()
                    .queryContentProviders(
                            processName,
                            uid,
                            flags,
                            BActivityThread.getUserId());
            return ParceledListSliceCompat.create(providers);
        }
    }

    @ProxyMethod("queryIntentReceivers")
    public static class QueryBroadcastReceivers extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
            String type = MethodParameterUtils.getFirstParam(args, String.class);
            int intentIndex = MethodParameterUtils.getIndex(args, Intent.class);
            int flagValue = MethodParameterUtils.getNumberAsIntAfter(
                    args, intentIndex, 0);
            List<ResolveInfo> resolves = ParallaxELiteInstaller.getBPackageManager().queryBroadcastReceivers(intent, flagValue, type, BActivityThread.getUserId());
            Slog.d(TAG, "queryIntentReceivers: " + resolves);

            // http://androidxref.com/7.0.0_r1/xref/frameworks/base/core/java/android/app/ApplicationPackageManager.java#872
            if (BuildCompat.isN()) {
                return ParceledListSliceCompat.create(resolves);
            }

            // http://androidxref.com/6.0.1_r10/xref/frameworks/base/core/java/android/app/ApplicationPackageManager.java#699
            return resolves;
        }
    }

    @ProxyMethod("resolveContentProvider")
    public static class ResolveContentProvider extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String authority = (String) args[0];
            int flags = MethodParameterUtils.toInt(args[1]);
            ProviderInfo providerInfo = ParallaxELiteInstaller.getBPackageManager().resolveContentProvider(authority, flags, BActivityThread.getUserId());
            if (providerInfo == null) {
                return method.invoke(who, args);
            }
            return providerInfo;
        }
    }

    @ProxyMethod("canRequestPackageInstalls")
    public static class CanRequestPackageInstalls extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getPackagesForUid")
    public static class GetPackagesForUid extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int uid = MethodParameterUtils.toInt(args[0]);
            if (uid == ParallaxELiteInstaller.getHostUid()) {
                args[0] = BActivityThread.getBUid();
                uid = MethodParameterUtils.toInt(args[0]);
            }
            String[] packagesForUid = ParallaxELiteInstaller.getBPackageManager().getPackagesForUid(uid);
            Slog.d(TAG, args[0] + " , " + BActivityThread.getAppProcessName() + " GetPackagesForUid: " + Arrays.toString(packagesForUid));
            return packagesForUid;
        }
    }

    @ProxyMethod("getInstallerPackageName")
    public static class GetInstallerPackageName extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // FIX: Google Play Store ke liye
            if (args != null && args.length > 0 && args[0] instanceof String) {
                String packageName = (String) args[0];
                if (GmsCore.VENDING_PKG.equals(packageName)) {
                    return "com.android.vending";
                }
            }
            return GmsCore.VENDING_PKG;
        }
    }

    @ProxyMethod("getSharedLibraries")
    public static class GetSharedLibraries extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // todo
            return ParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ProxyMethod("getComponentEnabledSetting")
    public static class getComponentEnabledSetting extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName componentName = (ComponentName) args[0];
            String packageName = componentName.getPackageName();

            ApplicationInfo applicationInfo = ParallaxELiteInstaller.getBPackageManager().getApplicationInfo(packageName,0, BActivityThread.getUserId());
            if(applicationInfo != null){
                return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            }
            if (AppSystemEnv.isOpenPackage(componentName)) {
                return method.invoke(who, args);
            }
            throw new IllegalArgumentException();
        }
    }
    
    @ProxyMethod("addPackageToPreferred")
    public static class addPackageToPreferred extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }
    
    @ProxyMethod("setSplashScreenTheme")
    public static class SetSplashScreenTheme extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String packageName = args.length > 0 ? (String) args[0] : "unknown";
            Slog.d(TAG, "SetSplashScreenTheme: Bypassing UID check for package: " + packageName);
            boolean isXiaomi = BuildCompat.isMIUI() || Build.MANUFACTURER.toLowerCase().contains("xiaomi") || Build.BRAND.toLowerCase().contains("xiaomi") || Build.DISPLAY.toLowerCase().contains("hyperos");
            if (isXiaomi) { Slog.d(TAG, "SetSplashScreenTheme: Detected Xiaomi/HyperOS, using enhanced bypass"); return null; }
            try {
                return method.invoke(who, args);
            } catch (SecurityException e) {
                Slog.w(TAG, "SetSplashScreenTheme: SecurityException caught, bypassing: " + e.getMessage());
                return null;
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Slog.w(TAG, "SetSplashScreenTheme: SecurityException (wrapped) caught, bypassing: " + e.getCause().getMessage());
                    return null;
                }
                throw e;
            }
        }
    }
    
    @ProxyMethod("checkSignatures")
    public static class checkSignatures extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args.length == 2) {
                if (args[0] instanceof String && args[1] instanceof String) {
                    String pkg1 = (String) args[0];
                    String pkg2 = (String) args[1];
                    PackageManager pm = ParallaxELiteInstaller.getPackageManager();
                    try {
                        PackageInfo pkgInfo1 = pm.getPackageInfo(pkg1, PackageManager.GET_SIGNATURES);
                        PackageInfo pkgInfo2 = pm.getPackageInfo(pkg2, PackageManager.GET_SIGNATURES);
                        Signature[] sigs1 = pkgInfo1.signatures;
                        Signature[] sigs2 = pkgInfo2.signatures;
                        if (sigs1 == null || sigs1.length == 0) {
                            return (sigs2 == null || sigs2.length == 0) ? 1 : -1;
                        }
                        if (sigs2 == null || sigs2.length == 0) {
                            return -2;
                        }
                        return Arrays.equals(sigs1, sigs2) ? 0 : -3;
                    } catch (Exception e) {
                        // Fall through
                    }
                }
            }
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("checkUidSignatures")
    public static class checkUidSignatures extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // FIX: Google Play Services ke liye
            return PackageManager.SIGNATURE_MATCH;
        }
    }
    
    @ProxyMethod("clearPackagePersistentPreferredActivities")
    public static class clearPackagePersistentPreferredActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("clearPackagePreferredActivities")
    public static class clearPackagePreferredActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("getApplicationBlockedSettingAsUser")
    public static class getApplicationBlockedSettingAsUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
    @ProxyMethod("setPackageStoppedState")
    public static class setPackageStoppedState extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
    
}
