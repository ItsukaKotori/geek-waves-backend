package com.geekwaves.aggregation.service;

import com.geekwaves.aggregation.adapter.FetchedItem;
import com.geekwaves.aggregation.domain.InfoSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryResolverTest {

    private final CategoryResolver resolver = new CategoryResolver();

    @Test
    void nonGithubSourcesAreNews() {
        assertEquals("NEWS", resolver.categoryOf(source("HN_API", "hn-top"), item(null)));
        assertEquals("NEWS", resolver.categoryOf(source("RSS", "java-blog"), item(null)));
    }

    @Test
    void githubTrendingCodeIsRepoEvenWithoutExtraJson() {
        assertEquals("REPO", resolver.categoryOf(source("GITHUB_API", "github-trending"), item(null)));
    }

    @Test
    void githubExtraJsonWithStarsIsRepo() {
        assertEquals("REPO", resolver.categoryOf(source("GITHUB_API", "github-api"),
                item("{\"stars\":100}")));
    }

    @Test
    void githubWithoutStarsIsReleaseExtraJsonNullFallsBackToCode() {
        assertEquals("RELEASE", resolver.categoryOf(source("GITHUB_API", "github-api"), item(null)));
    }

    @Test
    void githubWithoutStarsIsReleaseExtraJsonEmpty() {
        assertEquals("RELEASE", resolver.categoryOf(source("GITHUB_API", "github-api"), item("{}")));
    }

    private InfoSource source(String type, String code) {
        InfoSource s = new InfoSource();
        s.setType(type);
        s.setCode(code);
        return s;
    }

    private FetchedItem item(String extraJson) {
        return new FetchedItem("i1", "t", "https://github.com/x/y", "", "", "", "", extraJson, 0, LocalDateTime.now());
    }
}
