package com.parallaxelite.compat.oauth;

import android.net.Uri;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Validates callback ownership before returning provider results to a virtual app. */
public final class OAuthCallbackValidator {
    private static final int MAX_URL_LENGTH = 16_384;

    private OAuthCallbackValidator() {
    }

    public static boolean matches(Uri authUri, Uri expectedRedirect, Uri callback) {
        try {
            if (!validUri(authUri) || !validUri(expectedRedirect) || !validUri(callback)) {
                return false;
            }
            if (!lower(expectedRedirect.getScheme()).equals(lower(callback.getScheme()))
                    || !lower(expectedRedirect.getEncodedAuthority())
                    .equals(lower(callback.getEncodedAuthority()))
                    || !same(expectedRedirect.getEncodedPath(), callback.getEncodedPath())) {
                return false;
            }
            if (expectedRedirect.getFragment() != null
                    && !same(expectedRedirect.getEncodedFragment(), callback.getEncodedFragment())) {
                return false;
            }

            Map<String, List<String>> auth = parameters(authUri.getEncodedQuery());
            Map<String, List<String>> query = parameters(callback.getEncodedQuery());
            Map<String, List<String>> fixed = parameters(expectedRedirect.getEncodedQuery());
            String expectedState = single(auth, "state");
            String queryState = single(query, "state");
            String callbackState = queryState;

            // Meta's SDK form-decodes and merges query + fragment. Validate both
            // locations so that the SDK cannot consume a different state later.
            if (FacebookAuthHost.matches(authUri)) {
                String fragmentState = single(parameters(callback.getEncodedFragment()), "state");
                if (queryState != null && fragmentState != null
                        && !queryState.equals(fragmentState)) {
                    return false;
                }
                if (fragmentState != null) {
                    callbackState = fragmentState;
                }
            }
            if (expectedState != null
                    && (expectedState.isEmpty() || !expectedState.equals(callbackState))) {
                return false;
            }

            String requestToken = single(auth, "oauth_token");
            if (requestToken != null && !matchesRequestToken(requestToken, query)) {
                return false;
            }
            for (Map.Entry<String, List<String>> entry : fixed.entrySet()) {
                if (!entry.getValue().equals(query.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
            // Malformed input and ambiguous validation fields fail closed.
            return false;
        }
    }

    private static boolean matchesRequestToken(String requestToken,
            Map<String, List<String>> query) {
        if (requestToken.isEmpty()) {
            return false;
        }
        String token = single(query, "oauth_token");
        String denied = single(query, "denied");
        String verifier = single(query, "oauth_verifier");
        String error = single(query, "error");
        if (denied != null) {
            return requestToken.equals(denied) && token == null && verifier == null;
        }
        if (!requestToken.equals(token)) {
            return false;
        }
        return (error != null && !error.isEmpty())
                || (verifier != null && !verifier.isEmpty());
    }

    private static boolean validUri(Uri uri) {
        return uri != null && uri.isHierarchical() && uri.getScheme() != null
                && uri.toString().length() <= MAX_URL_LENGTH && uri.getUserInfo() == null;
    }

    private static Map<String, List<String>> parameters(String encoded) throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator), "UTF-8");
            String value = separator < 0 ? ""
                    : URLDecoder.decode(pair.substring(separator + 1), "UTF-8");
            List<String> values = result.get(key);
            if (values == null) {
                values = new ArrayList<>();
                result.put(key, values);
            }
            values.add(value);
        }
        return result;
    }

    private static String single(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values == null) {
            return null;
        }
        if (values.size() != 1) {
            throw new IllegalArgumentException("Ambiguous OAuth validation field");
        }
        return values.get(0);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
