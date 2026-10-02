package io.github.yoyocw.aichatkit.ai.starter.autoconfigure;

import io.github.yoyocw.aichatkit.ai.starter.config.AiStarterProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

class AiStarterCandidateSelectionTest {
    @Test void primaryIdentityCandidateIsAcceptedByActualVerifier() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WithPrimary.class)) {
            assertSame(context.getBean("primaryIdentity"), context.getBean(AiInvocationContextPort.class));
            String missing = missingCapabilities(context);
            assertFalse(missing.contains("AiInvocationContextPort"), missing);
            assertTrue(missing.contains("AiHostSessionPort"), "fixture intentionally omits unrelated required capabilities");
        }
    }
    @Test void multipleIdentityCandidatesWithoutPrimaryAreExplicitlyReported() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WithoutPrimary.class)) {
            String missing = missingCapabilities(context);
            assertTrue(missing.contains("AiInvocationContextPort（存在多个候选）"), missing);
        }
    }
    @Test void missingIdentityIsReportedWithoutClaimingCandidateAmbiguity() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            String missing = missingCapabilities(context);
            assertTrue(missing.contains("AiInvocationContextPort"), missing);
            assertFalse(missing.contains("AiInvocationContextPort（存在多个候选）"), missing);
        }
    }
    private static String missingCapabilities(AnnotationConfigApplicationContext context) {
        AiStarterProperties properties = new AiStarterProperties();
        properties.setNamespace("test-namespace");
        return assertThrows(IllegalStateException.class, () ->
                new AiStarterDependencyVerifier(context, properties).afterSingletonsInstantiated()).getMessage();
    }
    @Configuration(proxyBeanMethods = false)
    static class WithPrimary {
        @Bean @Primary AiInvocationContextPort primaryIdentity() { return actor -> null; }
        @Bean AiInvocationContextPort otherIdentity() { return actor -> null; }
    }
    @Configuration(proxyBeanMethods = false)
    static class WithoutPrimary {
        @Bean AiInvocationContextPort firstIdentity() { return actor -> null; }
        @Bean AiInvocationContextPort secondIdentity() { return actor -> null; }
    }
}
