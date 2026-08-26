package com.geekwaves.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AiProviderRegistry {
    private final Map<AiVendor, AiProvider> providers;

    private AiProviderRegistry(Map<AiVendor, AiProvider> providers) {
        this.providers = providers;
    }

    public static AiProviderRegistry of(List<AiProvider> providers) {
        return new AiProviderRegistry(providers.stream()
                .collect(Collectors.toMap(AiProvider::vendor, Function.identity())));
    }

    public Optional<AiProvider> match(AiVendor vendor) {
        return Optional.ofNullable(providers.get(vendor));
    }

    public Optional<AiProvider> match(String vendorName) {
        if (vendorName == null) return Optional.empty();
        try {
            return match(AiVendor.valueOf(vendorName.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
