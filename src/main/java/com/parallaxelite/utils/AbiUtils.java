package com.parallaxelite.utils;

import android.os.Build;
import android.os.Process;

import java.io.File;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ABI inspection for guest APKs.
 *
 * Uses the current process bitness and Build.SUPPORTED_*_BIT_ABIS instead of
 * deprecated Build.CPU_ABI. This prevents a 64-bit host process from extracting
 * 32-bit guest libraries (or the reverse), while still supporting legacy
 * armeabi fallback on 32-bit devices.
 */
public class AbiUtils {
    private final Set<String> mLibs = new HashSet<>();
    private static final Map<String, AbiUtils> sAbiUtilsMap = new HashMap<>();

    public static boolean isSupport(File apkFile) {
        if (apkFile == null || !apkFile.isFile()) {
            return false;
        }
        String key = apkFile.getAbsolutePath() + ":" + apkFile.length() + ":" + apkFile.lastModified();
        AbiUtils abiUtils = sAbiUtilsMap.get(key);
        if (abiUtils == null) {
            abiUtils = new AbiUtils(apkFile);
            sAbiUtilsMap.put(key, abiUtils);
        }
        if (abiUtils.isEmptyAbi()) {
            return true;
        }
        for (String abi : getProcessSupportedAbis()) {
            if (abiUtils.mLibs.contains(abi)) {
                return true;
            }
        }
        // Very old APKs may expose only armeabi and can run in a 32-bit process.
        return !isProcess64Bit() && abiUtils.mLibs.contains("armeabi");
    }

    public static String[] getProcessSupportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String[] preferred;
            if (isProcess64Bit()) {
                preferred = Build.SUPPORTED_64_BIT_ABIS;
            } else {
                preferred = Build.SUPPORTED_32_BIT_ABIS;
            }
            if (preferred != null && preferred.length > 0) {
                return preferred.clone();
            }
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                return Build.SUPPORTED_ABIS.clone();
            }
        }
        return new String[]{Build.CPU_ABI};
    }

    public static String getPreferredProcessAbi() {
        String[] abis = getProcessSupportedAbis();
        return abis.length == 0 ? Build.CPU_ABI : abis[0];
    }

    public static boolean isProcess64Bit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return Process.is64Bit();
            } catch (Throwable ignored) {
            }
        }
        for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
            if (abi != null && abi.equals(Build.CPU_ABI)) {
                return true;
            }
        }
        return false;
    }

    public AbiUtils(File apkFile) {
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name == null || !name.startsWith("lib/")) {
                    continue;
                }
                int slash = name.indexOf('/', 4);
                if (slash <= 4) {
                    continue;
                }
                String abi = name.substring(4, slash);
                if (!abi.isEmpty()) {
                    mLibs.add(abi);
                }
            }
        } catch (Exception e) {
            Slog.w("AbiUtils", "Unable to inspect " + apkFile + ": " + e.getClass().getSimpleName());
        }
    }

    public boolean is64Bit() {
        return mLibs.contains("arm64-v8a") || mLibs.contains("x86_64");
    }

    public boolean is32Bit() {
        return mLibs.contains("armeabi")
                || mLibs.contains("armeabi-v7a")
                || mLibs.contains("x86");
    }

    public boolean isEmptyAbi() {
        return mLibs.isEmpty();
    }

    // Kept for source compatibility with older callers.
    public boolean isEmptyAib() {
        return isEmptyAbi();
    }

    @Override
    public String toString() {
        return "AbiUtils" + Arrays.toString(mLibs.toArray());
    }
}
