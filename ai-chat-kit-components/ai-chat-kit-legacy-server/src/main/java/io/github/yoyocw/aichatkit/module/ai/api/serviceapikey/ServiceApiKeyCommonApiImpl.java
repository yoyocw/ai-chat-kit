package io.github.yoyocw.aichatkit.module.ai.api.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.ServiceApiKeyCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.module.ai.service.serviceapikey.ServiceApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;

import static io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult.success;

/**
 * MCP 服务密钥内部鉴权 API 实现。
 *
 * <p>该入口发生在调用方登录身份建立之前，因此忽略请求租户；服务层只信任密钥记录绑定的租户。</p>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Primary
public class ServiceApiKeyCommonApiImpl implements ServiceApiKeyCommonApi {

    /** MCP 服务密钥服务，负责摘要匹配和服务身份展开。 */
    private final ServiceApiKeyService serviceApiKeyService;

    /**
     * 认证 MCP 密钥摘要。
     *
     * @param request 64 位小写十六进制摘要请求
     * @return 有效时返回最小身份，无效时返回空 data
     */
    @Override
    @PermitAll
    @TenantIgnore
    public CommonResult<ServiceApiKeyAuthRespDTO> authenticate(ServiceApiKeyAuthReqDTO request) {
        return success(serviceApiKeyService.authenticate(request.getKeySha256()));
    }
}
