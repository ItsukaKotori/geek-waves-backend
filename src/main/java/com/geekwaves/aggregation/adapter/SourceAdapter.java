package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;

import java.time.Instant;
import java.util.List;

public interface SourceAdapter {
    SourceType type();
    List<FetchedItem> fetch(InfoSource source, Instant since) throws Exception;
}
