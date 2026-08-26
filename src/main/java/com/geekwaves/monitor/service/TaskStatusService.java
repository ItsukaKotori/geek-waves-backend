package com.geekwaves.monitor.service;

import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.aggregation.service.FetchService;
import com.geekwaves.ai.domain.AiProvider;
import com.geekwaves.ai.domain.mapper.AiProviderMapper;
import com.geekwaves.monitor.dto.TaskStatusDto;
import com.geekwaves.monitor.support.SchedulerRunRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 应用内任务状态聚合:全部为只读查询,不改变任何业务状态。
 * dueNow 复用 FetchService.isDue(纯读),Provider 只暴露脱敏字段。
 */
@Service
@RequiredArgsConstructor
public class TaskStatusService {
    private final InfoSourceMapper sourceMapper;
    private final AiProviderMapper providerMapper;
    private final NewsItemMapper newsItemMapper;
    private final FetchService fetchService;
    private final ThreadPoolTaskExecutor fetchPool;
    private final SchedulerRunRegistry runRegistry;

    public TaskStatusDto status() {
        List<InfoSource> sources = sourceMapper.selectList(null);
        List<AiProvider> providers = providerMapper.selectList(null);
        long newsTotal = newsItemMapper.selectCount(null);

        List<TaskStatusDto.SourceHealthDto> sourceHealth = sources.stream()
                .map(s -> new TaskStatusDto.SourceHealthDto(
                        s.getId(), s.getName(), s.getCode(), s.getEnabled(),
                        s.getLastFetchStatus(), s.getLastFetchAt(), s.getLastError(),
                        s.getFailCount(), fetchService.isDue(s)))
                .toList();

        List<TaskStatusDto.ProviderStatusDto> providerStatus = providers.stream()
                .map(p -> new TaskStatusDto.ProviderStatusDto(
                        p.getId(), p.getName(), p.getVendor(), p.getModel(),
                        p.getEnabled(), p.getIsDefault(), p.getApiKeyEnc() != null && !p.getApiKeyEnc().isBlank()))
                .toList();

        List<TaskStatusDto.SchedulerRunDto> schedulers = runRegistry.snapshot().values().stream()
                .map(r -> new TaskStatusDto.SchedulerRunDto(
                        r.name(), r.lastStartAt(), r.lastDurationMs(), r.itemCount(), r.lastError()))
                .toList();

        return new TaskStatusDto(schedulers, poolStatus(), sourceHealth, providerStatus, newsTotal,
                System.currentTimeMillis());
    }

    private TaskStatusDto.PoolStatusDto poolStatus() {
        ThreadPoolExecutor executor = fetchPool.getThreadPoolExecutor();
        return new TaskStatusDto.PoolStatusDto(
                fetchPool.getActiveCount(),
                fetchPool.getPoolSize(),
                executor == null ? 0 : executor.getQueue().size());
    }
}
