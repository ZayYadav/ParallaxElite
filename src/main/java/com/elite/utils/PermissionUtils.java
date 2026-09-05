package com.elite.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.elite.EliteInstaller;
import com.elite.app.RequestPermissionsActivity;
import com.elite.core.system.am.IRequestPermissionsResult;
import com.elite.utils.compat.BuildCompat;

// 20240801 add request permission add start 0
public class PermissionUtils {

    public static Set<String> DANGEROUS_PERMISSION = new HashSet<String>() {{
        // CALENDAR group
        add(Manifest.permission.READ_CALENDAR);
        add(Manifest.permission.WRITE_CALENDAR);

        // CAMERA
        add(Manifest.permission.CAMERA);

        // CONTACTS
        add(Manifest.permission.READ_CONTACTS);
        add(Manifest.permission.WRITE_CONTACTS);
        add(Manifest.permission.GET_ACCOUNTS);

        // LOCATION
        add(Manifest.permission.ACCESS_FINE_LOCATION);
        add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        // PHONE
        add(Manifest.permission.READ_PHONE_STATE);
        add(Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= 26) {
            add(Manifest.permission.READ_PHONE_NUMBERS);
            add(Manifest.permission.ANSWER_PHONE_CALLS);
        }
        if (Build.VERSION.SDK_INT >= 16) {
            add(Manifest.permission.READ_CALL_LOG);
            add(Manifest.permission.WRITE_CALL_LOG);
        }
        add(Manifest.permission.ADD_VOICEMAIL);
        add(Manifest.permission.USE_SIP);
        add(Manifest.permission.PROCESS_OUTGOING_CALLS);

        // SMS
        add(Manifest.permission.SEND_SMS);
        add(Manifest.permission.RECEIVE_SMS);
        add(Manifest.permission.READ_SMS);
        add(Manifest.permission.RECEIVE_WAP_PUSH);
        add(Manifest.permission.RECEIVE_MMS);

        // AUDIO / MICROPHONE
        add(Manifest.permission.RECORD_AUDIO);
        add(Manifest.permission.CAPTURE_AUDIO_OUTPUT);
        add(Manifest.permission.MODIFY_AUDIO_SETTINGS);

        // STORAGE / FILES & MEDIA (Android 9 -> 16)
        // Legacy storage for Android 9–12:
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 16) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        // Android 10+ (API 29): optional EXIF (GPS) read from images
        if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        }
        // Android 13+ (API 33): scoped media permissions
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES);
            add(Manifest.permission.READ_MEDIA_VIDEO);
            add(Manifest.permission.READ_MEDIA_AUDIO);
        }
        // Android 14+ (API 34): user-selected visual media (if you use “selected photos” mode)
        if (Build.VERSION.SDK_INT >= 34) {
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        }

        // Nearby devices / notifications
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN);
            add(Manifest.permission.BLUETOOTH_CONNECT);
            add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES);
            add(Manifest.permission.POST_NOTIFICATIONS);
        }

        // SENSORS
        if (Build.VERSION.SDK_INT >= 20) {
            add(Manifest.permission.BODY_SENSORS);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.BODY_SENSORS_BACKGROUND);
        }
    }};

    public static boolean isCheckPermissionRequired(ApplicationInfo info) {
        if (info == null || EliteInstaller.getContext() == null) {
            return false;
        }
        // Runtime permissions only exist on Android 6+. Legacy-target guests
        // (<23) do not request them themselves, so the host must bridge those
        // dangerous permissions when its own targetSdk participates in the
        // runtime-permission model.
        if (!BuildCompat.isM()) {
            return false;
        }
        ApplicationInfo hostInfo = EliteInstaller.getContext().getApplicationInfo();
        if (hostInfo == null || hostInfo.targetSdkVersion < Build.VERSION_CODES.M) {
            return false;
        }
        return info.targetSdkVersion < Build.VERSION_CODES.M;
    }

    public static String[] findDangerousPermissions(List<String> permissions) {
        if (permissions == null) {
            return null;
        }

        List<String> list = new ArrayList<>();
        for (String per : permissions) {
            if (DANGEROUS_PERMISSION.contains(per)) {
                list.add(per);
            }
        }
        return list.toArray(new String[0]);
    }

    // 判断是否有需要权限的要求
    public static boolean checkPermissions(String[] permissions) {
        if (permissions == null) {
            return true;
        }

        for (String permission : permissions) {
            if (!EliteInstaller.get().checkSelfPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    public interface CallBack {
        boolean onResult(int requestCode, String[] permissions, int[] grantResults);
    }

    public static void startRequestPermissions(Context context, String[] permissions, final CallBack callBack) {
        RequestPermissionsActivity.request(context, permissions, new IRequestPermissionsResult.Stub(){

            @Override
            public boolean onResult(int requestCode, String[] permissions, int[] grantResults) throws RemoteException {
                if (callBack != null) {
                    return callBack.onResult(requestCode, permissions, grantResults);
                }
                return false;
            }
        });
    }

    public static boolean isRequestGranted(int[] grantResults) {
        if (grantResults == null || grantResults.length == 0) {
            return false;
        }
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

}
// 20240801 add request permission add end 0