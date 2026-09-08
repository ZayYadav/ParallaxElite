package com.parallaxelite.compat.oauth;

import android.net.Uri;
import android.util.Log;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class AuthDiagnosticsTest {
    @Test public void reportsQueryAndFragmentWithoutExposingValues() {
        String shape = AuthDiagnostics.facebookResultShape(Uri.parse(
                "fbconnect://cct.example?code=PRIVATE_CODE&state=PRIVATE_STATE"
                        + "#access_token=PRIVATE_TOKEN&id_token=PRIVATE_ID&error_description=PRIVATE_ERROR"));
        assertTrue(shape.contains("code_present=true"));
        assertTrue(shape.contains("access_token_present=true"));
        assertTrue(shape.contains("id_token_present=true"));
        assertTrue(shape.contains("provider_error_present=true"));
        assertFalse(shape.contains("PRIVATE"));
        assertFalse(shape.contains("fbconnect"));
    }

    @Test public void doesNotMistakeValuesForFieldNames() {
        String shape = AuthDiagnostics.facebookResultShape(Uri.parse(
                "fbconnect://cct.example#state=access_token%3Dsecret%26error%3Dsecret"));
        assertTrue(shape.contains("access_token_present=false"));
        assertTrue(shape.contains("provider_error_present=false"));
        assertFalse(shape.contains("secret"));
    }

    @Test public void handlesNullAndEncodedFieldNames() {
        assertFalse(AuthDiagnostics.facebookResultShape(null).contains("=true"));
        assertTrue(AuthDiagnostics.facebookResultShape(Uri.parse(
                "fbconnect://cct.example#%65rror=private"))
                .contains("provider_error_present=true"));
    }

    @Test public void emitsInformationalMetadata() {
        ShadowLog.clear();
        AuthDiagnostics.info("ParallaxOAuth", "facebook stage=session_started");
        assertEquals(1, ShadowLog.getLogsForTag("ParallaxOAuth").size());
        ShadowLog.LogItem item = ShadowLog.getLogsForTag("ParallaxOAuth").get(0);
        assertEquals(Log.INFO, item.type);
        assertEquals("facebook stage=session_started", item.msg);
    }

    @Test public void boundsUntrustedDiagnosticInput() {
        StringBuilder oversized = new StringBuilder("fbconnect://cct.example#state=");
        for (int i = 0; i < 17_000; i++) oversized.append('x');
        assertTrue(AuthDiagnostics.facebookResultShape(Uri.parse(oversized.toString()))
                .contains("shape_unreadable=true"));
        assertTrue(AuthDiagnostics.facebookResultShape(Uri.parse("opaque:private"))
                .contains("shape_unreadable=true"));
    }
}
