package com.geekwaves.aggregation.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdapterRegistry {
    private final Map<SourceType, SourceAdapter> adapters;

    private AdapterRegistry(Map<SourceType, SourceAdapter> adapters) {
        this.adapters = adapters;
    }

    public static AdapterRegistry of(List<SourceAdapter> adapters) {
        return new AdapterRegistry(adapters.stream()
                .collect(Collectors.toMap(SourceAdapter::type, Function.identity())));
    }

    public Optional<SourceAdapter> match(String typeName) {
        if (typeName == null) return Optional.empty();
        try {
            return Optional.ofNullable(adapters.get(SourceType.valueOf(typeName.trim().toUpperCase())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
