package com.parallaxelite.core.system.pm.installer;

import com.parallaxelite.core.env.BEnvironment;
import com.parallaxelite.core.system.pm.BPackageSettings;
import com.parallaxelite.entity.pm.InstallOption;
import com.parallaxelite.utils.FileUtils;

public class RemoveUserExecutor implements Executor {
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        FileUtils.deleteDir(BEnvironment.getDataDir(packageName, userId));
        FileUtils.deleteDir(BEnvironment.getDeDataDir(packageName, userId));
        //FileUtils.deleteDir(BEnvironment.getExternalDataDir(packageName));
        FileUtils.deleteDir(BEnvironment.getExternalObbDir(packageName));
        return 0;
    }
}

