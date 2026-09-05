package com.elite.core.system.pm.installer;

import com.elite.core.system.pm.BPackageSettings;
import com.elite.entity.pm.InstallOption;

public interface Executor {
    public static final String TAG = "InstallExecutor";

    int exec(BPackageSettings bPackageSettings, InstallOption installOption, int i);
}
