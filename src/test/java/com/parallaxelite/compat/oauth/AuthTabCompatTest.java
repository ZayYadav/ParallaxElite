package com.parallaxelite.compat.oauth;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class AuthTabCompatTest {
    private static final String SERVICE = "android.support.customtabs.action.CustomTabsService";
    private static final String AUTH = "androidx.browser.auth.category.AuthTab";
    private static final String PRIVATE = "androidx.browser.customtabs.category.EphemeralBrowsing";
    private static final Uri FACEBOOK = Uri.parse(
            "https://m.facebook.com/dialog/oauth?state=original&redirect_uri=sample%3A%2F%2Fcallback");
    private static final Uri TWITTER = Uri.parse("https://x.com/i/oauth2/authorize?state=original");
    private Context context;
    private ShadowPackageManager pm;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        pm = shadowOf(context.getPackageManager());
    }

    private ResolveInfo browser(String name, boolean privateMode, Uri uri) {
        ApplicationInfo app = new ApplicationInfo();
        app.packageName = name;
        app.enabled = true;

        ResolveInfo service = new ResolveInfo();
        service.serviceInfo = new ServiceInfo();
        service.serviceInfo.packageName = name;
        service.serviceInfo.name = name + ".TabsService";
        service.serviceInfo.enabled = service.serviceInfo.exported = true;
        service.serviceInfo.applicationInfo = app;
        service.filter = new IntentFilter(SERVICE);
        service.filter.addCategory(AUTH);
        if (privateMode) service.filter.addCategory(PRIVATE);
        pm.addResolveInfoForIntent(new Intent(SERVICE).setPackage(name), service);

        ResolveInfo activity = new ResolveInfo();
        activity.activityInfo = new ActivityInfo();
        activity.activityInfo.packageName = name;
        activity.activityInfo.name = name + ".TabActivity";
        activity.activityInfo.enabled = activity.activityInfo.exported = true;
        activity.activityInfo.applicationInfo = app;
        Intent view = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        pm.addResolveInfoForIntent(view.setPackage(name), activity);
        return service;
    }

    @Test public void facebookUsesNormalParallaxSdkAuthTabAndPreservesUrl() {
        browser("com.android.chrome", false, FACEBOOK);
        Intent intent = AuthTabCompat.createLaunchIntent(
                context, FACEBOOK, "sample", "com.android.chrome");
        assertTrue(intent.getBooleanExtra(AuthTabCompat.EXTRA_LAUNCH_AUTH_TAB, false));
        assertEquals(FACEBOOK, intent.getData());
        assertEquals("sample", intent.getStringExtra(AuthTabCompat.EXTRA_REDIRECT_SCHEME));
        assertEquals("com.android.chrome", intent.getPackage());
        assertTrue(intent.hasExtra(AuthTabCompat.EXTRA_CUSTOM_TABS_SESSION));
        assertNull(intent.getExtras().getBinder(AuthTabCompat.EXTRA_CUSTOM_TABS_SESSION));
    }

    @Test public void facebookDoesNotRequireEphemeralBrowsingCapability() {
        browser("com.android.chrome", false, FACEBOOK);
        assertTrue(AuthTabCompat.isSupportedProvider(context, "com.android.chrome", FACEBOOK));
        assertEquals("com.android.chrome", AuthTabCompat.findProvider(context, FACEBOOK));
    }

    @Test public void facebookStillAcceptsBrowserThatAlsoSupportsPrivateMode() {
        browser("com.android.chrome", true, FACEBOOK);
        assertTrue(AuthTabCompat.isSupportedProvider(context, "com.android.chrome", FACEBOOK));
    }

    @Test public void findsNormalAlternativeWhenChromeIsUnavailable() {
        ResolveInfo alternative = browser("example.browser", false, FACEBOOK);
        pm.addResolveInfoForIntent(new Intent(SERVICE), Arrays.asList(alternative));
        assertEquals("example.browser", AuthTabCompat.findProvider(context, FACEBOOK));
    }

    @Test public void rejectsDisabledAuthTabService() {
        ResolveInfo service = browser("com.android.chrome", false, FACEBOOK);
        service.serviceInfo.enabled = false;
        assertFalse(AuthTabCompat.isSupportedProvider(context, "com.android.chrome", FACEBOOK));
    }

    @Test public void twitterBehaviorRemainsUnchanged() {
        ResolveInfo service = browser("example.browser", false, TWITTER);
        pm.addResolveInfoForIntent(new Intent(SERVICE), service);
        Intent intent = AuthTabCompat.createLaunchIntent(
                context, TWITTER, "sample", "example.browser");
        assertEquals(TWITTER, intent.getData());
        assertEquals("example.browser", intent.getPackage());
        assertTrue(intent.getBooleanExtra(AuthTabCompat.EXTRA_LAUNCH_AUTH_TAB, false));
    }
}
