package com.parallaxelite.utils.compat;

import android.content.Intent;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/** Delivers to an already registered activity in the current process. */
public final class NewIntentCompat {
    private NewIntentCompat() { }

    public static boolean deliver(Object thread, Object activityRecord,
            IBinder token, Intent intent) throws ReflectiveOperationException {
        if (thread == null || activityRecord == null || token == null || intent == null) {
            return false;
        }
        List<Intent> intents = Collections.singletonList(intent);

        // Modern ActivityThread takes its existing ActivityClientRecord, not
        // an IBinder. Use that process-owned record; never construct a record.
        Method method = find(thread, "handleNewIntent", activityRecord.getClass(), List.class);
        if (method != null) {
            method.invoke(thread, activityRecord, intents);
            return true;
        }
        method = find(thread, "performNewIntents", IBinder.class, List.class);
        if (method != null) {
            method.invoke(thread, token, intents);
            return true;
        }
        method = find(thread, "performNewIntents", IBinder.class, List.class, boolean.class);
        if (method != null) {
            method.invoke(thread, token, intents, true);
            return true;
        }
        method = find(thread, "handleNewIntent", IBinder.class, List.class);
        if (method != null) {
            method.invoke(thread, token, intents);
            return true;
        }
        return false;
    }

    private static Method find(Object thread, String name, Class<?>... parameters) {
        try {
            Method method = thread.getClass().getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
