package com.parallaxelite.compat.oauth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Exported custom-scheme trampoline for callbacks produced by the real Twitter/X app.
 * Callback data is accepted only when it matches a short-lived native-auth session.
 */
@Obfuscate
public final class TwitterOAuthCallbackActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        Uri callback = intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())
                ? null : intent.getData();

        // Legacy Twitter Kit 3.x uses twittersdk://callback after its request-token
        // authorize step. If that flow was handed to the installed X app, relay
        // the callback into the original guest OAuthActivity first; Twitter Kit
        // will then perform its own access-token exchange and return BGMI's result.
        if (TwitterKitExternalAuthBroker.relayCallbackFromHost(callback)) {
            finish();
            return;
        }

        TwitterOAuthSessionStore.Claim claim = TwitterOAuthSessionStore.claim(callback);
        if (claim == null) {
            finish();
            return;
        }

        Intent result = new Intent(Intent.ACTION_VIEW, callback);
        result.addCategory(Intent.CATEGORY_DEFAULT);
        result.addCategory(Intent.CATEGORY_BROWSABLE);

        boolean delivered = TwitterOAuthSessionStore.deliver(claim, RESULT_OK, result);
        if (delivered) {
            TwitterOAuthSessionStore.complete(claim.generation);
        } else {
            TwitterOAuthSessionStore.release(claim.generation);
        }
        finish();
    }
}
