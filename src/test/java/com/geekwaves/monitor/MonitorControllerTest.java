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

/**
 * 只断言结构与 success 标志,不断言具体数值(OSHI 在 macOS/Linux/CI 输出不同)。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:monitortestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
})
@AutoConfigureMockMvc
class MonitorControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void overviewReturnsSystemAndJvmStructure() throws Exception {
        mockMvc.perform(get("/api/monitor/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.system.sampledAt").exists())
                .andExpect(jsonPath("$.data.jvm.uptimeMs").exists())
                .andExpect(jsonPath("$.data.jvm.heap.usedBytes").exists())
                .andExpect(jsonPath("$.data.jvm.threads.live").exists());
    }

    @Test
    void processesReturnsSnapshotStructure() throws Exception {
        mockMvc.perform(get("/api/monitor/processes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sampledAt").exists())
                .andExpect(jsonPath("$.data.processes").isArray());
    }

    @Test
    void tasksReturnsAggregatedStructure() throws Exception {
        mockMvc.perform(get("/api/monitor/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sources").isArray())
                .andExpect(jsonPath("$.data.providers").isArray())
                .andExpect(jsonPath("$.data.schedulers").isArray())
                .andExpect(jsonPath("$.data.fetchPool.active").exists())
                .andExpect(jsonPath("$.data.newsTotal").exists());
    }
}
