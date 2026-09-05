package com.elite.core.system.pm.installer;

import com.elite.core.env.BEnvironment;
import com.elite.core.system.pm.BPackageSettings;
import com.elite.entity.pm.InstallOption;
import com.elite.utils.FileUtils;

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

