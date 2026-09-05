package com.parallaxelite.compat.auth;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.app.BActivityThread;

/**
 * Compatibility shim for the legacy GCloud/MSDK Twitter plugin bundled by BGMI.
 *
 * GCloud may be configured with loginUsingWeb=true. In that mode it prepares its
 * normal TwitterWrapper callback and then starts TwitterWebActivity with the
 * authorize URL in the "url" extra. At that exact boundary we can safely switch
 * the same vendor wrapper to its native Twitter Kit path. No credentials, OAuth
 * values or returned tokens are inspected or copied by this shim.
 *
 * If the official app, Twitter SDK classes, config keys or native initialization
 * are unavailable, this method returns false and the original GCloud WebView is
 * allowed to launch unchanged.
 */
public final class GCloudTwitterNativeCompat {
    private static final String TAG = "ParallaxAuth";

    private static final String GCLOUD_WEB_ACTIVITY =
            "com.itop.twitterwrapper.TwitterWebActivity";
    private static final String TWITTER_LOGIN =
            "com.itop.gcloud.msdk.login.TwitterLogin";
    private static final String TWITTER_TOOLS =
            "com.itop.gcloud.msdk.twitter.MSDKTwitterTools";
    private static final String TWITTER_WRAPPER =
            "com.itop.twitterwrapper.TwitterWrapper";

    private static final String[] OFFICIAL_PACKAGES = new String[] {
            "com.twitter.android",
            "com.x.android",
            "com.twitter.android.lite"
    };

    private static final ThreadLocal<Boolean> REENTRY = new ThreadLocal<>();

    private GCloudTwitterNativeCompat() {
    }

    public static boolean tryStartNative(
            android.content.Intent source,
            IBinder resultTo) {
        if (Boolean.TRUE.equals(REENTRY.get())
                || source == null
                || resultTo == null
                || source.getComponent() == null
                || !GCLOUD_WEB_ACTIVITY.equals(source.getComponent().getClassName())) {
            return false;
        }

        Uri authorizeUri = safeTwitterUri(source.getStringExtra("url"));
        if (authorizeUri == null || !hasOfficialTwitterApp()) {
            Log.i(TAG, "twitter gcloud native unavailable; using web fallback");
            return false;
        }

        Activity caller;
        try {
            caller = BActivityThread.getActivityByToken(resultTo);
        } catch (Throwable ignored) {
            caller = null;
        }
        if (caller == null || caller.isFinishing()) {
            Log.i(TAG, "twitter gcloud caller unavailable; using web fallback");
            return false;
        }

        ClassLoader loader = caller.getClassLoader();
        if (loader == null && BActivityThread.getApplication() != null) {
            loader = BActivityThread.getApplication().getClassLoader();
        }
        if (loader == null) {
            return false;
        }

        Field webModeField = null;
        Field sdkInitField = null;
        boolean oldWebMode = true;
        boolean oldSdkInit = false;

        try {
            REENTRY.set(Boolean.TRUE);

            Class<?> loginClass = Class.forName(TWITTER_LOGIN, true, loader);
            webModeField = loginClass.getDeclaredField("isWebLogin");
            webModeField.setAccessible(true);
            oldWebMode = webModeField.getBoolean(null);

            try {
                sdkInitField = loginClass.getDeclaredField("isSDKInitSuccess");
                sdkInitField.setAccessible(true);
                oldSdkInit = sdkInitField.getBoolean(null);
            } catch (Throwable ignored) {
                sdkInitField = null;
            }

            Class<?> wrapperClass = Class.forName(TWITTER_WRAPPER, true, loader);
            Field callbackField = wrapperClass.getDeclaredField("webCallback");
            callbackField.setAccessible(true);
            Object callback = callbackField.get(null);
            if (callback == null) {
                return false;
            }

            boolean initialized = oldSdkInit;
            if (!initialized) {
                Class<?> toolsClass = Class.forName(TWITTER_TOOLS, true, loader);
                Method init = toolsClass.getDeclaredMethod("init", String.class);
                init.setAccessible(true);
                Object initResult = init.invoke(null, "ParallaxELite");
                initialized = Boolean.TRUE.equals(initResult);
                if (sdkInitField != null) {
                    sdkInitField.setBoolean(null, initialized);
                }
            }
            if (!initialized) {
                restore(webModeField, oldWebMode, sdkInitField, oldSdkInit);
                Log.i(TAG, "twitter native sdk init unavailable; using web fallback");
                return false;
            }

            // TwitterLogin$1 reads this exact static field again from
            // onActivityResult, so changing it here switches both request-code
            // selection and result delivery to the vendor native path.
            webModeField.setBoolean(null, false);

            Class<?> callbackClass =
                    Class.forName("com.itop.twitterwrapper.TwitterWrapperCallback", true, loader);
            Method login = wrapperClass.getDeclaredMethod(
                    "login",
                    Activity.class,
                    boolean.class,
                    String.class,
                    callbackClass);
            login.setAccessible(true);
            login.invoke(null, caller, false, "", callback);

            Log.i(TAG, "twitter gcloud native authorize started");
            return true;
        } catch (Throwable error) {
            restore(webModeField, oldWebMode, sdkInitField, oldSdkInit);
            Log.w(TAG, "twitter native override failed; using web fallback",
                    safeCause(error));
            return false;
        } finally {
            REENTRY.remove();
        }
    }

    private static void restore(
            Field webModeField,
            boolean oldWebMode,
            Field sdkInitField,
            boolean oldSdkInit) {
        try {
            if (webModeField != null) {
                webModeField.setBoolean(null, oldWebMode);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (sdkInitField != null) {
                sdkInitField.setBoolean(null, oldSdkInit);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasOfficialTwitterApp() {
        try {
            PackageManager pm = ParallaxELiteInstaller.getContext().getPackageManager();
            for (String packageName : OFFICIAL_PACKAGES) {
                try {
                    ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                    if (info != null && info.enabled) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Uri safeTwitterUri(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 16_384) {
            return null;
        }
        try {
            Uri uri = Uri.parse(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(java.util.Locale.US);
            if ("twitter.com".equals(host)
                    || "x.com".equals(host)
                    || host.endsWith(".twitter.com")
                    || host.endsWith(".x.com")) {
                return uri;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Throwable safeCause(Throwable error) {
        if (error == null) {
            return new IllegalStateException("unknown");
        }
        Throwable cause = error.getCause();
        return cause == null ? error : cause;
    }
}
