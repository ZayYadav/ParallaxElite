package com.parallaxelite.compat.oauth;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Tracks Meta's real guest-side CustomTabMainActivity and completes it with the
 * exact Activity result shape expected by Facebook's CustomTabLoginMethodHandler.
 *
 * No provider credential, access token, password, or cookie is inspected here.
 * The callback URI has already been validated by the host OAuth bridge before it
 * is relayed into the exact virtual :pN process.
 */
public final class FacebookCustomTabResultCompat {
    public static final String METHOD_COMPLETE_GUEST =
            "parallaxelite.facebook.complete_guest_custom_tab";
    public static final String EXTRA_CALLBACK_URL = "facebook_callback_url";
    public static final String EXTRA_VIRTUAL_PACKAGE = "facebook_virtual_package";
    public static final String EXTRA_USER_ID = "facebook_user_id";
    public static final String EXTRA_BPID = "facebook_bpid";
    public static final String EXTRA_DELIVERED = "facebook_delivered";

    private static final String CUSTOM_TAB_MAIN_ACTIVITY =
            "com.facebook.CustomTabMainActivity";
    private static final String CUSTOM_TAB_EXTRA_URL =
            "CustomTabMainActivity.extra_url";

    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Application installedApplication;
    private static WeakReference<Activity> waitingActivity = new WeakReference<>(null);

    private FacebookCustomTabResultCompat() {
    }

    public static void install(Application application) {
        if (application == null) {
            return;
        }
        synchronized (LOCK) {
            if (installedApplication == application) {
                return;
            }
            if (installedApplication != null) {
                try {
                    installedApplication.unregisterActivityLifecycleCallbacks(CALLBACKS);
                } catch (Throwable ignored) {
                }
            }
            installedApplication = application;
            waitingActivity = new WeakReference<>(null);
            application.registerActivityLifecycleCallbacks(CALLBACKS);
        }
    }

    /**
     * Called only inside the exact guest process selected by ProxyContentProvider.
     * Returns true when a live Meta CustomTabMainActivity accepted the result.
     */
    public static boolean completeCallback(Uri callbackUri) {
        if (!isSafeCustomCallback(callbackUri)) {
            return false;
        }

        final Activity activity;
        synchronized (LOCK) {
            activity = waitingActivity.get();
        }
        if (!isUsableTarget(activity)) {
            return false;
        }

        final Intent result = new Intent();
        result.putExtra(CUSTOM_TAB_EXTRA_URL, callbackUri.toString());

        Runnable completion = () -> {
            if (!isUsableTarget(activity)) {
                return;
            }
            try {
                activity.setResult(Activity.RESULT_OK, result);
                activity.finish();
            } catch (Throwable ignored) {
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            completion.run();
        } else {
            MAIN.post(completion);
        }
        return true;
    }

    private static boolean isUsableTarget(Activity activity) {
        if (activity == null) {
            return false;
        }
        try {
            return CUSTOM_TAB_MAIN_ACTIVITY.equals(activity.getClass().getName())
                    && !activity.isFinishing()
                    && !activity.isDestroyed();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSafeCustomCallback(Uri uri) {
        if (uri == null || uri.toString().length() > 16_384) {
            return false;
        }
        String scheme = lower(uri.getScheme());
        if (scheme.isEmpty()
                || "http".equals(scheme)
                || "https".equals(scheme)
                || "file".equals(scheme)
                || "content".equals(scheme)
                || "javascript".equals(scheme)
                || "data".equals(scheme)
                || "intent".equals(scheme)) {
            return false;
        }
        return true;
    }

    private static void remember(Activity activity) {
        if (activity == null
                || !CUSTOM_TAB_MAIN_ACTIVITY.equals(activity.getClass().getName())) {
            return;
        }
        synchronized (LOCK) {
            waitingActivity = new WeakReference<>(activity);
        }
    }

    private static void forget(Activity activity) {
        synchronized (LOCK) {
            Activity current = waitingActivity.get();
            if (current == activity) {
                waitingActivity = new WeakReference<>(null);
            }
        }
    }

    private static final Application.ActivityLifecycleCallbacks CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle state) {
                    remember(activity);
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    remember(activity);
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    remember(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    forget(activity);
                }
            };

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
