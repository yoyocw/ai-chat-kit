package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.delegation.AiDelegatedChatController;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.AiChatController;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiDelegatedCallContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只使用线程请求上下文和模拟Servlet，不启动服务器。 */
class PlatformOriginContextAdapterTest {
    private static final String APP = "0123456789abcdef0123456789abcdef";
    private final PlatformOriginContextAdapter adapter = new PlatformOriginContextAdapter();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @BeforeEach
    void setup() { RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); }
    @AfterEach
    void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void disabledDoesNotRequireContext() {
        assertNull(adapter.capture(APP));
        verifyNoInteractions(request);
    }

    @Test
    void ordinaryEntryRemainsOptional() throws Exception {
        enable();
        HandlerMethod handler = new HandlerMethod(mock(AiChatController.class),
                AiChatController.class.getMethod("sendMessage", AiChatSendReqVO.class));
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)).thenReturn(handler);
        assertNull(adapter.capture(APP));
    }

    @Test
    void matchedDelegatedEntryCannotLoseContext() throws Exception {
        enable();
        HandlerMethod handler = new HandlerMethod(mock(AiDelegatedChatController.class),
                AiDelegatedChatController.class.getMethod("sendSingle", AiChatSendReqVO.class, String.class, String.class));
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)).thenReturn(handler);
        assertThrows(IllegalStateException.class, () -> adapter.capture(APP));
    }

    @Test
    void malformedContextRejected() throws Exception {
        enable();
        when(request.getAttribute(AiDelegatedCallContext.class.getName())).thenReturn("untrusted");
        assertThrows(IllegalStateException.class, () -> adapter.capture(APP));
    }

    @Test
    void missingOriginOrUnapprovedAppRejected() throws Exception {
        enable();
        AiServiceBindingDTO binding = new AiServiceBindingDTO();
        binding.setAppIds(Collections.singletonList(APP));
        when(request.getAttribute(AiDelegatedCallContext.class.getName()))
                .thenReturn(new AiDelegatedCallContext(binding, "unused-test-proof", null));
        assertThrows(IllegalStateException.class, () -> adapter.capture(APP));
        AiCallerOrigin origin = new AiCallerOrigin(1L, 7L, 23L, "client", "platform", "test");
        when(request.getAttribute(AiDelegatedCallContext.class.getName()))
                .thenReturn(new AiDelegatedCallContext(binding, "unused-test-proof", origin));
        assertThrows(IllegalStateException.class, () -> adapter.capture("other"));
        assertSame(origin, adapter.capture(APP));
    }

    private void enable() throws Exception {
        Field enabled = PlatformOriginContextAdapter.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.setBoolean(adapter, true);
    }
}
