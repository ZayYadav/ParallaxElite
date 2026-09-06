package com.parallaxelite.compat.oauth;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.proxy.ProxyManifest;
import com.parallaxelite.utils.provider.ProviderCall;

/**
 * Cross-process broker for the legacy Twitter Kit OAuth1 flow used by BGMI/GCloud.
 *
 * The guest process registers only the short-lived request-token authorization URL.
 * The host callback process never exchanges credentials or access tokens; it relays
 * the validated twittersdk callback back to the exact guest :pN process where the
 * original Twitter Kit WebView/Controller finishes its normal access-token exchange.
 */
public final class TwitterKitExternalAuthBroker {
    public static final String METHOD_BEGIN =
            "parallaxelite.twitterkit.begin_external_auth";
    public static final String METHOD_CANCEL =
            "parallaxelite.twitterkit.cancel_external_auth";
    public static final String METHOD_COMPLETE =
            "parallaxelite.twitterkit.complete_external_auth";
    public static final String METHOD_DELIVER_GUEST =
            "parallaxelite.twitterkit.deliver_guest_callback";

    public static final String EXTRA_AUTH_URL = "auth_url";
    public static final String EXTRA_CALLBACK_URL = "callback_url";
    public static final String EXTRA_VIRTUAL_PACKAGE = "virtual_package";
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_BPID = "bpid";
    public static final String EXTRA_DELIVERED = "delivered";

    private static final Object LOCK = new Object();
    private static final long TTL_MS = 3L * 60L * 1000L;
    private static final int MAX_SESSIONS = 4;
    private static final List<Session> SESSIONS = new ArrayList<>();

    private TwitterKitExternalAuthBroker() {
    }

    public static boolean beginFromGuest(Uri authUri) {
        if (!isTwitterKitAuthorizeUri(authUri)) {
            return false;
        }
        String virtualPackage = BActivityThread.getAppPackageName();
        int userId = BActivityThread.getUserId();
        int bpid = BActivityThread.getAppPid();
        if (virtualPackage == null || virtualPackage.trim().isEmpty()
                || userId < 0 || bpid < 0 || bpid > 24) {
            return false;
        }

        Bundle extras = new Bundle();
        extras.putString(EXTRA_AUTH_URL, authUri.toString());
        extras.putString(EXTRA_VIRTUAL_PACKAGE, virtualPackage);
        extras.putInt(EXTRA_USER_ID, userId);
        extras.putInt(EXTRA_BPID, bpid);
        Bundle result = ProviderCall.callSafely(
                systemAuthority(), METHOD_BEGIN, null, extras);
        return result != null && result.getBoolean(EXTRA_DELIVERED, false);
    }

    public static void cancelFromGuest(Uri authUri) {
        if (authUri == null) {
            return;
        }
        Bundle extras = new Bundle();
        extras.putString(EXTRA_AUTH_URL, authUri.toString());
        extras.putString(EXTRA_VIRTUAL_PACKAGE, BActivityThread.getAppPackageName());
        extras.putInt(EXTRA_USER_ID, BActivityThread.getUserId());
        extras.putInt(EXTRA_BPID, BActivityThread.getAppPid());
        ProviderCall.callSafely(systemAuthority(), METHOD_CANCEL, null, extras);
    }

    /** Called by the exported host callback Activity. */
    public static boolean relayCallbackFromHost(Uri callbackUri) {
        if (!isTwitterKitCallback(callbackUri)) {
            return false;
        }
        Bundle extras = new Bundle();
        extras.putString(EXTRA_CALLBACK_URL, callbackUri.toString());
        Bundle result = ProviderCall.callSafely(
                systemAuthority(), METHOD_COMPLETE, null, extras);
        return result != null && result.getBoolean(EXTRA_DELIVERED, false);
    }

    /**
     * Executes only inside SystemCallProvider's process. The provider itself is
     * non-exported; calls come from processes sharing the host UID.
     */
    public static Bundle handleSystemCall(String method, Bundle extras) {
        Bundle response = new Bundle();
        response.putBoolean(EXTRA_DELIVERED, false);
        if (method == null || extras == null) {
            return response;
        }

        if (METHOD_BEGIN.equals(method)) {
            Uri authUri = parse(extras.getString(EXTRA_AUTH_URL));
            String pkg = extras.getString(EXTRA_VIRTUAL_PACKAGE);
            int userId = extras.getInt(EXTRA_USER_ID, -1);
            int bpid = extras.getInt(EXTRA_BPID, -1);
            if (!isTwitterKitAuthorizeUri(authUri)
                    || pkg == null || pkg.trim().isEmpty()
                    || userId < 0 || bpid < 0 || bpid > 24) {
                return response;
            }
            String token = authUri.getQueryParameter("oauth_token");
            synchronized (LOCK) {
                long now = SystemClock.elapsedRealtime();
                purgeLocked(now);
                removeTargetLocked(pkg, userId);
                while (SESSIONS.size() >= MAX_SESSIONS) {
                    SESSIONS.remove(0);
                }
                SESSIONS.add(new Session(token, pkg, userId, bpid, now));
            }
            response.putBoolean(EXTRA_DELIVERED, true);
            return response;
        }

        if (METHOD_CANCEL.equals(method)) {
            Uri authUri = parse(extras.getString(EXTRA_AUTH_URL));
            String pkg = extras.getString(EXTRA_VIRTUAL_PACKAGE);
            int userId = extras.getInt(EXTRA_USER_ID, -1);
            int bpid = extras.getInt(EXTRA_BPID, -1);
            String token = authUri == null ? null : authUri.getQueryParameter("oauth_token");
            synchronized (LOCK) {
                purgeLocked(SystemClock.elapsedRealtime());
                Iterator<Session> iterator = SESSIONS.iterator();
                while (iterator.hasNext()) {
                    Session session = iterator.next();
                    if (session.userId == userId
                            && session.bpid == bpid
                            && safeEquals(session.virtualPackage, pkg)
                            && safeEquals(session.oauthToken, token)) {
                        iterator.remove();
                    }
                }
            }
            response.putBoolean(EXTRA_DELIVERED, true);
            return response;
        }

        if (METHOD_COMPLETE.equals(method)) {
            Uri callbackUri = parse(extras.getString(EXTRA_CALLBACK_URL));
            if (!isTwitterKitCallback(callbackUri)) {
                return response;
            }
            String token = callbackUri.getQueryParameter("oauth_token");
            Session matched = null;
            synchronized (LOCK) {
                long now = SystemClock.elapsedRealtime();
                purgeLocked(now);
                for (Session session : SESSIONS) {
                    if (safeEquals(session.oauthToken, token)) {
                        if (matched != null) {
                            return response;
                        }
                        matched = session;
                    }
                }
            }
            if (matched == null) {
                return response;
            }

            Bundle relay = new Bundle();
            relay.putString(EXTRA_CALLBACK_URL, callbackUri.toString());
            relay.putString(EXTRA_VIRTUAL_PACKAGE, matched.virtualPackage);
            relay.putInt(EXTRA_USER_ID, matched.userId);
            relay.putInt(EXTRA_BPID, matched.bpid);
            Bundle delivered = ProviderCall.callSafely(
                    ProxyManifest.getProxyAuthorities(matched.bpid),
                    METHOD_DELIVER_GUEST,
                    null,
                    relay);
            boolean ok = delivered != null
                    && delivered.getBoolean(EXTRA_DELIVERED, false);
            if (ok) {
                synchronized (LOCK) {
                    SESSIONS.remove(matched);
                }
            }
            response.putBoolean(EXTRA_DELIVERED, ok);
            return response;
        }

        return response;
    }

    public static boolean isTwitterKitAuthorizeUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = lower(uri.getHost());
        if (!("api.twitter.com".equals(host)
                || "twitter.com".equals(host)
                || "www.twitter.com".equals(host)
                || "mobile.twitter.com".equals(host)
                || "x.com".equals(host)
                || "www.x.com".equals(host))) {
            return false;
        }
        String path = lower(uri.getPath());
        if (!(path.endsWith("/oauth/authorize")
                || path.endsWith("/oauth/authenticate"))) {
            return false;
        }
        String token = uri.getQueryParameter("oauth_token");
        return token != null && !token.trim().isEmpty();
    }

    public static boolean isTwitterKitCallback(Uri uri) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }
        if (!"twittersdk".equals(lower(uri.getScheme()))
                || !"callback".equals(lower(uri.getHost()))) {
            return false;
        }
        String token = uri.getQueryParameter("oauth_token");
        String verifier = uri.getQueryParameter("oauth_verifier");
        return token != null && !token.isEmpty()
                && verifier != null && !verifier.isEmpty();
    }

    private static String systemAuthority() {
        return ParallaxELiteInstaller.getHostPkg() + ".SystemCallProvider";
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

    private static void purgeLocked(long now) {
        Iterator<Session> iterator = SESSIONS.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            long age = now - session.startedAt;
            if (age < 0L || age > TTL_MS) {
                iterator.remove();
            }
        }
    }

    private static void removeTargetLocked(String pkg, int userId) {
        Iterator<Session> iterator = SESSIONS.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.userId == userId && safeEquals(session.virtualPackage, pkg)) {
                iterator.remove();
            }
        }
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private static final class Session {
        final String oauthToken;
        final String virtualPackage;
        final int userId;
        final int bpid;
        final long startedAt;

        Session(String oauthToken, String virtualPackage, int userId, int bpid, long startedAt) {
            this.oauthToken = oauthToken;
            this.virtualPackage = virtualPackage;
            this.userId = userId;
            this.bpid = bpid;
            this.startedAt = startedAt;
        }
    }
}
