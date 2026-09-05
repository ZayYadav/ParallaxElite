package com.parallaxelite.fake.frameworks;

import android.app.job.JobInfo;
import android.os.RemoteException;

import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.core.system.ServiceManager;
import com.parallaxelite.core.system.am.IBJobManagerService;
import com.parallaxelite.entity.JobRecord;

/**
 * Created by @jagdish_vip on 3/30/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class BJobManager extends BlackManager<IBJobManagerService> {
    private static final BJobManager sJobManager = new BJobManager();

    public static BJobManager get() {
        return sJobManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.JOB_MANAGER;
    }

    public JobInfo schedule(JobInfo info) {
        try {
            return getService().schedule(info, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public JobRecord queryJobRecord(String processName, int jobId) {
        try {
            return getService().queryJobRecord(processName, jobId, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int[] cancelAll(String processName) {
        try {
            int[] ids = getService().cancelAll(processName, BActivityThread.getUserId());
            return ids == null ? new int[0] : ids;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new int[0];
    }

    public int cancel(String processName, int jobId) {
        try {
            return getService().cancel(processName, jobId, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
