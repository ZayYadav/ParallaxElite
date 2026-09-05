package com.elite.utils.compat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

/**
 * Dynamic receiver compatibility for Android 13+.
 *
 * Host/framework receivers must declare exported state on modern Android. Guest
 * binder calls that predate this requirement receive a conservative
 * RECEIVER_NOT_EXPORTED default instead of crashing the host process.
 */
public final class ReceiverCompat {
    private ReceiverCompat() {
    }

    public static Intent registerSystemReceiver(
            Context context, BroadcastReceiver receiver, IntentFilter filter) {
        if (context == null || receiver == null || filter == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        }
        return context.registerReceiver(receiver, filter);
    }

    public static Intent registerReceiver(
            Context context,
            BroadcastReceiver receiver,
            IntentFilter filter,
            String permission,
            Handler scheduler,
            boolean exported) {
        if (context == null || receiver == null || filter == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int flags = exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED;
            return context.registerReceiver(receiver, filter, permission, scheduler, flags);
        }
        return context.registerReceiver(receiver, filter, permission, scheduler);
    }

    public static int ensureExplicitExportFlag(int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return flags;
        }
        int mask = Context.RECEIVER_EXPORTED | Context.RECEIVER_NOT_EXPORTED;
        if ((flags & mask) == 0) {
            flags |= Context.RECEIVER_NOT_EXPORTED;
        }
        return flags;
    }
}
