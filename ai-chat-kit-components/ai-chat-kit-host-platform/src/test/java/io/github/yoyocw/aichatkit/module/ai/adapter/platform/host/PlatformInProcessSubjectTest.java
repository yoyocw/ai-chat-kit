package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** The ID source is mocked; cryptographic proof verification belongs to the caller. */
class PlatformInProcessSubjectTest {
    private final PlatformLocalSessionSource source = mock(PlatformLocalSessionSource.class);
    private final PlatformInProcessSubjectAdapter subjects = new PlatformInProcessSubjectAdapter(source);
    private final AiInvocationContext context = new AiInvocationContext("platform", "11", "101", "request-1");
    private final AiHostSession session = new AiHostSession("platform", "11", "101", "1001",
            System.currentTimeMillis() + 300_000L);

    PlatformInProcessSubjectTest() { when(source.namespace()).thenReturn("platform"); }

    @Test
    void mapReturnsOnlyOriginalNumericIdentityAfterCurrentSessionRecheck() {
        when(source.currentSession(context)).thenReturn(session);

        McpJwtV1Identity mapped = subjects.mapSession(context, session);

        assertThat(mapped.getServiceUserId()).isEqualTo(101L);
        assertThat(mapped.getTenantId()).isEqualTo(11L);
        assertThat(mapped.getAccessTokenId()).isEqualTo(1001L);
        verify(source).currentSession(context);
    }

    @Test
    void mapCannotSwitchToAnotherValidSessionOrTenant() {
        when(source.currentSession(context)).thenReturn(session);
        AiHostSession another = new AiHostSession("platform", "11", "101", "2002",
                System.currentTimeMillis() + 300_000L);

        assertThatThrownBy(() -> subjects.mapSession(context, another)).isInstanceOf(AiIdentityException.class);
    }

    @Test
    void mapRejectsNonCanonicalNumericIdentifiersAndWrongNamespace() {
        when(source.currentSession(context)).thenReturn(session);
        AiHostSession padded = new AiHostSession("platform", "011", "101", "1001",
                System.currentTimeMillis() + 300_000L);
        assertThatThrownBy(() -> subjects.mapSession(context, padded)).isInstanceOf(AiIdentityException.class);
        AiInvocationContext wrong = new AiInvocationContext("other", "11", "101", "request-2");
        when(source.currentSession(wrong)).thenReturn(session);
        assertThatThrownBy(() -> subjects.mapSession(wrong, session)).isInstanceOf(AiIdentityException.class);
    }

    @Test
    void verifiedNumericProofResolvesByCurrentOriginalSessionRecord() {
        McpJwtV1Identity proof = proof(101L, 11L, 1001L);
        when(source.resolveSession(101L, 11L, 1001L)).thenReturn(session);

        AiHostSession resolved = subjects.resolveSession(proof);

        assertThat(resolved).isSameAs(session);
        verify(source).resolveSession(101L, 11L, 1001L);
    }

    @Test
    void invalidProofTupleCannotQueryOriginalSessionSource() {
        assertThatThrownBy(() -> subjects.resolveSession(proof(101L, 11L, 0L)))
                .isInstanceOf(AiIdentityException.class);
        verify(source, never()).resolveSession(anyLong(), anyLong(), anyLong());
    }

    private McpJwtV1Identity proof(Long user, Long tenant, Long accessTokenId) {
        McpJwtV1Identity proof = new McpJwtV1Identity();
        proof.setServiceUserId(user);
        proof.setTenantId(tenant);
        proof.setAccessTokenId(accessTokenId);
        return proof;
    }
}
