# In-app Facebook login and native Twitter/X login

Facebook browser authentication is hosted in `FacebookWebViewActivity`. It no
longer selects Chrome, a Custom Tab, or an AndroidX Auth Tab. The original OAuth
URL, state, redirect, PKCE parameters, and Meta callback contract are preserved.

## Facebook behavior

- `VirtualOAuthRouter` routes trusted Facebook authorization URLs to the host
  bridge without looking up an installed browser.
- `AuthTabCompat` rejects Facebook URLs, providing a second guard against an
  accidental Chrome/external-browser launch.
- The WebView activity runs in the dedicated `:facebookauth` process. Android 9+
  receives a separate WebView data-directory suffix.
- Before each new login, WebView cookies, DOM storage, form data, HTTP auth data,
  cache, and history are cleared. A Chrome login and an earlier in-app Facebook
  login therefore are not reused.
- Top-level navigation is restricted to HTTPS Facebook hosts and the validated,
  registered OAuth callback. Unknown schemes and external sites are blocked
  instead of being delegated to another app.
- No JavaScript interface is installed. The SDK does not read form values,
  passwords, cookies, authorization codes, or tokens.
- Callback state, fixed redirect parameters, virtual package ownership, session
  generation, replay rejection, and lifecycle cleanup remain enforced by the
  existing OAuth bridge and session store.

## Twitter/X behavior

Twitter/X provider selection and callback routing are unchanged. A compatible
installed X/Twitter application remains preferred where the existing resolver
supports it; the existing browser/WebView fallback behavior is retained.

## Verification

Automated tests cover Facebook exclusion from Auth Tab selection, rejection of
an attempted Facebook Auth Tab launch, trusted WebView navigation hosts and safe
custom callbacks. The full release unit-test and AAR assembly tasks should be
run with JDK 17, Gradle 8.13, Android Platform 36, Build Tools 36.0.0 and NDK
27.2.12479018.

Real-device acceptance still requires rebuilding the consuming loader with the
new AAR. While signed into Facebook in normal Chrome, start Facebook login from
the virtual app and verify that an in-app WebView asks for credentials. Complete
and cancel login, retry with another virtual user, rotate the screen, and verify
that exactly one result returns to Meta's waiting guest activity.
