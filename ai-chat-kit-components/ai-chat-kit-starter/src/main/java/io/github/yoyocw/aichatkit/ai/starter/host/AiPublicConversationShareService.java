package io.github.yoyocw.aichatkit.ai.starter.host;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedConversation;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 公开分享读取能力；不要求访问者登录或冒充创建者，不提供创建、撤销和任意归属查询。
 * 引擎按部署固定命名空间、模式和不可预测分享码限制范围，数据库每次复核有效性。
 * 仅返回专用公开视图，不返回内部消息编号、模型会话、身份、请求编号或认证凭据。
 * 本类不开放 HTTP；宿主公开路由须设置 Cache-Control: no-store、X-Robots-Tag: noindex、
 * Referrer-Policy: no-referrer，且不记录分享码，防止撤销前内容被缓存及地址泄漏。
 */
public final class AiPublicConversationShareService {
    /** 与原公开分享协议保持一致的32位小写十六进制格式；格式正确不代表有权读取。 */
    private static final Pattern SHARE_CODE = Pattern.compile("[0-9a-f]{32}");
    /** 使用固定部署配置及真实分享记录的引擎服务，不从访客上下文取得归属。 */
    private final AiConversationShareService shareService;

    /** @param shareService 容器管理的分享引擎 @throws NullPointerException 服务缺失 */
    public AiPublicConversationShareService(AiConversationShareService shareService) {
        this.shareService = Objects.requireNonNull(shareService, "会话分享引擎不能为空");
    }

    /**
     * 动态读取当前已完成消息；分享撤销、到期或父会话删除时必须拒绝。
     * @param mode SINGLE 或 GROUP，限定对应分享模式
     * @param shareCode 持有者提供的不可预测分享码，不接受用户编号或租户作为替代条件
     * @return 专用公开字段，不能当作完整内部消息视图
     * @throws IllegalArgumentException 模式或分享码格式无效，消息不回显分享码
     * @throws IllegalStateException 分享不可用或真实存储查询失败
     */
    public AiSharedConversation read(AiChatMode mode, String shareCode) {
        if ((mode != AiChatMode.SINGLE && mode != AiChatMode.GROUP)
                || shareCode == null || !SHARE_CODE.matcher(shareCode).matches()) {
            throw new IllegalArgumentException("分享访问参数无效");
        }
        return shareService.readPublic(mode, shareCode);
    }
}
