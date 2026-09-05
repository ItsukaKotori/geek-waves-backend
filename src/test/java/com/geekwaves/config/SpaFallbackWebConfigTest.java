package com.geekwaves.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 桌面端 SPA 回退:geekwaves.web.spa-fallback=true 时非 /api 未命中 GET 回退 index.html。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spatestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "geekwaves.web.spa-fallback=true",
        "spring.web.resources.static-locations=file:src/test/resources/spa-fixture/",
})
@AutoConfigureMockMvc
class SpaFallbackWebConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootServesIndexHtml() throws Exception {
        // MockMvc 无法执行 forward:Boot 欢迎页将 / 映射为 forward:index.html,只能断言转发目标;
        // 真实容器中该转发由静态资源链渲染出 index.html 内容。
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void unknownDeepRouteFallsBackToIndexHtml() throws Exception {
        mockMvc.perform(get("/tools?tool=http"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GeekWaves SPA Fixture")));
    }

    @Test
    void realStaticFileIsServedAsIs() throws Exception {
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("console.log('fixture')")));
    }

    @Test
    void apiRoutesNeverFallBack() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("pong"));
        mockMvc.perform(get("/api/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
