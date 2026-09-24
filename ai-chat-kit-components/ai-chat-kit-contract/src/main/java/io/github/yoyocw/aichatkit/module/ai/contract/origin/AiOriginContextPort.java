package io.github.yoyocw.aichatkit.module.ai.contract.origin;

/** 宿主同步准备阶段的可信委托来源读取边界，不暴露 Servlet 或认证平台 DTO。 */
public interface AiOriginContextPort {
    /**
     * 获取本轮来源并核对实际应用授权。
     * @param appId 实际调用应用，由发送流程确定
     * @return 不可变来源；普通入口或记录关闭时返回 null
     * @throws IllegalStateException 记录开启但委托来源无效或应用未授权
     */
    AiCallerOrigin capture(String appId);
}
