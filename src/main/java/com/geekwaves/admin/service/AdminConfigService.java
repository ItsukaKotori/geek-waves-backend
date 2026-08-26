package com.geekwaves.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geekwaves.admin.dto.FrameworkUpsertRequest;
import com.geekwaves.admin.dto.ProviderUpsertRequest;
import com.geekwaves.admin.dto.SourceUpsertRequest;
import com.geekwaves.aggregation.domain.FrameworkWatch;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.FrameworkWatchMapper;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.service.FetchService;
import com.geekwaves.ai.domain.AiProvider;
import com.geekwaves.ai.domain.mapper.AiProviderMapper;
import com.geekwaves.config.CryptoService;
import com.geekwaves.config.port.LockPort;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.exception.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminConfigService {
    private final InfoSourceMapper sourceMapper;
    private final FrameworkWatchMapper frameworkMapper;
    private final AiProviderMapper providerMapper;
    private final CryptoService cryptoService;
    private final LockPort lockPort;
    private final FetchService fetchService;
    private final ThreadPoolTaskExecutor fetchPool;

    // ---------- sources ----------
    public Page<InfoSource> listSources(long current, long size) {
        return sourceMapper.selectPage(new Page<>(current, size),
                new QueryWrapper<InfoSource>().orderByAsc("sort_order").orderByDesc("update_time"));
    }

    public InfoSource createSource(SourceUpsertRequest req) {
        if (sourceMapper.selectCount(new QueryWrapper<InfoSource>().eq("code", req.getCode())) > 0) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "source code 已存在");
        }
        InfoSource s = doToDomain(req, new InfoSource());
        sourceMapper.insert(s);
        return s;
    }

    public InfoSource updateSource(Long id, SourceUpsertRequest req) {
        InfoSource s = sourceMapper.selectById(id);
        if (s == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "source 不存在");
        sourceMapper.updateById(doToDomain(req, s));
        return sourceMapper.selectById(id);
    }

    private InfoSource doToDomain(SourceUpsertRequest req, InfoSource s) {
        s.setName(req.getName());
        s.setCode(req.getCode());
        s.setType(req.getType());
        s.setBaseUrl(req.getBaseUrl());
        s.setConfigJson(req.getConfigJson());
        if (req.getEnabled() != null) s.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) s.setSortOrder(req.getSortOrder());
        if (req.getRefreshMinutes() != null) s.setRefreshMinutes(req.getRefreshMinutes());
        return s;
    }

    public void deleteSource(Long id) {
        sourceMapper.deleteById(id);
    }

    public boolean triggerFetch(Long id) {
        if (!lockPort.tryLock("fetch:trigger:" + id, Duration.ofMinutes(1))) {
            return false;
        }
        InfoSource source = sourceMapper.selectById(id);
        if (source == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "source 不存在");
        fetchPool.execute(() -> fetchService.fetchNow(source));
        return true;
    }

    // ---------- frameworks ----------
    public Page<FrameworkWatch> listFrameworks(long current, long size) {
        return frameworkMapper.selectPage(new Page<>(current, size),
                new QueryWrapper<FrameworkWatch>().orderByDesc("update_time"));
    }

    public FrameworkWatch createFramework(FrameworkUpsertRequest req) {
        FrameworkWatch f = new FrameworkWatch();
        f.setName(req.getName());
        f.setGithubRepo(req.getGithubRepo());
        frameworkMapper.insert(f);
        return f;
    }

    public FrameworkWatch updateFramework(Long id, FrameworkUpsertRequest req) {
        FrameworkWatch f = frameworkMapper.selectById(id);
        if (f == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "framework 不存在");
        f.setName(req.getName());
        f.setGithubRepo(req.getGithubRepo());
        frameworkMapper.updateById(f);
        return frameworkMapper.selectById(id);
    }

    public void deleteFramework(Long id) {
        frameworkMapper.deleteById(id);
    }

    // ---------- providers ----------
    public List<AiProvider> listProviders() {
        List<AiProvider> providers = providerMapper.selectList(
                new QueryWrapper<AiProvider>().orderByDesc("is_default"));
        providers.forEach(p -> p.setApiKeyEnc(null)); // 永不外发
        return providers;
    }

    public AiProvider createProvider(ProviderUpsertRequest req) {
        AiProvider p = new AiProvider();
        applyProvider(req, p, true);
        providerMapper.insert(p);
        return p;
    }

    @Transactional
    public AiProvider updateProvider(Long id, ProviderUpsertRequest req) {
        AiProvider p = providerMapper.selectById(id);
        if (p == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "provider 不存在");
        applyProvider(req, p, false);
        providerMapper.updateById(p);
        if (Boolean.TRUE.equals(p.getIsDefault())) setDefault(id);
        return providerMapper.selectById(id);
    }

    private void applyProvider(ProviderUpsertRequest req, AiProvider p, boolean isCreate) {
        p.setName(req.getName());
        p.setVendor(req.getVendor());
        p.setBaseUrl(req.getBaseUrl());
        p.setModel(req.getModel());
        if (req.getEnabled() != null) p.setEnabled(req.getEnabled());
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            p.setApiKeyEnc(cryptoService.encrypt(req.getApiKey()));
        } else if (isCreate) {
            p.setApiKeyEnc(null);
        }
        if (req.getIsDefault() != null) p.setIsDefault(req.getIsDefault());
    }

    @Transactional
    public void setDefault(Long id) {
        providerMapper.selectList(new QueryWrapper<AiProvider>().eq("is_default", 1))
                .forEach(o -> {
                    o.setIsDefault(false);
                    providerMapper.updateById(o);
                });
        AiProvider p = providerMapper.selectById(id);
        if (p == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "provider 不存在");
        p.setIsDefault(true);
        p.setEnabled(true);
        providerMapper.updateById(p);
    }

    public void deleteProvider(Long id) {
        providerMapper.deleteById(id);
    }
}
