package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.PageResult;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyCreateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyEndpointUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyIssuedRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyPageReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyStatusReqVO;
import io.github.yoyocw.aichatkit.module.ai.service.serviceapikey.ServiceApiKeyService;
import io.github.yoyocw.aichatkit.module.ai.service.serviceapikey.McpJwtSigningService;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.McpJwtPublicKeyRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import static io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult.success;

/**
 * MCP 第三方服务密钥管理接口，仅允许超级管理员配置当前租户的机器调用方。
 */
@Tag(name = "AI MCP 第三方服务密钥")
@RestController
@RequestMapping("/ai/service-api-key")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasRole('super_admin')")
public class ServiceApiKeyController {

    /** MCP 服务密钥管理服务。 */
    private final ServiceApiKeyService serviceApiKeyService;
    /** 部署级 JWT 密钥初始化服务，原始私钥不经过 HTTP 返回。 */
    private final McpJwtSigningService jwtSigningService;

    /**
     * 初始化当前部署的 RSA 密钥对，仅允许超级管理员操作。
     * @param response 用于禁止缓存管理响应
     * @return 公开的 issuer、audience 和公钥
     * @throws IllegalStateException 已存在密钥或部署配置无效
     */
    @PostMapping("/jwt/initialize")
    @Operation(summary = "首次生成并保存 MCP JWT RSA 密钥对")
    public CommonResult<McpJwtPublicKeyRespVO> initializeJwt(HttpServletResponse response) {
        disableSensitiveResponseCache(response);
        return success(jwtSigningService.initialize());
    }

    /**
     * 创建调用方并一次性返回原始密钥。
     *
     * @param request 调用方配置
     * @param response HTTP 响应，用于禁止缓存高敏感原始密钥
     * @return 一次性签发结果
     */
    @PostMapping("/create")
    @Operation(summary = "创建 MCP 第三方调用方并签发密钥")
    public CommonResult<ServiceApiKeyIssuedRespVO> create(@Valid @RequestBody ServiceApiKeyCreateReqVO request,
                                                           HttpServletResponse response) {
        disableSensitiveResponseCache(response);
        return success(serviceApiKeyService.create(request));
    }

    /**
     * 轮换调用方密钥并一次性返回新原始密钥。
     *
     * @param id 调用方编号
     * @param response HTTP 响应，用于禁止缓存高敏感原始密钥
     * @return 一次性签发结果
     */
    @PostMapping("/rotate")
    @Operation(summary = "轮换 MCP 调用方密钥")
    @Parameter(name = "id", description = "调用方编号", required = true)
    public CommonResult<ServiceApiKeyIssuedRespVO> rotate(@RequestParam("id") Long id,
                                                           HttpServletResponse response) {
        disableSensitiveResponseCache(response);
        return success(serviceApiKeyService.rotate(id));
    }

    /**
     * 清除轮换前密钥，使旧密钥立即失效。
     *
     * @param id 调用方编号
     * @return 操作成功返回 true
     */
    @PostMapping("/clear-previous")
    @Operation(summary = "清除 MCP 调用方上一密钥")
    public CommonResult<Boolean> clearPrevious(@RequestParam("id") Long id) {
        serviceApiKeyService.clearPreviousKey(id);
        return success(true);
    }

    /**
     * 修改调用方启停状态；停用后当前和上一密钥同时失效。
     *
     * @param request 调用方编号和目标状态
     * @return 操作成功返回 true
     */
    @PutMapping("/update-status")
    @Operation(summary = "修改 MCP 调用方状态")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody ServiceApiKeyStatusReqVO request) {
        serviceApiKeyService.updateStatus(request.getId(), request.getStatus());
        return success(true);
    }

    /**
     * 整体替换调用方的 MCP 只读端点权限。
     *
     * @param request 调用方编号和替换后的稳定端点编码集合
     * @return 操作成功返回 true
     */
    @PutMapping("/update-endpoints")
    @Operation(summary = "更新 MCP 调用方端点权限")
    public CommonResult<Boolean> updateEndpoints(@Valid @RequestBody ServiceApiKeyEndpointUpdateReqVO request) {
        serviceApiKeyService.updateEndpoints(request.getId(), request.getEndpointCodes());
        return success(true);
    }

    /**
     * 查询当前租户调用方详情，不返回密钥摘要。
     *
     * @param id 调用方编号
     * @return 调用方详情
     */
    @GetMapping("/get")
    @Operation(summary = "查询 MCP 调用方详情")
    public CommonResult<ServiceApiKeyRespVO> get(@RequestParam("id") Long id) {
        return success(serviceApiKeyService.get(id));
    }

    /**
     * 分页查询当前租户调用方，不返回密钥摘要。
     *
     * @param request 分页筛选参数
     * @return 调用方分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询 MCP 调用方")
    public CommonResult<PageResult<ServiceApiKeyRespVO>> page(@Valid ServiceApiKeyPageReqVO request) {
        return success(serviceApiKeyService.page(request));
    }

    /** 禁止浏览器、代理和其它中间节点缓存包含原始密钥的响应。 */
    private void disableSensitiveResponseCache(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
