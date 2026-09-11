package com.parallaxelite.core.system;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.core.env.AppSystemEnv;
import com.parallaxelite.core.env.BEnvironment;
import com.parallaxelite.core.system.accounts.BAccountManagerService;
import com.parallaxelite.core.system.am.BActivityManagerService;
import com.parallaxelite.core.system.am.BJobManagerService;
import com.parallaxelite.core.system.location.BLocationManagerService;
import com.parallaxelite.core.system.notification.BNotificationManagerService;
import com.parallaxelite.core.system.os.BStorageManagerService;
import com.parallaxelite.core.system.pm.BPackageInstallerService;
import com.parallaxelite.core.system.pm.BPackageManagerService;
import com.parallaxelite.core.system.pm.BXposedManagerService;
import com.parallaxelite.core.system.user.BUserHandle;
import com.parallaxelite.core.system.user.BUserManagerService;
import com.parallaxelite.entity.pm.InstallOption;
import com.parallaxelite.utils.FileUtils;

public class VBoxSystem {

    private static volatile VBoxSystem sVBoxSystem;
    private final List<ISystemService> mServices = new ArrayList<>();
    private final Object mStartupLock = new Object();
    private volatile boolean isStartup;

    private VBoxSystem() { }

    public static VBoxSystem getSystem() {
        if (sVBoxSystem == null) {
            synchronized (VBoxSystem.class) {
                if (sVBoxSystem == null) {
                    sVBoxSystem = new VBoxSystem();
                }
            }
        }
        return sVBoxSystem;
    }

    public boolean isStarted() {
        return isStartup;
    }

    public void startup() {
        if (isStartup) {
            return;
        }
        synchronized (mStartupLock) {
            if (isStartup) {
                return;
            }

            // Never publish a half-initialized system. The old getAndSet(true)
            // marked startup complete before any initialization ran, so one transient
            // failure could leave the process permanently stuck in a partial state.
            mServices.clear();
            boolean success = false;
            try {
                BEnvironment.load();

                mServices.add(BPackageManagerService.get());
                mServices.add(BUserManagerService.get());
                mServices.add(BActivityManagerService.get());
                mServices.add(BJobManagerService.get());
                mServices.add(BStorageManagerService.get());
                mServices.add(BPackageInstallerService.get());
                mServices.add(BXposedManagerService.get());
                mServices.add(BProcessManagerService.get());
                mServices.add(BAccountManagerService.get());
                mServices.add(BLocationManagerService.get());
                mServices.add(BNotificationManagerService.get());

                for (ISystemService service : mServices) {
                    try {
                        service.systemReady();
                    } catch (Throwable ignored) {
                        // One optional subsystem must not take down the whole server.
                    }
                }

                // Pre-install system apps. This remains best-effort and does not make
                // the core service unavailable when one package cannot be resolved.
                List<String> preInstallPackages = AppSystemEnv.getPreInstallPackages();
                for (String pkg : preInstallPackages) {
                    try {
                        if (!BPackageManagerService.get().isInstalled(pkg, BUserHandle.USER_ALL)) {
                            PackageInfo info = ParallaxELiteInstaller.getPackageManager().getPackageInfo(pkg, 0);
                            BPackageManagerService.get().installPackageAsUser(
                                    info.applicationInfo.sourceDir,
                                    InstallOption.installBySystem(),
                                    BUserHandle.USER_ALL);
                        }
                    } catch (PackageManager.NameNotFoundException ignored) {
                    } catch (Throwable ignored) {
                    }
                }
                success = true;
            } finally {
                // Publish readiness only after the complete core startup path succeeds.
                // On failure leave the system retryable instead of permanently wedged.
                isStartup = success;
                if (!success) {
                    mServices.clear();
                }
            }
        }
    }

    private void initJarEnv() {
        // OPTIONAL: junit.jar (ignore if missing)
        try {
            InputStream junit = ParallaxELiteInstaller.getContext().getAssets().open("junit.jar");
            FileUtils.copyFile(junit, android.MetaCore.RemoteManager.JUNIT_JAR);
        } catch (Throwable ignored) {
            // junit.jar not present -> safe to ignore
        }

        // REQUIRED: empty.jar
        try {
            InputStream empty = ParallaxELiteInstaller.getContext().getAssets().open("empty.jar");
            FileUtils.copyFile(empty, android.MetaCore.RemoteManager.EMPTY_JAR);
        } catch (Throwable e) {
            // empty.jar missing is a REAL problem
            e.printStackTrace();
        }
    }
}
