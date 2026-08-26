package com.geekwaves.ai;

import org.itsuka.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aitestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class AiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiService aiService;

    @BeforeEach
    void stubStream() {
        when(aiService.analyze(anyLong(), anyBoolean(), any()))
                .thenReturn(Flux.just(new AiChunk("好", false), new AiChunk("", true)));
    }

    @Test
    void analyzeStreamsChunksThenDone() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/ai/analyze/42").param("force", "false"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(res))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(containsString("好")))
                .andExpect(content().string(containsString("__DONE__")));
        verify(aiService).analyze(eq(42L), eq(false), any());
    }

    @Test
    void analyzeForcesWhenParamTrue() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/ai/analyze/42").param("force", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(res))
                .andExpect(status().isOk());
        verify(aiService).analyze(eq(42L), eq(true), any());
    }

    @Test
    void analyzeWithoutProviderReturns400() throws Exception {
        when(aiService.analyze(anyLong(), anyBoolean(), any()))
                .thenThrow(ServiceException.create(HttpStatus.BAD_REQUEST, "请先配置 AI 厂商(默认启用)"));

        mockMvc.perform(post("/api/ai/analyze/42").param("force", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请先配置 AI 厂商(默认启用)"));
    }

    @Test
    void refreshAiAlwaysForces() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/news/42/refresh-ai"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(res))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("__DONE__")));
        verify(aiService).analyze(eq(42L), eq(true), any());
    }
}
