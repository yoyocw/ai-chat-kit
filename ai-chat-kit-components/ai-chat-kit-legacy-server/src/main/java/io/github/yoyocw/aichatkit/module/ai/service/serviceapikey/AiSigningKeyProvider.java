package io.github.yoyocw.aichatkit.module.ai.service.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.security.config.McpServiceJwtProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiLocalDelegationSigningProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiSigningKeySource;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.serviceapikey.AiManagedSigningKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** AI 本地签发的单一私钥来源；来源由部署显式选择，缺失、停用和查询失败均拒绝。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.local-delegation-signing", name = "enabled", havingValue = "true")
public class AiSigningKeyProvider {
    /** 来源和秘密配置，不接受用户请求覆盖。 */
    private final AiLocalDelegationSigningProperties properties;
    /** 已部署的签发方和接收方，不根据用户或租户选择密钥。 */
    private final McpServiceJwtProperties jwt;
    /** 仅 DATABASE 模式解析 Mapper；配置模式不查询数据库。 */
    private final ObjectProvider<AiManagedSigningKeyMapper> mapperProvider;

    /**
     * 为当前一次签名加载私钥，调用者仍必须完成公私钥匹配及协议校验。
     * @return 仅供服务器签名使用的 PKCS#8 私钥，禁止日志、HTTP 或模型输出
     * @throws IllegalStateException 未启用、来源无效、密钥不存在/停用或读取失败，不切换来源
     */
    public String getPrivateKey() {
        try {
            if (!properties.isEnabled() || properties.getSource() == null) { throw failure(); }
            String key;
            if (properties.getSource() == AiSigningKeySource.CONFIGURATION) {
                key = properties.getPrivateKey();
            } else {
                // 配置中仍有私钥时拒绝混合来源，避免误把部署密钥与数据库密钥混用。
                if (properties.getPrivateKey() != null && !properties.getPrivateKey().trim().isEmpty()) {
                    throw failure();
                }
                if (!validIdentifier(jwt.getIssuer()) || !validIdentifier(jwt.getAudience())) { throw failure(); }
                key = mapperProvider.getObject().selectEnabledPrivateKey(jwt.getIssuer(), jwt.getAudience());
            }
            if (key == null || key.trim().isEmpty() || key.length() > 32768) { throw failure(); }
            return key;
        } catch (RuntimeException ex) {
            // 不将数据库响应、秘密配置及底层异常链交给外部调用者。
            throw failure();
        }
    }

    /** @param value 部署信任标识 @return 是否满足现有表字段边界 */
    private boolean validIdentifier(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 128;
    }

    /** @return 不含源地址、配置或私钥的固定错误 */
    private IllegalStateException failure() { return new IllegalStateException("AI 签发密钥不可用，请核对部署密钥来源"); }
}
