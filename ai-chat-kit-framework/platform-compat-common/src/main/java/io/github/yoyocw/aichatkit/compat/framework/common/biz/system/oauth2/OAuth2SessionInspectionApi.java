package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.RpcConstants;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 通用受限服务会话复核合同；宿主自行配置固定可信服务地址，不隐式建立 Feign 客户端。 */
public interface OAuth2SessionInspectionApi {
    /** 固定认证服务路径，不接收请求提供的目标地址。 */
    String PATH = RpcConstants.SYSTEM_PREFIX + "/oauth2/session/inspect";

    /**
     * 先认证服务机器及查询范围，再按原始访问凭据或受约束记录 ID 复核主体。
     * @param serviceAuthorization 受信任消费者的 Bearer 机器访问凭据
     * @param request 互斥查询条件和期望身份，不允许通过参数扩大服务查询范围
     * @return 无凭据的最小身份；越权、失效或配置不完整时拒绝
     */
    @PostMapping(PATH)
    CommonResult<OAuth2SessionInspectionRespDTO> inspect(
            @RequestHeader("Authorization") String serviceAuthorization,
            @RequestBody OAuth2SessionInspectionReqDTO request);
}
