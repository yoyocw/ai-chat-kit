package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceCallerRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedStopReceiver;
import io.github.yoyocw.aichatkit.module.ai.enums.AiHostedStopOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Objects;

/** AI 停止双身份复核，所有真实会话通过受限 inspect，不依赖 system 数据表或请求上下文。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.hosted-stop", name = "enabled", havingValue = "true")
public class AiHostedStopIdentityValidator {
    /** 无缓存的身份及固定权限复核。 */
    private final AiSessionInspectionClient inspector;
    /** AI 自有原业务接入绑定。 */
    private final AiInspectedDelegationService bindings;

    /** 验证本次新机器凭据和真实用户停止权限；不复用发送后已撤销的机器令牌。 */
    public AiHostedStopResult issueIdentity(String machineAuthorization, String userAuthorization,
            AiHostedStopOperation operation, AiHostedStopReceiver receiver) {
        OAuth2SessionInspectionReqDTO machineQuery = machineQuery(receiver, false);
        machineQuery.setAccessToken(raw(machineAuthorization));
        OAuth2SessionInspectionReqDTO userQuery = userQuery(receiver, operation);
        userQuery.setAccessToken(raw(userAuthorization));
        return result(inspector.inspect(userQuery), inspector.inspect(machineQuery), receiver);
    }

    /** 按票据持久化的原两会话编号和原用户再次复核，不能替换为消费者身份。 */
    public AiHostedStopResult revalidate(AiHostedStopTicket ticket, AiHostedStopOperation operation,
            AiHostedStopReceiver receiver) {
        OAuth2SessionInspectionReqDTO machineQuery = machineQuery(receiver, false);
        machineQuery.setSessionId(ticket.getOriginalMachineSessionId());
        OAuth2SessionInspectionReqDTO userQuery = userQuery(receiver, operation);
        userQuery.setSessionId(ticket.getUserSessionId());
        userQuery.setExpectedUserId(ticket.getUserId());
        return result(inspector.inspect(userQuery), inspector.inspect(machineQuery), receiver);
    }

    /** 独立消费者必须匹配接收方实体、租户、固定消费 scope 和资源，不能是原业务客户端。 */
    public void validateConsumer(String authorization, AiHostedStopReceiver receiver) {
        OAuth2SessionInspectionReqDTO query = machineQuery(receiver, true);
        query.setAccessToken(raw(authorization));
        OAuth2SessionInspectionRespDTO consumer = inspector.inspect(query);
        if (!Objects.equals(receiver.getConsumerClientId(), consumer.getClientId())) { throw failure(); }
    }

    /** 机器查询绑定部署实体，ID 查询也显式约束 userId=0。 */
    private OAuth2SessionInspectionReqDTO machineQuery(AiHostedStopReceiver receiver, boolean consumer) {
        OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
        query.setSubjectType("MACHINE");
        query.setExpectedTenantId(receiver.getTenantId());
        query.setExpectedUserId(0L);
        query.setExpectedClientRecordId(consumer ? receiver.getConsumerClientRecordId() : receiver.getOriginalClientRecordId());
        query.setRequiredScope(consumer ? "ai.stop.consume" : "ai.invoke");
        query.setRequiredResource(consumer ? receiver.getResourceId() : "platform-ai");
        return query;
    }

    /** 只允许枚举固定停止权限，绝不接受请求传入权限或发送权限替代。 */
    private OAuth2SessionInspectionReqDTO userQuery(AiHostedStopReceiver receiver, AiHostedStopOperation operation) {
        if (operation == null) { throw failure(); }
        OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
        query.setSubjectType("USER");
        query.setExpectedTenantId(receiver.getTenantId());
        query.setRequiredPermissions(Collections.singletonList(operation.getPermission()));
        return query;
    }

    /** 核对当前原 caller 的系统、环境及应用清单；期限直接取原两会话 UTC 最小值。 */
    private AiHostedStopResult result(OAuth2SessionInspectionRespDTO user, OAuth2SessionInspectionRespDTO machine,
            AiHostedStopReceiver receiver) {
        AiServiceBindingDTO binding = bindings.bindingFor(machine);
        if (!Objects.equals(machine.getClientId(), receiver.getOriginalClientId())
                || !Objects.equals(binding.getBusinessSystem(), receiver.getBusinessSystem())
                || !Objects.equals(binding.getEnvironment(), receiver.getEnvironment())
                || !Objects.equals(user.getTenantId(), machine.getTenantId())) { throw failure(); }
        long expires = Math.min(user.getExpiresAtMillis(), machine.getExpiresAtMillis());
        if (expires <= Instant.now().toEpochMilli()) { throw failure(); }
        AiServiceCallerRespDTO caller = new AiServiceCallerRespDTO();
        caller.setClientId(machine.getClientId());
        caller.setClientRecordId(machine.getClientRecordId());
        caller.setTenantId(machine.getTenantId());
        caller.setBinding(binding);
        caller.setExpiresTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(machine.getExpiresAtMillis()), ZoneId.systemDefault()));
        return new AiHostedStopResult(user.getSessionId(), machine.getSessionId(), user.getUserId(), caller, expires);
    }

    /** 只拆固定 Bearer 包装；长度、格式和真实类型由公共 client 严格验证。 */
    private String raw(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) { throw failure(); }
        return authorization.substring(7);
    }

    /** @return 不含任何令牌、票据或底层 cause 的错误 */
    private IllegalStateException failure() { return new IllegalStateException("AI 停止授权身份或权限无效"); }
}
