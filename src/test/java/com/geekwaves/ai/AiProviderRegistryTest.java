package com.geekwaves.ai;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderRegistryTest {

    private AiProvider stub(AiVendor vendor) {
        return new AiProvider() {
            @Override
            public AiVendor vendor() {
                return vendor;
            }

            @Override
            public Flux<AiChunk> streamChat(AiProviderConfig cfg, List<AiMessage> messages) {
                return Flux.empty();
            }
        };
    }

    @Test
    void emptyRegistryMatchesNothing() {
        AiProviderRegistry registry = AiProviderRegistry.of(List.of());
        assertTrue(registry.match(AiVendor.OPENAI_COMPAT).isEmpty());
        assertTrue(registry.match("ANTHROPIC").isEmpty());
    }

    @Test
    void nullVendorNameMatchesNothing() {
        AiProviderRegistry registry = AiProviderRegistry.of(List.of(stub(AiVendor.OPENAI_COMPAT)));
        assertTrue(registry.match((String) null).isEmpty());
    }

    @Test
    void matchFindsRegisteredProviderByVendor() {
        AiProvider provider = stub(AiVendor.ANTHROPIC);
        AiProviderRegistry registry = AiProviderRegistry.of(List.of(provider));
        Optional<AiProvider> matched = registry.match(AiVendor.ANTHROPIC);
        assertTrue(matched.isPresent());
        assertSame(provider, matched.get());
    }

    @Test
    void matchByStringNameIsCaseInsensitive() {
        AiProvider provider = stub(AiVendor.OPENAI_COMPAT);
        AiProviderRegistry registry = AiProviderRegistry.of(List.of(provider));
        assertSame(provider, registry.match("openai_compat").get());
        assertSame(provider, registry.match("OpenAi_Compat").get());
        assertSame(provider, registry.match("OPENAI_COMPAT").get());
    }

    @Test
    void unknownStringNameMatchesNothing() {
        AiProviderRegistry registry = AiProviderRegistry.of(List.of(stub(AiVendor.OPENAI_COMPAT)));
        assertTrue(registry.match("MISSING_VENDOR").isEmpty());
    }

    @Test
    void duplicateVendorThrowsIllegalStateException() {
        AiProvider first = stub(AiVendor.OPENAI_COMPAT);
        AiProvider second = stub(AiVendor.OPENAI_COMPAT);
        assertThrows(IllegalStateException.class, () -> AiProviderRegistry.of(List.of(first, second)));
    }
}
