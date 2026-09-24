package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure;

import org.springframework.beans.factory.ListableBeanFactory;

/** 安全适配器不通过 Primary 或候选顺序猜测权威身份源。 */
final class AiMcpV1Beans {
    private AiMcpV1Beans() { }
    /** @return 唯一显式适配实现；缺失、多实现均启动失败，不回显实例或配置 */
    static <T> T unique(ListableBeanFactory factory, Class<T> type) {
        String[] names = factory.getBeanNamesForType(type, true, false);
        if (names.length != 1) { throw new IllegalStateException("MCP 必需适配器缺失或不唯一：" + type.getSimpleName()); }
        return factory.getBean(names[0], type);
    }
}
