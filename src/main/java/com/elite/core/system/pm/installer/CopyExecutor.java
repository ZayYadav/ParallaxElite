package com.elite.core.system.pm.installer;

import android.content.pm.ApplicationInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.elite.core.env.BEnvironment;
import com.elite.core.system.pm.BPackageSettings;
import com.elite.entity.pm.InstallOption;
import com.elite.utils.FileUtils;
import com.elite.utils.NativeUtils;

/**
 * Copies guest package code and native libraries.
 *
 * Modern Android apps frequently place classes/resources/native libraries in
 * split APKs. Preserve those paths when they are available instead of treating
 * every package as a single base.apk.
 */
public class CopyExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        if (ps == null || ps.pkg == null || ps.pkg.baseCodePath == null) {
            return -1;
        }

        try {
            if (!option.isFlag(InstallOption.FLAG_SYSTEM)) {
                File nativeDir = BEnvironment.getAppLibDir(ps.pkg.packageName);
                for (File codePath : collectExistingCodePaths(ps)) {
                    NativeUtils.copyNativeLib(codePath, nativeDir);
                }
            }

            if (option.isFlag(InstallOption.FLAG_STORAGE)) {
                copyStoredPackage(ps, option);
            }
            return 0;
        } catch (Throwable error) {
            error.printStackTrace();
            return -1;
        }
    }

    private static List<File> collectExistingCodePaths(BPackageSettings ps) {
        List<File> result = new ArrayList<>();
        addIfFile(result, ps.pkg.baseCodePath);

        ApplicationInfo info = ps.pkg.applicationInfo;
        if (info != null && info.splitSourceDirs != null) {
            for (String split : info.splitSourceDirs) {
                addIfFile(result, split);
            }
        }
        return result;
    }

    private static void addIfFile(List<File> result, String path) {
        if (path == null || path.trim().isEmpty()) return;
        File file = new File(path);
        if (file.isFile()) {
            result.add(file);
        }
    }

    private static void copyStoredPackage(BPackageSettings ps, InstallOption option)
            throws IOException {
        File originalBase = new File(ps.pkg.baseCodePath);
        File newBase = BEnvironment.getBaseApkDir(ps.pkg.packageName);
        copyOrMove(originalBase, newBase, option.isFlag(InstallOption.FLAG_URI_FILE));

        ps.pkg.baseCodePath = newBase.getAbsolutePath();
        ApplicationInfo info = ps.pkg.applicationInfo;
        if (info != null) {
            info.sourceDir = newBase.getAbsolutePath();
            info.publicSourceDir = newBase.getAbsolutePath();
            copySplits(info, BEnvironment.getAppDir(ps.pkg.packageName));
        }
    }

    private static void copySplits(ApplicationInfo info, File appDir) throws IOException {
        String[] original = info.splitSourceDirs;
        if (original == null || original.length == 0) {
            return;
        }

        List<String> stored = new ArrayList<>();
        for (int i = 0; i < original.length; i++) {
            String splitPath = original[i];
            if (splitPath == null) continue;

            File source = new File(splitPath);
            if (!source.isFile()) {
                continue;
            }

            String safeName = source.getName().replaceAll("[^A-Za-z0-9._-]", "_");
            if (!safeName.toLowerCase().endsWith(".apk")) {
                safeName = "split_" + i + ".apk";
            } else if ("base.apk".equals(safeName)) {
                safeName = "split_" + i + "_base.apk";
            }

            File destination = new File(appDir, safeName);
            if (!sameFile(source, destination)) {
                FileUtils.copyFile(source, destination);
            }
            stored.add(destination.getAbsolutePath());
        }

        if (!stored.isEmpty()) {
            String[] paths = stored.toArray(new String[0]);
            info.splitSourceDirs = paths;
            info.splitPublicSourceDirs = paths.clone();
        }
    }

    private static boolean sameFile(File left, File right) {
        try {
            return left.getCanonicalPath().equals(right.getCanonicalPath());
        } catch (IOException ignored) {
            return left.getAbsolutePath().equals(right.getAbsolutePath());
        }
    }

    private static void copyOrMove(File source, File destination, boolean preferMove)
            throws IOException {
        if (sameFile(source, destination)) {
            return;
        }
        if (preferMove && FileUtils.renameTo(source, destination)) {
            return;
        }
        FileUtils.copyFile(source, destination);
    }
}
