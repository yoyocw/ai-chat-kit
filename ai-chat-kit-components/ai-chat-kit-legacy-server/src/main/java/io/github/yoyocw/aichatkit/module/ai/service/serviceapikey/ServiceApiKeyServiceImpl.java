package io.github.yoyocw.aichatkit.module.ai.service.serviceapikey;

import cn.hutool.core.util.StrUtil;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission.PermissionCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission.dto.UserDataPermissionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.user.ServiceUserCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.user.dto.ServiceUserRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.CommonStatusEnum;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.PageResult;
import io.github.yoyocw.aichatkit.compat.framework.common.util.object.BeanUtils;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyCreateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyIssuedRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyPageReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyRespVO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.serviceapikey.ServiceApiKeyDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.serviceapikey.ServiceApiKeyEndpointDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.serviceapikey.ServiceApiKeyMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.serviceapikey.ServiceApiKeyEndpointMapper;
import io.github.yoyocw.aichatkit.module.ai.util.McpKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static io.github.yoyocw.aichatkit.compat.framework.common.util.collection.CollectionUtils.convertList;

import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.SERVICE_API_KEY_CLIENT_CODE_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.SERVICE_API_KEY_DATA_SCOPE_INVALID;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.SERVICE_API_KEY_DIGEST_CONFLICT;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.SERVICE_API_KEY_EXPIRE_TIME_INVALID;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.SERVICE_API_KEY_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.SERVICE_API_KEY_USER_INVALID;

/**
 * MCP 第三方服务密钥管理与鉴权服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceApiKeyServiceImpl implements ServiceApiKeyService {

    /** SHA-256 小写十六进制摘要格式，内部接口输入也必须执行防御性校验。 */
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    /** 服务密钥 Mapper，用于全局鉴权查询和当前租户管理。 */
    private final ServiceApiKeyMapper serviceApiKeyMapper;

    /** 调用方端点授权 Mapper，用于持久化和加载数据库侧实际访问范围。 */
    private final ServiceApiKeyEndpointMapper serviceApiKeyEndpointMapper;

    /** 服务用户公共 API，用于校验绑定账号的租户和启停状态。 */
    private final ServiceUserCommonApi serviceUserApi;

    /** 权限公共 API，用于把 B1 部门数据范围展开为允许创建人集合。 */
    private final PermissionCommonApi permissionApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceApiKeyIssuedRespVO create(ServiceApiKeyCreateReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        validateExpireTime(request.getExpireTime());
        validateServiceIdentity(tenantId, request.getServiceUserId());
        if (serviceApiKeyMapper.selectByTenantIdAndClientCode(tenantId, request.getClientCode()) != null) {
            throw exception(SERVICE_API_KEY_CLIENT_CODE_EXISTS);
        }

        // 原文只存在于当前方法局部变量和一次性响应，数据库永久只保存不可逆摘要。
        String token = McpKeyUtils.generateToken();
        String digest = McpKeyUtils.sha256Hex(token);
        validateDigestUnused(digest);
        ServiceApiKeyDO record = BeanUtils.toBean(request, ServiceApiKeyDO.class);
        record.setTenantId(tenantId);
        record.setActiveKeySha256(digest);
        record.setStatus(CommonStatusEnum.ENABLE.getStatus());
        serviceApiKeyMapper.insert(record);
        insertEndpointAuthorizations(record.getId(), tenantId, request.getEndpointCodes());
        return new ServiceApiKeyIssuedRespVO(record.getId(), token, digest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceApiKeyIssuedRespVO rotate(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ServiceApiKeyDO record = getLockedRecord(id, tenantId);
        String token = McpKeyUtils.generateToken();
        String digest = McpKeyUtils.sha256Hex(token);
        validateDigestUnused(digest);

        // 行锁保证并发轮换串行执行，上一槽位始终对应本次轮换前的当前密钥。
        ServiceApiKeyDO update = new ServiceApiKeyDO();
        update.setId(record.getId());
        update.setPreviousKeySha256(record.getActiveKeySha256());
        update.setActiveKeySha256(digest);
        serviceApiKeyMapper.updateById(update);
        return new ServiceApiKeyIssuedRespVO(record.getId(), token, digest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearPreviousKey(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ServiceApiKeyDO record = getLockedRecord(id, tenantId);
        serviceApiKeyMapper.clearPreviousKey(record.getId());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        ServiceApiKeyDO record = getTenantRecord(id);
        ServiceApiKeyDO update = new ServiceApiKeyDO();
        update.setId(record.getId());
        update.setStatus(status);
        serviceApiKeyMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEndpoints(Long id, Set<String> endpointCodes) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ServiceApiKeyDO record = getLockedRecord(id, tenantId);
        Set<String> existingEndpointCodes = loadEndpointCodes(record.getId());
        // 差量删除已取消授权，保留未变化关系的原主键和审计信息。
        Set<String> removedEndpointCodes = new LinkedHashSet<>(existingEndpointCodes);
        removedEndpointCodes.removeAll(endpointCodes);
        if (!removedEndpointCodes.isEmpty()) {
            serviceApiKeyEndpointMapper.deleteByServiceApiKeyIdAndEndpointCodes(
                    record.getId(), tenantId, removedEndpointCodes);
        }
        // 仅插入新增授权，避免重复更新时为相同端点生成新的关系记录。
        Set<String> addedEndpointCodes = new LinkedHashSet<>(endpointCodes);
        addedEndpointCodes.removeAll(existingEndpointCodes);
        if (!addedEndpointCodes.isEmpty()) {
            insertEndpointAuthorizations(record.getId(), tenantId, addedEndpointCodes);
        }
    }

    @Override
    public ServiceApiKeyRespVO get(Long id) {
        return toRespVO(getTenantRecord(id));
    }

    @Override
    public PageResult<ServiceApiKeyRespVO> page(ServiceApiKeyPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PageResult<ServiceApiKeyDO> page = serviceApiKeyMapper.selectPage(request, tenantId);
        return new PageResult<>(convertList(page.getList(), this::toRespVO), page.getTotal());
    }

    @Override
    @TenantIgnore
    public ServiceApiKeyAuthRespDTO authenticate(String keySha256) {
        if (keySha256 == null || !SHA256_PATTERN.matcher(keySha256).matches()) {
            return null;
        }
        ServiceApiKeyDO record = serviceApiKeyMapper.selectByKeySha256(keySha256);
        if (!isUsable(record)) {
            // 只输出失败阶段和记录编号，不输出密钥或摘要。
            log.warn("[authenticate][fixed-key rejected reason={} clientId={}]",
                    record == null ? "KEY_NOT_FOUND" :
                            (!CommonStatusEnum.isEnable(record.getStatus()) ? "KEY_DISABLED" : "KEY_EXPIRED"),
                    record == null ? null : record.getId());
            return null;
        }
        // 外部租户头不参与身份选择；只使用已匹配数据库记录绑定的可信租户。
        return executeInTenant(record.getTenantId(), () -> buildAuthResult(record));
    }

    /** 校验服务账号和数据范围，避免创建一条永远无法通过认证的配置。 */
    private void validateServiceIdentity(Long tenantId, Long userId) {
        executeInTenant(tenantId, () -> buildAuthResult(null, tenantId, userId));
    }

    /** 根据调用方记录加载服务用户和数据范围，组装不含摘要的鉴权结果。 */
    private ServiceApiKeyAuthRespDTO buildAuthResult(ServiceApiKeyDO record) {
        return buildAuthResult(record, record.getTenantId(), record.getServiceUserId());
    }

    /** 在指定租户上下文中校验服务身份；record 为空时仅执行配置校验。 */
    private ServiceApiKeyAuthRespDTO buildAuthResult(ServiceApiKeyDO record, Long tenantId, Long userId) {
        ServiceUserRespDTO user = serviceUserApi.getServiceUser(userId).getCheckedData();
        if (user == null || !CommonStatusEnum.isEnable(user.getStatus()) || !tenantId.equals(user.getTenantId())) {
            log.warn("[authenticate][fixed-key rejected reason=SERVICE_USER_INVALID userId={} tenantId={}]", userId, tenantId);
            throw exception(SERVICE_API_KEY_USER_INVALID);
        }
        UserDataPermissionRespDTO permission = permissionApi.getUserDataPermission(userId).getCheckedData();
        if (permission == null || Boolean.TRUE.equals(permission.getAll())
                || permission.getUserIds() == null || permission.getUserIds().isEmpty()) {
            log.warn("[authenticate][fixed-key rejected reason=DATA_SCOPE_INVALID userId={} all={} userCount={}]",
                    userId, permission == null ? null : permission.getAll(),
                    permission == null || permission.getUserIds() == null ? 0 : permission.getUserIds().size());
            throw exception(SERVICE_API_KEY_DATA_SCOPE_INVALID);
        }
        if (record == null) {
            return null;
        }
        ServiceApiKeyAuthRespDTO result = BeanUtils.toBean(record, ServiceApiKeyAuthRespDTO.class);
        result.setClientId(record.getId());
        result.setNickname(user.getNickname());
        result.setDeptId(user.getDeptId());
        result.setAllowedCreatorIds(permission.getUserIds());
        result.setAllowedEndpointCodes(loadEndpointCodes(record.getId()));
        return result;
    }

    /** 返回当前租户记录，不存在或跨租户时使用统一不存在错误。 */
    private ServiceApiKeyDO getTenantRecord(Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ServiceApiKeyDO record = serviceApiKeyMapper.selectByTenantIdAndId(tenantId, id);
        if (record == null) {
            throw exception(SERVICE_API_KEY_NOT_EXISTS);
        }
        return record;
    }

    /** 获取当前租户记录并加行锁，供轮换类写操作使用。 */
    private ServiceApiKeyDO getLockedRecord(Long id, Long tenantId) {
        ServiceApiKeyDO record = serviceApiKeyMapper.selectByIdForUpdate(id);
        if (record == null || !tenantId.equals(record.getTenantId())) {
            throw exception(SERVICE_API_KEY_NOT_EXISTS);
        }
        return record;
    }

    /** 判断调用方是否启用且未超过整体有效期。 */
    private boolean isUsable(ServiceApiKeyDO record) {
        return record != null && CommonStatusEnum.isEnable(record.getStatus())
                && (record.getExpireTime() == null || record.getExpireTime().isAfter(LocalDateTime.now()));
    }

    /** 校验新生成摘要未被任一调用方的当前或上一槽位占用。 */
    private void validateDigestUnused(String digest) {
        if (serviceApiKeyMapper.selectCountByAnyKeySha256(digest) > 0) {
            throw exception(SERVICE_API_KEY_DIGEST_CONFLICT);
        }
    }

    /** 校验可选失效时间必须位于未来。 */
    private void validateExpireTime(LocalDateTime expireTime) {
        if (expireTime != null && !expireTime.isAfter(LocalDateTime.now())) {
            throw exception(SERVICE_API_KEY_EXPIRE_TIME_INVALID);
        }
    }

    /** 转换管理响应并仅暴露是否保留上一密钥，不暴露摘要值。 */
    private ServiceApiKeyRespVO toRespVO(ServiceApiKeyDO record) {
        ServiceApiKeyRespVO response = BeanUtils.toBean(record, ServiceApiKeyRespVO.class);
        response.setPreviousKeyActive(StrUtil.isNotBlank(record.getPreviousKeySha256()));
        response.setEndpointCodes(loadEndpointCodes(record.getId()));
        return response;
    }

    /** 批量写入调用方端点授权；调用方和授权关系显式使用同一可信租户。 */
    private void insertEndpointAuthorizations(Long serviceApiKeyId, Long tenantId, Set<String> endpointCodes) {
        List<ServiceApiKeyEndpointDO> records = new ArrayList<>(endpointCodes.size());
        for (String endpointCode : endpointCodes) {
            ServiceApiKeyEndpointDO record = new ServiceApiKeyEndpointDO();
            record.setServiceApiKeyId(serviceApiKeyId);
            record.setEndpointCode(endpointCode);
            record.setTenantId(tenantId);
            records.add(record);
        }
        serviceApiKeyEndpointMapper.insertBatch(records);
    }

    /** 加载调用方已授权端点编码并保持数据库排序，空集合表示默认拒绝全部端点。 */
    private Set<String> loadEndpointCodes(Long serviceApiKeyId) {
        List<ServiceApiKeyEndpointDO> records =
                serviceApiKeyEndpointMapper.selectListByServiceApiKeyId(serviceApiKeyId);
        Set<String> endpointCodes = new LinkedHashSet<>(records.size());
        for (ServiceApiKeyEndpointDO record : records) {
            endpointCodes.add(record.getEndpointCode());
        }
        return endpointCodes;
    }

    /**
     * 在可信数据库租户中执行身份查询并原样传播业务异常，完成后恢复原线程上下文。
     *
     * @param tenantId 密钥记录绑定租户编号
     * @param supplier 需要在该租户内执行的查询
     * @param <T> 查询结果类型
     * @return 查询结果
     */
    private <T> T executeInTenant(Long tenantId, Supplier<T> supplier) {
        Long previousTenantId = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(false);
            return supplier.get();
        } finally {
            TenantContextHolder.setTenantId(previousTenantId);
            TenantContextHolder.setIgnore(previousIgnore);
        }
    }
}
