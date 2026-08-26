package com.geekwaves.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapDataRunnerTest {

    private InfoSourceMapper sourceMapper;
    private BootstrapDataRunner runner;

    @BeforeEach
    void setUp() {
        sourceMapper = mock(InfoSourceMapper.class);
        runner = new BootstrapDataRunner(sourceMapper);
    }

    @Test
    void seedsThreeSourcesWhenTableEmpty() {
        when(sourceMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        runner.run(null);
        ArgumentCaptor<InfoSource> captor = ArgumentCaptor.forClass(InfoSource.class);
        verify(sourceMapper, times(3)).insert(captor.capture());
        List<InfoSource> rows = captor.getAllValues();
        assertEquals("hn", findRow(rows, "hn").getCode());
        assertEquals("github-trending", findRow(rows, "github-trending").getCode());
        assertEquals("v2ex-hot", findRow(rows, "v2ex-hot").getCode());
        assertEquals("{}", findRow(rows, "hn").getConfigJson());
        assertEquals(Boolean.TRUE, findRow(rows, "hn").getEnabled());
        assertEquals(Integer.valueOf(15), findRow(rows, "hn").getRefreshMinutes());
        assertNotNull(findRow(rows, "hn").getBaseUrl());
    }

    @Test
    void skipsSeedingWhenSourcesAlreadyExist() {
        when(sourceMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        runner.run(null);
        verify(sourceMapper, never()).insert(any(InfoSource.class));
    }

    private InfoSource findRow(List<InfoSource> rows, String code) {
        return rows.stream().filter(r -> code.equals(r.getCode())).findFirst().orElseThrow();
    }
}
