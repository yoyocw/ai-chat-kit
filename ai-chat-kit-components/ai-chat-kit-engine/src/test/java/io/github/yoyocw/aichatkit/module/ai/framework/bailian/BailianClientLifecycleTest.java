package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BailianClientLifecycleTest {
    private static final String APP = "0123456789abcdef0123456789abcdef";

    @Test
    void closedClientRejectsSingleAndGroupWithoutConnecting() throws Exception {
        try (Loopback server = new Loopback(); BailianClient client = new BailianClient(properties(server))) {
            client.close();
            client.close();
            assertThrows(BailianCallException.class, () -> client.stream(1L, APP, "test", null, event -> { }));
            assertThrows(BailianCallException.class, () -> client.streamGroupWorkflow(2L, APP, "test", null, null));
            assertFalse(server.accepted.await(100, TimeUnit.MILLISECONDS), "closed client connected upstream");
        }
    }

    @Test
    void closeBetweenRequestConstructionAndRegistrationRejectsCall() throws Exception {
        try (Loopback server = new Loopback()) {
            CountDownLatch constructing = new CountDownLatch(1);
            CountDownLatch resume = new CountDownLatch(1);
            BailianProperties props = new BailianProperties() {
                @Override public String getApiKey() {
                    constructing.countDown();
                    try {
                        if (!resume.await(5, TimeUnit.SECONDS)) { throw new AssertionError("construction release timed out"); }
                    } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
                    return super.getApiKey();
                }
            };
            configure(props, server);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try (BailianClient client = new BailianClient(props)) {
                Future<?> invocation = worker.submit(() -> assertThrows(BailianCallException.class,
                        () -> client.stream(3L, APP, "test", null, event -> { })));
                assertTrue(constructing.await(2, TimeUnit.SECONDS));
                client.close();
                resume.countDown();
                invocation.get(4, TimeUnit.SECONDS);
                assertFalse(server.accepted.await(100, TimeUnit.MILLISECONDS), "call registered after close connected upstream");
                assertFalse(client.cancel(3L));
            } finally { resume.countDown(); worker.shutdownNow(); }
        }
    }

    @Test
    void closeCancelsRegisteredCallBlockedInTlsHandshake() throws Exception {
        try (Loopback server = new Loopback(); BailianClient client = new BailianClient(properties(server))) {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<?> invocation = worker.submit(() -> assertThrows(BailianCallException.class,
                        () -> client.stream(4L, APP, "test", null, event -> { })));
                assertTrue(server.accepted.await(2, TimeUnit.SECONDS), "call did not reach loopback socket");
                client.close();
                invocation.get(1, TimeUnit.SECONDS);
                assertFalse(client.cancel(4L));
            } finally { worker.shutdownNow(); }
        }
    }

    private static BailianProperties properties(Loopback server) {
        BailianProperties props = new BailianProperties(); configure(props, server); return props;
    }
    private static void configure(BailianProperties props, Loopback server) {
        props.setApiKey("synthetic-test-key");
        props.setBaseUrl("https://127.0.0.1:" + server.socket.getLocalPort());
        props.setConnectTimeoutSeconds(2);
        props.setReadTimeoutSeconds(2);
    }
    private static final class Loopback implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        final CountDownLatch accepted = new CountDownLatch(1);
        volatile Socket connection;
        final ExecutorService listener = Executors.newSingleThreadExecutor();
        Loopback() throws Exception {
            listener.submit(() -> {
                try { connection = socket.accept(); accepted.countDown(); }
                catch (java.io.IOException ignored) { }
            });
        }
        @Override public void close() throws Exception {
            socket.close(); listener.shutdownNow();
            listener.awaitTermination(2, TimeUnit.SECONDS);
            if (connection != null) { connection.close(); }
        }
    }
}
