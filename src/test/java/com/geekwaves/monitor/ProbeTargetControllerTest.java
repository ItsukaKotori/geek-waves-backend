package com.geekwaves.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:probetestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class ProbeTargetControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void crudLifecycle() throws Exception {
        String body = """
                {"name":"本机后端","host":"127.0.0.1","port":8082,"timeoutMs":1000,"intervalSeconds":60}
                """;
        // 创建
        String id = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/monitor/probe-targets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.id").exists())
                        .andExpect(jsonPath("$.data.lastStatus").doesNotExist())
                        .andReturn().getResponse().getContentAsString(), "$.data.id").toString();

        // 列表含新目标
        mockMvc.perform(get("/api/monitor/probe-targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("本机后端"));

        // 手动探测回填结果
        mockMvc.perform(post("/api/monitor/probe-targets/{id}/probe", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lastStatus").exists());

        // 删除
        mockMvc.perform(delete("/api/monitor/probe-targets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createWithUrlHostReturns400() throws Exception {
        mockMvc.perform(post("/api/monitor/probe-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"host\":\"http://127.0.0.1\",\"port\":80}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createWithOutOfRangePortReturns400() throws Exception {
        mockMvc.perform(post("/api/monitor/probe-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"host\":\"127.0.0.1\",\"port\":70000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void probeNonexistentTargetReturns404() throws Exception {
        mockMvc.perform(post("/api/monitor/probe-targets/999999/probe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
