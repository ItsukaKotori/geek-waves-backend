package com.geekwaves.admin;

import com.geekwaves.admin.dto.ProviderUpsertRequest;
import com.geekwaves.admin.dto.SourceUpsertRequest;
import com.geekwaves.admin.service.AdminConfigService;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.FrameworkWatchMapper;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.service.FetchService;
import com.geekwaves.ai.domain.AiProvider;
import com.geekwaves.ai.domain.mapper.AiProviderMapper;
import com.geekwaves.config.CryptoService;
import com.geekwaves.config.port.LockPort;
import org.itsuka.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminConfigServiceTest {
    private InfoSourceMapper sourceMapper;
    private FrameworkWatchMapper frameworkMapper;
    private AiProviderMapper providerMapper;
    private CryptoService cryptoService;
    private LockPort lockPort;
    private FetchService fetchService;
    private ThreadPoolTaskExecutor fetchPool;
    private AdminConfigService service;

    @BeforeEach
    void setUp() {
        sourceMapper = mock(InfoSourceMapper.class);
        frameworkMapper = mock(FrameworkWatchMapper.class);
        providerMapper = mock(AiProviderMapper.class);
        cryptoService = mock(CryptoService.class);
        lockPort = mock(LockPort.class);
        fetchService = mock(FetchService.class);
        fetchPool = mock(ThreadPoolTaskExecutor.class);
        service = new AdminConfigService(sourceMapper, frameworkMapper, providerMapper, cryptoService, lockPort, fetchService, fetchPool);
        when(cryptoService.encrypt("secret")).thenReturn("E1");
    }

    @Test
    void createProviderEncryptsKey() {
        when(providerMapper.insert(any(AiProvider.class))).thenReturn(1);
        ProviderUpsertRequest req = new ProviderUpsertRequest();
        req.setName("DeepSeek");
        req.setVendor("OPENAI_COMPAT");
        req.setApiKey("secret");
        req.setModel("deepseek-chat");
        req.setEnabled(true);
        service.createProvider(req);
        ArgumentCaptor<AiProvider> captor = ArgumentCaptor.forClass(AiProvider.class);
        verify(providerMapper).insert(captor.capture());
        assertEquals("E1", captor.getValue().getApiKeyEnc());
    }

    @Test
    void listProvidersMasksKey() {
        AiProvider p = new AiProvider();
        p.setId(1L);
        p.setApiKeyEnc("SECRET");
        when(providerMapper.selectList(any())).thenReturn(List.of(p));
        List<AiProvider> list = service.listProviders();
        assertNull(list.get(0).getApiKeyEnc());
    }

    @Test
    void createSourceDuplicateCodeThrows400() {
        when(sourceMapper.selectCount(any())).thenReturn(1L);
        SourceUpsertRequest req = new SourceUpsertRequest();
        req.setName("rss");
        req.setCode("rss");
        req.setType("RSS");
        ServiceException e = assertThrows(ServiceException.class, () -> service.createSource(req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void updateSourceNotFoundThrows404() {
        when(sourceMapper.selectById(99L)).thenReturn(null);
        SourceUpsertRequest req = new SourceUpsertRequest();
        req.setName("rss");
        req.setCode("rss");
        req.setType("RSS");
        ServiceException e = assertThrows(ServiceException.class, () -> service.updateSource(99L, req));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void updateProviderNotFoundThrows404() {
        when(providerMapper.selectById(99L)).thenReturn(null);
        ProviderUpsertRequest req = new ProviderUpsertRequest();
        req.setName("DeepSeek");
        req.setVendor("OPENAI_COMPAT");
        ServiceException e = assertThrows(ServiceException.class, () -> service.updateProvider(99L, req));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void updateProviderWithBlankApiKeyKeepsExisting() {
        AiProvider p = new AiProvider();
        p.setId(1L);
        p.setApiKeyEnc("E0");
        p.setIsDefault(false);
        when(providerMapper.selectById(1L)).thenReturn(p);
        ProviderUpsertRequest req = new ProviderUpsertRequest();
        req.setName("DeepSeek");
        req.setVendor("OPENAI_COMPAT");
        req.setApiKey(" ");
        req.setIsDefault(null);
        service.updateProvider(1L, req);
        ArgumentCaptor<AiProvider> captor = ArgumentCaptor.forClass(AiProvider.class);
        verify(providerMapper).updateById(captor.capture());
        assertEquals("E0", captor.getValue().getApiKeyEnc());
    }

    @Test
    void updateProviderReFetchesAfterSetDefault() {
        AiProvider p = new AiProvider();
        p.setId(1L);
        p.setIsDefault(false);
        when(providerMapper.selectById(1L)).thenReturn(p);
        when(providerMapper.selectList(any())).thenReturn(List.of());
        ProviderUpsertRequest req = new ProviderUpsertRequest();
        req.setName("DeepSeek");
        req.setVendor("OPENAI_COMPAT");
        req.setIsDefault(true);
        AiProvider result = service.updateProvider(1L, req);
        assertEquals(p, result);
        assertEquals(Boolean.TRUE, result.getIsDefault());
        verify(providerMapper, times(3)).selectById(1L);
    }

    @Test
    void setDefaultClearsOthersAndSetsTarget() {
        AiProvider oldDefault = new AiProvider();
        oldDefault.setId(1L);
        oldDefault.setIsDefault(true);
        AiProvider target = new AiProvider();
        target.setId(5L);
        when(providerMapper.selectList(any())).thenReturn(List.of(oldDefault));
        when(providerMapper.selectById(5L)).thenReturn(target);
        service.setDefault(5L);
        assertEquals(Boolean.FALSE, oldDefault.getIsDefault());
        assertEquals(Boolean.TRUE, target.getIsDefault());
        assertEquals(Boolean.TRUE, target.getEnabled());
        verify(providerMapper).updateById(oldDefault);
        verify(providerMapper).updateById(target);
    }

    @Test
    void triggerFetchAcquiresLockAndSubmits() {
        InfoSource source = new InfoSource();
        source.setId(1L);
        when(lockPort.tryLock("fetch:trigger:1", Duration.ofMinutes(1))).thenReturn(true);
        when(sourceMapper.selectById(1L)).thenReturn(source);
        assertTrue(service.triggerFetch(1L));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(fetchPool).execute(captor.capture());
        captor.getValue().run();
        verify(fetchService).fetchNow(source);
    }

    @Test
    void triggerFetchReturnsFalseWhenLockBusy() {
        when(lockPort.tryLock("fetch:trigger:1", Duration.ofMinutes(1))).thenReturn(false);
        assertFalse(service.triggerFetch(1L));
        verify(fetchPool, never()).execute(any());
    }

    @Test
    void triggerFetchThrows404WhenSourceMissing() {
        when(lockPort.tryLock("fetch:trigger:99", Duration.ofMinutes(1))).thenReturn(true);
        when(sourceMapper.selectById(99L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.triggerFetch(99L));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }
}
