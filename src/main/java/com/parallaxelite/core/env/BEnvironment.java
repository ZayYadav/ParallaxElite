package com.parallaxelite.core.env;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import com.parallaxelite.utils.compat.BuildCompat;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.utils.FileUtils;
import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.core.system.api.MetaActivationManager;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class BEnvironment {
   
  //  private static final File sExternalVirtualRoot = ParallaxELiteInstaller.getContext().getExternalFilesDir("ParallaxELiteCore");
    private static final File sVBoxRoot = ParallaxELiteInstaller.getContext().getFilesDir();
  //  private static final File sVBoxRoot = new File(ParallaxELiteInstaller.getContext().getCacheDir().getParent(), "ParallaxELiteCore");
    private static final File sExternalVBoxRoot = Environment.getExternalStorageDirectory();
    
	public static void load() {
		FileUtils.mkdirs(sVBoxRoot);
        FileUtils.mkdirs(sExternalVBoxRoot);
		FileUtils.mkdirs(getSystemDir());
		FileUtils.mkdirs(getCacheDir());
		FileUtils.mkdirs(getProcDir());
	}
    
    public static File getVBoxRoot() {
		return sVBoxRoot;
	}
    
    public static ArrayList<String> getAllDex(String packageName) {
        File appDir = getAppDir(packageName);
        ArrayList<String> result = new ArrayList<>();
        File[] apkFiles = appDir.listFiles((dir, name) ->
                name != null && name.toLowerCase(Locale.US).endsWith(".apk"));
        if (apkFiles == null || apkFiles.length == 0) {
            return result;
        }

        // Android's class path expects base.apk before feature/config splits.
        Arrays.sort(apkFiles, (left, right) -> {
            boolean leftBase = "base.apk".equals(left.getName());
            boolean rightBase = "base.apk".equals(right.getName());
            if (leftBase != rightBase) return leftBase ? -1 : 1;
            return left.getName().compareTo(right.getName());
        });

        for (File file : apkFiles) {
            if (BuildCompat.isUpsideDownCake()) {
                try {
                    file.setReadOnly();
                } catch (Throwable ignored) {
                }
            }
            result.add(file.getAbsolutePath());
        }
        return result;
    }

    public static File getSystemDir() {
        return new File(sVBoxRoot, "system");
    }

    public static File getProcDir() {
        return new File(sVBoxRoot, "proc");
    }

    public static File getCacheDir() {
        return new File(sVBoxRoot, "cache");
    }
    
    public static File getUserDir(int userId) {
        return new File(sVBoxRoot, String.format(Locale.CHINA, "data/user/%d", userId));
    }

    public static File getDeDataDir(String packageName, int userId) {
        return new File(sVBoxRoot, String.format(Locale.CHINA, "data/user_de/%d/%s", userId, packageName));
    }
    
    public static File getDataDir(String packageName, int userId) {
        return new File(sVBoxRoot, String.format(Locale.CHINA, "data/user/%d/%s", userId, packageName));
    }
    
    public static File getAppDir(String packageName) {
        return new File(sVBoxRoot, "data/app/" + packageName);
    }

    public static File getBaseApkDir(String packageName) {
        return new File(sVBoxRoot, "data/app/" + packageName + "/base.apk");
    }

    public static File getUserInfoConf() {
        return new File(getSystemDir(), "user.conf");
    }

    public static File getAccountsConf() {
        return new File(getSystemDir(), "accounts.conf");
    }

    public static File getUidConf() {
        return new File(getSystemDir(), "uid.conf");
    }

    public static File getSharedUserConf() {
        return new File(getSystemDir(), "shared-user.conf");
    }

    public static File getXPModuleConf() {
        return new File(getSystemDir(), "xposed-module.conf");
    }

    public static File getFakeLocationConf() {
        return new File(getSystemDir(), "fake-location.conf");
    }
    
    public static File getFakeDeviceConf() {
        return new File(getSystemDir(), "fake-device.conf");
    }
    
    public static File getPackageConf(String packageName) {
        return new File(getAppDir(packageName), "package.conf");
    }
    
    public static File getExternalStorageDirectory() {
        if (Build.VERSION.SDK_INT == 29) return new File(sExternalVBoxRoot, "SdCard");
        return new File(sExternalVBoxRoot, "SdCard");
    }

    public static File getExternalDataDir(String packageName) {
        return new File(getExternalStorageDirectory(),String.format(Locale.CHINA, "Android/data/%s", packageName));
    }

    public static File getExternalObbDir(String packageName) {
        return new File(getExternalStorageDirectory(),String.format(Locale.CHINA, "Android/obb/%s/", packageName));
    }
    
    public static File getProcDir(int pid) {
        File file = new File(getProcDir(), String.format(Locale.CHINA, "%d", pid));
        FileUtils.mkdirs(file);
        return file;
    }

    public static File getExternalDataFilesDir(String packageName) {
        return new File(getExternalDataDir(packageName), "files");
    }

    public static File getDataFilesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "files");
    }

    public static File getExternalDataCacheDir(String packageName) {
        return new File(getExternalDataDir(packageName), "cache");
    }

    public static File getDataCacheDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "cache");
    }

    public static File getDataLibDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "lib");
    }

    public static File getDataDatabasesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "databases");
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getAppLibDir(String packageName) {
        return new File(getAppDir(packageName), "lib");
    }
    
    public static File getXSharedPreferences(String packageName, String prefFileName) {
		return new File(BEnvironment.getDataDir(packageName, BActivityThread.getUserId()),"shared_prefs/" + prefFileName + ".xml");
	}
    
    
}
