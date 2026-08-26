package com.geekwaves.aggregation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.aggregation.dto.NewsSourceBrief;
import com.geekwaves.config.port.CachePort;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.exception.ServiceException;
import org.itsuka.web.dto.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NewsQueryService {
    private final NewsItemMapper newsItemMapper;
    private final InfoSourceMapper infoSourceMapper;
    private final CachePort cachePort;
    static final Duration TTL = Duration.ofMinutes(5);

    record NewsPageCache(List<NewsItem> records, long current, long size, long total) {
        PageResult<NewsItem> toPageResult() {
            return new PageResult<>(records, current, size, total);
        }
    }

    public PageResult<NewsItem> page(String category, Long sourceId, long current, long size) {
        String key = "news:list:" + (category == null || category.isBlank() ? "-" : category)
                + ":" + current + ":" + size + ":" + (sourceId == null ? "-" : sourceId);
        Optional<NewsPageCache> cached = cachePort.get(key, NewsPageCache.class);
        if (cached.isPresent()) return cached.get().toPageResult();
        QueryWrapper<NewsItem> qw = new QueryWrapper<>();
        qw.select(NewsItem.class, f -> !"content".equals(f.getColumn()));
        if (category != null && !category.isBlank()) qw.eq("category", category);
        if (sourceId != null) qw.eq("source_id", sourceId);
        qw.orderByDesc("published_at").orderByDesc("id");
        Page<NewsItem> result = newsItemMapper.selectPage(new Page<>(current, size), qw);
        NewsPageCache value = new NewsPageCache(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
        cachePort.put(key, value, TTL);
        return value.toPageResult();
    }

    public NewsItem detail(Long id) {
        NewsItem item = newsItemMapper.selectById(id);
        if (item == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "资讯不存在");
        return item;
    }

    public List<NewsSourceBrief> enabledSources() {
        List<InfoSource> sources = infoSourceMapper.selectList(
                new QueryWrapper<InfoSource>().eq("enabled", true).orderByAsc("sort_order"));
        return sources.stream()
                .map(s -> new NewsSourceBrief(s.getId(), s.getName(), s.getType()))
                .toList();
    }
}
