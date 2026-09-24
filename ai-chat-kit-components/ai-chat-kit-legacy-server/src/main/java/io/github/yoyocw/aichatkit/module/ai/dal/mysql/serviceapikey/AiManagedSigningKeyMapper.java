package io.github.yoyocw.aichatkit.module.ai.dal.mysql.serviceapikey;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 宿主部署级密钥的首次写入端口，使用 AI 自身数据源，不访问 system 的业务数据源。
 * 表不属于用户租户数据；调用服务必须先验证系统租户的真实平台管理员身份。
 * 部署必须禁止本 Mapper 的 SQL 参数日志及数据源代理参数日志，私钥不得进入日志。
 */
@Mapper
public interface AiManagedSigningKeyMapper {
    /**
     * 原子创建一组密钥；已有记录（包括停用记录）不覆盖，不隐式轮换。
     * @param issuer 仅来自部署配置的签发方标识
     * @param audience 仅来自部署配置的接收方标识
     * @param privateKey 新生成的 PKCS#8 PEM 私钥，仅用于数据库写入，禁止返回或记录
     * @param publicKey 同一密钥对的 X.509 PEM 公钥
     * @return 插入一行时为 1，已有同签发方和接收方记录时为 0
     */
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Insert("INSERT INTO ai_mcp_jwt_signing_key (issuer, audience, private_key, public_key, enabled) "
            + "VALUES (#{issuer}, #{audience}, #{privateKey}, #{publicKey}, true) "
            + "ON CONFLICT (issuer, audience) DO NOTHING")
    int initialize(@Param("issuer") String issuer, @Param("audience") String audience,
                   @Param("privateKey") String privateKey, @Param("publicKey") String publicKey);

    /**
     * 从 AI 自有数据源读取当前启用密钥，仅供服务器签发，不返回 HTTP。
     * @param issuer 部署配置的签发方标识
     * @param audience 部署配置的接收方标识
     * @return PKCS#8 私钥；不存在或停用返回 null，调用方必须拒绝且不得回退配置密钥
     */
    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("SELECT private_key FROM ai_mcp_jwt_signing_key WHERE issuer = #{issuer} "
            + "AND audience = #{audience} AND enabled = true")
    String selectEnabledPrivateKey(@Param("issuer") String issuer, @Param("audience") String audience);
}
