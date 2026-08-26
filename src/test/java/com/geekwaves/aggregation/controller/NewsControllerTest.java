package com.geekwaves.aggregation.controller;

import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:newstestdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class NewsControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NewsItemMapper newsItemMapper;

    private NewsItem insert(String title, String category, long sourceId, LocalDateTime publishedAt) {
        NewsItem n = new NewsItem();
        n.setSourceId(sourceId);
        n.setCategory(category);
        n.setSourceItemId(category + "-" + title);
        n.setTitle(title);
        n.setUrl("https://example.com/" + title);
        n.setPublishedAt(publishedAt);
        newsItemMapper.insert(n);
        return n;
    }

    @Test
    void listFiltersDetailAndSecondCallHitsCache() throws Exception {
        NewsItem older = insert("vue-tips", "frontend", 1L, LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        NewsItem newer = insert("ai-breakthrough", "ai", 2L, LocalDateTime.of(2026, 1, 3, 4, 5, 6));

        mockMvc.perform(get("/api/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].title").value("ai-breakthrough"))
                .andExpect(jsonPath("$.data.records[0].publishedAt").value("2026-01-03T04:05:06"));

        mockMvc.perform(get("/api/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].title").value("ai-breakthrough"));

        mockMvc.perform(get("/api/news").param("sourceId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("ai-breakthrough"));

        mockMvc.perform(get("/api/news").param("category", "frontend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("vue-tips"));

        mockMvc.perform(get("/api/news/" + older.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("vue-tips"));

        mockMvc.perform(get("/api/news/" + newer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("ai-breakthrough"));
    }

    @Test
    void detailMissingReturns404() throws Exception {
        mockMvc.perform(get("/api/news/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void sourcesReturnsEnabledBriefsOnly() throws Exception {
        mockMvc.perform(get("/api/news/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].type").exists())
                .andExpect(jsonPath("$.data[0].code").doesNotExist())
                .andExpect(jsonPath("$.data[0].baseUrl").doesNotExist())
                .andExpect(jsonPath("$.data[1].name").exists());
    }
}
