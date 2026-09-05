package com.parallax;

import android.content.Context;

import com.parallaxelite.ParallaxELiteInstaller;
import com.parallaxelite.core.HostApp;
import com.parallaxelite.core.system.api.MetaActivationManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single public entry point for the ParallaxELite SDK.
 *
 * App integrations only need:
 * import com.parallax.ELite;
 */
public final class ELite {
    private static final AtomicBoolean ATTACHED = new AtomicBoolean(false);
    private static final AtomicBoolean CREATED = new AtomicBoolean(false);

    private ELite() {
    }

    /**
     * Call from Application.attachBaseContext(base).
     */
    public static void attach(Context base) {
        if (base == null) {
            throw new IllegalArgumentException("Context is null");
        }
        if (ATTACHED.compareAndSet(false, true)) {
            ParallaxELiteInstaller.get().doAttachBaseContext(base, new HostApp(base));
        }
    }

    /**
     * Call from Application.onCreate() after attach().
     */
    public static void create() {
        if (!ATTACHED.get()) {
            throw new IllegalStateException("ELite.attach(context) must be called first");
        }
        if (CREATED.compareAndSet(false, true)) {
            ParallaxELiteInstaller.get().doCreate();
        }
    }

    /**
     * Convenience initializer for integrations that do not need two-phase lifecycle wiring.
     * For virtualization hosts, attach() in attachBaseContext and create() in onCreate
     * remains the preferred setup.
     */
    public static void init(Context context) {
        attach(context);
        create();
    }

    /**
     * Activates the SDK asynchronously using the configured secure panel.
     */
    public static void activate(String key) {
        MetaActivationManager.activateSdk(key);
    }

    public static boolean isActivated() {
        return MetaActivationManager.getActivatedStatus();
    }

    public static String getMessage() {
        return MetaActivationManager.getServerMessage();
    }
}
