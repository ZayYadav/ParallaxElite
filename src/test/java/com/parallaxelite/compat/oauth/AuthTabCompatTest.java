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
        ApplicationInfo app = new ApplicationInfo(); app.packageName = name; app.enabled = true;
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
        ResolveInfo activity = new ResolveInfo(); activity.activityInfo = new ActivityInfo();
        activity.activityInfo.packageName = name; activity.activityInfo.name = name + ".TabActivity";
        activity.activityInfo.enabled = activity.activityInfo.exported = true;
        activity.activityInfo.applicationInfo = app;
        Intent view = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        pm.addResolveInfoForIntent(view.setPackage(name), activity);
        return service;
    }

    @Test public void facebookLaunchRequestsFreshSessionAndPreservesUrl() {
        browser("com.android.chrome", true, FACEBOOK);
        Intent intent = AuthTabCompat.createLaunchIntent(context, FACEBOOK, "sample", "com.android.chrome");
        assertTrue(intent.getBooleanExtra(AuthTabCompat.EXTRA_ENABLE_EPHEMERAL_BROWSING, false));
        assertTrue(intent.getBooleanExtra(AuthTabCompat.EXTRA_LAUNCH_AUTH_TAB, false));
        assertEquals(FACEBOOK, intent.getData());
        assertEquals("sample", intent.getStringExtra(AuthTabCompat.EXTRA_REDIRECT_SCHEME));
        assertEquals("com.android.chrome", intent.getPackage());
        assertTrue(intent.hasExtra(AuthTabCompat.EXTRA_CUSTOM_TABS_SESSION));
        assertNull(intent.getExtras().getBinder(AuthTabCompat.EXTRA_CUSTOM_TABS_SESSION));
    }
    @Test public void rejectsSharedSessionOnlyBrowser() {
        browser("com.android.chrome", false, FACEBOOK);
        assertFalse(AuthTabCompat.isSupportedProvider(context, "com.android.chrome", FACEBOOK));
        try {
            AuthTabCompat.createLaunchIntent(context, FACEBOOK, "sample", "com.android.chrome");
            fail("Must not silently open a shared Facebook session");
        } catch (IllegalStateException expected) { }
    }
    @Test public void findsPrivateAlternativeWhenChromeCannotIsolate() {
        ResolveInfo chrome = browser("com.android.chrome", false, FACEBOOK);
        ResolveInfo alternative = browser("example.privatebrowser", true, FACEBOOK);
        pm.addResolveInfoForIntent(new Intent(SERVICE), Arrays.asList(chrome, alternative));
        assertEquals("example.privatebrowser", AuthTabCompat.findProvider(context, FACEBOOK));
    }
    @Test public void rejectsDisabledPrivateService() {
        ResolveInfo service = browser("com.android.chrome", true, FACEBOOK);
        service.serviceInfo.enabled = false;
        assertFalse(AuthTabCompat.isSupportedProvider(context, "com.android.chrome", FACEBOOK));
    }
    @Test public void twitterBrowserBehaviorDoesNotRequirePrivateCapability() {
        ResolveInfo service = browser("example.browser", false, TWITTER);
        pm.addResolveInfoForIntent(new Intent(SERVICE), service);
        Intent intent = AuthTabCompat.createLaunchIntent(context, TWITTER, "sample", "example.browser");
        assertFalse(intent.hasExtra(AuthTabCompat.EXTRA_ENABLE_EPHEMERAL_BROWSING));
        assertEquals(TWITTER, intent.getData());
    }
}
