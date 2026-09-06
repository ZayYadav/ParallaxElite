package com.parallaxelite.compat.oauth;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.lsposed.lsparanoid.Obfuscate;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.compat.auth.ExternalAuthRouter;
import com.parallaxelite.fake.frameworks.BPackageManager;
import com.parallaxelite.proxy.ProxyManifest;
import com.parallaxelite.utils.FileUtils;
import com.parallaxelite.utils.compat.BundleCompat;
import com.parallaxelite.utils.provider.ProviderCall;

/**
 * Host-main trampoline dedicated to real Twitter/X application OAuth.
 *
 * <p>The original guest Activity result target is retained in the bridge extras,
 * while the provider-owned Activity is launched by Android outside the virtual
 * process. A validated custom-scheme callback is captured by
 * {@link TwitterOAuthCallbackActivity} and relayed to that original guest target.
 * If a provider build returns the callback directly as an Activity result, this
 * bridge accepts the same validated URI without waiting for the exported callback
 * Activity.</p>
 *
 * <p>For a real {@code /i/oauth2/authorize} URL the bridge targets the verified
 * {@code com.x.android.deeplink.XUrlInterpreterActivity}, which is the exported
 * HTTPS VIEW handler in current X builds. X then performs its own internal
 * transition into {@code com.x.android.main.MainActivity}, where the authorization
 * UI runs. This preserves X's deep-link parser instead of forcing a launcher-only
 * Activity to consume a URL directly.</p>
 */
@Obfuscate
public final class TwitterNativeAuthBridgeActivity extends Activity {
    private static final String TAG = "TwitterNativeAuth";
    private static final int REQUEST_TWITTER_APP = 0x5854;
    private static final int REQUEST_TWITTER_WEB = 0x5855;
    private static final long CALLBACK_SETTLE_MS = 1_800L;

    private static final String OFFICIAL_TWITTER_PACKAGE = "com.twitter.android";
    private static final String X_URL_INTERPRETER_ACTIVITY =
            "com.x.android.deeplink.XUrlInterpreterActivity";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String virtualPackage;
    private int userId = -1;
    private long generation = -1L;
    private Uri authUri;
    private Uri expectedRedirectUri;
    private boolean providerLaunched;
    private boolean completionPending;
    private boolean fallbackLaunched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent bridge = getIntent();
        Intent providerIntent = bridge == null ? null
                : bridge.getParcelableExtra(ExternalAuthRouter.EXTRA_PROVIDER_INTENT);
        String providerPackage = ExternalAuthRouter.getTrustedProviderPackage(providerIntent);
        String authUrl = bridge == null ? null
                : bridge.getStringExtra(VirtualOAuthRouter.EXTRA_AUTH_URL);
        String redirectValue = bridge == null ? null
                : bridge.getStringExtra(VirtualOAuthRouter.EXTRA_REDIRECT_URI);
        virtualPackage = bridge == null ? null
                : bridge.getStringExtra(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE);
        userId = bridge == null ? -1
                : bridge.getIntExtra(ExternalAuthRouter.EXTRA_USER_ID, -1);

        authUri = safeUri(authUrl);
        expectedRedirectUri = safeUri(redirectValue);
        if (!validResultTarget(bridge)
                || !ExternalAuthRouter.isTwitterProviderPackage(providerPackage)
                || !ExternalAuthRouter.isTrustedTwitterOAuthUri(authUri)
                || !TwitterOAuthSessionStore.isHostCaptureSupported(expectedRedirectUri)
                || !redirectResolvesToVirtualPackage(expectedRedirectUri)
                || providerIntent == null) {
            relayCancellation();
            finish();
            return;
        }

        if (savedInstanceState == null) {
            generation = TwitterOAuthSessionStore.begin(
                    bridge, authUri, expectedRedirectUri, providerPackage);
            if (generation < 0L) {
                relayCancellation();
                finish();
                return;
            }

            try {
                providerIntent = prepareProviderIntent(
                        providerIntent, providerPackage, authUri);
                providerLaunched = true;
                startActivityForResult(providerIntent, REQUEST_TWITTER_APP);
            } catch (Throwable ignored) {
                Log.w(TAG, "native app launch failed; using private WebView fallback");
                launchWebFallback();
            }
        } else {
            providerLaunched = true;
        }
    }

    /**
     * Use the manifest-verified X URL interpreter for modern OAuth2 links. Older
     * Twitter/X builds and OAuth1 links keep the existing package-only resolver.
     */
    private Intent prepareProviderIntent(
            Intent original,
            String providerPackage,
            Uri authUri) {
        Intent prepared = new Intent(original);
        prepared.putExtra(ExternalAuthRouter.EXTRA_DIRECT_PROVIDER_DISPATCH, true);

        if (TwitterOAuthUrl.isModernOAuth2Authorize(authUri.toString())) {
            ComponentName exact =
                    findPreferredModernAuthorizeComponent(providerPackage, authUri);
            if (exact != null) {
                prepared.setAction(Intent.ACTION_VIEW);
                prepared.setData(authUri);
                prepared.addCategory(Intent.CATEGORY_DEFAULT);
                prepared.addCategory(Intent.CATEGORY_BROWSABLE);
                prepared.setComponent(exact);
                Log.i(TAG,
                        "OAuth2 authorize handoff=verified_x_url_interpreter"
                                + " target_ui=com.x.android.main.MainActivity");
                return prepared;
            }
            Log.w(TAG,
                    "OAuth2 authorize handoff=package_fallback reason=component_unavailable");
        }

        prepared.setComponent(null);
        prepared.setPackage(providerPackage);
        return prepared;
    }

    private ComponentName findPreferredModernAuthorizeComponent(
            String providerPackage,
            Uri authUri) {
        ComponentName[] candidates = new ComponentName[]{
                new ComponentName(providerPackage, X_URL_INTERPRETER_ACTIVITY),
                new ComponentName(OFFICIAL_TWITTER_PACKAGE, X_URL_INTERPRETER_ACTIVITY),
                new ComponentName("com.x.android", X_URL_INTERPRETER_ACTIVITY)
        };
        for (ComponentName candidate : candidates) {
            if (candidate == null
                    || !ExternalAuthRouter.isTwitterProviderPackage(
                    candidate.getPackageName())) {
                continue;
            }
            if (isLaunchableExactXAuthorizeComponent(candidate, authUri)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isLaunchableExactXAuthorizeComponent(
            ComponentName component,
            Uri authUri) {
        try {
            if (component == null || authUri == null
                    || ParallaxELiteInstaller.getContext() == null
                    || !ExternalAuthRouter.isTwitterProviderPackage(
                    component.getPackageName())) {
                return false;
            }

            PackageManager pm = ParallaxELiteInstaller.getContext().getPackageManager();
            ActivityInfo info = pm.getActivityInfo(component, 0);
            if (!isUsableExactXActivity(info, component)) {
                return false;
            }

            String permission = info.permission;
            if (permission != null && !permission.trim().isEmpty()
                    && pm.checkPermission(permission, getPackageName())
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            Intent probe = new Intent(Intent.ACTION_VIEW, authUri);
            probe.addCategory(Intent.CATEGORY_DEFAULT);
            probe.addCategory(Intent.CATEGORY_BROWSABLE);
            probe.setComponent(component);
            ResolveInfo resolved = pm.resolveActivity(
                    probe, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null
                    && isUsableExactXActivity(resolved.activityInfo, component);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isUsableExactXActivity(
            ActivityInfo info,
            ComponentName expected) {
        return info != null
                && expected != null
                && expected.getPackageName().equals(info.packageName)
                && expected.getClassName().equals(info.name)
                && ExternalAuthRouter.isTwitterProviderPackage(info.packageName)
                && info.enabled
                && info.exported
                && (info.applicationInfo == null || info.applicationInfo.enabled);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!providerLaunched || completionPending || virtualPackage == null || userId < 0) {
            return;
        }
        if (TwitterOAuthSessionStore.isCompleted(virtualPackage, userId)) {
            TwitterOAuthSessionStore.clear(virtualPackage, userId);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_TWITTER_WEB) {
            if (handleOAuthResult(data)) {
                return;
            }
            TwitterOAuthSessionStore.clear(virtualPackage, userId);
            relayCancellation();
            finish();
            return;
        }

        if (requestCode != REQUEST_TWITTER_APP || completionPending) {
            return;
        }

        if (handleOAuthResult(data)) {
            return;
        }

        // Several Twitter/X builds finish or cancel their provider Activity just
        // before Android dispatches the custom-scheme callback. Give that callback
        // a short bounded window; only then fall back to our in-app WebView.
        completionPending = true;
        mainHandler.postDelayed(() -> {
            completionPending = false;
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (TwitterOAuthSessionStore.isCompleted(virtualPackage, userId)) {
                TwitterOAuthSessionStore.clear(virtualPackage, userId);
                finish();
                return;
            }
            Log.i(TAG, "native callback unavailable; opening private WebView fallback");
            launchWebFallback();
        }, CALLBACK_SETTLE_MS);
    }

    private boolean handleOAuthResult(Intent data) {
        Uri callback = data == null ? null : data.getData();
        if (callback == null) {
            return false;
        }

        TwitterOAuthSessionStore.Claim claim = TwitterOAuthSessionStore.claim(callback);
        if (claim == null
                || virtualPackage == null
                || !virtualPackage.equals(claim.virtualPackage)
                || userId != claim.userId) {
            return false;
        }

        Intent result = new Intent(data);
        boolean delivered = TwitterOAuthSessionStore.deliver(
                claim, RESULT_OK, result);
        if (!delivered) {
            TwitterOAuthSessionStore.release(claim.generation);
            return false;
        }

        TwitterOAuthSessionStore.complete(claim.generation);
        TwitterOAuthSessionStore.clear(virtualPackage, userId);
        finish();
        return true;
    }

    private void launchWebFallback() {
        if (fallbackLaunched || isFinishing() || isDestroyed()) {
            return;
        }
        if (!ExternalAuthRouter.isTrustedTwitterOAuthUri(authUri)
                || !TwitterOAuthSessionStore.isHostCaptureSupported(expectedRedirectUri)) {
            TwitterOAuthSessionStore.clear(virtualPackage, userId);
            relayCancellation();
            finish();
            return;
        }

        try {
            fallbackLaunched = true;
            Intent web = new Intent(this, TwitterWebViewActivity.class);
            web.putExtra(TwitterWebViewActivity.EXTRA_AUTH_URL, authUri.toString());
            web.putExtra(
                    TwitterWebViewActivity.EXTRA_REDIRECT_URI,
                    expectedRedirectUri.toString());
            startActivityForResult(web, REQUEST_TWITTER_WEB);
        } catch (Throwable ignored) {
            TwitterOAuthSessionStore.clear(virtualPackage, userId);
            relayCancellation();
            finish();
        }
    }

    private boolean validResultTarget(Intent bridge) {
        if (bridge == null) {
            return false;
        }
        Bundle extras = bridge.getExtras();
        if (extras == null) {
            return false;
        }
        IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
        int requestCode = extras.getInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
        int bpid = extras.getInt(ExternalAuthRouter.EXTRA_BPID, -1);
        return resultTo != null
                && requestCode >= 0
                && bpid >= 0 && bpid <= 24
                && userId >= 0
                && virtualPackage != null
                && !virtualPackage.trim().isEmpty();
    }

    private boolean redirectResolvesToVirtualPackage(Uri redirectUri) {
        if (redirectUri == null || virtualPackage == null || userId < 0) {
            return false;
        }
        try {
            Intent callback = new Intent(Intent.ACTION_VIEW, redirectUri);
            callback.addCategory(Intent.CATEGORY_DEFAULT);
            callback.addCategory(Intent.CATEGORY_BROWSABLE);
            callback.setPackage(virtualPackage);
            ResolveInfo resolved = BPackageManager.get().resolveActivity(
                    callback,
                    FileUtils.FileMode.MODE_IWUSR,
                    null,
                    userId);
            return resolved != null
                    && resolved.activityInfo != null
                    && virtualPackage.equals(resolved.activityInfo.packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void relayCancellation() {
        try {
            Intent bridge = getIntent();
            Bundle extras = bridge == null ? null : bridge.getExtras();
            if (extras == null) {
                return;
            }
            IBinder resultTo = extras.getBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER);
            String resultWho = extras.getString(ExternalAuthRouter.EXTRA_RESULT_WHO);
            int originalRequestCode = extras.getInt(
                    ExternalAuthRouter.EXTRA_REQUEST_CODE, -1);
            int bpid = extras.getInt(ExternalAuthRouter.EXTRA_BPID, -1);
            if (resultTo == null || originalRequestCode < 0 || bpid < 0 || bpid > 24
                    || virtualPackage == null || virtualPackage.trim().isEmpty()) {
                return;
            }

            Bundle relay = new Bundle();
            BundleCompat.putBinder(
                    relay, ExternalAuthRouter.EXTRA_RESULT_BINDER, resultTo);
            relay.putString(ExternalAuthRouter.EXTRA_RESULT_WHO, resultWho);
            relay.putInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, originalRequestCode);
            relay.putInt(ExternalAuthRouter.EXTRA_RESULT_CODE, RESULT_CANCELED);
            relay.putInt(ExternalAuthRouter.EXTRA_BPID, bpid);
            relay.putInt(ExternalAuthRouter.EXTRA_USER_ID, userId);
            relay.putString(
                    ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE, virtualPackage);
            ProviderCall.callSafely(
                    ProxyManifest.getProxyAuthorities(bpid),
                    ExternalAuthRouter.METHOD_DELIVER_ACTIVITY_RESULT,
                    null,
                    relay);
        } catch (Throwable ignored) {
        }
    }

    private static Uri safeUri(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(value.trim());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
