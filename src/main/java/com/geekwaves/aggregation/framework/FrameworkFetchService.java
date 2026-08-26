package com.geekwaves.aggregation.framework;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.aggregation.domain.FrameworkWatch;
import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.FrameworkWatchMapper;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.config.port.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.itsuka.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Slf4j
@RequiredArgsConstructor
public class FrameworkFetchService {
    static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String REPO_PATTERN = "^[\\w.-]+/[\\w.-]+$";
    private static final int SUMMARY_LIMIT = 300;
    private final FrameworkWatchMapper watchMapper;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final NewsItemMapper newsItemMapper;
    private final CachePort cachePort;

    public Boolean fetchNow(FrameworkWatch watch) {
        return fetchNow(watch, GITHUB_API_BASE);
    }

    boolean fetchNow(FrameworkWatch watch, String baseUrl) {
        String repo = normalizeRepo(watch.getGithubRepo());
        if (repo == null) {
            log.warn("framework {} repo {} 非法,跳过抓取", watch.getId(), watch.getGithubRepo());
            return false;
        }
        String url = trimTrailingSlash(baseUrl);
        try {
            String body = webClientBuilder.build()
                    .get()
                    .uri(URI.create(url + "/repos/" + repo + "/releases/latest"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode node = (body == null || body.isBlank()) ? null : objectMapper.readTree(body);
            String tag = nodeTag(node);
            if (tag.isBlank()) {
                log.warn("framework {} repo {} 响应无 tag_name", watch.getId(), repo);
                return false;
            }
            watch.setLatestVersion(tag);
            watch.setLastReleaseAt(LocalDateTime.now());
            watchMapper.updateById(watch);
            log.info("framework {} repo {} 拉到最新版本 {}", watch.getId(), repo, tag);
            tryPublishNewsItem(watch, repo, tag, node);
            return true;
        } catch (Exception e) {
            log.warn("framework {} repo {} 抓取失败: {}", watch.getId(), repo, e.getMessage());
            return false;
        }
    }

    void tryPublishNewsItem(FrameworkWatch watch, String repo, String tag, JsonNode node) {
        try {
            String notes = nodeNotes(node);
            String name = watch.getName() == null || watch.getName().isBlank() ? repo : watch.getName();
            String title = name + " " + tag + " 发布";
            String url = "https://github.com/" + repo + "/releases/tag/" + tag;
            tryUpsertNewsItem(watch, tag, title, url, notes, repo,
                    parsePublishedAt(nodePathText(node, "published_at")));
            cachePort.invalidateByPrefix("news:list:");
        } catch (Exception e) {
            log.warn("framework {} repo {} tag {} 写入资讯失败: {}", watch.getId(), repo, tag, e.getMessage());
        }
    }

    private int tryUpsertNewsItem(FrameworkWatch watch, String tag, String title, String url, String notes,
                                  String repo, LocalDateTime publishedAt) {
        NewsItem existing = newsItemMapper.selectOne(new QueryWrapper<NewsItem>()
                .eq("source_id", watch.getId())
                .eq("source_item_id", tag));
        if (existing != null) {
            existing.setTitle(title);
            existing.setUrl(url);
            existing.setSummary(notes.length() > SUMMARY_LIMIT ? notes.substring(0, SUMMARY_LIMIT) : notes);
            existing.setContent(notes);
            existing.setTags(repo);
            existing.setPublishedAt(publishedAt);
            newsItemMapper.updateById(existing);
            return 2;
        }
        NewsItem item = new NewsItem();
        item.setSourceId(watch.getId());
        item.setSourceItemId(tag);
        item.setCategory("RELEASE");
        item.setTitle(title);
        item.setUrl(url);
        item.setSummary(notes.length() > SUMMARY_LIMIT ? notes.substring(0, SUMMARY_LIMIT) : notes);
        item.setContent(notes);
        item.setAuthor("");
        item.setTags(repo);
        item.setExtraJson("{}");
        item.setScore(0);
        item.setPublishedAt(publishedAt);
        item.setFetchedAt(LocalDateTime.now());
        item.setAiStatus("NONE");
        try {
            newsItemMapper.insert(item);
            return 1;
        } catch (DuplicateKeyException e) {
            return 1;
        }
    }

    private static LocalDateTime parsePublishedAt(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try {
            return Instant.parse(raw.trim()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeException e) {
            return LocalDateTime.now();
        }
    }

    public FrameworkWatch refresh(Long id) {
        return refresh(id, GITHUB_API_BASE);
    }

    FrameworkWatch refresh(Long id, String baseUrl) {
        FrameworkWatch watch = watchMapper.selectById(id);
        if (watch == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "framework 不存在");
        fetchNow(watch, baseUrl);
        return watch;
    }

    static String normalizeRepo(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        for (String p : new String[]{"https://github.com/", "http://github.com/", "github.com/", "https://www.github.com/", "http://www.github.com/"}) {
            if (s.startsWith(p)) {
                s = s.substring(p.length());
                break;
            }
        }
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.matches(REPO_PATTERN) ? s : null;
    }

    private static String nodeTag(JsonNode node) {
        return node == null || node.path("tag_name").isMissingNode() || node.path("tag_name").isNull()
                ? "" : node.path("tag_name").asString().trim();
    }

    private static String nodeNotes(JsonNode node) {
        return nodePathText(node, "body");
    }

    private static String nodePathText(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        return value.asString().trim();
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
