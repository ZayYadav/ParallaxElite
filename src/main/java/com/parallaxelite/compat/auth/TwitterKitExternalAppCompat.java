package com.parallaxelite.compat.auth;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.compat.oauth.TwitterKitExternalAuthBroker;

/**
 * Compatibility bridge for BGMI's archived Twitter Kit 3.x OAuthActivity.
 *
 * Modern X no longer exports com.twitter.android.SingleSignOnActivity, so legacy
 * Twitter Kit falls back to its internal WebView. This callback waits until
 * Twitter Kit itself has obtained the OAuth1 request token and generated the
 * authorize URL, then hands that exact URL to the installed X app. The original
 * OAuthActivity remains alive and later receives the twittersdk callback through
 * the process broker, so Twitter Kit still performs its own access-token exchange.
 */
public final class TwitterKitExternalAppCompat
        implements Application.ActivityLifecycleCallbacks {
    private static final String OAUTH_ACTIVITY =
            "com.twitter.sdk.android.core.identity.OAuthActivity";
    private static final String X_PACKAGE = "com.twitter.android";
    private static final String X_URL_ACTIVITY =
            "com.x.android.deeplink.XUrlInterpreterActivity";

    private static final long POLL_MS = 100L;
    private static final int MAX_POLLS = 200;
    private static final long RETURN_FALLBACK_MS = 2_500L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean installed;
    private static WeakReference<Activity> activeActivity = new WeakReference<>(null);
    private static WeakReference<WebView> activeWebView = new WeakReference<>(null);
    private static Uri activeAuthorizeUri;
    private static boolean externalLaunched;
    private static boolean callbackInjected;

    private TwitterKitExternalAppCompat() {
    }

    public static synchronized void install(Application application) {
        if (installed || application == null) {
            return;
        }
        installed = true;
        application.registerActivityLifecycleCallbacks(new TwitterKitExternalAppCompat());
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (!isTwitterOAuthActivity(activity)) {
            return;
        }
        synchronized (TwitterKitExternalAppCompat.class) {
            activeActivity = new WeakReference<>(activity);
            activeWebView = new WeakReference<>(findTwitterWebView(activity));
            activeAuthorizeUri = null;
            externalLaunched = false;
            callbackInjected = false;
        }
        pollAuthorizeUrl(activity, 0);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (!isTwitterOAuthActivity(activity)) {
            return;
        }
        final Uri authorizeUri;
        synchronized (TwitterKitExternalAppCompat.class) {
            if (!externalLaunched || callbackInjected) {
                return;
            }
            authorizeUri = activeAuthorizeUri;
        }

        // If the provider Activity was closed/cancelled without returning the
        // twittersdk callback, restore Twitter Kit's own web flow rather than
        // leaving BGMI on a hidden OAuthActivity.
        MAIN.postDelayed(() -> {
            synchronized (TwitterKitExternalAppCompat.class) {
                if (callbackInjected || !externalLaunched
                        || activeActivity.get() != activity
                        || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                externalLaunched = false;
            }
            TwitterKitExternalAuthBroker.cancelFromGuest(authorizeUri);
            WebView webView = activeWebView.get();
            if (webView == null) {
                webView = findTwitterWebView(activity);
                activeWebView = new WeakReference<>(webView);
            }
            if (webView != null && authorizeUri != null) {
                try {
                    webView.loadUrl(authorizeUri.toString());
                } catch (Throwable ignored) {
                }
            }
        }, RETURN_FALLBACK_MS);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (!isTwitterOAuthActivity(activity)) {
            return;
        }
        synchronized (TwitterKitExternalAppCompat.class) {
            if (activeActivity.get() == activity) {
                activeActivity = new WeakReference<>(null);
                activeWebView = new WeakReference<>(null);
                activeAuthorizeUri = null;
                externalLaunched = false;
                callbackInjected = false;
            }
        }
    }

    private static void pollAuthorizeUrl(Activity activity, int attempt) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || attempt >= MAX_POLLS) {
            return;
        }

        WebView webView = activeWebView.get();
        if (webView == null) {
            webView = findTwitterWebView(activity);
            if (webView != null) {
                activeWebView = new WeakReference<>(webView);
            }
        }

        Uri authorizeUri = null;
        if (webView != null) {
            try {
                authorizeUri = parse(webView.getUrl());
            } catch (Throwable ignored) {
            }
        }

        if (TwitterKitExternalAuthBroker.isTwitterKitAuthorizeUri(authorizeUri)
                && launchExternalX(activity, webView, authorizeUri)) {
            return;
        }

        MAIN.postDelayed(() -> pollAuthorizeUrl(activity, attempt + 1), POLL_MS);
    }

    private static boolean launchExternalX(
            Activity activity, WebView webView, Uri authorizeUri) {
        synchronized (TwitterKitExternalAppCompat.class) {
            if (externalLaunched || callbackInjected) {
                return true;
            }
        }
        if (!isXUrlActivityAvailable() || !TwitterKitExternalAuthBroker.beginFromGuest(authorizeUri)) {
            return false;
        }

        try {
            Intent external = new Intent(Intent.ACTION_VIEW, authorizeUri);
            external.addCategory(Intent.CATEGORY_DEFAULT);
            external.addCategory(Intent.CATEGORY_BROWSABLE);
            external.setComponent(new ComponentName(X_PACKAGE, X_URL_ACTIVITY));
            external.putExtra(ExternalAuthRouter.EXTRA_DIRECT_PROVIDER_DISPATCH, true);
            activity.startActivity(external);

            synchronized (TwitterKitExternalAppCompat.class) {
                activeAuthorizeUri = authorizeUri;
                externalLaunched = true;
                callbackInjected = false;
            }
            if (webView != null) {
                try {
                    webView.stopLoading();
                    webView.setVisibility(View.INVISIBLE);
                } catch (Throwable ignored) {
                }
            }
            return true;
        } catch (Throwable error) {
            TwitterKitExternalAuthBroker.cancelFromGuest(authorizeUri);
            return false;
        }
    }

    /**
     * Called inside the exact guest :pN process by ProxyContentProvider.
     * No token/secret is logged or persisted. The callback is fed to Twitter Kit's
     * existing OAuthWebViewClient, which validates it and performs access-token exchange.
     */
    public static boolean deliverCallback(Uri callbackUri) {
        if (!TwitterKitExternalAuthBroker.isTwitterKitCallback(callbackUri)) {
            return false;
        }

        final Activity activity;
        final WebView webView;
        final Uri authorizeUri;
        synchronized (TwitterKitExternalAppCompat.class) {
            activity = activeActivity.get();
            webView = activeWebView.get();
            authorizeUri = activeAuthorizeUri;
            if (activity == null || webView == null || authorizeUri == null
                    || activity.isFinishing() || activity.isDestroyed()
                    || !sameOAuthToken(authorizeUri, callbackUri)) {
                return false;
            }
            callbackInjected = true;
        }

        MAIN.post(() -> {
            try {
                webView.setVisibility(View.INVISIBLE);
                webView.loadUrl(callbackUri.toString());
            } catch (Throwable ignored) {
                synchronized (TwitterKitExternalAppCompat.class) {
                    callbackInjected = false;
                }
            }
        });
        return true;
    }

    private static boolean sameOAuthToken(Uri authorizeUri, Uri callbackUri) {
        try {
            String expected = authorizeUri.getQueryParameter("oauth_token");
            String actual = callbackUri.getQueryParameter("oauth_token");
            return expected != null && !expected.isEmpty() && expected.equals(actual);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isXUrlActivityAvailable() {
        try {
            ActivityInfo info = ParallaxELiteInstaller.getContext()
                    .getPackageManager()
                    .getActivityInfo(new ComponentName(X_PACKAGE, X_URL_ACTIVITY), 0);
            return info != null
                    && info.enabled
                    && info.exported
                    && (info.applicationInfo == null || info.applicationInfo.enabled);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static WebView findTwitterWebView(Activity activity) {
        if (activity == null) {
            return null;
        }
        try {
            Field field = activity.getClass().getDeclaredField("webView");
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof WebView ? (WebView) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isTwitterOAuthActivity(Activity activity) {
        return activity != null && OAUTH_ACTIVITY.equals(activity.getClass().getName());
    }

    private static Uri parse(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 16_384) {
            return null;
        }
        try {
            return Uri.parse(value.trim());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
}
