package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiInspectedDelegationService;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiUserDelegationRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.UserTypeEnum;
import io.github.yoyocw.aichatkit.compat.framework.common.util.servlet.ServletUtils;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.util.TenantUtils;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatService;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

/** 独立 AI 的服务入口适配，只接收机器凭据和受限用户委托，不接收原始用户登录令牌。 */
@Service
@RequiredArgsConstructor
public class AiDelegatedChatService {
    /** 本地双重认证；未配置受限认证时按需拒绝，不回退 system 专用 RPC。 */
    private final ObjectProvider<AiInspectedDelegationService> authenticator;
    /** 复用单聊事务与异步响应实现。 */
    private final AiChatService chatService;
    /** 复用群聊会话归属和工作流实现。 */
    private final AiGroupChatService groupChatService;
    /** 当前部署所服务的业务系统；未配置时不开放委托入口。 */
    @Value("${ai-chat-kit.ai.delegated-entry.business-system:}")
    private String businessSystem;
    /** 当前部署环境，必须与认证侧客户端绑定一致。 */
    @Value("${ai-chat-kit.ai.delegated-entry.environment:}")
    private String environment;
    /** 来源记录默认关闭；开启后旧认证侧缺失实体编号必须拒绝。 */
    @Value("${ai-chat-kit.ai.delegated-entry.record-origin-enabled:false}")
    private boolean recordOriginEnabled;

    /** 单聊入口，模式由代码固定；用户身份不能来自请求字段或透传 login-user 头。 */
    public StreamingResponseBody sendSingle(AiChatSendReqVO request, String serviceToken, String delegation) {
        return execute(serviceToken, delegation, "single", userId -> chatService.sendMessage(request, userId));
    }

    /** 群聊入口，继续使用既有会话成员和用户归属检查。 */
    public StreamingResponseBody sendGroup(AiGroupChatSendReqVO request, String serviceToken, String delegation) {
        return execute(serviceToken, delegation, "group", userId -> groupChatService.sendMessage(request, userId));
    }

    /** 同步准备阶段建立可信用户上下文；异步流只捕获已有发送实现提取的租户和凭据。 */
    private StreamingResponseBody execute(String serviceToken, String delegation, String mode,
                                          Function<Long, StreamingResponseBody> action) {
        AiUserDelegationRespDTO identity = authenticate(serviceToken, delegation, mode);
        AiCallerOrigin origin = recordOriginEnabled ? new AiCallerOrigin(
                identity.getUser().getTenantId(), identity.getUser().getUserId(),
                identity.getCaller().getClientRecordId(), identity.getCaller().getClientId(),
                identity.getCaller().getBinding().getBusinessSystem(),
                identity.getCaller().getBinding().getEnvironment()) : null;
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            throw new IllegalStateException("缺少 AI 委托请求上下文");
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        Object previousDelegation = request.getAttribute(AiDelegatedCallContext.class.getName());
        try {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            LoginUser user = new LoginUser();
            user.setId(identity.getUser().getUserId());
            user.setUserType(UserTypeEnum.ADMIN.getValue());
            user.setTenantId(identity.getUser().getTenantId());
            user.setAccessTokenId(identity.getUser().getAccessTokenId());
            user.setExpiresTime(identity.getUser().getExpiresTime());
            user.setInfo(Collections.emptyMap());
            SecurityFrameworkUtils.setLoginUser(user, request);
            request.setAttribute(AiDelegatedCallContext.class.getName(),
                    new AiDelegatedCallContext(identity.getCaller().getBinding(), "Bearer " + delegation, origin));
            return TenantUtils.execute(user.getTenantId(), () -> action.apply(user.getId()));
        } finally {
            SecurityContextHolder.setContext(previous);
            if (previousDelegation == null) {
                request.removeAttribute(AiDelegatedCallContext.class.getName());
            } else {
                request.setAttribute(AiDelegatedCallContext.class.getName(), previousDelegation);
            }
        }
    }

    /** 对认证侧错误统一脱敏，禁止把含凭据的 Feign 请求或响应附入异常。 */
    private AiUserDelegationRespDTO authenticate(String serviceToken, String delegation, String mode) {
        if (businessSystem.trim().isEmpty() || environment.trim().isEmpty()
                || delegation == null || delegation.length() > 16384 || !delegation.startsWith("mcp_jwt_")) {
            throw new IllegalStateException("AI 委托入口未配置或凭据无效");
        }
        AiUserDelegationRespDTO result;
        try {
            result = checkIdentity(serviceToken, delegation, mode);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("当前无法验证 AI 委托身份");
        }
        if (result == null || result.getUser() == null || result.getUser().getUserId() == null
                || result.getUser().getUserId() <= 0 || result.getCaller() == null
                || result.getCaller().getBinding() == null
                || !Objects.equals(result.getUser().getTenantId(), result.getCaller().getBinding().getTenantId())
                || !businessSystem.equals(result.getCaller().getBinding().getBusinessSystem())
                || !environment.equals(result.getCaller().getBinding().getEnvironment())) {
            throw new IllegalStateException("AI 委托身份与当前部署不匹配");
        }
        return result;
    }

    /** 本地验证委托并通过通用 inspect 复核真实会话，无旧认证回退。 */
    private AiUserDelegationRespDTO checkIdentity(String serviceToken, String delegation, String mode) {
        return authenticator.getObject().validate(serviceToken, delegation, mode);
    }
}
