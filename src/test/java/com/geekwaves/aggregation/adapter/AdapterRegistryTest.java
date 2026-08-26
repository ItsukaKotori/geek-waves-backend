package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdapterRegistryTest {

    private SourceAdapter stub(SourceType type) {
        return new SourceAdapter() {
            @Override
            public SourceType type() {
                return type;
            }

            @Override
            public List<FetchedItem> fetch(InfoSource source, Instant since) {
                return List.of();
            }
        };
    }

    @Test
    void emptyRegistryMatchesNothing() {
        AdapterRegistry registry = AdapterRegistry.of(List.of());
        assertTrue(registry.match("HN_API").isEmpty());
    }

    @Test
    void nullTypeNameMatchesNothing() {
        AdapterRegistry registry = AdapterRegistry.of(List.of(stub(SourceType.HN_API)));
        assertTrue(registry.match(null).isEmpty());
    }

    @Test
    void matchFindsRegisteredAdapter() {
        SourceAdapter hn = stub(SourceType.HN_API);
        AdapterRegistry registry = AdapterRegistry.of(List.of(hn));
        Optional<SourceAdapter> matched = registry.match("HN_API");
        assertTrue(matched.isPresent());
        assertSame(hn, matched.get());
    }

    @Test
    void matchIsCaseInsensitive() {
        SourceAdapter gh = stub(SourceType.GITHUB_API);
        AdapterRegistry registry = AdapterRegistry.of(List.of(gh));
        assertSame(gh, registry.match("github_api").get());
        assertSame(gh, registry.match("Github_Api").get());
    }

    @Test
    void unknownTypeNameMatchesNothing() {
        AdapterRegistry registry = AdapterRegistry.of(List.of(stub(SourceType.RSS)));
        assertTrue(registry.match("JIRA_API").isEmpty());
    }
}
