package com.elite.fake.service.context.providers;

import android.os.IInterface;

import java.lang.reflect.Method;

import black.android.content.BRAttributionSource;
import com.elite.EliteInstaller;
import com.elite.fake.hook.ClassInvocationStub;
import com.elite.utils.compat.ContextCompat;

/**
 * Created by @jagdish_vip on 4/8/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class SystemProviderStub extends ClassInvocationStub implements BContentProvider {
	private IInterface mBase;

	@Override
	public IInterface wrapper(IInterface contentProviderProxy, String appPkg) {
		mBase = contentProviderProxy;
		injectHook();
		return (IInterface) getProxyInvocation();
	}

	@Override
	protected Object getWho() {
		return mBase;
	}

	@Override
	protected void inject(Object baseInvocation, Object proxyInvocation) {}

	@Override
	protected void onBindMethod() {}

	@Override
	public boolean isBadEnv() {
		return false;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if ("asBinder".equals(method.getName())) {
			return method.invoke(mBase, args);
		}
		if (args != null && args.length > 0) {
			Object arg = args[0];
			if (arg instanceof String) {
				args[0] = EliteInstaller.getHostPkg();
			} else if (arg.getClass().getName().equals(BRAttributionSource.getRealClass().getName())) {
				ContextCompat.fixAttributionSourceState(arg, EliteInstaller.getHostUid());
			}
		}
		return method.invoke(mBase, args);
	}
}
