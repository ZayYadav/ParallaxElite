package com.parallaxelite.compat.oauth;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TwitterOAuthUrlTest {
    private static final String QUERY =
            "?response_type=code&client_id=test&redirect_uri=https%3A%2F%2Fexample.com"
                    + "&scope=users.read&state=test&code_challenge=test"
                    + "&code_challenge_method=S256";

    @Test
    public void acceptsOfficialXAndTwitterOAuth2AuthorizeUrls() {
        assertTrue(TwitterOAuthUrl.isModernOAuth2Authorize(
                "https://x.com/i/oauth2/authorize" + QUERY));
        assertTrue(TwitterOAuthUrl.isModernOAuth2Authorize(
                "https://www.twitter.com/i/oauth2/authorize" + QUERY));
        assertTrue(TwitterOAuthUrl.isModernOAuth2Authorize(
                "https://mobile.x.com/i/oauth2/authorize" + QUERY));
    }

    @Test
    public void rejectsLookalikeHostsAndNonAuthorizeUrls() {
        assertFalse(TwitterOAuthUrl.isModernOAuth2Authorize(
                "https://x.com.example/i/oauth2/authorize" + QUERY));
        assertFalse(TwitterOAuthUrl.isModernOAuth2Authorize(
                "https://x.com@evil.example/i/oauth2/authorize" + QUERY));
        assertFalse(TwitterOAuthUrl.isModernOAuth2Authorize(
                "http://x.com/i/oauth2/authorize" + QUERY));
        assertFalse(TwitterOAuthUrl.isModernOAuth2Authorize("https://x.com/home"));
        assertFalse(TwitterOAuthUrl.isModernOAuth2Authorize(
                "https://x.com/i/oauth/authorize?oauth_token=test"));
    }

    @Test
    public void requiresCanonicalEndpointAndHttpsPort() {
        assertTrue(TwitterOAuthUrl.isModernOAuth2Authorize(
                "https://x.com:443/i/oauth2/authorize" + QUERY));
        for (String url : new String[] {
                "https://x.com:8443/i/oauth2/authorize",
                "https://x.com/I/OAUTH2/AUTHORIZE",
                "https://x.com/i/oauth2/%61uthorize",
                "https://x.com/i/oauth2/authorize#fragment"
        }) {
            assertFalse(TwitterOAuthUrl.isModernOAuth2Authorize(url));
        }
    }
}
