package com.elite.utils.compat;

/**
 * Temporarily installs the guest ClassLoader as the thread context loader.
 *
 * ServiceLoader, coroutine libraries, serializers, Firebase components and many
 * plugin-aware SDKs consult Thread.getContextClassLoader() instead of an explicit
 * Android Context. Restoring the previous loader avoids leaking guest state into
 * unrelated host work.
 */
public final class ScopedClassLoader implements AutoCloseable {
    private final Thread thread;
    private final ClassLoader previous;
    private boolean closed;

    private ScopedClassLoader(Thread thread, ClassLoader previous) {
        this.thread = thread;
        this.previous = previous;
    }

    public static ScopedClassLoader enter(ClassLoader guest) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (guest != null && guest != previous) {
            try {
                thread.setContextClassLoader(guest);
            } catch (Throwable ignored) {
            }
        }
        return new ScopedClassLoader(thread, previous);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            thread.setContextClassLoader(previous);
        } catch (Throwable ignored) {
        }
    }
}
