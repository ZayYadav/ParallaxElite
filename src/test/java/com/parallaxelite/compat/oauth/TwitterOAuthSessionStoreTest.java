package com.parallaxelite.compat.oauth;

import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import com.parallaxelite.compat.auth.ExternalAuthRouter;
import java.time.Duration;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class TwitterOAuthSessionStoreTest {
    private static final String PACKAGE = "example.authclient";
    private static final Uri REDIRECT = Uri.parse("twittersdk://callback");

    private static long begin(String request) {
        Bundle target = new Bundle();
        target.putBinder(ExternalAuthRouter.EXTRA_RESULT_BINDER, new Binder());
        target.putInt(ExternalAuthRouter.EXTRA_REQUEST_CODE, 10);
        target.putInt(ExternalAuthRouter.EXTRA_BPID, 0);
        target.putInt(ExternalAuthRouter.EXTRA_USER_ID, 0);
        target.putString(ExternalAuthRouter.EXTRA_VIRTUAL_PACKAGE, PACKAGE);
        return TwitterOAuthSessionStore.begin(new Intent().putExtras(target),
                Uri.parse("https://api.twitter.com/oauth/authorize?oauth_token=" + request),
                REDIRECT, "com.twitter.android");
    }

    private static Uri callback(String request) {
        return Uri.parse(REDIRECT + "?oauth_token=" + request + "&oauth_verifier=verifier");
    }

    @After public void clear() {
        TwitterOAuthSessionStore.clear(PACKAGE, 0);
    }

    @Test public void staleBridgeCannotClaimOrClearReplacementLogin() {
        long oldGeneration = begin("same");
        long newGeneration = begin("same");
        assertTrue(oldGeneration > 0L);
        assertTrue(newGeneration > 0L);
        TwitterOAuthSessionStore.clear(oldGeneration);
        assertTrue(TwitterOAuthSessionStore.contains(newGeneration));
        assertNull(TwitterOAuthSessionStore.claim(callback("same"), oldGeneration));
        assertNotNull(TwitterOAuthSessionStore.claim(callback("same"), newGeneration));
    }

    @Test public void callbacksAreBoundToRequestIncludingDenial() {
        long generation = begin("request");
        assertNull(TwitterOAuthSessionStore.claim(callback("other"), generation));
        assertNull(TwitterOAuthSessionStore.claim(Uri.parse(REDIRECT + "?denied=other")));
        assertNotNull(TwitterOAuthSessionStore.claim(Uri.parse(REDIRECT + "?denied=request")));
    }

    @Test public void completionPreventsReplayAndReleaseAllowsRetry() {
        long generation = begin("request");
        assertNotNull(TwitterOAuthSessionStore.claim(callback("request")));
        assertNull(TwitterOAuthSessionStore.claim(callback("request")));
        TwitterOAuthSessionStore.release(generation);
        assertNotNull(TwitterOAuthSessionStore.claim(callback("request")));
        TwitterOAuthSessionStore.complete(generation);
        TwitterOAuthSessionStore.release(generation);
        assertTrue(TwitterOAuthSessionStore.isCompleted(generation));
        assertNull(TwitterOAuthSessionStore.claim(callback("request")));
    }

    @Test public void expiredSessionsCannotClaimCallbacks() {
        long generation = begin("request");
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4));
        assertFalse(TwitterOAuthSessionStore.contains(generation));
        assertNull(TwitterOAuthSessionStore.claim(callback("request")));
    }
}
