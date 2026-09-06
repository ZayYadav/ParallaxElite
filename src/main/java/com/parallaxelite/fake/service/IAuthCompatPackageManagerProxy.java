package com.parallaxelite.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
 * <p>Facebook compatibility behavior is delegated unchanged to
 * {@link IFacebookWebPackageManagerProxy}. Legacy Twitter Kit probes the exact
 * {@code com.twitter.android.SingleSignOnActivity}; the SDK reports native SSO
 * only when that real exported Activity is genuinely present in the installed
 * X/Twitter build.</p>
 *
 * <p><b>TWITTER FORCE‑SSO MODIFICATION:</b>
 * This version always returns a synthetic ResolveInfo for the legacy
 * SingleSignOnActivity, even when the installed X/Twitter app does not export it.
 * This causes the Twitter SDK to attempt the native SSO flow.</p>
 *
 * <p>To make the flow actually work, you must also hook the activity launch
 * (e.g. via {@code IActivityManagerProxy}) and redirect the Intent to the
 * working authentication surface (web OAuth, AccountAuthenticator, or URL
 * interpreter).</p>
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
            Intent intent = findIntent(args);
            final boolean legacyTwitterProbe = isExactTwitterSsoProbe(intent);

            // ------------------------------------------------------------------
            // MODIFICATION: always return a synthetic entry for Twitter SSO probe
            // ------------------------------------------------------------------
            if (legacyTwitterProbe) {
                Log.i(TAG, "Twitter SSO probe intercepted; returning synthetic entry"
                        + processSuffix());
                return createSyntheticTwitterSsoResult(method);
            }

            // Keep the already-shipped Facebook behavior for all other intents.
            return existingCompat.hook(who, method, args);
        }
    }

    private static Intent findIntent(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Intent) {
                return (Intent) arg;
            }
        }
        return null;
    }

    private static boolean isExactTwitterSsoProbe(Intent intent) {
        if (intent == null) {
            return false;
        }
        ComponentName component = intent.getComponent();
        return component != null
                && TWITTER_PACKAGE.equals(component.getPackageName())
                && TWITTER_SSO_ACTIVITY.equals(component.getClassName());
    }

    /**
     * Creates a synthetic ResolveInfo that pretends the legacy SingleSignOnActivity
     * is exported and enabled.
     */
    private static Object createSyntheticTwitterSsoResult(Method method) {
        List<ResolveInfo> list = new ArrayList<>(1);
        list.add(buildFakeResolveInfo());
        return wrapResult(list, method);
    }

    private static ResolveInfo buildFakeResolveInfo() {
        ResolveInfo resolveInfo = new ResolveInfo();

        // Create a minimal ActivityInfo with the required identity
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = TWITTER_PACKAGE;
        activityInfo.name = TWITTER_SSO_ACTIVITY;
        activityInfo.exported = true;
        activityInfo.enabled = true;

        // Set a dummy ApplicationInfo so that the package is considered installed
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = TWITTER_PACKAGE;
        appInfo.enabled = true;
        // Use a placeholder source dir (must be non‑null)
        appInfo.sourceDir = "/system/placeholder";
        appInfo.publicSourceDir = appInfo.sourceDir;
        activityInfo.applicationInfo = appInfo;

        // ActivityInfo also needs a processName; we can copy from packageName
        activityInfo.processName = TWITTER_PACKAGE;

        // Fill the ResolveInfo with this ActivityInfo
        resolveInfo.activityInfo = activityInfo;

        // Also set the match to something meaningful (e.g., default)
        resolveInfo.match = PackageManager.MATCH_DEFAULT_ONLY;

        return resolveInfo;
    }

    private static Object wrapResult(List<ResolveInfo> list, Method method) {
        if (ParceledListSliceCompat.isReturnParceledListSlice(method)) {
            return ParceledListSliceCompat.create(list);
        }
        return list;
    }

    @SuppressWarnings("unused") // kept for compatibility
    private static boolean containsUsableTwitterSso(Object result) {
        // Not used anymore; we always inject.
        return true;
    }

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
