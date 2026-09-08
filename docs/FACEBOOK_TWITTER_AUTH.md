# Facebook and Twitter authentication maintenance

## Reference comparison

Reviewed ParallaxElite `a06c1fe0cdf2d40088bac0ed30b880ea60cac3c9` against
ParallaxSDK branch `KESHAVXOWNER`, commit
`f5815daf51349d63888212cd9833edadbf40f693`.
The reference module is `BLACK-OPNE/RIYAZ-VIP`; the nested
`KESHAVXOWNERSDK/BLACK-OPNE/RIYAZ-VIP` is a separate, older copy.

After namespace normalization, the reference Facebook callback activity,
session store, callback validator, and browser bridge already matched Elite.
Elite also has additional Twitter Kit broker, GCloud, and WebView compatibility
paths. Replacing these with the reference would remove existing functionality.
The reference-only `TwitterLegacyTlsCompat` removes certificate pins at runtime;
it has not been imported. Provider TLS verification remains enabled.

## Changes

- Facebook fragment state uses form decoding, including `+` as space and `%2B`
  as a literal plus. Query and fragment state must agree when both are present.
- Duplicate validation fields and malformed callback parameters are rejected.
  Registered callback targets and fixed query parameters still have to match.
- OAuth1 success requires the original request token and a verifier. Denial
  must identify that request token; unbound errors cannot claim a session.
- Twitter OAuth2 endpoint classification requires the canonical path, standard
  HTTPS port, and no fragment.
- Facebook and Twitter bridge completion, claims, and cleanup use the session generation.
  An old bridge cannot clear or claim a replacement login for the same app/user.
- Pending Facebook fallback/settle work is saved across Activity recreation;
  callbacks are removed when the old Activity is destroyed. After process death,
  a missing in-memory session ends the bridge so the caller can retry.
- Twitter also restores the session generation, native-launch status, pending
  callback wait, and WebView-fallback status across Activity recreation.

## Automated checks

Use JDK 17, Gradle 8.13, Android Platform 36, Build Tools 36.0.0 and
NDK 27.2.12479018, matching `.github/workflows/build-sdk.yml`:

```sh
gradle testReleaseUnitTest assembleRelease --no-daemon
```

The callback and session tests use Robolectric 4.16 with Android API 28 behavior,
including Android URI parsing and elapsed-time expiry. They cover provider
success/cancellation, callback correlation, duplicate state, redirect matching,
session replacement, retry, replay rejection, and expiry. They do not contact
Facebook or X and do not use real account credentials.

## Device validation still required

Run on the consuming host application with its actual package/signing identity
and provider configuration. Check Facebook app and browser login; X native,
OAuth1 and OAuth2 login; cancellation; returning from the browser; rotation;
background/resume; and restarting login after process death. Verify that one
provider result reaches the original app/user. Repeat on the supported Android
versions and both packaged ARM ABIs.

Use only lifecycle/error metadata when collecting diagnostics. Do not include
authorization URLs, cookies, consumer secrets, access tokens, or account data.
Real-device login has not been established by these local regression tests.

## Protocol references

- [OAuth1 callback token and verifier](https://www.rfc-editor.org/rfc/rfc5849#section-2.2)
- [Meta Android SDK URL parameter decoding](https://github.com/facebook/facebook-android-sdk/blob/main/facebook-core/src/main/java/com/facebook/internal/Utility.kt)
- [Robolectric setup](https://robolectric.org/getting-started/)
