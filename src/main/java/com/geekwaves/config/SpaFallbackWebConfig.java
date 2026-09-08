package com.geekwaves.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * 桌面端 SPA 回退:geekwaves.web.spa-fallback=true 时,非 /api 且无静态文件命中的 GET 转发 index.html,
 * 支撑 history 路由刷新/直达。默认关闭,现有 Web 部署零影响。
 * 位置来源二选一:
 *  - geekwaves.web.webapp-dir(桌面端用):纯文件系统路径,PathResource 直读,无 URL 语义——
 *    Windows 下 file:D:/x 为 opaque URI、file:/D:/x 的 ssp 又带前置斜杠,任何 file: URL 形式都无法
 *    同时满足 createRelative 与 File 解析,故桌面端一律走本属性(CI windows 冒烟实修)。
 *  - spring.web.resources.static-locations(未设置 webapp-dir 时沿用):location 须以 / 结尾。
 */
@Configuration
@ConditionalOnProperty(prefix = "geekwaves.web", name = "spa-fallback", havingValue = "true")
public class SpaFallbackWebConfig implements WebMvcConfigurer {

    private final WebProperties webProperties;
    private final String webappDir;

    public SpaFallbackWebConfig(
            WebProperties webProperties,
            @Value("${geekwaves.web.webapp-dir:}") String webappDir) {
        this.webProperties = webProperties;
        this.webappDir = webappDir;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径固定转发 index.html:Boot 4 的 WelcomePageHandlerMapping 在 webapp-dir 模式下
        // 找不到欢迎页会对 / 直接 404 且不落回资源链(实测)——显式转发接管,两种模式行为一致。
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        ResourceHandlerRegistration handler = registry.addResourceHandler("/**");
        if (webappDir != null && !webappDir.isBlank()) {
            // 注册器要求目录 location 以 / 结尾;FileSystemResource 无 URL 语义,斜杠分隔跨平台安全
            String dir = webappDir.endsWith("/") ? webappDir : webappDir + "/";
            handler.addResourceLocations(new FileSystemResource(dir));
        } else {
            handler.addResourceLocations(webProperties.getResources().getStaticLocations());
        }
        handler.resourceChain(true).addResolver(new PathResourceResolver() {
            @Override
            protected Resource getResource(String resourcePath, Resource location) throws IOException {
                if (resourcePath.isBlank()) {
                    // 根路径不直接返回目录资源,走 index 回退
                    Resource rootIndex = location.createRelative("index.html");
                    if (rootIndex.exists() && rootIndex.isReadable()) {
                        return rootIndex;
                    }
                }
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
