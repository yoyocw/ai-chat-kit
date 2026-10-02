package io.github.yoyocw.aichatkit.module.ai.service.execution;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.*;
import io.github.yoyocw.aichatkit.module.ai.contract.config.*;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AiInvocationGuardTest {
    private final AiInvocationContext context = new AiInvocationContext("namespace", "tenant", "actor", "round");
    private static AiApplicationConfig config(String mcp, String... tools) {
        return new AiApplicationConfig("application", mcp, Arrays.asList(tools));
    }
    @Test void applicationsWithoutToolsStillAuthorizeBothModesOncePerRound() {
        AtomicInteger authorizations = new AtomicInteger();
        AiInvocationAuthorizationPort port = request -> {
            authorizations.incrementAndGet();
            assertEquals("application", request.getAppId());
            assertEquals("namespace", request.getNamespace());
            assertEquals("tenant", request.getTenantId());
            assertEquals("actor", request.getActorId());
            assertEquals("round", request.getInvocationId());
            assertNull(request.getMcpId());
            assertEquals(Collections.emptyList(), request.getToolIds());
            return new AiInvocationAuthorizationResult(null);
        };
        assertNull(AiInvocationGuard.authorize(port, context, config(null), AiChatMode.SINGLE));
        assertNull(AiInvocationGuard.authorize(port, context, config(null), AiChatMode.GROUP));
        assertEquals(2, authorizations.get());
    }
    @Test void missingAuthorizationResultAndUnexpectedCredentialsAreRejected() {
        assertThrows(IllegalStateException.class, () -> AiInvocationGuard.authorize(null, context, config(null), AiChatMode.SINGLE));
        assertThrows(IllegalStateException.class, () -> AiInvocationGuard.authorize(request -> null, context, config(null), AiChatMode.SINGLE));
        for (String unexpected : Arrays.asList("", " ", "synthetic-tool-delegation")) {
            assertThrows(IllegalStateException.class, () -> AiInvocationGuard.authorize(
                    request -> new AiInvocationAuthorizationResult(unexpected), context, config(null), AiChatMode.SINGLE));
        }
    }
    @Test void configuredToolsRequireNonBlankCredentialsInEverySupportedMode() {
        for (String missing : Arrays.asList(null, "", " \t")) {
            AiInvocationAuthorizationPort port = request -> new AiInvocationAuthorizationResult(missing);
            assertThrows(IllegalStateException.class, () -> AiInvocationGuard.authorize(port, context, config("mcp"), AiChatMode.SINGLE));
            assertThrows(IllegalStateException.class, () -> AiInvocationGuard.authorize(port, context, config(null, "tool"), AiChatMode.SINGLE));
            assertThrows(IllegalStateException.class, () -> AiInvocationGuard.authorize(port, context, config(null, "tool"), AiChatMode.GROUP));
        }
    }
    @Test void matchingToolScopeIsAuthorizedAndItsDelegationIsReturned() {
        AtomicInteger calls = new AtomicInteger();
        AiInvocationAuthorizationPort single = request -> {
            calls.incrementAndGet();
            assertEquals("application", request.getAppId());
            assertEquals("mcp", request.getMcpId());
            assertEquals(Arrays.asList("tool-a", "tool-b"), request.getToolIds());
            assertEquals("actor", request.getActorId());
            return new AiInvocationAuthorizationResult("synthetic-tool-delegation");
        };
        assertEquals("synthetic-tool-delegation", AiInvocationGuard.authorize(single, context,
                config("mcp", "tool-a", "tool-b"), AiChatMode.SINGLE));
        AiInvocationAuthorizationPort group = request -> {
            calls.incrementAndGet();
            assertNull(request.getMcpId());
            assertEquals(Collections.singletonList("tool-a"), request.getToolIds());
            return new AiInvocationAuthorizationResult("synthetic-group-delegation");
        };
        assertEquals("synthetic-group-delegation", AiInvocationGuard.authorize(group, context,
                config(null, "tool-a"), AiChatMode.GROUP));
        assertEquals(2, calls.get());
    }
    @Test void groupMcpBindingIsRejectedBeforeAuthorization() {
        AtomicInteger calls = new AtomicInteger();
        AiInvocationAuthorizationPort port = request -> {
            calls.incrementAndGet();
            return new AiInvocationAuthorizationResult("synthetic-tool-delegation");
        };
        assertThrows(IllegalStateException.class, () -> AiInvocationGuard.authorize(port, context,
                config("mcp", "tool"), AiChatMode.GROUP));
        assertEquals(0, calls.get());
    }
    @Test void capturedIdentityMustMatchPreauthorizedNamespaceTenantAndActor() {
        for (AiInvocationContext different : Arrays.asList(
                new AiInvocationContext("other", "tenant", "actor", "next"),
                new AiInvocationContext("namespace", "other", "actor", "next"),
                new AiInvocationContext("namespace", "tenant", "other", "next"))) {
            assertThrows(IllegalStateException.class, () -> AiInvocationGuard.capture(actor -> different, "actor", context));
        }
        assertThrows(IllegalStateException.class, () -> AiInvocationGuard.capture(actor -> null, "actor", context));
        AiInvocationContext captured = new AiInvocationContext("namespace", "tenant", "actor", "next");
        assertSame(captured, AiInvocationGuard.capture(actor -> {
            assertEquals("actor", actor);
            return captured;
        }, "actor", context));
    }
}
