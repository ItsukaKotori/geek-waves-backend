package com.geekwaves.aggregation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.geekwaves.aggregation.adapter.FetchedItem;
import com.geekwaves.aggregation.adapter.SourceItemIdDigest;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {
    private static final int ITEM_ID_MAX = 64;
    private final NewsItemMapper newsItemMapper;
    private final CategoryResolver categoryResolver;

    static String normalizeSourceItemId(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        return raw.length() <= ITEM_ID_MAX ? raw : SourceItemIdDigest.sha256First40(raw);
    }

    public int persist(InfoSource source, List<FetchedItem> items) {
        int created = 0;
        for (FetchedItem item : items) {
            NewsItem entity = toEntity(source, item);
            try {
                newsItemMapper.insert(entity);
                created++;
            } catch (DuplicateKeyException e) {
                log.debug("duplicate item skipped source={} url={}", source.getCode(), item.url(), e);
                tryBackfillContent(source, item);
            }
        }
        return created;
    }

    /**
     * 重复键时尝试为旧条目回填 content:仅当已存在行 content 为空且新抓取内容非空时,
     * 用 UpdateWrapper 只回填 content/summary/fetched_at 三列(不覆盖已有全文)。
     */
    private void tryBackfillContent(InfoSource source, FetchedItem item) {
        String newContent = item.content();
        if (newContent == null || newContent.isBlank()) return;
        String sourceItemId = normalizeSourceItemId(item.sourceItemId());
        NewsItem existing = newsItemMapper.selectOne(new QueryWrapper<NewsItem>()
                .eq("source_id", source.getId())
                .eq("source_item_id", sourceItemId));
        if (existing == null) return;
        if (existing.getContent() != null && !existing.getContent().isBlank()) return;
        newsItemMapper.update(null, new UpdateWrapper<NewsItem>()
                .eq("source_id", source.getId())
                .eq("source_item_id", sourceItemId)
                .set("content", newContent)
                .set("summary", item.summary())
                .set("fetched_at", LocalDateTime.now()));
        log.info("backfill content for source={} item={}", source.getCode(), sourceItemId);
    }

    private NewsItem toEntity(InfoSource source, FetchedItem item) {
        NewsItem n = new NewsItem();
        n.setSourceId(source.getId());
        n.setCategory(categoryResolver.categoryOf(source, item));
        n.setSourceItemId(normalizeSourceItemId(item.sourceItemId()));
        n.setTitle(item.title());
        n.setUrl(item.url());
        n.setSummary(item.summary());
        n.setContent(item.content());
        n.setAuthor(item.author());
        n.setTags(item.tagsString());
        n.setExtraJson(item.extraJson());
        n.setScore(item.score());
        n.setPublishedAt(item.publishedAt());
        n.setFetchedAt(LocalDateTime.now());
        n.setAiStatus("NONE");
        return n;
    }
}
