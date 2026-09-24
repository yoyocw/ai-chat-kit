package io.github.yoyocw.aichatkit.testnative.module.system.api.oauth2;

import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import io.github.yoyocw.aichatkit.testnative.framework.common.pojo.CommonResult;

/** Test-only concrete native target with injectable behavior. */
public class OAuth2TokenApiImpl implements OAuth2TokenCommonApi {
    private CommonResult<OAuth2AccessTokenCheckRespDTO> raw;
    private CommonResult<OAuth2AccessTokenCheckRespDTO> session;
    private String lastRaw;
    private Long lastSessionId;
    private int rawCalls;
    private int sessionCalls;

    public void setRaw(CommonResult<OAuth2AccessTokenCheckRespDTO> value) { raw = value; }
    public void setSession(CommonResult<OAuth2AccessTokenCheckRespDTO> value) { session = value; }
    public String getLastRaw() { return lastRaw; }
    public Long getLastSessionId() { return lastSessionId; }
    public int getRawCalls() { return rawCalls; }
    public int getSessionCalls() { return sessionCalls; }
    @Override public CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessToken(String token) {
        rawCalls++;
        lastRaw = token;
        return raw;
    }
    @Override public CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessTokenSession(Long accessTokenId) {
        sessionCalls++;
        lastSessionId = accessTokenId;
        return session;
    }
}
