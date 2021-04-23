package com.example.mediacodecdemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.PermissionChecker;

public class PermissionUtil {

    public static boolean checkPermissionGranted(Context context, String[] permission) {
        // Android 6.0 以前，全部默认授权
        boolean result = true;
        int targetSdkVersion = 21;
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            targetSdkVersion = info.applicationInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (targetSdkVersion >= Build.VERSION_CODES.M) {
                // targetSdkVersion >= 23, 使用Context#checkSelfPermission

                for (int i = 0; i < permission.length; i++) {
                    result &= context.checkSelfPermission(permission[i])
                            == PackageManager.PERMISSION_GRANTED;
                }

            } else {
                // targetSdkVersion < 23, 需要使用 PermissionChecker
                for (int i = 0; i < permission.length; i++) {
                    result &= PermissionChecker.checkSelfPermission(context, permission[i])
                            == PermissionChecker.PERMISSION_GRANTED;
                }
            }
        }
        return result;
    }
}
