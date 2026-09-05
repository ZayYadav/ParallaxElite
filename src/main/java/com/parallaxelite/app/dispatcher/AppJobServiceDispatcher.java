package com.parallaxelite.app.dispatcher;

import android.app.job.IJobCallback;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.content.res.Configuration;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import black.android.app.job.BRJobParameters;
import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.app.BActivityThread;
import com.parallaxelite.entity.JobRecord;
import com.parallaxelite.utils.compat.ScopedClassLoader;

/**
 * Dispatches host JobScheduler callbacks into virtual guest JobServices while
 * translating the host-only job ID namespace back to the guest's original ID.
 */
public class AppJobServiceDispatcher {
    private static final AppJobServiceDispatcher sServiceDispatcher =
            new AppJobServiceDispatcher();
    private final Map<Integer, JobRecord> mJobRecords = new HashMap<>();

    public static AppJobServiceDispatcher get() {
        return sServiceDispatcher;
    }

    public boolean onStartJob(JobParameters hostParams) {
        if (hostParams == null) {
            return false;
        }
        try {
            JobRecord record = getJobRecord(hostParams.getJobId());
            if (record == null || record.mJobService == null) {
                return false;
            }
            JobParameters guestParams = createGuestParameters(hostParams, record);
            try (ScopedClassLoader ignored =
                         ScopedClassLoader.enter(record.mJobService.getClassLoader())) {
                return record.mJobService.onStartJob(guestParams);
            }
        } catch (Throwable error) {
            error.printStackTrace();
            return false;
        }
    }

    public boolean onStopJob(JobParameters hostParams) {
        if (hostParams == null) {
            return false;
        }

        JobRecord record = getJobRecord(hostParams.getJobId());
        if (record == null || record.mJobService == null) {
            return false;
        }

        boolean reschedule = false;
        try {
            JobParameters guestParams = createGuestParameters(hostParams, record);
            try (ScopedClassLoader ignored =
                         ScopedClassLoader.enter(record.mJobService.getClassLoader())) {
                reschedule = record.mJobService.onStopJob(guestParams);
            }
        } catch (Throwable error) {
            error.printStackTrace();
        } finally {
            destroyRecord(hostParams.getJobId(), record);
        }
        return reschedule;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        for (JobRecord record : snapshotRecords()) {
            if (record.mJobService == null) continue;
            try (ScopedClassLoader ignored =
                         ScopedClassLoader.enter(record.mJobService.getClassLoader())) {
                record.mJobService.onConfigurationChanged(newConfig);
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }
    }

    public void onDestroy() {
        List<Map.Entry<Integer, JobRecord>> records;
        synchronized (mJobRecords) {
            records = new ArrayList<>(mJobRecords.entrySet());
            mJobRecords.clear();
        }
        for (Map.Entry<Integer, JobRecord> entry : records) {
            JobRecord record = entry.getValue();
            if (record == null || record.mJobService == null) continue;
            try (ScopedClassLoader ignored =
                         ScopedClassLoader.enter(record.mJobService.getClassLoader())) {
                record.mJobService.onDestroy();
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }
    }

    public void onLowMemory() {
        for (JobRecord record : snapshotRecords()) {
            if (record.mJobService == null) continue;
            try (ScopedClassLoader ignored =
                         ScopedClassLoader.enter(record.mJobService.getClassLoader())) {
                record.mJobService.onLowMemory();
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }
    }

    public void onTrimMemory(int level) {
        for (JobRecord record : snapshotRecords()) {
            if (record.mJobService == null) continue;
            try (ScopedClassLoader ignored =
                         ScopedClassLoader.enter(record.mJobService.getClassLoader())) {
                record.mJobService.onTrimMemory(level);
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }
    }

    private List<JobRecord> snapshotRecords() {
        synchronized (mJobRecords) {
            return new ArrayList<>(mJobRecords.values());
        }
    }

    private void destroyRecord(int hostJobId, JobRecord record) {
        synchronized (mJobRecords) {
            mJobRecords.remove(hostJobId);
        }
        if (record.mJobService != null) {
            try (ScopedClassLoader ignored =
                         ScopedClassLoader.enter(record.mJobService.getClassLoader())) {
                record.mJobService.onDestroy();
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }
    }

    private JobRecord getJobRecord(int hostJobId) {
        synchronized (mJobRecords) {
            JobRecord cached = mJobRecords.get(hostJobId);
            if (cached != null && cached.mJobService != null) {
                return cached;
            }

            try {
                JobRecord record = ParallaxELiteInstaller.getBJobManager().queryJobRecord(
                        BActivityThread.getAppProcessName(), hostJobId);
                if (record == null || record.mServiceInfo == null) {
                    return null;
                }

                record.mJobService = BActivityThread.currentActivityThread()
                        .createJobService(record.mServiceInfo);
                if (record.mJobService == null) {
                    return null;
                }

                mJobRecords.put(hostJobId, record);
                return record;
            } catch (Throwable error) {
                error.printStackTrace();
                return null;
            }
        }
    }

    private JobParameters createGuestParameters(
            JobParameters hostParams, final JobRecord record) {
        Parcel parcel = Parcel.obtain();
        try {
            hostParams.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            JobParameters guestParams = JobParameters.CREATOR.createFromParcel(parcel);

            IBinder originalCallbackBinder = BRJobParameters.get(guestParams).callback();
            final IJobCallback originalCallback =
                    IJobCallback.Stub.asInterface(originalCallbackBinder);
            if (originalCallback != null) {
                IJobCallback remappingCallback = new IJobCallback.Stub() {
                    @Override
                    public void acknowledgeStartMessage(int jobId, boolean ongoing)
                            throws RemoteException {
                        originalCallback.acknowledgeStartMessage(
                                record.mHostJobId, ongoing);
                    }

                    @Override
                    public void acknowledgeStopMessage(int jobId, boolean reschedule)
                            throws RemoteException {
                        originalCallback.acknowledgeStopMessage(
                                record.mHostJobId, reschedule);
                    }

                    @Override
                    public JobWorkItem dequeueWork(int jobId) throws RemoteException {
                        return originalCallback.dequeueWork(record.mHostJobId);
                    }

                    @Override
                    public boolean completeWork(int jobId, int workId)
                            throws RemoteException {
                        return originalCallback.completeWork(
                                record.mHostJobId, workId);
                    }

                    @Override
                    public void jobFinished(int jobId, boolean reschedule)
                            throws RemoteException {
                        originalCallback.jobFinished(
                                record.mHostJobId, reschedule);
                    }
                };
                BRJobParameters.get(guestParams)
                        ._set_callback(remappingCallback.asBinder());
            }

            BRJobParameters.get(guestParams)._set_jobId(record.mGuestJobId);
            return guestParams;
        } finally {
            parcel.recycle();
        }
    }
}
