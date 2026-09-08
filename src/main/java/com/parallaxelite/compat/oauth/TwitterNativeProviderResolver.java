package com.parallaxelite.compat.oauth;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.parallaxelite.compat.auth.ExternalAuthRouter;

/** Selects only an installed provider's declared, accessible OAuth URL handler. */
public final class TwitterNativeProviderResolver {
    private TwitterNativeProviderResolver() { }

    public static Intent resolve(PackageManager pm, Uri uri, String provider, String caller) {
        if (pm == null || caller == null || caller.isEmpty()
                || !ExternalAuthRouter.isTwitterProviderPackage(provider)
                || uri == null || !uri.isHierarchical() || uri.getUserInfo() != null
                || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !ExternalAuthRouter.isTrustedTwitterOAuthUri(uri)) {
            return null;
        }
        try {
            Intent candidate = new Intent(Intent.ACTION_VIEW, uri);
            candidate.addCategory(Intent.CATEGORY_DEFAULT);
            candidate.addCategory(Intent.CATEGORY_BROWSABLE);
            candidate.setPackage(provider);
            // Keep the probe implicit: an explicit component can resolve even
            // when its intent filters do not accept this authorization URL.
            ResolveInfo resolved = pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY);
            ActivityInfo info = resolved == null ? null : resolved.activityInfo;
            if (info == null || !provider.equals(info.packageName)
                    || info.name == null || info.name.isEmpty() || !info.enabled || !info.exported
                    || (info.applicationInfo != null && !info.applicationInfo.enabled)) {
                return null;
            }
            if (info.permission != null && !info.permission.isEmpty()
                    && pm.checkPermission(info.permission, caller) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            candidate.setComponent(new ComponentName(info.packageName, info.name));
            return candidate;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
