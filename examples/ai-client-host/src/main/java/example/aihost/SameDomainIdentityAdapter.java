package example.aihost;

import io.github.yoyocw.aichatkit.aiclient.AiChatMode;
import io.github.yoyocw.aichatkit.aiclient.AiHostIdentity;
import io.github.yoyocw.aichatkit.aiclient.AiHostIdentityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import javax.servlet.http.HttpServletRequest;

/**
 * 同认证域 Bearer 透传样例：AI 代理通过 system 通用会话接口复核真实会话、租户和功能权限。
 * 仅适用宿主与目标共享认证域的接入；其他框架须替换适配，禁止冒充身份或使用共享账号。
 */
@Component
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.client", name = "enabled", havingValue = "true")
public class SameDomainIdentityAdapter implements AiHostIdentityProvider {
    /** @param mode 固定模式 @param stopping 是否停止 @return 本请求用户的原访问凭据，无缺省身份 */
    @Override
    public AiHostIdentity requireCurrentIdentity(AiChatMode mode, boolean stopping) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少当前用户");
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        try {
            String tenant = request.getHeader("tenant-id");
            if (tenant == null || !tenant.matches("[0-9]{1,19}")) { throw new IllegalArgumentException(); }
            return new AiHostIdentity(request.getHeader("Authorization"), Long.parseLong(tenant));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少有效用户凭据与租户");
        }
    }
}
