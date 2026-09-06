package com.parallaxelite.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.content.pm.BRParceledListSlice;

import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.fake.hook.MethodHook;
import com.parallaxelite.fake.hook.ProxyMethod;
import com.parallaxelite.fake.hook.ScanClass;
import com.parallaxelite.utils.compat.ParceledListSliceCompat;

/**
 * Combined external-auth PackageManager compatibility layer.
 *
 * <p>Facebook compatibility is delegated unchanged to
 * {@link IFacebookWebPackageManagerProxy}.</p>
 *
 * <p><b>Twitter SSO compatibility:</b> We deliberately do not fabricate the
 * legacy {@code SingleSignOnActivity} because newer X/Twitter builds no longer
 * export it. The Twitter SDK will correctly fall back to the web OAuth flow,
 * which launches the X app's main activity and handles the login.</p>
 *
 * <p>No provider result, account, token, cookie, or credential is fabricated.</p>
 */
@ScanClass({IPackageManagerProxy.class})
public final class IAuthCompatPackageManagerProxy extends IPackageManagerProxy {

    private static final String TAG = "TwitterSSOCompat";
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String TWITTER_SSO_ACTIVITY =
            "com.twitter.android.SingleSignOnActivity";

    @Override
    public void injectHook() {
        super.injectHook();
        addMethodHook("resolveIntent",
                new IFacebookWebPackageManagerProxy.ResolveIntentFacebookWebFirst());
        addMethodHook("resolveService",
                new IFacebookWebPackageManagerProxy.ResolveServiceFacebookWebFirst());
        addMethodHook("queryIntentActivities", new QueryExternalAuthActivities());
    }

    @ProxyMethod("queryIntentActivities")
    public static final class QueryExternalAuthActivities extends MethodHook {
        private final IFacebookWebPackageManagerProxy.QueryFacebookCallbackActivities
                existingCompat =
                new IFacebookWebPackageManagerProxy.QueryFacebookCallbackActivities();

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Let the existing Facebook logic handle all queries,
            // including the Twitter SSO probe. Since the real activity is not exported,
            // the system query will return empty, and the Twitter SDK will fall back
            // to the web OAuth flow, which works correctly.
            return existingCompat.hook(who, method, args);
        }
    }

    // --------------------------------------------------------------
    // Helper methods (kept for compatibility, not used in the hook)
    // --------------------------------------------------------------
    private static Intent findIntent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Intent) return (Intent) arg;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static boolean isExactTwitterSsoProbe(Intent intent) {
        if (intent == null) return false;
        ComponentName component = intent.getComponent();
        return component != null
                && TWITTER_PACKAGE.equals(component.getPackageName())
                && TWITTER_SSO_ACTIVITY.equals(component.getClassName());
    }

    @SuppressWarnings("unused")
    static boolean isWireCompatibleTwitterSsoClass(String className) {
        return TWITTER_SSO_ACTIVITY.equals(className);
    }

    @SuppressWarnings("unused")
    private static boolean containsUsableTwitterSso(Object result) {
        List<?> list = extractList(result);
        if (list == null || list.isEmpty()) return false;

        for (Object item : list) {
            if (!(item instanceof ResolveInfo)) continue;
            ActivityInfo activityInfo = ((ResolveInfo) item).activityInfo;
            if (activityInfo != null
                    && TWITTER_PACKAGE.equals(activityInfo.packageName)
                    && TWITTER_SSO_ACTIVITY.equals(activityInfo.name)
                    && activityInfo.enabled
                    && activityInfo.exported
                    && (activityInfo.applicationInfo == null
                    || activityInfo.applicationInfo.enabled)) {
                return true;
            }
        }
        return false;
    }

    private static List<?> extractList(Object result) {
        if (result instanceof List) return (List<?>) result;
        if (ParceledListSliceCompat.isParceledListSlice(result)) {
            try {
                return BRParceledListSlice.get(result).getList();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static Object emptyResult(Method method) {
        List<ResolveInfo> empty = Collections.emptyList();
        if (ParceledListSliceCompat.isReturnParceledListSlice(method)) {
            return ParceledListSliceCompat.create(empty);
        }
        return empty;
    }

    private static String processSuffix() {
        try {
            return " [bpid=" + BActivityThread.getAppPid() + "]";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
