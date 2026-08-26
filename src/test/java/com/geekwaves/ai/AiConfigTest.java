package com.geekwaves.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class AiConfigTest {

    @Autowired
    private AiProviderRegistry registry;

    @Test
    void registryBeanRegistersAllVendors() {
        assertTrue(registry.match(AiVendor.OPENAI_COMPAT).isPresent());
        assertTrue(registry.match(AiVendor.ANTHROPIC).isPresent());
    }
}
