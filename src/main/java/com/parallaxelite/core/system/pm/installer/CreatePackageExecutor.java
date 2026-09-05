package com.parallaxelite.core.system.pm.installer;

import com.parallaxelite.core.env.BEnvironment;
import com.parallaxelite.core.system.pm.BPackageSettings;
import com.parallaxelite.entity.pm.InstallOption;
import com.parallaxelite.utils.FileUtils;


public class CreatePackageExecutor implements Executor {
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        FileUtils.deleteDir(BEnvironment.getAppDir(ps.pkg.packageName));
        FileUtils.mkdirs(BEnvironment.getAppDir(ps.pkg.packageName));
        FileUtils.mkdirs(BEnvironment.getAppLibDir(ps.pkg.packageName));
        return 0;
    }
}
