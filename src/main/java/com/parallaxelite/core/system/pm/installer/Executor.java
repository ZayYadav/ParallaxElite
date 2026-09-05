package com.parallaxelite.core.system.pm.installer;

import com.parallaxelite.core.system.pm.BPackageSettings;
import com.parallaxelite.entity.pm.InstallOption;

public interface Executor {
    public static final String TAG = "InstallExecutor";

    int exec(BPackageSettings bPackageSettings, InstallOption installOption, int i);
}
