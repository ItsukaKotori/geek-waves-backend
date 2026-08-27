package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GitHubAdapter implements SourceAdapter {
    static final Duration DEFAULT_PERIOD = Duration.ofDays(7);
    static final String DEFAULT_TRENDING_URL = "https://github.com/trending";
    static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public SourceType type() {
        return SourceType.GITHUB_API;
    }

    @Override
    public List<FetchedItem> fetch(InfoSource source, Instant since) throws Exception {
        var config = objectMapper.readValue(source.getConfigJson() == null || source.getConfigJson().isBlank()
                ? "{}" : source.getConfigJson(), JsonNode.class);
        if (config.has("html")) {
            return fetchTrending(config.path("html").asString(DEFAULT_TRENDING_URL));
        }
        int minStars = config.path("minStars").asInt(100);
        String tag = config.path("tag").asString("");
        LocalDate created = since == null ? LocalDate.now().minusDays(DEFAULT_PERIOD.toDays()) : since.atZone(ZoneId.systemDefault()).toLocalDate().minusDays(2);
        String q = "created:>" + created + " stars:>=" + minStars
                + (tag.isBlank() ? "" : " " + tag);
        WebClient client = webClientBuilder.baseUrl(source.getBaseUrl()).build();
        String token = config.path("token").asString("").trim();
        var uriBuilder = client.get().uri(u -> u.path("/search/repositories")
                .queryParam("q", q).queryParam("sort", "stars")
                .queryParam("order", "desc").queryParam("per_page", "30").build());
        JsonNode result = (token.isEmpty() ? uriBuilder.retrieve() : uriBuilder.headers(h -> h.setBearerAuth(token)).retrieve())
                .bodyToMono(JsonNode.class).block();
        List<FetchedItem> items = new ArrayList<>();
        if (result == null) return items;
        for (JsonNode repo : result.path("items")) {
            Instant createdAt;
            try {
                createdAt = Instant.parse(repo.path("created_at").asString(""));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (since != null && createdAt.isBefore(since)) continue;
            String desc = repo.path("description").asString("");
            String language = repo.path("language").asString("");
            items.add(new FetchedItem(
                    repo.path("full_name").asString(""),
                    repo.path("full_name").asString("") + " — " + desc,
                    repo.path("html_url").asString(""),
                    desc,
                    "",
                    repo.path("owner").path("login").asString(""),
                    language,
                    "{\"stars\":" + repo.path("stargazers_count").asInt(0)
                            + ",\"forks\":" + repo.path("forks_count").asInt(0)
                            + ",\"language\":\"" + language.replace("\"", "") + "\"}",
                    repo.path("stargazers_count").asInt(0),
                    LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault())));
        }
        return items;
    }

    private List<FetchedItem> fetchTrending(String htmlUrl) {
        String html = webClientBuilder.build().get().uri(URI.create(htmlUrl))
                .retrieve().bodyToMono(String.class).timeout(FETCH_TIMEOUT).block();
        return fetchTrendingFromHtml(html == null ? "" : html);
    }

    List<FetchedItem> fetchTrendingFromHtml(String html) {
        List<FetchedItem> items = new ArrayList<>();
        for (var row : Jsoup.parse(html).select("article.Box-row")) {
            var a = row.selectFirst("h2 a");
            if (a == null) continue;
            String fullName = a.text().replace(" ", "").replace("\n", "");
            var desc = row.selectFirst("p");
            long stars = 0;
            var extras = row.select("span.d-inline-block");
            if (!extras.isEmpty()) {
                try {
                    stars = Long.parseLong(extras.get(0).text().replace(",", "").split("\\s")[0]);
                } catch (RuntimeException ignored) {
                }
            }
            items.add(new FetchedItem(
                    fullName,
                    fullName + (desc != null ? " — " + desc.text().trim() : ""),
                    "https://github.com/" + fullName,
                    desc == null ? "" : desc.text().trim(),
                    "",
                    "",
                    "",
                    "{\"stars\":" + stars + "}",
                    (int) stars,
                    LocalDateTime.now()));
        }
        return items;
    }
}
