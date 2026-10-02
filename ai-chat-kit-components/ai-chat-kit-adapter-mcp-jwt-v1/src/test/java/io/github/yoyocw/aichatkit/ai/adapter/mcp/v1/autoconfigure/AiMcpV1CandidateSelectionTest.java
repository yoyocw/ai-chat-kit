package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

class AiMcpV1CandidateSelectionTest {
    @Test void securityCandidatesRemainAmbiguousDespitePrimary() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Multiple.class)) {
            assertSame(context.getBean("primaryIdentity"), context.getBean(AiInvocationContextPort.class));
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> AiMcpV1Beans.unique(context, AiInvocationContextPort.class));
            assertEquals("MCP 必需适配器缺失或不唯一：AiInvocationContextPort", failure.getMessage());
        }
    }
    @Test void securityCandidatesWithoutPrimaryAlsoFail() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(UnqualifiedMultiple.class)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> AiMcpV1Beans.unique(context, AiInvocationContextPort.class));
            assertEquals("MCP 必需适配器缺失或不唯一：AiInvocationContextPort", failure.getMessage());
        }
    }
    @Test void exactlyOneSecurityCandidateIsSelected() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Single.class)) {
            assertSame(context.getBean("identity"), AiMcpV1Beans.unique(context, AiInvocationContextPort.class));
        }
    }
    @Test void absentSecurityCandidateFailsExplicitly() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            assertThrows(IllegalStateException.class, () -> AiMcpV1Beans.unique(context, AiInvocationContextPort.class));
        }
    }
    @Configuration(proxyBeanMethods = false)
    static class Multiple {
        @Bean @Primary AiInvocationContextPort primaryIdentity() { return actor -> null; }
        @Bean AiInvocationContextPort otherIdentity() { return actor -> null; }
    }
    @Configuration(proxyBeanMethods = false)
    static class UnqualifiedMultiple {
        @Bean AiInvocationContextPort firstIdentity() { return actor -> null; }
        @Bean AiInvocationContextPort secondIdentity() { return actor -> null; }
    }
    @Configuration(proxyBeanMethods = false)
    static class Single {
        @Bean AiInvocationContextPort identity() { return actor -> null; }
    }
}
