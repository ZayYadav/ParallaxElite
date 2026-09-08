package com.parallaxelite.compat.oauth;

import android.net.Uri;
import android.util.Log;

/** Auth lifecycle metadata that remains available in release builds. */
final class AuthDiagnostics {
    private AuthDiagnostics() { }

    // Call only with constant stages and boolean/numeric metadata. Never pass
    // an Intent, Bundle, URL, exception message, or provider result value here.
    static void info(String tag, String metadata) {
        // Release rules remove Log.i. println preserves these audited events
        // without enabling unrelated informational logs that may contain URLs.
        Log.println(Log.INFO, tag, metadata);
    }

    /** Presence only: provider values, including error text, must stay private. */
    static String facebookResultShape(Uri callback) {
        boolean code = false;
        boolean accessToken = false;
        boolean idToken = false;
        boolean error = false;
        boolean malformed = false;
        if (callback != null) {
            try {
                if (!callback.isHierarchical() || callback.toString().length() > 16_384) {
                    throw new IllegalArgumentException();
                }
                String[] parts = {callback.getEncodedQuery(), callback.getEncodedFragment()};
                for (String part : parts) {
                    if (part == null) continue;
                    for (String field : part.split("&")) {
                        int separator = field.indexOf('=');
                        String name = Uri.decode(separator < 0 ? field : field.substring(0, separator));
                        if ("code".equals(name)) code = true;
                        if ("access_token".equals(name)) accessToken = true;
                        if ("id_token".equals(name)) idToken = true;
                        if ("error".equals(name) || "error_type".equals(name)
                                || "error_code".equals(name) || "error_message".equals(name)
                                || "error_description".equals(name)) error = true;
                    }
                }
            } catch (RuntimeException ignored) {
                malformed = true;
            }
        }
        return " code_present=" + code + " access_token_present=" + accessToken
                + " id_token_present=" + idToken + " provider_error_present=" + error
                + " shape_unreadable=" + malformed;
    }
}
