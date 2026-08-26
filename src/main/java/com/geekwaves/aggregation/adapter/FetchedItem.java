package com.geekwaves.aggregation.adapter;

import java.time.LocalDateTime;

public record FetchedItem(
        String sourceItemId,
        String title,
        String url,
        String summary,
        String content,
        String author,
        String tagsString,
        String extraJson,
        int score,
        LocalDateTime publishedAt) {
}
