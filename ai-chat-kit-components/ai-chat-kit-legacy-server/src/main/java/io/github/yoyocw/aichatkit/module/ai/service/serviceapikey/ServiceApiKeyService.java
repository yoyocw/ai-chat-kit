package io.github.yoyocw.aichatkit.module.ai.service.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.PageResult;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyCreateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyIssuedRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyPageReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyRespVO;

import java.util.Set;

/**
 * MCP 第三方服务密钥管理与鉴权服务。
 *
 * <p>适用于百炼、DeepSeek 等机器调用方的独立凭据签发、轮换、吊销和认证。</p>
 */
public interface ServiceApiKeyService {

    /**
     * 为当前租户创建第三方调用方并一次性签发原始密钥。
     *
     * @param request 调用方、服务账号、端点权限、分页上限和有效期配置
     * @return 调用方编号、一次性原始密钥和摘要
     */
    ServiceApiKeyIssuedRespVO create(ServiceApiKeyCreateReqVO request);

    /**
     * 轮换当前租户调用方密钥，原当前摘要进入上一摘要槽位。
     *
     * @param id 调用方主键编号
     * @return 新原始密钥和摘要，只在本次调用返回
     */
    ServiceApiKeyIssuedRespVO rotate(Long id);

    /**
     * 清除上一密钥摘要，使轮换前密钥立即失效。
     *
     * @param id 调用方主键编号
     */
    void clearPreviousKey(Long id);

    /**
     * 修改当前租户调用方启停状态。
     *
     * @param id 调用方主键编号
     * @param status 目标状态，0 启用、1 停用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 整体替换当前租户调用方的 MCP 端点授权。
     *
     * @param id 调用方主键编号
     * @param endpointCodes 非空、已去重的稳定端点编码集合
     */
    void updateEndpoints(Long id, Set<String> endpointCodes);

    /**
     * 查询当前租户调用方详情，不返回任何密钥摘要。
     *
     * @param id 调用方主键编号
     * @return 调用方详情
     */
    ServiceApiKeyRespVO get(Long id);

    /**
     * 分页查询当前租户调用方，不返回任何密钥摘要。
     *
     * @param request 分页筛选参数
     * @return 调用方分页结果
     */
    PageResult<ServiceApiKeyRespVO> page(ServiceApiKeyPageReqVO request);

    /**
     * 根据摘要认证调用方并展开绑定服务账号的数据范围。
     *
     * @param keySha256 完整 MCP 原始密钥的 SHA-256 摘要
     * @return 有效时返回最小服务身份，无效凭据返回 null；下游服务故障时抛出异常并失败关闭
     */
    ServiceApiKeyAuthRespDTO authenticate(String keySha256);
}
