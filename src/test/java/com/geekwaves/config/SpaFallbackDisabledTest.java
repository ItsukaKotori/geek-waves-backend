package com.geekwaves.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 默认关闭:未匹配路径应 404,Web 部署行为不变。 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spaofftestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
})
@AutoConfigureMockMvc
class SpaFallbackDisabledTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownRouteIs404WhenDisabled() throws Exception {
        mockMvc.perform(get("/tools")).andExpect(status().isNotFound());
    }
}
