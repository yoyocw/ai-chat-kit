package io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.permission;

import io.github.yoyocw.aichatkit.testnative.framework.common.pojo.CommonResult;

/** Test-only native permission API. */
public interface PermissionCommonApi {
    CommonResult<Boolean> hasAnyPermissions(Long userId, String... permissions);
    CommonResult<Boolean> hasAnyRoles(Long userId, String... roles);
}
