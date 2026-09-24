package io.github.yoyocw.aichatkit.module.ai.controller.admin.mcpkey;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyCreateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyIssuedRespVO;
import io.github.yoyocw.aichatkit.module.ai.service.serviceapikey.ServiceApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import static io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult.success;

/**
 * MCP 服务密钥签发接口，仅供超级管理员创建第三方调用方并生成一次性服务凭据。
 */
@Tag(name = "AI MCP 服务密钥")
@RestController
@RequestMapping("/ai/mcp-key")
@RequiredArgsConstructor
public class McpKeyController {

    /** MCP 第三方服务密钥服务，负责配置校验、密钥生成和数据库写入。 */
    private final ServiceApiKeyService serviceApiKeyService;

    /**
     * 创建第三方调用方，自动生成 MCP 原始密钥和 SHA-256 摘要并写入数据库。
     *
     * @param request 调用方编码、名称、服务账号、端点权限、分页上限和可选有效期
     * @param response HTTP 响应，用于禁止浏览器、代理和中间缓存保存原始密钥
     * @return 调用方编号、原始服务密钥及其 SHA-256 摘要；原始密钥只在本次响应中展示
     */
    @PostMapping("/generate")
    @Operation(summary = "创建 MCP 调用方并生成密钥",
            description = "自动绑定当前租户、默认启用并持久化密钥摘要；原始密钥只在本次响应中展示")
    @PreAuthorize("@ss.hasRole('super_admin')")
    public CommonResult<ServiceApiKeyIssuedRespVO> generateKey(
            @Valid @RequestBody ServiceApiKeyCreateReqVO request, HttpServletResponse response) {
        // 密钥属于高敏感响应，明确禁止浏览器、代理服务器和其它中间节点缓存。
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        // 复用数据库调用方创建服务，保证 generate 与管理端 create 的校验、转换和入库行为一致。
        return success(serviceApiKeyService.create(request));
    }
}
