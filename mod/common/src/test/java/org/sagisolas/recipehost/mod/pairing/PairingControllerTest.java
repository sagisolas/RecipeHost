package org.sagisolas.recipehost.mod.pairing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

public class PairingControllerTest {

    private final FakeFactory factory = new FakeFactory();
    private final FakeScheduler scheduler = new FakeScheduler();
    private final Iterator<ByteString> tokens =
            List.of(token(1), token(2), token(3)).iterator();

    private final PairingController controller =
            new PairingController(factory, scheduler, tokens::next, () -> List.of(addr("10.0.0.2")));

    @Test
    public void openBindsWithFreshTokenAndReturnsPairingUri() throws Exception {
        String uri = controller.open();

        assertEquals(1, factory.bound.size());
        assertEquals(token(1), factory.bound.get(0).token);
        assertEquals(
                "recipehost://pair?v=1&h=10.0.0.2&p=4000&t="
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(token(1).toByteArray()),
                uri);
    }

    @Test
    public void screenCloseUnbindsAfterSixtySecondGrace() throws Exception {
        controller.open();
        controller.screenClosed();

        scheduler.advance(Duration.ofSeconds(59));
        assertFalse("still bound inside grace", factory.bound.get(0).closed);

        scheduler.advance(Duration.ofSeconds(1));
        assertTrue("unbound at 60 s", factory.bound.get(0).closed);
    }

    @Test
    public void reopenInsideGraceRotatesTokenAndCancelsPendingUnbind() throws Exception {
        controller.open();
        controller.screenClosed();
        scheduler.advance(Duration.ofSeconds(30));

        controller.open();

        assertTrue("previous listener closed on reopen", factory.bound.get(0).closed);
        assertEquals(token(2), factory.bound.get(1).token);

        scheduler.advance(Duration.ofSeconds(60));
        assertFalse("old grace timer must not kill the new listener", factory.bound.get(1).closed);
    }

    @Test
    public void deliveredSnapshotUnbindsImmediately() throws Exception {
        controller.open();

        factory.bound.get(0).onSnapshotDelivered.run();

        assertTrue(factory.bound.get(0).closed);
    }

    @Test
    public void staleDeliveryFromPreviousPairingDoesNotUnbindCurrent() throws Exception {
        controller.open();
        controller.open();

        factory.bound.get(0).onSnapshotDelivered.run();

        assertFalse(factory.bound.get(1).closed);
    }

    @Test
    public void worldLeaveUnbindsImmediately() throws Exception {
        controller.open();

        controller.worldLeft();

        assertTrue(factory.bound.get(0).closed);
    }

    @Test
    public void graceTimerAlreadyFiringDoesNotUnbindANewerPairing() throws Exception {
        controller.open();
        controller.screenClosed();
        Runnable inFlight = scheduler.lastTask(); // timer thread already running: cancel() can't stop it

        controller.open();
        inFlight.run();

        assertFalse(factory.bound.get(1).closed);
    }

    // --- fakes -------------------------------------------------------------

    private static ByteString token(int seed) {
        byte[] b = new byte[32];
        b[0] = (byte) seed;
        return ByteString.copyFrom(b);
    }

    private static InetAddress addr(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class FakeListener implements PairingController.Listener {
        final ByteString token;
        final Runnable onSnapshotDelivered;
        final int port;
        boolean closed;

        FakeListener(ByteString token, Runnable onSnapshotDelivered, int port) {
            this.token = token;
            this.onSnapshotDelivered = onSnapshotDelivered;
            this.port = port;
        }

        @Override
        public int port() {
            return port;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeFactory implements PairingController.ListenerFactory {
        final List<FakeListener> bound = new ArrayList<>();

        @Override
        public PairingController.Listener bind(ByteString token, Runnable onSnapshotDelivered) {
            FakeListener l = new FakeListener(token, onSnapshotDelivered, 4000 + bound.size());
            bound.add(l);
            return l;
        }
    }

    private static final class FakeScheduler implements PairingController.Scheduler {
        private record Task(Duration at, Runnable task, boolean[] cancelled) {}

        private final List<Task> tasks = new ArrayList<>();
        private Duration now = Duration.ZERO;

        @Override
        public PairingController.Cancellable schedule(Runnable task, Duration delay) {
            boolean[] cancelled = {false};
            tasks.add(new Task(now.plus(delay), task, cancelled));
            return () -> cancelled[0] = true;
        }

        Runnable lastTask() {
            return tasks.get(tasks.size() - 1).task;
        }

        void advance(Duration d) {
            now = now.plus(d);
            for (Task t : List.copyOf(tasks)) {
                if (!t.cancelled[0] && t.at.compareTo(now) <= 0) {
                    tasks.remove(t);
                    t.task.run();
                }
            }
        }
    }
}
