package io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 本次真实会话和数据库权限复核结果，不包含原 JWT、密码或完整若依用户对象。 */
public final class RuoyiVerifiedSession {
    /** 含真实会话关联和保守 UTC 毫秒截止，不提供新的登录能力。 */
    private final AiHostSession session;
    /** 当前实际用户及角色查得的菜单权限副本。 */
    private final Set<String> permissions;
    /** 由已复核活动用户按若依原 ID 规则判断的管理员身份。 */
    private final boolean administrator;

    /** @param session 当前真实会话 @param permissions 当前权限 @param administrator 原宿主判断结果 */
    public RuoyiVerifiedSession(AiHostSession session, Set<String> permissions, boolean administrator) {
        this.session = session;
        this.permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
        this.administrator = administrator;
    }

    /** @return 仅含关联信息的会话快照 */
    public AiHostSession getSession() { return session; }
    /** @return 当前数据库权限的不可变副本 */
    public Set<String> getPermissions() { return permissions; }
    /** @return 当前活动用户是否符合若依管理员规则 */
    public boolean isAdministrator() { return administrator; }
}
