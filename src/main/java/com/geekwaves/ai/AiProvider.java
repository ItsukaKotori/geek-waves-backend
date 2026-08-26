package com.geekwaves.ai;

import reactor.core.publisher.Flux;

import java.util.List;

public interface AiProvider {
    AiVendor vendor();

    Flux<AiChunk> streamChat(AiProviderConfig cfg, List<AiMessage> messages);
}
