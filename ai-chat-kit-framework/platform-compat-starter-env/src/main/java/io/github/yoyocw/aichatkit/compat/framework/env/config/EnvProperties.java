package io.github.yoyocw.aichatkit.compat.framework.env.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 环境配置
 *
 * @author kelecc
 */
@ConfigurationProperties(prefix = "aichatkit.env")
@Data
public class EnvProperties {

    public static final String TAG_KEY = "aichatkit.env.tag";

    /**
     * 环境标签
     */
    private String tag;

}
