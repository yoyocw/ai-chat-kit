package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission.dto.UserDataPermissionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.RpcConstants;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = RpcConstants.SYSTEM_NAME, primary = false) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 权限")
public interface PermissionCommonApi {

    String PREFIX = RpcConstants.SYSTEM_PREFIX + "/permission";

    @GetMapping(PREFIX + "/has-any-permissions")
    @Operation(summary = "判断是否有权限，任一一个即可")
    @Parameters({
            @Parameter(name = "userId", description = "用户编号", example = "1", required = true),
            @Parameter(name = "permissions", description = "权限", example = "read,write", required = true)
    })
    CommonResult<Boolean> hasAnyPermissions(@RequestParam("userId") Long userId,
                                            @RequestParam("permissions") String... permissions);

    @GetMapping(PREFIX + "/has-any-roles")
    @Operation(summary = "判断是否有角色，任一一个即可")
    @Parameters({
            @Parameter(name = "userId", description = "用户编号", example = "1", required = true),
            @Parameter(name = "roles", description = "角色数组", example = "2", required = true)
    })
    CommonResult<Boolean> hasAnyRoles(@RequestParam("userId") Long userId,
                                      @RequestParam("roles") String... roles);

    @GetMapping(PREFIX + "/get-dept-data-permission")
    @Operation(summary = "获得登陆用户的部门数据权限")
    @Parameter(name = "userId", description = "用户编号", example = "2", required = true)
    CommonResult<DeptDataPermissionRespDTO> getDeptDataPermission(@RequestParam("userId") Long userId);

    /**
     * 将用户的角色部门数据范围展开为允许访问数据的用户编号集合。
     *
     * @param userId 用户主键编号，必须为正数
     * @return 全部数据标识和允许的用户编号集合
     */
    @GetMapping(PREFIX + "/get-user-data-permission")
    @Operation(summary = "获得用户维度的数据权限")
    @Parameter(name = "userId", description = "用户编号", example = "1", required = true)
    CommonResult<UserDataPermissionRespDTO> getUserDataPermission(@RequestParam("userId") Long userId);

}
