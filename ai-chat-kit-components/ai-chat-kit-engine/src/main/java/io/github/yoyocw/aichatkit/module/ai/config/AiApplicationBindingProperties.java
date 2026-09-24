package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 单个部署应用绑定；只保存公开标识，不保存工具令牌或平台API Key。 */
@Data
public class AiApplicationBindingProperties {
    /** 已发布应用ID；使用对应模式时必须配置。 */
    private String appId;
    /** 可选MCP服务ID；群聊目前不支持。 */
    private String mcpId;
    /** 用户级插件工具ID列表；默认空，最多10个且不可重复。 */
    private List<String> userAuthToolIds = new ArrayList<String>();
}
