package com.geekwaves.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 桌面端 SPA 回退(纯路径模式):geekwaves.web.webapp-dir 优先于 static-locations,
 * 无 URL 语义——Windows 下任何 file: URL 形式都解析不了,桌面端一律走本属性。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spawebappdirdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "geekwaves.web.spa-fallback=true",
        "geekwaves.web.webapp-dir=${user.dir}/src/test/resources/spa-fixture",
})
@AutoConfigureMockMvc
class SpaFallbackWebappDirTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootAndDeepRouteFallBackToIndexHtml() throws Exception {
        // 根路径走显式 forward(MockMvc 记录 forward 不执行);深路由走资源链由 MockMvc 实际解析
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
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
