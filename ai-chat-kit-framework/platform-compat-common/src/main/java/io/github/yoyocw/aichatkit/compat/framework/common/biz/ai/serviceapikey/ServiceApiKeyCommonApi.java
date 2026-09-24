package io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.RpcConstants;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;

/**
 * MCP 服务密钥内部鉴权 API。
 *
 * <p>供 forest、woodland 等业务微服务向 AI 服务验证密钥摘要并获取最小服务身份，
 * 该接口仅允许在可信服务网络内调用，禁止由外部网关路由。</p>
 */
@FeignClient(name = RpcConstants.AI_NAME, primary = false)
@Tag(name = "RPC 服务 - MCP 服务密钥鉴权")
public interface ServiceApiKeyCommonApi {

    /** AI 服务密钥内部接口前缀。 */
    String PREFIX = RpcConstants.AI_PREFIX + "/service-api-key";

    /**
     * 根据完整 MCP 密钥摘要认证服务调用方。
     *
     * @param request 密钥摘要请求，摘要必须为 64 位小写十六进制字符串
     * @return 认证成功时返回绑定租户、服务用户、分页上限和允许创建人；无效凭据返回空 data
     */
    @PostMapping(PREFIX + "/authenticate")
    @Operation(summary = "认证 MCP 服务密钥摘要")
    CommonResult<ServiceApiKeyAuthRespDTO> authenticate(@Valid @RequestBody ServiceApiKeyAuthReqDTO request);
}
