package com.geekwaves.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiCompatProvider implements AiProvider {
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public AiVendor vendor() {
        return AiVendor.OPENAI_COMPAT;
    }

    @Override
    public Flux<AiChunk> streamChat(AiProviderConfig cfg, List<AiMessage> messages) {
        WebClient client = webClientBuilder.baseUrl(cfg.baseUrl()).clone().build();
        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "messages", messages.stream()
                        .map(m -> Map.of("role", m.role(), "content", m.content()))
                        .toList(),
                "stream", true,
                "max_tokens", 1024);
        return client.post().uri("/chat/completions")
                .headers(h -> {
                    if (cfg.apiKey() != null && !cfg.apiKey().isBlank()) {
                        h.setBearerAuth(cfg.apiKey());
                    }
                })
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .mapNotNull(this::parseLine);
    }

    private AiChunk parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String payload = line.startsWith("data:") ? line.substring(5) : line;
        payload = payload.trim();
        if ("[DONE]".equals(payload)) {
            return new AiChunk("", true);
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            String delta = node.path("choices").path(0).path("delta").path("content").asString("");
            return new AiChunk(delta, false);
        } catch (Exception e) {
            return null;
        }
    }
}
