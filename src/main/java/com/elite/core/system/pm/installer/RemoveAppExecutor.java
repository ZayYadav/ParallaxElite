package com.elite.core.system.pm.installer;

import com.elite.core.env.BEnvironment;
import com.elite.core.system.pm.BPackageSettings;
import com.elite.entity.pm.InstallOption;
import com.elite.utils.FileUtils;

public class RemoveAppExecutor implements Executor {
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        FileUtils.deleteDir(BEnvironment.getAppDir(ps.pkg.packageName));
        return 0;
    }
}
