package io.github.yoyocw.aichatkit.testnative.module.system.api.permission;

import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.permission.PermissionCommonApi;
import io.github.yoyocw.aichatkit.testnative.framework.common.pojo.CommonResult;

/** Test-only concrete native permission target. */
public class PermissionApiImpl implements PermissionCommonApi {
    private CommonResult<Boolean> permissions = CommonResult.success(false);
    private CommonResult<Boolean> roles = CommonResult.success(false);
    public void setPermissions(CommonResult<Boolean> value) { permissions = value; }
    public void setRoles(CommonResult<Boolean> value) { roles = value; }
    @Override public CommonResult<Boolean> hasAnyPermissions(Long userId, String... requested) {
        return permissions;
    }
    @Override public CommonResult<Boolean> hasAnyRoles(Long userId, String... requested) {
        return roles;
    }
}
