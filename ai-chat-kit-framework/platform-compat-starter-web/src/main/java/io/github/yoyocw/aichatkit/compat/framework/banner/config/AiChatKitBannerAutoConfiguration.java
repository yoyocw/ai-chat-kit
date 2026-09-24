package io.github.yoyocw.aichatkit.compat.framework.banner.config;

import io.github.yoyocw.aichatkit.compat.framework.banner.core.BannerApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Banner 的自动配置类
 *
 * @author kelecc
 */
@AutoConfiguration
public class AiChatKitBannerAutoConfiguration {

    @Bean
    public BannerApplicationRunner bannerApplicationRunner() {
        return new BannerApplicationRunner();
    }

}
