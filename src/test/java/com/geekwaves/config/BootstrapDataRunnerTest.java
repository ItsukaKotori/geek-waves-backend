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
    void seedsAllBuiltinsWhenNoneExist() {
        when(sourceMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        runner.run(null);
        ArgumentCaptor<InfoSource> captor = ArgumentCaptor.forClass(InfoSource.class);
        verify(sourceMapper, times(3)).insert(captor.capture());
        List<InfoSource> rows = captor.getAllValues();
        assertEquals("github-trending", findRow(rows, "github-trending").getCode());
        assertEquals("v2ex-hot", findRow(rows, "v2ex-hot").getCode());
        assertEquals("RSS", findRow(rows, "v2ex-hot").getType());
        assertEquals("https://www.v2ex.com/index.xml", findRow(rows, "v2ex-hot").getBaseUrl());
        assertEquals("{}", findRow(rows, "v2ex-hot").getConfigJson());
        assertEquals(Boolean.TRUE, findRow(rows, "v2ex-hot").getEnabled());
        assertEquals(Integer.valueOf(60), findRow(rows, "v2ex-hot").getRefreshMinutes());
        assertLinuxDo(findRow(rows, "linux-do"));
        assertEquals(3, rows.size());
    }

    @Test
    void seedsOnlyMissingBuiltinsOnExistingInstall() {
        // 依次检查 github-trending / v2ex-hot / linux-do:前两个已存在,linux-do 缺失
        when(sourceMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L, 1L, 0L);
        runner.run(null);
        ArgumentCaptor<InfoSource> captor = ArgumentCaptor.forClass(InfoSource.class);
        verify(sourceMapper, times(1)).insert(captor.capture());
        assertEquals("linux-do", captor.getValue().getCode());
        assertLinuxDo(captor.getValue());
    }

    @Test
    void insertsNothingWhenAllBuiltinsPresent() {
        when(sourceMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        runner.run(null);
        verify(sourceMapper, never()).insert(any(InfoSource.class));
    }

    private void assertLinuxDo(InfoSource row) {
        assertEquals("Linux Do", row.getName());
        assertEquals("RSS", row.getType());
        assertEquals("https://linux.do/latest.rss", row.getBaseUrl());
        assertEquals("{}", row.getConfigJson());
        assertEquals(Boolean.TRUE, row.getEnabled());
        assertEquals(Integer.valueOf(60), row.getRefreshMinutes());
    }

    private InfoSource findRow(List<InfoSource> rows, String code) {
        return rows.stream().filter(r -> code.equals(r.getCode())).findFirst().orElseThrow();
    }
}
