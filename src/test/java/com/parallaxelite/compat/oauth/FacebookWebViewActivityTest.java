package com.parallaxelite.compat.oauth;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class FacebookWebViewActivityTest {
    @Test public void acceptsOnlyFacebookHttpsAsTopLevelWebContent() {
        assertTrue(FacebookWebViewActivity.isTrustedFacebookHttps(
                Uri.parse("https://m.facebook.com/dialog/oauth")));
        assertTrue(FacebookWebViewActivity.isTrustedFacebookHttps(
                Uri.parse("https://checkpoint.facebook.com/login")));

        assertFalse(FacebookWebViewActivity.isTrustedFacebookHttps(
                Uri.parse("http://m.facebook.com/dialog/oauth")));
        assertFalse(FacebookWebViewActivity.isTrustedFacebookHttps(
                Uri.parse("https://facebook.com.attacker.example/dialog/oauth")));
        assertFalse(FacebookWebViewActivity.isTrustedFacebookHttps(
                Uri.parse("intent://m.facebook.com/dialog/oauth")));
    }

    @Test public void acceptsRegisteredStyleCustomCallbacksButNotWebOrActiveSchemes() {
        assertTrue(FacebookWebViewActivity.isSupportedCustomRedirect(
                Uri.parse("fbconnect://cct.123456")));
        assertTrue(FacebookWebViewActivity.isSupportedCustomRedirect(
                Uri.parse("sample-app://oauth/callback")));

        assertFalse(FacebookWebViewActivity.isSupportedCustomRedirect(
                Uri.parse("https://example.com/callback")));
        assertFalse(FacebookWebViewActivity.isSupportedCustomRedirect(
                Uri.parse("javascript:alert(1)")));
        assertFalse(FacebookWebViewActivity.isSupportedCustomRedirect(
                Uri.parse("intent://callback")));
    }

    @Test public void rejectsMissingAndOversizedLaunchUrls() {
        assertNull(FacebookWebViewActivity.parseUri(null));
        assertNull(FacebookWebViewActivity.parseUri("   "));

        StringBuilder oversized = new StringBuilder("https://facebook.com/");
        while (oversized.length() <= 16_384) {
            oversized.append('a');
        }
        assertNull(FacebookWebViewActivity.parseUri(oversized.toString()));
    }
}
