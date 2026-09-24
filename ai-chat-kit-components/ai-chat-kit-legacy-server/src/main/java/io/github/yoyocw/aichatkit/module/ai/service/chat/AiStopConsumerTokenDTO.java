package io.github.yoyocw.aichatkit.module.ai.service.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** OAuth 响应的最小凭据字段；不接收刷新令牌，不生成含秘密的 toString。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiStopConsumerTokenDTO {
    /** 本次独立消费者访问令牌，仅保留于进程内。 */
    @JsonProperty("access_token")
    private String accessToken;
    /** 必须是 bearer（忽略大小写）。 */
    @JsonProperty("token_type")
    private String tokenType;
    /** 服务器报告的剩余有效秒数，需扣除申请耗时及安全余量。 */
    @JsonProperty("expires_in")
    private Long expiresIn;
    /** 必须精确为本 Provider 请求的 ai.stop.consume。 */
    private String scope;
}
