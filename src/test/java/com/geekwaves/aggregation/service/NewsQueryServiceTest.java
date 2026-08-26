package com.geekwaves.aggregation.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.aggregation.dto.NewsSourceBrief;
import com.geekwaves.config.port.LocalCachePort;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.itsuka.core.exception.ServiceException;
import org.itsuka.web.dto.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsQueryServiceTest {
    private NewsItemMapper newsItemMapper;
    private InfoSourceMapper infoSourceMapper;
    private LocalCachePort cachePort;
    private NewsQueryService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), NewsItem.class);
        newsItemMapper = mock(NewsItemMapper.class);
        infoSourceMapper = mock(InfoSourceMapper.class);
        cachePort = spy(new LocalCachePort(JsonMapper.builder().build()));
        service = new NewsQueryService(newsItemMapper, infoSourceMapper, cachePort);
    }

    private NewsItem item(long id, String title, String category, long sourceId, LocalDateTime publishedAt) {
        NewsItem n = new NewsItem();
        n.setId(id);
        n.setTitle(title);
        n.setCategory(category);
        n.setSourceId(sourceId);
        n.setPublishedAt(publishedAt);
        return n;
    }

    @Test
    void pageOnCacheMissFetchesDbPutsCacheAndSecondCallHitsCacheWithoutDb() {
        LocalDateTime at = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        NewsItem dbItem = item(1L, "hello news", "frontend", 7L, at);
        Page<NewsItem> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(dbItem));
        dbPage.setTotal(1);
        when(newsItemMapper.selectPage(any(Page.class), any())).thenReturn(dbPage);

        PageResult<NewsItem> first = service.page("frontend", 7L, 1, 10);
        PageResult<NewsItem> second = service.page("frontend", 7L, 1, 10);

        assertEquals(1, first.getRecords().size());
        assertEquals("hello news", first.getRecords().get(0).getTitle());
        assertEquals(at, first.getRecords().get(0).getPublishedAt());
        assertEquals(Long.valueOf(1), first.getTotal());
        assertEquals(dbItem, second.getRecords().get(0));
        assertEquals(at, second.getRecords().get(0).getPublishedAt());
        assertEquals(first.getTotal(), second.getTotal());
        verify(newsItemMapper, times(1)).selectPage(any(Page.class), any());
        verify(cachePort).put(eq("news:list:frontend:1:10:7"), any(), eq(NewsQueryService.TTL));
    }

    @Test
    void pageUsesDashForBlankCategoryAndNullSourceIdInKey() {
        when(newsItemMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));
        service.page(" ", null, 2, 20);
        verify(cachePort).put(eq("news:list:-:2:20:-"), any(), eq(NewsQueryService.TTL));
    }

    @Test
    void pageOnCacheMissQueriesDbWhenFiltersDiffer() {
        LocalDateTime at = LocalDateTime.of(2026, 1, 1, 0, 0);
        Page<NewsItem> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(item(1L, "a", "ai", 1L, at)));
        dbPage.setTotal(1);
        when(newsItemMapper.selectPage(any(Page.class), any())).thenReturn(dbPage);

        service.page("frontend", 7L, 1, 10);
        service.page("frontend", 7L, 1, 10);
        service.page("ai", 7L, 1, 10);
        service.page("frontend", 8L, 1, 10);

        verify(newsItemMapper, times(3)).selectPage(any(Page.class), any());
    }

    @Test
    void pageQueryExcludesContentColumn() {
        when(newsItemMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 10));

        service.page("frontend", null, 1, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<NewsItem>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(newsItemMapper).selectPage(any(Page.class), captor.capture());
        String sqlSelect = captor.getValue().getSqlSelect();
        assertNotNull(sqlSelect);
        List<String> columns = Arrays.stream(sqlSelect.split(","))
                .map(c -> c.trim().replaceAll("(?i)\\s+as\\s+.*$", "").trim().toLowerCase())
                .toList();
        assertFalse(columns.contains("content"));
        assertTrue(columns.contains("summary"));
    }

    @Test
    void detailReturnsNewsItem() {
        NewsItem n = item(5L, "solo", "ai", 3L, null);
        when(newsItemMapper.selectById(5L)).thenReturn(n);
        assertEquals(n, service.detail(5L));
    }

    @Test
    void detailMissingThrowsNotFound() {
        when(newsItemMapper.selectById(99L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.detail(99L));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("资讯不存在", e.getMessage());
    }

    @Test
    void enabledSourcesMapsEnabledOnlyBriefs() {
        InfoSource enabled = new InfoSource();
        enabled.setId(1L);
        enabled.setName("Hacker News");
        enabled.setType("HN_API");
        enabled.setCode("hn");
        enabled.setBaseUrl("https://example.com");
        when(infoSourceMapper.selectList(any())).thenReturn(List.of(enabled));

        List<NewsSourceBrief> briefs = service.enabledSources();

        assertEquals(1, briefs.size());
        assertEquals(1L, briefs.get(0).id());
        assertEquals("Hacker News", briefs.get(0).name());
        assertEquals("HN_API", briefs.get(0).type());
    }
}
