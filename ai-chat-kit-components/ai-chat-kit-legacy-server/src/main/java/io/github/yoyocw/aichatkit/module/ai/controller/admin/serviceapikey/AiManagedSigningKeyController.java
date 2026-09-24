package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.McpJwtPublicKeyRespVO;
import io.github.yoyocw.aichatkit.module.ai.service.serviceapikey.AiManagedSigningKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/** AI 自有密钥初始化候选入口；不替换旧地址，服务层复核真实平台管理员资格。 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.key-management", name = "enabled", havingValue = "true")
@RequestMapping("/ai/key-management")
public class AiManagedSigningKeyController {
    /** 仅在可信管理员核验后生成并持久化密钥，控制器不操作 Mapper。 */
    private final AiManagedSigningKeyService service;

    /**
     * 首次初始化当前部署配置对应的密钥，不接收私钥或任意身份参数。
     * @param authorization 真实管理员访问令牌，仅交受限认证接口验证
     * @param response 禁止缓存密钥管理响应
     * @return 公开标识及公钥，不包含私钥
     */
    @PostMapping("/initialize")
    public CommonResult<McpJwtPublicKeyRespVO> initialize(
            @RequestHeader("Authorization") String authorization, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(service.initialize(authorization));
    }
}
