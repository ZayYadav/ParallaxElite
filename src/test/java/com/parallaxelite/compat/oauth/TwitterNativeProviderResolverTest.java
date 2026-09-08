package com.parallaxelite.compat.oauth;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class TwitterNativeProviderResolverTest {
    private static final String PROVIDER = "com.twitter.android";
    private static final String CALLER = "example.host";
    private static final Uri URI = Uri.parse("https://x.com/i/oauth2/authorize?state=original&code_challenge=original");
    private PackageManager pm;
    @Before public void setup() { pm = RuntimeEnvironment.getApplication().getPackageManager(); }
    private ResolveInfo register(boolean exported) {
        ResolveInfo info = new ResolveInfo(); info.activityInfo = new ActivityInfo();
        info.activityInfo.name = PROVIDER + ".FutureOAuthActivity";
        info.activityInfo.packageName = PROVIDER;
        info.activityInfo.exported = exported; info.activityInfo.enabled = true;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = PROVIDER;
        info.activityInfo.applicationInfo.enabled = true;
        Intent probe = new Intent(Intent.ACTION_VIEW, URI).setPackage(PROVIDER)
                .addCategory(Intent.CATEGORY_DEFAULT).addCategory(Intent.CATEGORY_BROWSABLE);
        shadowOf(pm).addResolveInfoForIntent(probe, info);
        return info;
    }
    @Test public void acceptsDeclaredHandlerWithoutDependingOnInternalName() {
        register(true);
        Intent result = TwitterNativeProviderResolver.resolve(pm, URI, PROVIDER, CALLER);
        assertNotNull(result);
        assertEquals(new ComponentName(PROVIDER, PROVIDER + ".FutureOAuthActivity"), result.getComponent());
        assertEquals(URI, result.getData());
        assertEquals(Intent.ACTION_VIEW, result.getAction());
        assertEquals(0, result.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    @Test public void unavailableProviderDoesNotForceInternalActivity() {
        assertNull(TwitterNativeProviderResolver.resolve(pm, URI, PROVIDER, CALLER));
    }
    @Test public void rejectsUnexportedHandler() {
        register(false);
        assertNull(TwitterNativeProviderResolver.resolve(pm, URI, PROVIDER, CALLER));
    }
    @Test public void rejectsHandlerRequiringUngrantedPermission() {
        register(true).activityInfo.permission = "example.permission.PROVIDER_INTERNAL";
        assertNull(TwitterNativeProviderResolver.resolve(pm, URI, PROVIDER, CALLER));
    }
    @Test public void rejectsDisabledProviderApplication() {
        register(true).activityInfo.applicationInfo.enabled = false;
        assertNull(TwitterNativeProviderResolver.resolve(pm, URI, PROVIDER, CALLER));
    }
    @Test public void rejectsUnexpectedProviderAndNoncanonicalUrls() {
        register(true);
        assertNull(TwitterNativeProviderResolver.resolve(pm, URI, "example.impostor", CALLER));
        assertNull(TwitterNativeProviderResolver.resolve(pm,
                Uri.parse("https://user@x.com/i/oauth2/authorize"), PROVIDER, CALLER));
        assertNull(TwitterNativeProviderResolver.resolve(pm,
                Uri.parse("https://x.com:8443/i/oauth2/authorize"), PROVIDER, CALLER));
    }
}
