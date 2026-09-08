package com.parallaxelite.compat.oauth;

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class OAuthCallbackValidatorTest {
    private static final String FB = "https://www.facebook.com/dialog/oauth?state=sample";
    private static final String X1 = "https://api.twitter.com/oauth/authorize?oauth_token=request";
    private static final String X2 = "https://x.com/i/oauth2/authorize?state=sample";
    private static final String REDIRECT = "sampleapp://callback/login";

    private static boolean matches(String auth, String callback) {
        return OAuthCallbackValidator.matches(Uri.parse(auth), Uri.parse(REDIRECT),
                Uri.parse(REDIRECT + callback));
    }

    @Test public void acceptsFacebookQueryAndFragmentResults() {
        assertTrue(matches(FB, "?state=sample&code=result"));
        assertTrue(matches(FB, "#state=sample&access_token=result"));
        assertTrue(matches(FB, "?state=sample#state=sample&code=result"));
        assertTrue(matches(FB, "#state=sample&error=access_denied"));
    }

    @Test public void decodesFacebookFragmentLikeProviderSdk() {
        assertTrue(matches("https://facebook.com/dialog/oauth?state=hello%20world",
                "#state=hello+world&code=result"));
        assertTrue(matches("https://facebook.com/dialog/oauth?state=a%2Bb",
                "#state=a%2Bb&code=result"));
        assertTrue(matches(FB, "#st%61te=sample&code=result"));
    }

    @Test public void rejectsAmbiguousAndMissingState() {
        assertFalse(matches(FB, "?state=sample#state=different&code=result"));
        assertFalse(matches(FB, "?state=sample&state=sample&code=result"));
        assertFalse(matches(FB, "#state=sample&st%61te=sample&code=result"));
        assertFalse(matches(FB + "&state=sample", "?state=sample&code=result"));
        assertFalse(matches(FB, "?code=result"));
        assertFalse(matches(FB, "#state=&code=result"));
    }

    @Test public void preservesTwitterQueryOnlyState() {
        assertTrue(matches(X2, "?state=sample&code=result"));
        assertTrue(matches(X2, "?state=sample&error=access_denied"));
        assertFalse(matches(X2, "#state=sample&code=result"));
        assertFalse(matches(X2, "?state=other&code=result"));
    }

    @Test public void acceptsOnlyRequestBoundOAuth1Success() {
        assertTrue(matches(X1, "?oauth_token=request&oauth_verifier=verifier"));
        assertFalse(matches(X1, "?oauth_token=other&oauth_verifier=verifier"));
        assertFalse(matches(X1, "?oauth_token=request"));
        assertFalse(matches(X1, "?oauth_token=request&oauth_verifier="));
        assertFalse(matches(X1, "?oauth_token=request&oauth_token=request&oauth_verifier=verifier"));
    }

    @Test public void acceptsRequestBoundOAuth1DenialAndError() {
        assertTrue(matches(X1, "?denied=request"));
        assertTrue(matches(X1, "?oauth_token=request&error=access_denied"));
        assertFalse(matches(X1, "?denied=other"));
        assertFalse(matches(X1, "?error=access_denied"));
        assertFalse(matches(X1, "?denied=request&oauth_token=request&oauth_verifier=verifier"));
    }

    @Test public void preservesExactRedirectAndFixedParameters() {
        Uri auth = Uri.parse(FB);
        Uri expected = Uri.parse(REDIRECT + "?tenant=one&tenant=two");
        assertTrue(OAuthCallbackValidator.matches(auth, expected,
                Uri.parse(REDIRECT + "?tenant=one&tenant=two&state=sample")));
        assertFalse(OAuthCallbackValidator.matches(auth, expected,
                Uri.parse(REDIRECT + "?tenant=two&tenant=one&state=sample")));
        for (String target : new String[] {"other://callback/login", "sampleapp://other/login",
                "sampleapp://callback/LOGIN", "sampleapp://callback:99/login"}) {
            assertFalse(OAuthCallbackValidator.matches(auth, Uri.parse(REDIRECT),
                    Uri.parse(target + "?state=sample")));
        }
    }

    @Test public void rejectsMalformedOpaqueAndOversizedCallbacks() {
        assertFalse(matches(FB, "?state=%broken"));
        assertFalse(OAuthCallbackValidator.matches(Uri.parse(FB), Uri.parse("sampleapp:opaque"),
                Uri.parse("sampleapp:opaque")));
        assertFalse(OAuthCallbackValidator.matches(null, Uri.parse(REDIRECT), Uri.parse(REDIRECT)));
        assertFalse(matches(FB, "?state=sample&padding=" + new String(new char[16_384])));
    }
}
