package com.elite.core;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import com.elite.EliteInstaller;
import com.elite.utils.Slog;

/**
 * Google Play Services aur Facebook login ke liye complete fix
 * Sirf inhi do cheezon ke liye banaya gaya hai
 */

public class AuthCore {
    
    private static final String TAG = "GmsFacebookFix";
    
    // ========== Google Play Services Packages ==========
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";
    public static final String GMS_SIGNIN_SERVICE = "com.google.android.gms.auth.api.signin.service.START";
    public static final String GMS_MEASUREMENT_SERVICE = "com.google.android.gms.measurement.START";
    
    // ========== Facebook Packages ==========
    public static final String FB_PKG = "com.facebook.katana";
    public static final String FB_WAKIZASHI_PKG = "com.facebook.wakizashi";
    public static final String FB_LITE_PKG = "com.facebook.lite";
    public static final String FB_ORCA_PKG = "com.facebook.orca";
    
    /**
     * Check if package needs fix (GMS ya Facebook)
     */
    public static boolean needsFix(String packageName) {
        if (packageName == null) return false;
        
        // Google Play Services check
        if (packageName.equals(GMS_PKG) || 
            packageName.equals(GSF_PKG) || 
            packageName.equals(VENDING_PKG) ||
            packageName.startsWith("com.google.android.gms.")) {
            return true;
        }
        
        // Facebook check
        if (packageName.equals(FB_PKG) || 
            packageName.equals(FB_WAKIZASHI_PKG) || 
            packageName.equals(FB_LITE_PKG) ||
            packageName.equals(FB_ORCA_PKG) ||
            packageName.contains("facebook")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if intent needs fix (GMS ya Facebook intent)
     */
    public static boolean needsFix(Intent intent) {
        if (intent == null) return false;
        
        // Check component package
        if (intent.getComponent() != null) {
            String pkg = intent.getComponent().getPackageName();
            if (needsFix(pkg)) return true;
        }
        
        // Check action
        String action = intent.getAction();
        if (action != null) {
            if (action.contains("gms") || 
                action.contains("measurement") || 
                action.contains("signin") ||
                action.contains("facebook") ||
                action.equals(GMS_SIGNIN_SERVICE) ||
                action.equals(GMS_MEASUREMENT_SERVICE)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get dummy PackageInfo for GMS/Facebook
     */
    public static PackageInfo getDummyPackageInfo(String packageName) {
        Slog.d(TAG, "Creating dummy PackageInfo for: " + packageName);
        
        PackageInfo dummyInfo = new PackageInfo();
        dummyInfo.packageName = packageName;
        
        // GMS version
        if (packageName.equals(GMS_PKG) || packageName.equals(GSF_PKG)) {
            dummyInfo.versionCode = 12451000; // Common GMS version
            dummyInfo.versionName = "12.4.51";
        } else {
            dummyInfo.versionCode = 1;
            dummyInfo.versionName = "1.0";
        }
        
        ApplicationInfo appInfo = getDummyApplicationInfo(packageName);
        dummyInfo.applicationInfo = appInfo;
        
        return dummyInfo;
    }
    
    /**
     * Get dummy ApplicationInfo for GMS/Facebook
     */
    public static ApplicationInfo getDummyApplicationInfo(String packageName) {
        Slog.d(TAG, "Creating dummy ApplicationInfo for: " + packageName);
        
        ApplicationInfo dummyInfo = new ApplicationInfo();
        dummyInfo.packageName = packageName;
        dummyInfo.uid = EliteInstaller.getHostUid();
        dummyInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        dummyInfo.sourceDir = "/system/app/" + packageName + ".apk";
        dummyInfo.publicSourceDir = dummyInfo.sourceDir;
        dummyInfo.dataDir = "/data/data/" + packageName;
        
        return dummyInfo;
    }
    
    /**
     * Get dummy ResolveInfo for intent (Activity ya Service)
     */
    public static ResolveInfo getDummyResolveInfo(Intent intent, boolean isService) {
        Slog.d(TAG, "Creating dummy ResolveInfo for: " + intent);
        
        ResolveInfo dummyResolve = new ResolveInfo();
        String packageName = getPackageNameFromIntent(intent);
        
        ApplicationInfo appInfo = getDummyApplicationInfo(packageName);
        
        if (isService) {
            dummyResolve.serviceInfo = new ServiceInfo();
            dummyResolve.serviceInfo.packageName = packageName;
            dummyResolve.serviceInfo.name = getServiceName(intent, packageName);
            dummyResolve.serviceInfo.applicationInfo = appInfo;
            dummyResolve.serviceInfo.permission = null;
        } else {
            dummyResolve.activityInfo = new ActivityInfo();
            dummyResolve.activityInfo.packageName = packageName;
            dummyResolve.activityInfo.name = getActivityName(intent, packageName);
            dummyResolve.activityInfo.applicationInfo = appInfo;
            dummyResolve.activityInfo.permission = null;
        }
        
        return dummyResolve;
    }
    
    /**
     * Get package name from intent
     */
    private static String getPackageNameFromIntent(Intent intent) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getPackageName();
        }
        
        String action = intent.getAction();
        if (action != null) {
            if (action.contains("gms") || action.contains("measurement") || action.contains("signin")) {
                return GMS_PKG;
            }
            if (action.contains("facebook")) {
                return FB_PKG;
            }
        }
        
        return GMS_PKG; // Default
    }
    
    /**
     * Get service name for intent
     */
    private static String getServiceName(Intent intent, String packageName) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getClassName();
        }
        
        String action = intent.getAction();
        if (action != null) {
            if (action.contains("measurement") || action.equals(GMS_MEASUREMENT_SERVICE)) {
                return "com.google.android.gms.measurement.AppMeasurementService";
            }
            if (action.contains("signin") || action.equals(GMS_SIGNIN_SERVICE)) {
                return "com.google.android.gms.auth.api.signin.internal.SignInHubService";
            }
        }
        
        return packageName + ".BaseService";
    }
    
    /**
     * Get activity name for intent
     */
    private static String getActivityName(Intent intent, String packageName) {
        if (intent.getComponent() != null) {
            return intent.getComponent().getClassName();
        }
        
        String action = intent.getAction();
        if (action != null) {
            if (action.contains("facebook")) {
                return "com.facebook.ProxyAuthActivity";
            }
        }
        
        return packageName + ".BaseActivity";
    }
    
    /**
     * Handle getPackageInfo and getApplicationInfo in one method
     */
    public static Object handlePackageInfo(String packageName, int flags, boolean isApplicationInfo) {
        if (!needsFix(packageName)) {
            return null; // Not our concern
        }
        
        if (isApplicationInfo) {
            return getDummyApplicationInfo(packageName);
        } else {
            return getDummyPackageInfo(packageName);
        }
    }
    
    /**
     * Handle resolveIntent and resolveService in one method
     */
    public static Object handleResolve(Intent intent, boolean isService) {
        if (!needsFix(intent)) {
            return null; // Not our concern
        }
        
        return getDummyResolveInfo(intent, isService);
    }
}