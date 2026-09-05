// IRequestPermissionsResult.aidl
package com.elite.core.system.am;

interface IRequestPermissionsResult {
    boolean onResult(int requestCode,in String[] permissions,in int[] grantResults);
}