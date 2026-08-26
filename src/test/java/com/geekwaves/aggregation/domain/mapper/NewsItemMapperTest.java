package com.geekwaves.aggregation.domain.mapper;

import com.geekwaves.aggregation.domain.NewsItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class NewsItemMapperTest {
    @Autowired NewsItemMapper mapper;

    @Test
    void insertAndQuery() {
        NewsItem item = new NewsItem();
        item.setSourceId(1L);
        item.setCategory("NEWS");
        item.setSourceItemId("h-123");
        item.setTitle("title");
        item.setUrl("https://example.com");
        item.setPublishedAt(LocalDateTime.now());
        item.setFetchedAt(LocalDateTime.now());
        mapper.insert(item);
        NewsItem got = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<NewsItem>()
                .eq("source_item_id", "h-123"));
        Assertions.assertEquals("title", got.getTitle());
    }
}
