package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiInspectedDelegationService;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceCallerRespDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiUserDelegationRespDTO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.lang.reflect.Field;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.core.context.SecurityContextHolder;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 模拟可信认证响应缺少新字段，不能静默降级为普通发送。 */
class AiDelegatedOriginValidationTest {
    @Test
    void missingClientRecordIdRejectsBeforeCallingChat() throws Exception {
        AiInspectedDelegationService authenticator = mock(AiInspectedDelegationService.class);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("authenticator", authenticator);
        AiChatService chat = mock(AiChatService.class);
        AiGroupChatService group = mock(AiGroupChatService.class);
        AiDelegatedChatService service = new AiDelegatedChatService(beans.getBeanProvider(AiInspectedDelegationService.class), chat, group);
        field(service, "businessSystem", "platform");
        field(service, "environment", "test");
        field(service, "recordOriginEnabled", true);
        AiServiceBindingDTO binding = new AiServiceBindingDTO();
        binding.setBusinessSystem("platform"); binding.setEnvironment("test"); binding.setTenantId(1L);
        AiServiceCallerRespDTO caller = new AiServiceCallerRespDTO();
        caller.setBinding(binding); caller.setClientId("client");
        OAuth2AccessTokenCheckRespDTO user = new OAuth2AccessTokenCheckRespDTO();
        user.setUserId(7L); user.setTenantId(1L);
        AiUserDelegationRespDTO response = new AiUserDelegationRespDTO();
        response.setCaller(caller); response.setUser(user);
        when(authenticator.validate("test-service", "mcp_jwt_test", "single")).thenReturn(response);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.sendSingle(new AiChatSendReqVO(), "test-service", "mcp_jwt_test"));
        assertEquals("委托来源身份缺失或无效", failure.getMessage());
        verifyNoInteractions(chat, group);
    }

    @Test
    void disabledOriginAllowsLegacyResponseWithoutClientRecordId() throws Exception {
        AiInspectedDelegationService authenticator = mock(AiInspectedDelegationService.class);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("authenticator", authenticator);
        AiChatService chat = mock(AiChatService.class);
        AiGroupChatService group = mock(AiGroupChatService.class);
        AiDelegatedChatService service = new AiDelegatedChatService(beans.getBeanProvider(AiInspectedDelegationService.class), chat, group);
        field(service, "businessSystem", "platform");
        field(service, "environment", "test");
        field(service, "recordOriginEnabled", false);
        AiServiceBindingDTO binding = new AiServiceBindingDTO();
        binding.setBusinessSystem("platform"); binding.setEnvironment("test"); binding.setTenantId(1L);
        AiServiceCallerRespDTO caller = new AiServiceCallerRespDTO();
        caller.setBinding(binding); caller.setClientId("client");
        OAuth2AccessTokenCheckRespDTO user = new OAuth2AccessTokenCheckRespDTO();
        user.setUserId(7L); user.setTenantId(1L);
        AiUserDelegationRespDTO response = new AiUserDelegationRespDTO();
        response.setCaller(caller); response.setUser(user);
        when(authenticator.validate("test-service", "mcp_jwt_test", "single")).thenReturn(response);
        StreamingResponseBody expected = output -> { };
        AiChatSendReqVO request = new AiChatSendReqVO();
        when(chat.sendMessage(request, 7L)).thenReturn(expected);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mock(HttpServletRequest.class)));
        try {
            assertSame(expected, service.sendSingle(request, "test-service", "mcp_jwt_test"));
            verify(chat).sendMessage(request, 7L);
            verifyNoInteractions(group);
        } finally {
            RequestContextHolder.resetRequestAttributes();
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
