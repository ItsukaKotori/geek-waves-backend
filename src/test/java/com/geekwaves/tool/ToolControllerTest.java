package com.geekwaves.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tooltestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class ToolControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void ssrfUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/tools/http-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"GET","url":"http://127.0.0.1:8080/internal"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("目标地址不在允许范围"));
    }

    @Test
    void unsupportedMethodReturns400() throws Exception {
        mockMvc.perform(post("/api/tools/http-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"FOO","url":"http://example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void blankUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/tools/http-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"GET","url":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
