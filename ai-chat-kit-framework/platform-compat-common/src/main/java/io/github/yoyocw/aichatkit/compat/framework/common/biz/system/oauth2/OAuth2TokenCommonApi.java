package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2;

import io.github.yoyocw.aichatkit.compat.framework.common.enums.RpcConstants;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@FeignClient(name = RpcConstants.SYSTEM_NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - OAuth2.0 令牌")
public interface OAuth2TokenCommonApi {

    String PREFIX = RpcConstants.SYSTEM_PREFIX + "/oauth2/token";

    /**
     * 校验 Token 的 URL 地址，主要是提供给 Gateway 使用
     */
    @SuppressWarnings("HttpUrlsUsage")
    String URL_CHECK = "http://" + RpcConstants.SYSTEM_NAME + PREFIX + "/check";

    @PostMapping(PREFIX + "/create")
    @Operation(summary = "创建访问令牌")
    CommonResult<OAuth2AccessTokenRespDTO> createAccessToken(@Valid @RequestBody OAuth2AccessTokenCreateReqDTO reqDTO);

    @GetMapping(PREFIX + "/check")
    @Operation(summary = "校验访问令牌")
    @Parameter(name = "accessToken", description = "访问令牌", required = true, example = "tudou")
    CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessToken(@RequestParam("accessToken") String accessToken);

    /**
     * 按原访问令牌记录编号复核会话、后台账号及租户当前状态。
     * 编号仅为内部关联条件，不能代替登录凭据。
     *
     * @param accessTokenId 正整数访问令牌记录编号
     * @return 不含原始凭据的当前会话身份和期限
     */
    @GetMapping(PREFIX + "/check-session")
    CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessTokenSession(
            @RequestParam("accessTokenId") Long accessTokenId);

    @DeleteMapping(PREFIX + "/remove")
    @Operation(summary = "移除访问令牌")
    @Parameter(name = "accessToken", description = "访问令牌", required = true, example = "tudou")
    CommonResult<OAuth2AccessTokenRespDTO> removeAccessToken(@RequestParam("accessToken") String accessToken);

    @PutMapping(PREFIX + "/refresh")
    @Operation(summary = "刷新访问令牌")
    @Parameters({
        @Parameter(name = "refreshToken", description = "刷新令牌", required = true, example = "haha"),
        @Parameter(name = "clientId", description = "客户端编号", required = true, example = "aichatkityuanma")
    })
    CommonResult<OAuth2AccessTokenRespDTO> refreshAccessToken(@RequestParam("refreshToken") String refreshToken,
                                                              @RequestParam("clientId") String clientId);

    @GetMapping(PREFIX + "/online-user-count")
    @Operation(summary = "获取在线用户数量（持有未过期 access_token 的去重用户数）")
    @Parameter(name = "userType", description = "用户类型，可选；不传则统计全部", example = "1")
    CommonResult<Long> getOnlineUserCount(@RequestParam(value = "userType", required = false) Integer userType);

}
