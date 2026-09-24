package io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import java.util.Objects;
import java.util.Set;

/** 复读若依原 Redis 会话及数据库用户权限，不续期、不替换会话、不保存认证上下文。 */
@RequiredArgsConstructor
public final class RuoyiSessionReader {
    /** 来自 starter 的固定命名空间。 */
    private final String namespace;
    /** 显式部署单租户隔离域，不由请求决定。 */
    private final String tenantId;
    /** 宿主原 Redis 会话存储，禁止创建替代连接或缓存后备。 */
    private final RedisCache redis;
    /** 宿主原用户服务，每次取得真实状态和当前角色关系。 */
    private final ISysUserService users;
    /** 宿主原菜单权限查询，不使用 LoginUser 缓存 permissions。 */
    private final SysPermissionService permissions;

    /**
     * @param authenticated 已由原 TokenService 验证并与当前 principal 匹配的登录会话
     * @param principalExpiry 当前已认证 principal 的原始截止毫秒，不追溯延長
     * @return 本次真实会话和权限，期限不超过传入快照及两次 Redis 读取期限
     * @throws AiIdentityException 会话过期、账号拒绝、明确依赖故障或未知核验失败
     */
    public RuoyiVerifiedSession read(LoginUser authenticated, long principalExpiry) {
        try {
            validateSeed(authenticated);
            AiHostSession first = readLive(authenticated, Math.min(authenticated.getExpireTime(), principalExpiry));
            SysUser user = users.selectUserById(authenticated.getUserId());
            if (user == null) { throw new AiIdentityException(AiIdentityError.FORBIDDEN); }
            if (!Objects.equals(user.getUserId(), authenticated.getUserId())) { throw failed(); }
            if (user.getStatus() == null || user.getDelFlag() == null) { throw failed(); }
            if (!"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) {
                throw new AiIdentityException(AiIdentityError.FORBIDDEN);
            }
            // 传入刚查询的角色关系，避免以登录时缓存角色查询已撤销权限。
            Set<String> actualPermissions = permissions.getMenuPermission(user);
            if (actualPermissions == null) { throw failed(); }
            boolean administrator = SecurityUtils.isAdmin(user.getUserId());
            // 用户或菜单源调用期间可能退出、到期；返回前重新检查原会话且不延长期限。
            AiHostSession current = readLive(authenticated, first.getExpiresAtMillis());
            return new RuoyiVerifiedSession(current, actualPermissions, administrator);
        } catch (AiIdentityException ex) {
            throw new AiIdentityException(ex.getError());
        } catch (DataAccessResourceFailureException | QueryTimeoutException ex) {
            throw new AiIdentityException(AiIdentityError.DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException ex) {
            // 类型错误、非法数据和未知业务异常不等于网络故障，也不回显原异常。
            throw failed();
        }
    }

    /** 原 UUID 只用来复查已认证关联；不接受它建立新的登录身份。 */
    private AiHostSession readLive(LoginUser expected, long originalExpiry) {
        long started = System.currentTimeMillis();
        String key = CacheConstants.LOGIN_TOKEN_KEY + expected.getToken();
        LoginUser current = redis.getCacheObject(key);
        if (current == null) { throw expired(); }
        if (!Objects.equals(expected.getUserId(), current.getUserId())
                || !Objects.equals(expected.getToken(), current.getToken())) {
            throw new AiIdentityException(AiIdentityError.FORBIDDEN);
        }
        if (current.getExpireTime() == null) { throw failed(); }
        long ttlSeconds = redis.getExpire(key);
        if (ttlSeconds == -2 || ttlSeconds == 0 || current.getExpireTime() <= System.currentTimeMillis()) { throw expired(); }
        if (ttlSeconds < 0 || ttlSeconds > (Long.MAX_VALUE - started) / 1000) { throw failed(); }
        long expiry = Math.min(originalExpiry, Math.min(current.getExpireTime(), started + ttlSeconds * 1000));
        if (expiry <= System.currentTimeMillis()) { throw expired(); }
        return new AiHostSession(namespace, tenantId, expected.getUserId().toString(), expected.getToken(), expiry);
    }

    /** 校验宿主已认证快照的必要字段；空字段不作为可猜测的登录过期信号。 */
    private void validateSeed(LoginUser user) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0 || user.getToken() == null
                || !user.getToken().matches("[A-Za-z0-9_-]{1,128}") || user.getExpireTime() == null) { throw failed(); }
        if (user.getExpireTime() <= System.currentTimeMillis()) { throw expired(); }
    }

    /** @return 已确证真实会话缺失或到期 */
    private static AiIdentityException expired() { return new AiIdentityException(AiIdentityError.UNAUTHENTICATED); }
    /** @return 不能据原异常猜测身份状态的安全失败 */
    private static AiIdentityException failed() { return new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }
}
