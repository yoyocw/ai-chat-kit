package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * 用户维度的数据权限结果。
 *
 * <p>将角色的部门数据范围展开为允许访问数据的用户编号集合，供不具备 dept_id 字段的业务表按创建人过滤。</p>
 */
@Data
public class UserDataPermissionRespDTO {

    /** 是否拥有全部数据权限；服务秘钥场景必须为 false，防止机器身份获得全量数据。 */
    private Boolean all = false;

    /** 允许访问数据的创建人用户编号集合；已包含 SELF 范围对应的当前用户。 */
    private Set<Long> userIds = new HashSet<>();

}
