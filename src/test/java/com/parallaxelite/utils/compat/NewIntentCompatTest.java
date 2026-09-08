package com.parallaxelite.utils.compat;

import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class NewIntentCompatTest {
    private final IBinder token = new Binder();
    private final Record record = new Record();
    private final Intent intent = new Intent(Intent.ACTION_VIEW,
            Uri.parse("sample://callback#state=test&code=private"))
            .putExtra("callback_extra", "preserved");

    public static class Record { }
    public static class Modern {
        Object receivedRecord;
        Intent received;
        int calls;
        public void handleNewIntent(Record r, List<Intent> intents) {
            receivedRecord = r;
            received = intents.get(0);
            calls++;
        }
        public void performNewIntents(IBinder token, List<Intent> intents) {
            throw new AssertionError("Legacy method must not run after modern delivery");
        }
    }
    public static class Legacy {
        IBinder receivedToken;
        Intent received;
        public void performNewIntents(IBinder token, List<Intent> intents) {
            receivedToken = token;
            received = intents.get(0);
        }
    }
    public static class LegacyResume {
        boolean resumed;
        public void performNewIntents(IBinder token, List<Intent> intents, boolean resume) {
            resumed = resume;
        }
    }
    public static class TokenHandler {
        Intent received;
        public void handleNewIntent(IBinder token, List<Intent> intents) {
            received = intents.get(0);
        }
    }
    public static class Throwing extends Modern {
        @Override public void handleNewIntent(Record r, List<Intent> intents) {
            calls++;
            throw new IllegalStateException("Synthetic callback failure");
        }
        @Override public void performNewIntents(IBinder token, List<Intent> intents) {
            throw new AssertionError("Must not redeliver a partially handled callback");
        }
    }

    @Test public void deliversModernRecordAndPreservesCallback() throws Exception {
        Modern thread = new Modern();
        assertTrue(NewIntentCompat.deliver(thread, record, token, intent));
        assertSame(record, thread.receivedRecord);
        assertSame(intent, thread.received);
        assertEquals("state=test&code=private", thread.received.getData().getFragment());
        assertEquals("preserved", thread.received.getStringExtra("callback_extra"));
        assertEquals(1, thread.calls);
    }
    @Test public void retainsLegacyTokenDelivery() throws Exception {
        Legacy thread = new Legacy();
        assertTrue(NewIntentCompat.deliver(thread, record, token, intent));
        assertSame(token, thread.receivedToken);
        assertSame(intent, thread.received);
    }
    @Test public void retainsLegacyResumeDelivery() throws Exception {
        LegacyResume thread = new LegacyResume();
        assertTrue(NewIntentCompat.deliver(thread, record, token, intent));
        assertTrue(thread.resumed);
    }
    @Test public void retainsTokenHandleNewIntent() throws Exception {
        TokenHandler thread = new TokenHandler();
        assertTrue(NewIntentCompat.deliver(thread, record, token, intent));
        assertSame(intent, thread.received);
    }
    @Test public void rejectsMissingActivityOrInput() throws Exception {
        Modern thread = new Modern();
        assertFalse(NewIntentCompat.deliver(thread, null, token, intent));
        assertFalse(NewIntentCompat.deliver(thread, record, null, intent));
        assertFalse(NewIntentCompat.deliver(thread, record, token, null));
        assertEquals(0, thread.calls);
    }
    @Test public void reportsUnsupportedSignature() throws Exception {
        assertFalse(NewIntentCompat.deliver(new Object(), record, token, intent));
    }
    @Test public void neverRetriesAfterCallbackException() throws Exception {
        Throwing thread = new Throwing();
        try {
            NewIntentCompat.deliver(thread, record, token, intent);
            fail("Expected invocation failure");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }
        assertEquals(1, thread.calls);
    }
}
