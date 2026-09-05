# ParallaxElite Modern Compatibility Architecture

This document records the compatibility design used by ParallaxElite after reviewing
BlackBox/VirtualApp-style containers, DroidPlugin, Tencent Shadow, SandHook, Dobby,
LSPosed, EdXposed and Dreamland designs.

## Design rules

1. Prefer public/current Android behavior first and keep hidden/reflection fallbacks narrow.
2. Preserve guest identity inside the virtual namespace; normalize identity only at real
   Android/provider boundaries where Binder validates the host UID.
3. Preserve provider-owned authentication results rather than fabricating accounts/tokens.
4. Keep one native inline-hook backend. ParallaxElite uses Dobby for ARM32/ARM64.
5. Keep guest ClassLoader isolation. Never search arbitrary host/system loaders as a generic
   ClassNotFoundException recovery path.
6. Treat split APKs as first-class code/resource/native-library inputs.
7. Fail closed for unsupported external-provider routing and fail safe for OEM hidden-API drift.

## Adopted ideas

### BlackBox / VirtualApp family
- ActivityThread/LoadedApk based component virtualization.
- Binder service proxying with per-API compatibility wrappers.
- Virtual PackageManager and ActivityManager boundaries.
- Per-process Context and provider lifecycle management.

### Tencent Shadow
- Strong ClassLoader separation.
- Explicit plugin/guest code path ownership.
- Avoid unnecessary reflection when an explicit capability wrapper can be used.
- Preserve package metadata instead of synthesizing the minimum old-framework subset.

### DroidPlugin
- Component lifecycle routing concepts and explicit host/guest boundaries.
- Plugin-specific class loading instead of broad host class fallback.

### Dobby
- Single native inline-hook backend for supported ARM ABIs.
- Native backend remains isolated behind the existing ParallaxElite Hook layer.

### SandHook / LSPosed / EdXposed / Dreamland
- ClassLoader-aware callback execution.
- ART-version changes must be isolated behind compatibility layers.
- Native/Java hook engines should not be stacked just to increase hook count.

## Implemented modern compatibility

- Android API 24-36 build target.
- Android 12-16 Service Context creation from the already-bound guest LoadedApk.
- Android 13-16 dynamic receiver export-flag handling.
- Android 16 PermissionManager/checkPermissionForDevice compatibility.
- Per-process WebView data directory handling.
- Guest Thread Context ClassLoader scoping for Services, JobServices and Receivers.
- Bound guest ClassLoader fallback for Activity creation.
- Base + split APK resource paths.
- Base + split APK native library extraction.
- Current-process ABI selection using SUPPORTED_32_BIT_ABIS / SUPPORTED_64_BIT_ABIS.
- PackageInfo/ApplicationInfo metadata preservation.
- Existing shared-library metadata preservation.
- Google/Facebook/X external authentication result/callback routing.

## Intentionally not adopted

- Multiple simultaneous inline-hook engines.
- Generic host/system ClassLoader fallback for missing guest classes.
- Broad hidden-framework object replacement based on guessed private layouts.
- Provider/component suppression intended to defeat anti-cheat, anti-tamper or
  virtual-environment detection.
- Fabricated signatures, provider tokens, accounts or authentication responses.

Those approaches can make individual apps appear to work temporarily but create fragile,
unsafe behavior and usually regress newer Android releases.
