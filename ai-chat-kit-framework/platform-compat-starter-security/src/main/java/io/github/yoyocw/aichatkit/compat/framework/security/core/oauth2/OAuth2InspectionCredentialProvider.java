package io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2;

/** 宿主提供受限会话核验的服务消费者凭据；不接受外部请求传入的身份。 */
@FunctionalInterface
public interface OAuth2InspectionCredentialProvider {
    /**
     * 获取本次调用可用的服务访问令牌，由宿主管理签发、刷新及存储。
     * @return 不带 Bearer 前缀的令牌；不得返回主体用户令牌
     * @throws IllegalStateException 当前无有效服务凭据；调用方拒绝请求且不回退
     */
    String getAccessToken();
}
