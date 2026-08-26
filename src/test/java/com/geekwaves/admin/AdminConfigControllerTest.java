package com.geekwaves.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admintestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class AdminConfigControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void updateNonexistentSourceReturns404() throws Exception {
        mockMvc.perform(put("/api/admin/sources/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"rss","code":"rss","type":"RSS"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createProviderWithoutNameReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendor\":\"OPENAI_COMPAT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void malformedJsonBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(patch("/api/admin/sources/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }
}
