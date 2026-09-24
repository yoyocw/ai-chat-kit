package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SigningKey;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1AppPolicy;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1Properties;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1AuthorizationService;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Codec;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Policy;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationResult;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real adapter, subject mapping, authorization, and signing with isolated session and key fixtures. */
class PlatformInProcessSigningChainTest {
    @Test
    void toolCredentialIsSignedOnlyForTheConfiguredAppToolAndEndpoint() throws Exception {
        PlatformLocalSessionSource source = mock(PlatformLocalSessionSource.class);
        when(source.namespace()).thenReturn("platform");
        AiHostSession live = new AiHostSession("platform", "11", "101", "1001",
                System.currentTimeMillis() + 300_000L);
        when(source.captureCurrentSession()).thenReturn(live);
        when(source.currentSession(any())).thenReturn(live);
        when(source.resolveSession(101L, 11L, 1001L)).thenReturn(live);
        when(source.hasPermission(any(), eq("ai:mcp:invoke"))).thenReturn(true);
        PlatformInProcessAuthenticationAdapter host = new PlatformInProcessAuthenticationAdapter(source);
        PlatformInProcessSubjectAdapter subjects = new PlatformInProcessSubjectAdapter(source);
        AiMcpV1Properties properties = new AiMcpV1Properties();
        properties.setAuthorizationEnabled(true);
        properties.setSigningEnabled(true);
        properties.setIssuer("test-issuer");
        properties.setAudience("test-audience");
        AiMcpV1AppPolicy app = new AiMcpV1AppPolicy();
        app.setAppId("tool-app");
        app.setPermission("ai:mcp:invoke");
        app.setMcpId("mcp-1");
        app.setToolIds(Collections.singletonList("tool-1"));
        app.setEndpointCodes(Collections.singletonList("endpoint-1"));
        properties.setApps(Collections.singletonList(app));
        AiMcpV1SigningKey signingKey = testKeyPair();
        AiMcpV1KeySource keys = mock(AiMcpV1KeySource.class);
        when(keys.signingKey("test-issuer", "test-audience")).thenReturn(signingKey);
        AiMcpV1AuthorizationService authorization = new AiMcpV1AuthorizationService("platform", properties,
                host, host, host, () -> subjects, () -> keys);
        AiInvocationContext incoming = new AiInvocationContext("platform", "11", "101", "invocation-1");

        AiInvocationAuthorizationResult approved = authorization.authorize(new AiInvocationAuthorizationRequest(
                incoming, "tool-app", "mcp-1", Collections.singletonList("tool-1")));
        assertThat(approved.hasToolCredential()).isTrue();
        String header = approved.toolAuthorization();
        assertThat(header).startsWith("Bearer ");
        McpJwtV1Identity verified = new McpJwtV1Codec(
                new McpJwtV1Policy("test-issuer", "test-audience", 10, true))
                .verify(header.substring(7), signingKey.publicKey());
        assertThat(verified.getServiceUserId()).isEqualTo(101L);
        assertThat(verified.getTenantId()).isEqualTo(11L);
        assertThat(verified.getAccessTokenId()).isEqualTo(1001L);
        assertThat(verified.getAllowedEndpointCodes()).containsExactly("endpoint-1");
        verify(source, atLeastOnce()).resolveSession(101L, 11L, 1001L);

        assertThatThrownBy(() -> authorization.authorize(new AiInvocationAuthorizationRequest(
                incoming, "unknown-app", "mcp-1", Collections.singletonList("tool-1"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> authorization.authorize(new AiInvocationAuthorizationRequest(
                incoming, "tool-app", "mcp-1", Arrays.asList("tool-1", "tool-2"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> authorization.authorize(new AiInvocationAuthorizationRequest(
                incoming, "tool-app", "mcp-2", Collections.singletonList("tool-1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private AiMcpV1SigningKey testKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new AiMcpV1SigningKey(pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    }

    private String pem(String kind, byte[] encoded) {
        return "-----BEGIN " + kind + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + kind + "-----";
    }
}
