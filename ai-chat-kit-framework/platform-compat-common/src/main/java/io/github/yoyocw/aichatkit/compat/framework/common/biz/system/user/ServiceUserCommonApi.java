package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.user;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.user.dto.ServiceUserRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.RpcConstants;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 服务身份用户公共 API。
 *
 * <p>适用于 API Key 等机器身份认证场景，只提供构造受限登录上下文所需的最小用户信息。</p>
 */
@FeignClient(name = RpcConstants.SYSTEM_NAME, primary = false)
@Tag(name = "RPC 服务 - 服务身份用户")
public interface ServiceUserCommonApi {

    String PREFIX = RpcConstants.SYSTEM_PREFIX + "/service-user";

    /**
     * 查询服务身份绑定的后台用户。
     *
     * @param userId 后台用户主键编号，必须为正数
     * @return 用户不存在时 data 为 null；存在时返回用户、租户、状态和部门信息
     */
    @GetMapping(PREFIX + "/get")
    @Operation(summary = "查询服务身份绑定的后台用户")
    @Parameter(name = "userId", description = "后台用户主键编号", required = true, example = "1")
    CommonResult<ServiceUserRespDTO> getServiceUser(@RequestParam("userId") Long userId);

}
