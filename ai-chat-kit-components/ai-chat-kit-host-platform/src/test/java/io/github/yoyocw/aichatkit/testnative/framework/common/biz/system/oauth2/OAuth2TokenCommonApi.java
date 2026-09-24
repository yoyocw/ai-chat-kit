package io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2;

import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import io.github.yoyocw.aichatkit.testnative.framework.common.pojo.CommonResult;

/** Test-only native API; deliberately separate from relocated component compatibility types. */
public interface OAuth2TokenCommonApi {
    CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessToken(String token);
    CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessTokenSession(Long accessTokenId);
}
