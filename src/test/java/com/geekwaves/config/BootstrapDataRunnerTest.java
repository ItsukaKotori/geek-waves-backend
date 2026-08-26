package com.geekwaves.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void seedsTwoSourcesWhenTableEmpty() {
        when(sourceMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        runner.run(null);
        ArgumentCaptor<InfoSource> captor = ArgumentCaptor.forClass(InfoSource.class);
        verify(sourceMapper, times(2)).insert(captor.capture());
        List<InfoSource> rows = captor.getAllValues();
        assertEquals("github-trending", findRow(rows, "github-trending").getCode());
        assertEquals("v2ex-hot", findRow(rows, "v2ex-hot").getCode());
        assertEquals("RSS", findRow(rows, "v2ex-hot").getType());
        assertEquals("https://www.v2ex.com/index.xml", findRow(rows, "v2ex-hot").getBaseUrl());
        assertEquals("{}", findRow(rows, "v2ex-hot").getConfigJson());
        assertEquals(Boolean.TRUE, findRow(rows, "v2ex-hot").getEnabled());
        assertEquals(Integer.valueOf(60), findRow(rows, "v2ex-hot").getRefreshMinutes());
        assertEquals(2, rows.size());
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
