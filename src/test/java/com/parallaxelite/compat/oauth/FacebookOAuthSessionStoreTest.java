package com.parallaxelite.compat.oauth;

import android.net.Uri;
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
public class FacebookOAuthSessionStoreTest {
    private static final String PACKAGE = "example.authclient";
    private static final Uri REDIRECT = Uri.parse("fbconnect://cct.example.authclient");

    private static long begin(String state) {
        return FacebookOAuthSessionStore.begin(
                Uri.parse("https://facebook.com/dialog/oauth?state=" + state), REDIRECT, PACKAGE, 0);
    }

    private static Uri callback(String state) {
        return Uri.parse(REDIRECT + "#state=" + state + "&code=result");
    }

    @After public void clear() {
        FacebookOAuthSessionStore.clear(PACKAGE, 0);
        FacebookOAuthSessionStore.clear(PACKAGE, 1);
    }

    @Test public void staleCleanupCannotRemoveNewLogin() {
        long oldGeneration = begin("first");
        long newGeneration = begin("second");
        FacebookOAuthSessionStore.clear(oldGeneration);
        assertFalse(FacebookOAuthSessionStore.contains(oldGeneration));
        assertTrue(FacebookOAuthSessionStore.contains(newGeneration));
        assertNotNull(FacebookOAuthSessionStore.claim(callback("second"), newGeneration));
    }

    @Test public void staleBridgeCannotClaimOrCompleteNewLogin() {
        long oldGeneration = begin("same");
        long newGeneration = begin("same");
        assertNull(FacebookOAuthSessionStore.claim(callback("same"), oldGeneration));
        FacebookOAuthSessionStore.complete(oldGeneration);
        assertFalse(FacebookOAuthSessionStore.isCompleted(newGeneration));
        assertNotNull(FacebookOAuthSessionStore.claim(callback("same"), newGeneration));
    }

    @Test public void completionRejectsReplayAndReleaseAllowsRetry() {
        long generation = begin("sample");
        assertNotNull(FacebookOAuthSessionStore.claim(callback("sample")));
        assertNull(FacebookOAuthSessionStore.claim(callback("sample")));
        FacebookOAuthSessionStore.release(generation);
        assertNotNull(FacebookOAuthSessionStore.claim(callback("sample")));
        FacebookOAuthSessionStore.complete(generation);
        FacebookOAuthSessionStore.release(generation);
        assertTrue(FacebookOAuthSessionStore.isCompleted(generation));
        assertNull(FacebookOAuthSessionStore.claim(callback("sample")));
    }

    @Test public void rejectsAmbiguousHostCallbackAcrossUsers() {
        begin("same");
        FacebookOAuthSessionStore.begin(Uri.parse("https://facebook.com/dialog/oauth?state=same"),
                REDIRECT, PACKAGE, 1);
        assertNull(FacebookOAuthSessionStore.claim(callback("same")));
    }

    @Test public void expiresAbandonedAndCompletedSessions() {
        long generation = begin("sample");
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4));
        assertFalse(FacebookOAuthSessionStore.contains(generation));
        assertNull(FacebookOAuthSessionStore.claim(callback("sample")));
        generation = begin("sample");
        FacebookOAuthSessionStore.complete(generation);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(11));
        assertFalse(FacebookOAuthSessionStore.isCompleted(generation));
    }
}
