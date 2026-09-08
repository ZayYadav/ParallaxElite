# September 8 login capture review

The capture demonstrates a Facebook browser round trip and subsequent activity
result delivery, but does not establish successful login or the contents of the
final game result. The reported error number (possibly 9999) is unconfirmed.
This change repairs a concrete Android 16 new-intent dispatch compatibility gap,
diagnostic visibility and manifest/CI maintenance issues. End-to-end game login
still requires validation with a rebuilt host on the affected device.

## Evidence and scope

- Source: [ALL-LOG_20260908_150832.txt](https://github.com/ZayYadav/ParallaxSDK/blob/ParallaxELiteLoader/ALL-LOG_20260908_150832.txt).
- Capture: 18,601,854 bytes, 115,275 lines, September 8 15:08:32–15:11:27.
- SHA-256: `c66948bab444b6e34345a723ffd17c4072225311edb191ec197042c2a3f35ad9`.
- Reviewed source: ParallaxElite main `9e8880ee54112d9fbe92e699dbee2c1e7306e21f`.
- Device: Nothing A069, Android 16/API 36. Host: `com.onecore.loader`;
  guest: `com.pubg.imobile`. Relevant observed PIDs: 11055 and 13388.
- The complete file was scanned for crash markers, channel counts, relevant
  process warnings, authentication events and callback lifecycle. Source review
  focused on the authentication bridge, validators, session stores, manifest,
  activity routing, release rules and build workflow. This is not an exhaustive
  audit of all native and Java code in the repository.
- No reliable embedded SDK commit identifier was identified. The captured APK
  must not be assumed to contain the reviewed main revision.

## Authentication timeline

Times below use the embedded device logcat timestamp, not the collection time.
Line numbers refer to the original file.

| Device time | Line | Observation |
| --- | ---: | --- |
| 15:09:43.274 | 64013 | FacebookActivity launch requested. |
| 15:09:43.416 | 64170 | CustomTabMainActivity launch requested. |
| 15:09:43.473 | 64270 | Host VirtualOAuthBridgeActivity started. |
| 15:09:44.069 | 64703 | Chrome CustomTabActivity resumed. |
| 15:09:51.295 | 74788 | Chrome requested its activity finish. |
| 15:09:51.451 | 75165 | Guest CustomTabActivity launch requested. |
| 15:09:51.509 | 75295 | Host bridge received an activity result. |
| 15:09:52.349 | 77065 | Guest CustomTabMainActivity resumed. |
| 15:09:52.433 | 77312 | FacebookActivity received an activity result. |
| 15:09:52.826 | 78255 | IMSDKProxyActivity received an activity result. |
| 15:10:00.458 | 93066 | TwitterWebActivity launch requested. |

An activity-result lifecycle event does not expose its result code or payload.
A component launch request likewise does not prove successful callback handling.
Therefore the file cannot distinguish provider rejection, cancellation, invalid
state, a lifecycle race or a downstream game SDK error.

Meta SDK 16's [CustomTabActivity](https://github.com/facebook/facebook-android-sdk/blob/sdk-version-16.0.0/facebook-common/src/main/java/com/facebook/CustomTabActivity.kt)
passes the redirect to CustomTabMainActivity. Its
[CustomTabMainActivity](https://github.com/facebook/facebook-android-sdk/blob/sdk-version-16.0.0/facebook-common/src/main/java/com/facebook/CustomTabMainActivity.kt)
returns cancellation when resumed without a handled redirect. An ordering race
is a possible explanation, not a diagnosis proven by this capture. Changing
state validation or timeout constants on this evidence alone is unjustified.

## Confirmed issues and changes

1. **Modern Android new-intent callbacks can be silently dropped.**
   `BActivityThread.handleNewIntent` checked only token-based method signatures.
   Android 16's [ActivityThread](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/app/ActivityThread.java)
   instead takes an existing ActivityClientRecord and the intent list. None of
   the previous branches handles that signature, and the method had no final
   diagnostic. The new dispatcher selects the record-based signature using the
   existing process activity registry, retains the three legacy signatures,
   rejects missing/stale targets and reports unsupported or failed delivery.
   It preserves the original intent and does not retry after an invoked callback
   throws. The signature gap is confirmed in source; its responsibility for this
   particular game error remains a strong hypothesis until device verification.
2. **Release authentication diagnostics disappear.** Existing `Log.i` calls are
   stripped by `proguard-rules.pro`. No ParallaxOAuth/ParallaxAuth tag records are
   present in this capture. Audited metadata now uses `AuthDiagnostics.info`,
   which calls `Log.println` at INFO priority. Broad logging remains unchanged.
3. **Provider result shape was not recorded.** Facebook events now record only
   the presence of code/access-token/id-token/error fields across query and
   fragment. They never include the values, URL, state, provider error text or
   arbitrary extras. Oversized/opaque input receives an unreadable indicator.
   Presence does not mean a token is valid or that login succeeded.
4. **Duplicate manifest permission.** Removed the second GMS
   ACTIVITY_RECOGNITION declaration, matching capture warnings at lines 21804,
   22967 and 22976. Removed the obsolete manifest package attribute; Gradle's
   existing namespace remains authoritative.
5. **CI coverage/report retention.** Pull requests to main now run the existing
   workflow. Removed the `clean` between tests and assembly, which deleted test
   reports; reports are uploaded even after a failed preceding step.

## Other findings

| Finding | Assessment |
| --- | --- |
| No FATAL EXCEPTION, Fatal signal or ANR-in markers | No such crash evidence in the supplied capture; absence is not proof of no crash outside it. |
| 2,600 lines containing write-failed EPIPE | Many are system diagnostic dump writes. They are not evidence of OAuth network transport failure. |
| Eight repeated 1,187-line device-property blocks | Collector appears to record whole property dumps under multiple headings, increasing noise. |
| Facebook native package visibility warnings | Native-provider discovery warning; browser launch still occurred. Not enough to blame the final login error on visibility. |
| Hidden API bootstrap warnings | Reflection compatibility warning; host/guest subsequently continue. No causal link to final login failure established. |
| Firebase SERVICE_NOT_AVAILABLE / GMS measurement errors | Push/analytics service failures. They must not automatically be treated as Facebook authentication failures. |
| Native resource/audio/camera/property warnings | Separate compatibility or guest/OEM messages; not a proven cause of the login result issue. |
| Skipped-frame messages | Observed UI stalls. Diagnostic collection may contribute; no controlled performance comparison was performed. |
| EXIT_INFO-labelled surface/window content | Those labels do not supply a reliable Android ApplicationExitInfo crash diagnosis. |

## Collection privacy

The capture's opening assertion that sensitive values are redacted is not
reliable: two captured-link records still contain OAuth correlation parameters.
No such values are reproduced here. This patch cannot redact logs generated by
Android, Chrome, the game, or the separate ParallaxSDK collector. Avoid sharing
the original capture further until those URL query/fragment values are removed.
The raw capture is deliberately excluded from the deliverable archive.

For another diagnostic run, collect the ParallaxOAuth and ParallaxAuth metadata
tags plus the exact visible error code/text, host version and SDK commit. Do not
collect full authorization URLs, cookies, credentials or raw result Bundles.
Compare normal installation and container behavior on the same device. The
updated AAR requires rebuilding the consuming host APK; changing this SDK alone
does not update an installed loader.

## Remaining validation

Local verification completed with JDK 17, Gradle 8.13, Android Platform 36 and
NDK 27.2.12479018: `testReleaseUnitTest assembleRelease` succeeded. All 34 tests
passed (22 existing, 5 diagnostic privacy/shape tests and 7 dispatch tests), with
zero failures, errors or skips. The release AAR is 1,869,237 bytes and contains
both ARM32 and ARM64 native libraries. All three ARM64 LOAD segments have 16 KB
alignment. Inspection of the release classes confirms that both the new
dispatcher and the INFO-level diagnostic logging calls survive R8. These checks
do not replace device validation.

The game's final login outcome remains unverified. No physical-device login or
provider account was exercised, and no game access token was inspected.
Successful unit tests or AAR assembly cannot establish end-to-end game login.
The dispatch regression tests use synthetic framework signatures under
Robolectric API 28; they do not execute a real Android 16 ActivityThread. The
exact error and a fresh metadata-only capture are needed if the rebuilt host
still fails. Provider state, redirect, signature and TLS checks remain intact.
