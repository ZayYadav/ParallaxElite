# Private Facebook login and native Twitter/X login

Facebook browser authentication now requests an ephemeral Auth Tab. This is the
fresh in-app browser option approved for this change. It is not a host-owned
Facebook WebView and does not share the browser's normal saved login session.
The browser owns the isolated cookies and clears the ephemeral session when its
tab closes; the SDK does not extract, copy or clear browser credentials.

## Facebook behavior

- The browser must advertise both Auth Tab and ephemeral-browsing support.
- Chrome remains preferred when it supports both. Another capable browser may
  be selected if Chrome cannot supply an isolated session.
- Every newly launched Facebook Auth Tab sets AndroidX's public ephemeral flag.
  The original authorization URI, state, redirect and PKCE parameters are kept.
- Capabilities are checked again before launch. If no supported browser is
  available, the host shows an update-browser message and cancels that bridge.
  The missing-provider route does not fall through to a shared browser session.
- Existing registered-target checks, callback state validation, session
  generation and replay rejection still apply. The Android 16 new-intent fix
  from PR #3 is included in the base of this change.

The reference `ParallaxSDK/ParallaxELiteLoader` branch's
`BLACK-OPNE/RIYAZ-VIP` module also used a Chrome Auth Tab for Facebook. The
comparison did not identify a separate Facebook WebView in that implementation.
No certificate-pin removal code from that repository was imported.

## Twitter/X behavior

Native OAuth routing remains the first choice when a supported installed X or
Twitter package advertises an enabled, exported handler for the exact OAuth URL.
Selection and launch now share one resolver. It probes an implicit, package-scoped
ACTION_VIEW intent, then uses the returned component. It checks required activity
permissions and application enabled state. There is no assumption about X's
internal activity names, nor a forced launch of an undeclared authorization UI.

The original authorization URL is preserved. The native bridge's short callback
wait also accepts a subsequent validated result instead of discarding it solely
because a no-data result arrived first. Missing native handlers use the existing
fallback behavior; app-to-app login cannot be guaranteed on provider builds that
do not expose a compatible handler or callback protocol. Legacy Twitter Kit SSO
is not replaced with an OAuth2 activity or a fabricated native result.

## Verification and device acceptance

Regression coverage checks private-session intent flags and original URL
preservation, rejection of shared-session-only or disabled services, discovery
of a private-capable alternative, unchanged Twitter browser selection, native
handler discovery without hard-coded internal names, and rejection of missing,
unexported, permission-protected, disabled or noncanonical native targets.

Tests use local synthetic PackageManager fixtures under Robolectric API 28.
Local `testReleaseUnitTest assembleRelease` passed all 45 tests with no failures,
errors or skips using JDK 17, Gradle 8.13, Platform 36 and NDK 27.2.12479018.
The release AAR contains both ARM ABIs; all ARM64 LOAD segments have 16-KB
alignment. Release bytecode retains the ephemeral-browsing flag and native
provider resolver.
They cannot establish that BGMI accepts a live provider result. Rebuild the
consuming loader APK with this AAR and verify on the affected device:

1. Stay signed into Facebook in normal Chrome, then start Facebook login from
   the host. The private auth session should start separately from that account.
2. Complete login and verify that the original game/user receives the result.
3. Close login, retry, rotate the activity and repeat with another game user.
4. Test without an ephemeral-capable browser; verify the explanation and
   cancellation instead of reuse of a normal browser session.
5. With X/Twitter installed, verify that its declared authorization handler opens
   and the validated callback returns. Also test cancellation, app removal and
   unsupported native handlers.

Only lifecycle/boolean result metadata should be collected. Provider identity,
state, redirect, TLS and game-side result validation are not bypassed. A valid
provider login can still be rejected by the consuming game's own configuration
or service; that result is not converted into success by this SDK.

## Primary references

- [Chrome: Ephemeral Custom Tabs and Auth Tabs](https://developer.chrome.com/docs/android/custom-tabs/guide-ephemeral-tab)
- [AndroidX AuthTabIntent](https://github.com/androidx/androidx/blob/androidx-main/browser/browser/src/main/java/androidx/browser/auth/AuthTabIntent.java)
- [AndroidX capability categories](https://github.com/androidx/androidx/blob/androidx-main/browser/browser/src/main/java/androidx/browser/customtabs/CustomTabsService.java)
