package com.geekwaves.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnthropicProvider implements AiProvider {
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public AiVendor vendor() {
        return AiVendor.ANTHROPIC;
    }

    @Override
    public Flux<AiChunk> streamChat(AiProviderConfig cfg, List<AiMessage> messages) {
        WebClient client = webClientBuilder.baseUrl(cfg.baseUrl()).clone().build();
        String system = messages.stream()
                .filter(m -> "system".equals(m.role()))
                .map(AiMessage::content)
                .findFirst()
                .orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.model());
        body.put("max_tokens", 1024);
        body.put("stream", true);
        if (system != null) {
            body.put("system", system);
        }
        body.put("messages", messages.stream()
                .filter(m -> !"system".equals(m.role()))
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        return client.post().uri("/v1/messages")
                .headers(h -> {
                    h.set("x-api-key", cfg.apiKey());
                    h.set("anthropic-version", ANTHROPIC_VERSION);
                })
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .mapNotNull(this::parseLine)
                .onErrorResume(e -> Flux.just(new AiChunk("\n[流式中断] " + e.getMessage(), true)));
    }

    private AiChunk parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String payload = line.startsWith("data:") ? line.substring(5) : line;
        payload = payload.trim();
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asString("");
            if ("message_stop".equals(type)) {
                return new AiChunk("", true);
            }
            if ("content_block_delta".equals(type)
                    && "text_delta".equals(node.path("delta").path("type").asString())) {
                return new AiChunk(node.path("delta").path("text").asString(""), false);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
