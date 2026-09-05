package com.geekwaves.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * 桌面端 SPA 回退:geekwaves.web.spa-fallback=true 时,非 /api 且无静态文件命中的 GET 转发 index.html,
 * 支撑 history 路由刷新/直达。默认关闭,现有 Web 部署零影响。
 * 注意:location 字符串必须以 / 结尾(file: 目录),WebProperties 的 static-locations 天然满足。
 */
@Configuration
@ConditionalOnProperty(prefix = "geekwaves.web", name = "spa-fallback", havingValue = "true")
public class SpaFallbackWebConfig implements WebMvcConfigurer {

    private final WebProperties webProperties;

    public SpaFallbackWebConfig(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(webProperties.getResources().getStaticLocations())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/")) {
                            return null; // API 未匹配一律 404,绝不回退
                        }
                        // 本 location 无 index.html 则返回 null,让 resolver 继续尝试下一个 location
                        Resource index = location.createRelative("index.html");
                        return (index.exists() && index.isReadable()) ? index : null;
                    }
                });
    }
}
