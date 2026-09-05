package com.elite.utils.compat;

import android.content.ContentProvider;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Binder;

import black.android.app.BRContextImpl;
import black.android.app.BRContextImplKitkat;
import black.android.content.AttributionSourceStateContext;
import black.android.content.BRAttributionSource;
import black.android.content.BRAttributionSourceState;
import black.android.content.BRContentResolver;
import com.elite.EliteInstaller;
import com.elite.app.BActivityThread;
/**
 * Created by @jagdish_via on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ContextCompat {
	public static final String TAG = "ContextCompat";

	public static void fixAttributionSourceState(Object obj, int uid) {
		fixAttributionSourceState(obj, uid, 0);
	}

	public static void fixAttributionSourceState(Object obj, int uid, int depth) {
		if (depth >= 10) return;
		if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
			Object mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();
			AttributionSourceStateContext attributionSourceStateContext = BRAttributionSourceState.get(mAttributionSourceState);
			attributionSourceStateContext._set_packageName(EliteInstaller.getHostPkg());
			attributionSourceStateContext._set_uid(uid);
			fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid, depth + 1);
		}
	}

	public static void fix(Context context) {
        if (context == null) return;
		try {
			int deep = 0;
			while (context instanceof ContextWrapper) {
				context = ((ContextWrapper) context).getBaseContext();
				deep++;
				if (deep >= 10) {
					return;
				}
			}
			BRContextImpl.get(context)._set_mPackageManager(null);
			try {
				context.getPackageManager();
			} catch (Throwable e) {
				e.printStackTrace();
			}

			BRContextImpl.get(context)._set_mBasePackageName(EliteInstaller.getHostPkg());
			BRContextImplKitkat.get(context)._set_mOpPackageName(EliteInstaller.getHostPkg());
			BRContentResolver.get(context.getContentResolver())._set_mPackageName(EliteInstaller.getHostPkg());

			if (BuildCompat.isS()) {
				fixAttributionSourceState(BRContextImpl.get(context).getAttributionSource(), BActivityThread.getBUid());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
