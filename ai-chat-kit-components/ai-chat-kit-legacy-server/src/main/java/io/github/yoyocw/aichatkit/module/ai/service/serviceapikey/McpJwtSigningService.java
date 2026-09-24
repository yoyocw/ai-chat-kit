package io.github.yoyocw.aichatkit.module.ai.service.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.util.servlet.ServletUtils;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.McpJwtPublicKeyRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import javax.servlet.http.HttpServletRequest;

/** 保留 AI 密钥初始化地址，由 AI 自有服务执行真实平台管理员校验和原子首次初始化。 */
@Service
@RequiredArgsConstructor
public class McpJwtSigningService {
    /** 未配置初始化功能时按需拒绝；禁止回退 system 专用接口。 */
    private final ObjectProvider<AiManagedSigningKeyService> keyService;

    /**
     * 交由 AI 本地初始化服务处理，管理员资格仍由通用受限认证接口实时裁决。
     * @return 公开标识和公钥，不返回私钥
     * @throws IllegalStateException 凭据缺失、无权限、已有密钥或认证侧不可用
     */
    public McpJwtPublicKeyRespVO initialize() {
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null || request.getHeader("Authorization") == null) {
            throw new IllegalStateException("缺少密钥管理的真实用户凭据");
        }
        try {
            McpJwtPublicKeyRespVO result = keyService.getObject().initialize(request.getHeader("Authorization"));
            if (result == null) {
                throw new IllegalStateException("AI 未返回密钥初始化结果");
            }
            return result;
        } catch (RuntimeException ex) {
            // 不传播认证 HTTP、秘密配置或数据库异常，保持旧公开入口的脱敏错误边界。
            throw new IllegalStateException("密钥初始化未完成，请确认平台管理权限、密钥是否已存在及认证服务状态");
        }
    }
}
