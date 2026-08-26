package com.geekwaves.config.service;

import com.geekwaves.config.ProxyProperties;
import com.geekwaves.config.domain.ProxyConfig;
import com.geekwaves.config.domain.mapper.ProxyConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxySettingsServiceTest {

    private ProxyConfigMapper mapper;
    private ProxyProperties props;
    private ProxySettingsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProxyConfigMapper.class);
        props = new ProxyProperties();
        props.setEnabled(true);
        props.setHost("127.0.0.1");
        props.setPort(7890);
        service = new ProxySettingsService(mapper, props);
    }

    @Test
    void seedsDefaultFromPropertiesWhenTableEmpty() {
        when(mapper.selectList(any())).thenReturn(List.of());
        ProxyConfig cfg = service.current();
        assertTrue(cfg.getEnabled());
        assertEquals("127.0.0.1", cfg.getHost());
        assertEquals(7890, cfg.getPort());
        verify(mapper).insert(any(ProxyConfig.class));
    }

    @Test
    void returnsExistingRowWithoutSeeding() {
        ProxyConfig existing = new ProxyConfig();
        existing.setEnabled(false);
        existing.setHost("10.0.0.1");
        existing.setPort(3128);
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        ProxyConfig cfg = service.current();
        assertFalse(cfg.getEnabled());
        assertEquals("10.0.0.1", cfg.getHost());
        assertEquals(3128, cfg.getPort());
        verify(mapper, never()).insert(any(ProxyConfig.class));
    }

    @Test
    void updatePersistsChangesAndRefreshesCache() {
        ProxyConfig existing = new ProxyConfig();
        existing.setEnabled(true);
        existing.setHost("old");
        existing.setPort(1);
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        service.update(true, "new-host", 8888, "user", "secret");
        ArgumentCaptor<ProxyConfig> captor = ArgumentCaptor.forClass(ProxyConfig.class);
        verify(mapper).updateById(captor.capture());
        ProxyConfig saved = captor.getValue();
        assertEquals("new-host", saved.getHost());
        assertEquals(8888, saved.getPort());
        assertEquals("user", saved.getUsername());
        assertEquals("secret", saved.getPassword());
        // 更新后 current 立即读到新值
        assertEquals("new-host", service.current().getHost());
    }

    @Test
    void masksPasswordOnReadButKeepsRawForClient() {
        ProxyConfig existing = new ProxyConfig();
        existing.setEnabled(true);
        existing.setHost("h");
        existing.setPort(7890);
        existing.setPassword("secret");
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        // 先读一次填充缓存
        service.current();
        assertEquals("********", service.current().getPassword());
        assertEquals("secret", service.rawPassword());
        // 掩码更新后清除
        service.update(true, "h", 7890, null, "********");
        assertNull((service.current()).getPassword());
        assertNull(service.rawPassword());
    }

    @Test
    void blankPasswordClearsExisting() {
        ProxyConfig existing = new ProxyConfig();
        existing.setEnabled(true);
        existing.setHost("h");
        existing.setPort(7890);
        existing.setPassword("secret");
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        service.current();
        service.update(true, "h", 7890, null, "");
        assertNull(service.rawPassword());
    }
}
