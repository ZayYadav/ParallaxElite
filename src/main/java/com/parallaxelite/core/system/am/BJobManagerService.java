package com.parallaxelite.core.system.am;

import android.app.job.JobInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import black.android.app.job.BRJobInfo;
import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.core.system.BProcessManagerService;
import com.parallaxelite.core.system.ISystemService;
import com.parallaxelite.core.system.ProcessRecord;
import com.parallaxelite.core.system.pm.BPackageManagerService;
import com.parallaxelite.entity.JobRecord;
import com.parallaxelite.proxy.ProxyManifest;

/**
 * Virtual JobScheduler namespace.
 *
 * Android's real JobScheduler namespaces jobs by host UID + jobId. Every guest
 * in ParallaxELite shares the host UID, so forwarding guest IDs directly lets
 * unrelated virtual apps replace/cancel each other's jobs. This service maps
 * each guest (user/process/jobId) to a stable host-side ID.
 */
public class BJobManagerService extends IBJobManagerService.Stub implements ISystemService {
    private static final BJobManagerService sService = new BJobManagerService();

    private final Map<Integer, JobRecord> mHostJobRecords = new HashMap<>();
    private final Map<String, Integer> mGuestToHostJobIds = new HashMap<>();

    public static BJobManagerService get() {
        return sService;
    }

    @Override
    public JobInfo schedule(JobInfo info, int userId) throws RemoteException {
        if (info == null || info.getService() == null) {
            return null;
        }

        ComponentName componentName = info.getService();
        Intent intent = new Intent().setComponent(componentName);
        ResolveInfo resolveInfo = BPackageManagerService.get()
                .resolveService(intent, 0, null, userId);
        if (resolveInfo == null || resolveInfo.serviceInfo == null) {
            return null;
        }

        ServiceInfo serviceInfo = resolveInfo.serviceInfo;
        ProcessRecord processRecord = BProcessManagerService.get().findProcessRecord(
                serviceInfo.packageName, serviceInfo.processName, userId);
        if (processRecord == null) {
            processRecord = BProcessManagerService.get().startProcessLocked(
                    serviceInfo.packageName,
                    serviceInfo.processName,
                    userId,
                    -1,
                    Binder.getCallingPid());
        }
        if (processRecord == null) {
            throw new RuntimeException("Unable to create Process " + serviceInfo.processName);
        }
        return scheduleJob(processRecord, info, serviceInfo, userId);
    }

    @Override
    public JobRecord queryJobRecord(String processName, int jobId, int userId)
            throws RemoteException {
        synchronized (mHostJobRecords) {
            JobRecord record = mHostJobRecords.get(jobId);
            if (record == null
                    || record.mUserId != userId
                    || !safeEquals(processName, record.mProcessName)) {
                return null;
            }
            return record;
        }
    }

    public JobInfo scheduleJob(
            ProcessRecord processRecord, JobInfo info, ServiceInfo serviceInfo, int userId) {
        final int guestJobId = info.getId();
        final String processName = processRecord.processName;
        final String guestKey = formatGuestKey(processName, guestJobId, userId);

        synchronized (mHostJobRecords) {
            int hostJobId = allocateHostJobIdLocked(guestKey, processName, guestJobId, userId);

            JobRecord jobRecord = new JobRecord();
            jobRecord.mJobInfo = info;
            jobRecord.mServiceInfo = serviceInfo;
            jobRecord.mGuestJobId = guestJobId;
            jobRecord.mHostJobId = hostJobId;
            jobRecord.mUserId = userId;
            jobRecord.mProcessName = processName;

            mGuestToHostJobIds.put(guestKey, hostJobId);
            mHostJobRecords.put(hostJobId, jobRecord);

            BRJobInfo.get(info)._set_jobId(hostJobId);
            BRJobInfo.get(info)._set_service(new ComponentName(
                    ParallaxELiteInstaller.getHostPkg(),
                    ProxyManifest.getProxyJobService(processRecord.bpid)));
            return info;
        }
    }

    @Override
    public int[] cancelAll(String processName, int userId) throws RemoteException {
        if (processName == null) {
            return new int[0];
        }

        List<Integer> hostIds = new ArrayList<>();
        synchronized (mHostJobRecords) {
            List<String> guestKeysToRemove = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : mGuestToHostJobIds.entrySet()) {
                JobRecord record = mHostJobRecords.get(entry.getValue());
                if (record != null
                        && record.mUserId == userId
                        && safeEquals(processName, record.mProcessName)) {
                    hostIds.add(record.mHostJobId);
                    guestKeysToRemove.add(entry.getKey());
                }
            }

            for (String key : guestKeysToRemove) {
                Integer hostId = mGuestToHostJobIds.remove(key);
                if (hostId != null) {
                    mHostJobRecords.remove(hostId);
                }
            }
        }

        int[] result = new int[hostIds.size()];
        for (int i = 0; i < hostIds.size(); i++) {
            result[i] = hostIds.get(i);
        }
        return result;
    }

    @Override
    public int cancel(String processName, int jobId, int userId) throws RemoteException {
        String guestKey = formatGuestKey(processName, jobId, userId);
        synchronized (mHostJobRecords) {
            Integer hostId = mGuestToHostJobIds.remove(guestKey);
            if (hostId == null) {
                // Deterministic fallback helps clean up a job after the virtual
                // manager was restarted and lost its in-memory bookkeeping.
                return computeBaseHostJobId(processName, jobId, userId);
            }
            mHostJobRecords.remove(hostId);
            return hostId;
        }
    }

    private int allocateHostJobIdLocked(
            String guestKey, String processName, int guestJobId, int userId) {
        Integer existing = mGuestToHostJobIds.get(guestKey);
        if (existing != null) {
            return existing;
        }

        int candidate = computeBaseHostJobId(processName, guestJobId, userId);
        final int start = candidate;
        while (mHostJobRecords.containsKey(candidate)) {
            candidate++;
            if (candidate < 0 || candidate < 0x40000000) {
                candidate = 0x40000000;
            }
            if (candidate == start) {
                throw new IllegalStateException("No JobScheduler IDs available");
            }
        }
        return candidate;
    }

    private static int computeBaseHostJobId(String processName, int guestJobId, int userId) {
        int hash = 17;
        hash = 31 * hash + userId;
        hash = 31 * hash + (processName == null ? 0 : processName.hashCode());
        hash = 31 * hash + guestJobId;
        return 0x40000000 | (hash & 0x3fffffff);
    }

    private static String formatGuestKey(String processName, int guestJobId, int userId) {
        return userId + "|" + String.valueOf(processName) + "|" + guestJobId;
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    @Override
    public void systemReady() {
    }
}
