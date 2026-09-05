package com.elite.core;

import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import com.elite.EliteInstaller;
import com.elite.app.BActivityThread;
import com.elite.entity.pm.InstallResult;
import com.elite.utils.auth.Auth;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class GmsCore {
    private static final HashSet<String> GOOGLE_APP = new HashSet<>();
    private static final HashSet<String> GOOGLE_SERVICE = new HashSet<>();
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";

    static {
        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add("com.google.android.play.games");
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");

        // GMS must install at first
        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");
    }

    public static ApplicationInfo applyVirtualAppGmsSafety(ApplicationInfo info) {
        if (info == null || info.packageName == null || Build.VERSION.SDK_INT < 36) {
            return info;
        }
        String virtualPackage = BActivityThread.getAppPackageName();
        if (virtualPackage == null || !virtualPackage.equals(info.packageName)
                || info.packageName.equals(EliteInstaller.getHostPkg())
                || isGoogleAppOrService(info.packageName)) {
            return info;
        }

        // Analytics is not required for Google authentication. Disabling its
        // automatic bootstrap prevents Android 16 from sending a virtual package
        // identity to a real GMS measurement service under the Loader UID.
        Bundle metaData = info.metaData == null ? new Bundle() : new Bundle(info.metaData);
        metaData.putBoolean("firebase_analytics_collection_deactivated", true);
        metaData.putBoolean("firebase_analytics_collection_enabled", false);
        metaData.putBoolean("google_analytics_adid_collection_enabled", false);
        info.metaData = metaData;
        return info;
    }

    public static boolean isGoogleAppOrService(String str) {
        return GOOGLE_APP.contains(str) || GOOGLE_SERVICE.contains(str);
    }
    
    public static boolean setGoogleAppOrService(String pkg) {
		if (pkg == null) return false;
        for (String p : Auth.AUTH_PKG_SET) {
            if (pkg.equals(p) || pkg.contains(p)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Android 16 runtime guard: analytics/measurement is not an authentication
     * dependency and must not be sent to real GMS under a virtual package identity.
     */
    public static boolean isMeasurementIntent(Intent intent) {
        if (intent == null || Build.VERSION.SDK_INT < 36) return false;

        ComponentName component = intent.getComponent();
        if (component != null && isMeasurementName(component.getClassName())) {
            return true;
        }

        String action = intent.getAction();
        if (action == null) return false;
        String lower = action.toLowerCase(Locale.US);
        if (!isMeasurementName(lower)) return false;

        String targetPackage = component != null ? component.getPackageName() : intent.getPackage();
        return targetPackage == null
                || GMS_PKG.equals(targetPackage)
                || action.startsWith("com.google.android.gms.measurement");
    }

    private static boolean isMeasurementName(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.startsWith("com.google.android.gms.measurement.")
                || lower.contains("appmeasurement")
                || lower.contains("firebaseanalytics")
                || lower.contains("measurementdynamite");
    }

    public static boolean isGmsIntent(Intent intent) {
		if (intent == null) return false;
		String action = intent.getAction();
		if (action == null) return false;
		// Google Play Services actions
		return action.startsWith("com.google.android.gms") || action.startsWith("com.google.android.gsf") || action.contains(".gms.") || action.contains(".play.");
	}

    private static InstallResult installPackages(Set<String> list, int userId) {
        EliteInstaller sEliteInstaller = EliteInstaller.get();
        for (String packageName : list) {
            if (sEliteInstaller.isInstalled(packageName, userId)) {
                continue;
            }

            try {
                EliteInstaller.getContext().getPackageManager().getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException ignored) {
                continue;
            }

            InstallResult installResult = sEliteInstaller.installPackageAsUser(packageName, userId);
            if (!installResult.success) {
                return installResult;
            }
        }
        return new InstallResult();
    }

    private static void uninstallPackages(Set<String> list, int userId) {
        EliteInstaller sEliteInstaller = EliteInstaller.get();
        for (String packageName : list) {
            sEliteInstaller.uninstallPackageAsUser(packageName, userId);
        }
    }

    public static InstallResult installGApps(int userId) {
        Set<String> googleApps = new HashSet<>();

        googleApps.addAll(GOOGLE_SERVICE);
        googleApps.addAll(GOOGLE_APP);

        InstallResult installResult = installPackages(googleApps, userId);
        if (!installResult.success) {
            uninstallGApps(userId);
            return installResult;
        }
        return installResult;
    }

    public static void uninstallGApps(int userId) {
        uninstallPackages(GOOGLE_SERVICE, userId);
        uninstallPackages(GOOGLE_APP, userId);
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }

    public static boolean isSupportGms() {
        try {
            EliteInstaller.getPackageManager().getPackageInfo(GMS_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) { }
        return false;
    }

    public static boolean isInstalledGoogleService(int userId) {
        return EliteInstaller.get().isInstalled(GMS_PKG, userId);
    }
}