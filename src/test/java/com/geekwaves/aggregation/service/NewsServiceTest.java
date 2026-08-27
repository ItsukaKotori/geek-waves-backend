package com.geekwaves.aggregation.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.geekwaves.aggregation.adapter.FetchedItem;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsServiceTest {    private NewsItemMapper newsItemMapper;
    private CategoryResolver categoryResolver;
    private NewsService service;

    @BeforeEach
    void setUp() {
        newsItemMapper = mock(NewsItemMapper.class);
        categoryResolver = mock(CategoryResolver.class);
        service = new NewsService(newsItemMapper, categoryResolver);
    }

    @Test
    void persistInsertsEveryItemAndReturnsCount() {
        InfoSource source = source(1L);
        assertEquals(2, service.persist(source, List.of(item("i1"), item("i2"))));
        verify(newsItemMapper, times(2)).insert(any(NewsItem.class));
    }

    @Test
    void persistIgnoresDuplicateKeyAndCountsOnlyNewOnes() {
        InfoSource source = source(1L);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.getAndIncrement() == 0) throw new DuplicateKeyException("dup");
            return 1;
        }).when(newsItemMapper).insert(any(NewsItem.class));
        assertEquals(1, service.persist(source, List.of(item("i1"), item("i2"))));
        verify(newsItemMapper, times(2)).insert(any(NewsItem.class));
    }

    @Test
    void longSourceItemIdIsHashedToFitColumn() {
        InfoSource source = source(1L);
        String longId = "https://spring.io/blog/2026/08/20/spring-batch-6-0-5-and-6-1-0-M1-available-now-" + "x".repeat(30);
        assertEquals(1, service.persist(source, List.of(item(longId))));

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper).insert(captor.capture());
        String saved = captor.getValue().getSourceItemId();
        assertEquals(40, saved.length());
        assertEquals(saved, NewsService.normalizeSourceItemId(longId));
        assertEquals(longId, NewsService.normalizeSourceItemId(longId).length() >= 40 ? longId : saved);
    }

    @Test
    void shortSourceItemIdPassesThrough() {
        assertEquals("i1", NewsService.normalizeSourceItemId("i1"));
        assertEquals("abc", NewsService.normalizeSourceItemId("abc"));
    }

    @Test
    void fortyCharShaHexFromAdapterPassesThroughUnchanged() throws Exception {
        String sha40 = java.util.HexFormat.of().formatHex(java.security.MessageDigest
                .getInstance("SHA-256").digest("adapter-item".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .substring(0, 40);
        assertEquals(sha40, NewsService.normalizeSourceItemId(sha40));
        assertEquals(40, NewsService.normalizeSourceItemId(sha40).length());
    }

    @Test
    void persistDoesNotThrowOnSingleDuplicate() {
        InfoSource source = source(1L);
        doThrow(new DuplicateKeyException("dup")).when(newsItemMapper).insert(any(NewsItem.class));
        assertEquals(0, service.persist(source, List.of(item("i1"))));
    }

    @Test
    void entityFieldsAreMappedFromFetchedItem() {
        InfoSource source = source(7L);
        when(categoryResolver.categoryOf(eq(source), any())).thenReturn("REPO");
        LocalDateTime published = LocalDateTime.of(2026, 1, 2, 3, 4);
        FetchedItem item = new FetchedItem("i1", "标题", "https://example.com/1", "摘要", "内容", "作者", "java", "{\"stars\":1}", 42, published);
        service.persist(source, List.of(item));
        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper).insert(captor.capture());
        NewsItem n = captor.getValue();
        assertEquals(7L, n.getSourceId());
        assertEquals("REPO", n.getCategory());
        assertEquals("i1", n.getSourceItemId());
        assertEquals("标题", n.getTitle());
        assertEquals("https://example.com/1", n.getUrl());
        assertEquals("摘要", n.getSummary());
        assertEquals("内容", n.getContent());
        assertEquals("作者", n.getAuthor());
        assertEquals("java", n.getTags());
        assertEquals("{\"stars\":1}", n.getExtraJson());
        assertEquals(42, n.getScore());
        assertEquals(published, n.getPublishedAt());
        assertNotNull(n.getFetchedAt());
        assertEquals("NONE", n.getAiStatus());
    }

    @Test
    void duplicateWithEmptyExistingContentBackfills() {
        InfoSource source = source(1L);
        doThrow(new DuplicateKeyException("dup")).when(newsItemMapper).insert(any(NewsItem.class));
        NewsItem existing = new NewsItem();
        existing.setSourceId(1L);
        existing.setSourceItemId("i1");
        existing.setContent(null);
        when(newsItemMapper.selectOne(any())).thenReturn(existing);
        FetchedItem item = new FetchedItem("i1", "t", "https://example.com/i1", "s",
                "<p>全文 HTML</p>", "a", "", null, 0, LocalDateTime.now());

        assertEquals(0, service.persist(source, List.of(item)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<NewsItem>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(newsItemMapper).update(isNull(), captor.capture());
        UpdateWrapper<NewsItem> wrapper = (UpdateWrapper<NewsItem>) captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertNotNull(sqlSet);
        assertTrue(sqlSet.contains("content"));
        assertTrue(sqlSet.contains("summary"));
        assertTrue(sqlSet.contains("fetched_at"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("<p>全文 HTML</p>"));
    }

    @Test
    void duplicateWithExistingContentSkipsUpdate() {
        InfoSource source = source(1L);
        doThrow(new DuplicateKeyException("dup")).when(newsItemMapper).insert(any(NewsItem.class));
        NewsItem existing = new NewsItem();
        existing.setContent("<p>已有全文</p>");
        when(newsItemMapper.selectOne(any())).thenReturn(existing);
        FetchedItem item = new FetchedItem("i1", "t", "https://example.com/i1", "s",
                "<p>新全文</p>", "a", "", null, 0, LocalDateTime.now());

        assertEquals(0, service.persist(source, List.of(item)));

        verify(newsItemMapper, never()).update(any(), any());
    }

    @Test
    void duplicateWithBlankNewContentSkipsUpdate() {
        InfoSource source = source(1L);
        doThrow(new DuplicateKeyException("dup")).when(newsItemMapper).insert(any(NewsItem.class));
        NewsItem existing = new NewsItem();
        existing.setContent(null);
        when(newsItemMapper.selectOne(any())).thenReturn(existing);
        FetchedItem item = new FetchedItem("i1", "t", "https://example.com/i1", "s",
                "   ", "a", "", null, 0, LocalDateTime.now());

        assertEquals(0, service.persist(source, List.of(item)));

        verify(newsItemMapper, never()).update(any(), any());
    }

    private InfoSource source(Long id) {
        InfoSource s = new InfoSource();
        s.setId(id);
        s.setType("RSS");
        return s;
    }

    private FetchedItem item(String sourceItemId) {
        return new FetchedItem(sourceItemId, "t", "https://example.com/" + sourceItemId, "s", "c", "a", "", null, 0, LocalDateTime.now());
    }
}
