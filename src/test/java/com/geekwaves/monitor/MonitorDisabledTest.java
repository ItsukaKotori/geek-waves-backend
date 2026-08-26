package com.geekwaves.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** geekwaves.monitor.enabled=false 时指标端点返回降级结构(采样关闭) */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:monitordistestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "geekwaves.monitor.enabled=false",
})
@AutoConfigureMockMvc
class MonitorDisabledTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void overviewDegradedWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/monitor/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.system.error").value("监控已禁用"));
    }

    @Test
    void processesDegradedWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/monitor/processes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.error").value("监控已禁用"));
    }
}
