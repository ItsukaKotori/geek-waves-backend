package com.geekwaves.config;

import com.geekwaves.admin.controller.ProxyAdminController;
import com.geekwaves.config.domain.ProxyConfig;
import com.geekwaves.config.service.ProxySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:proxycfg;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class ProxyConfigIntegrationTest {

    @Autowired
    private ProxySettingsService service;
    @Autowired
    private ProxyAdminController controller;

    @Test
    void bootSeedsDefaultAndReadsViaController() {
        ProxyConfig cfg = controller.get().getData();
        assertTrue(Boolean.TRUE.equals(cfg.getEnabled()));
        assertEquals("127.0.0.1", cfg.getHost());
        assertEquals(7890, cfg.getPort());
    }

    @Test
    void updateReflectsImmediately() {
        service.update(true, "10.0.0.5", 8888, null, null);
        assertEquals("10.0.0.5", service.effective().getHost());
        assertEquals(8888, service.effective().getPort());
        // 还原,避免影响同上下文其它测试
        service.update(true, "127.0.0.1", 7890, null, null);
    }
}
