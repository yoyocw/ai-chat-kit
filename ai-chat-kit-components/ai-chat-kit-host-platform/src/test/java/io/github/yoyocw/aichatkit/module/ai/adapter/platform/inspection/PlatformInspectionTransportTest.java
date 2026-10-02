package io.github.yoyocw.aichatkit.module.ai.adapter.platform.inspection;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Loopback TLS exercises the real transport with certificate validation intact. */
class PlatformInspectionTransportTest {
    private static final String PATH = "/rpc-api/system/oauth2/session/inspect";
    private SSLContext oldTls;
    private String oldTrustStore;
    private String oldTrustStorePassword;
    private String oldTrustStoreType;
    private HttpsServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String body;
    private volatile boolean chunked;
    private volatile boolean disconnect;
    private volatile String authorization;
    private volatile String tenantHeader;
    private volatile String path;

    @BeforeEach
    void start() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = getClass().getResourceAsStream("/localhost-test-tls.p12")) {
            assertThat(input).isNotNull();
            store.load(input, "changeit".toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "changeit".toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        oldTls = SSLContext.getDefault();
        oldTrustStore = System.getProperty("javax.net.ssl.trustStore");
        oldTrustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
        oldTrustStoreType = System.getProperty("javax.net.ssl.trustStoreType");
        System.setProperty("javax.net.ssl.trustStore",
                new java.io.File(getClass().getResource("/localhost-test-tls.p12").toURI()).getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        SSLContext.setDefault(tls);
        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/", this::handle);
        server.start();
        body = validBody();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        if (oldTls != null) SSLContext.setDefault(oldTls);
        restore("javax.net.ssl.trustStore", oldTrustStore);
        restore("javax.net.ssl.trustStorePassword", oldTrustStorePassword);
        restore("javax.net.ssl.trustStoreType", oldTrustStoreType);
    }

    @Test
    void validTlsUsesFixedPathAndDeploymentConsumerTenant() {
        try (PlatformInspectionTransport client = client()) {
            PlatformInspectionResult result = client.inspect(request());
            assertThat(result.getUserId()).isEqualTo(101L);
            assertThat(path).isEqualTo(PATH);
            assertThat(authorization).isEqualTo("Bearer consumer-secret");
            assertThat(tenantHeader).isEqualTo("7");
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void invalidOriginAndNonTlsFailBeforeCredentialLookup() {
        for (String url : Arrays.asList("http://localhost", "https://user@localhost",
                "https://localhost/path", "https://localhost?x=1", "https://localhost#x")) {
            assertThatThrownBy(() -> new PlatformInspectionTransport(url, 7L,
                    () -> { throw new AssertionError("credential read during construction"); }))
                    .isInstanceOf(IllegalStateException.class).hasNoCause();
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void redirectAndDisconnectDoNotRetryOrRevealSecrets() {
        status = 302;
        body = "redirect-secret";
        try (PlatformInspectionTransport client = client()) { denied(() -> client.inspect(request())); }
        assertThat(calls).hasValue(1);
        status = 200;
        disconnect = true;
        try (PlatformInspectionTransport client = client()) { denied(() -> client.inspect(request())); }
        assertThat(calls).hasValue(2);
    }

    @Test
    void boundedAndStrictJsonResponsesRejectAmbiguity() {
        chunked = true;
        body = validBody() + repeat(' ', 16385);
        try (PlatformInspectionTransport client = client()) { denied(() -> client.inspect(request())); }
        chunked = false;
        body = validBody().replace("\"userId\":101", "\"userId\":101,\"userId\":101");
        try (PlatformInspectionTransport client = client()) { denied(() -> client.inspect(request())); }
        body = validBody().replace("\"userId\":101", "\"userId\":\"101\"");
        try (PlatformInspectionTransport client = client()) { denied(() -> client.inspect(request())); }
        body = validBody() + "{}";
        try (PlatformInspectionTransport client = client()) { denied(() -> client.inspect(request())); }
        assertThat(calls).hasValue(4);
    }

    @Test
    void closeIsIdempotentAndTimeoutPolicyIsFinite() throws Exception {
        PlatformInspectionTransport client = client();
        Field field = PlatformInspectionTransport.class.getDeclaredField("http");
        field.setAccessible(true);
        OkHttpClient http = (OkHttpClient) field.get(client);
        assertThat(http.followRedirects()).isFalse();
        assertThat(http.followSslRedirects()).isFalse();
        assertThat(http.retryOnConnectionFailure()).isFalse();
        assertThat(http.connectTimeoutMillis()).isEqualTo((int) TimeUnit.SECONDS.toMillis(3));
        assertThat(http.readTimeoutMillis()).isEqualTo((int) TimeUnit.SECONDS.toMillis(5));
        assertThat(http.writeTimeoutMillis()).isEqualTo((int) TimeUnit.SECONDS.toMillis(5));
        assertThat(http.callTimeoutMillis()).isEqualTo((int) TimeUnit.SECONDS.toMillis(8));
        client.close();
        client.close();
        denied(() -> client.inspect(request()));
        assertThat(calls).hasValue(0);
    }

    private PlatformInspectionTransport client() {
        return new PlatformInspectionTransport("https://localhost:" + server.getAddress().getPort(),
                7L, () -> "consumer-secret");
    }

    private PlatformInspectionRequest request() {
        PlatformInspectionRequest value = new PlatformInspectionRequest();
        value.setSubjectType("USER");
        value.setAccessToken("raw-user");
        value.setExpectedTenantId(11L);
        value.setExpectedUserId(101L);
        return value;
    }

    private String validBody() {
        return "{\"code\":0,\"data\":{\"sessionId\":1001,\"userId\":101,\"userType\":2,"
                + "\"tenantId\":11,\"clientId\":\"web\",\"clientRecordId\":3,"
                + "\"expiresAtMillis\":" + (System.currentTimeMillis() + 300000L)
                + ",\"permissionsSatisfied\":true}}";
    }

    private void handle(HttpExchange exchange) {
        calls.incrementAndGet();
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        tenantHeader = exchange.getRequestHeaders().getFirst("tenant-id");
        path = exchange.getRequestURI().getPath();
        try {
            exchange.getRequestBody().close();
            if (disconnect) return;
            if (status == 302) exchange.getResponseHeaders().add("Location",
                    "https://localhost:" + server.getAddress().getPort() + PATH);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, chunked ? 0 : bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (Exception ignored) {
            // A dropped connection tests the no-retry boundary.
        } finally {
            exchange.close();
        }
    }

    private void denied(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(IllegalStateException.class).hasNoCause()
                .hasMessageNotContaining("consumer-secret")
                .hasMessageNotContaining("raw-user")
                .hasMessageNotContaining("redirect-secret");
    }

    private String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private void restore(String name, String previous) {
        if (previous == null) System.clearProperty(name);
        else System.setProperty(name, previous);
    }
}
