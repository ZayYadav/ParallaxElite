package com.parallaxelite.core.system.pm.installer;

import com.parallaxelite.core.env.BEnvironment;
import com.parallaxelite.core.system.pm.BPackageSettings;
import com.parallaxelite.entity.pm.InstallOption;
import com.parallaxelite.utils.FileUtils;

public class CreateUserExecutor implements Executor {
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        FileUtils.mkdirs(BEnvironment.getDataDir(packageName, userId));
        FileUtils.mkdirs(BEnvironment.getDeDataDir(packageName, userId));
        FileUtils.mkdirs(BEnvironment.getDataCacheDir(packageName, userId));
        FileUtils.mkdirs(BEnvironment.getDataDatabasesDir(packageName, userId));
        FileUtils.mkdirs(BEnvironment.getDataFilesDir(packageName, userId));
        FileUtils.mkdirs(BEnvironment.getExternalDataCacheDir(packageName));
        FileUtils.mkdirs(BEnvironment.getExternalDataFilesDir(packageName));
        return 0;
    }
}
