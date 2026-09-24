package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** JWT 初始化公开结果；禁止加入私钥字段，公钥供业务服务部署验签。 */
@Data
@AllArgsConstructor
public class McpJwtPublicKeyRespVO {
    /** 签发方标识，与 AI 服务配置一致。 */
    private String issuer;
    /** 目标服务标识，与业务服务配置一致。 */
    private String audience;
    /** X.509 PEM 格式 RSA 公钥，不包含任何私钥材料。 */
    private String publicKey;
}
