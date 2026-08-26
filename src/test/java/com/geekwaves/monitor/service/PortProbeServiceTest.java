package com.geekwaves.monitor.service;

import com.geekwaves.config.MonitorProperties;
import com.geekwaves.monitor.domain.ProbeTarget;
import com.geekwaves.monitor.domain.mapper.ProbeTargetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.ServerSocket;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用真实本地 ServerSocket 验证 TCP 探测(快、确定、无外部依赖);
 * 持久化用 mock Mapper 断言 UpdateWrapper 调用。
 */
@ExtendWith(MockitoExtension.class)
class PortProbeServiceTest {

    @Mock
    private ProbeTargetMapper mapper;
    @Mock
    private ThreadPoolTaskExecutor fetchPool;

    private PortProbeService service;

    @BeforeEach
    void setUp() {
        MonitorProperties props = new MonitorProperties();
        service = new PortProbeService(mapper, props, fetchPool);
    }

    private ProbeTarget target(String host, int port) {
        ProbeTarget t = new ProbeTarget();
        t.setId(1L);
        t.setName("t");
        t.setHost(host);
        t.setPort(port);
        t.setEnabled(true);
        t.setTimeoutMs(500);
        t.setIntervalSeconds(60);
        t.setFailCount(0);
        return t;
    }

    @Test
    void probeOpenLocalPortIsUpAndResetsFailCount() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            ProbeTarget t = target("127.0.0.1", server.getLocalPort());
            t.setFailCount(3);

            ProbeTarget result = service.probeOne(t);

            assertEquals("UP", result.getLastStatus());
            assertNotNull(result.getLastLatencyMs());
            assertNotNull(result.getLastProbeAt());
            assertEquals(0, result.getFailCount());

            verify(mapper).update(any(), any());
        }
    }

    @Test
    void probeClosedPortIsDownAndIncrementsFailCount() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();
        } // 关闭后端口几乎必然不可连
        ProbeTarget t = target("127.0.0.1", port);

        ProbeTarget result = service.probeOne(t);

        assertEquals("DOWN", result.getLastStatus());
        assertEquals(1, result.getFailCount());
        assertNotNull(result.getLastError());

        verify(mapper).update(any(), any());
    }

    // 注:TIMEOUT/DNS 失败路径依赖网络环境(本机透明代理会接受任意外网连接),
    // 无法确定性构造;其异常处理与 DOWN 路径共用同一 catch 结构,由 closed-port 用例覆盖。

    @Test
    void neverProbedTargetIsDue() {
        ProbeTarget t = target("127.0.0.1", 80);
        assertTrue(service.isDue(t));
    }

    @Test
    void probedWithinIntervalIsNotDue() {
        ProbeTarget t = target("127.0.0.1", 80);
        t.setLastProbeAt(LocalDateTime.now().minusSeconds(10));
        t.setIntervalSeconds(60);
        assertEquals(false, service.isDue(t));

        t.setLastProbeAt(LocalDateTime.now().minusSeconds(120));
        assertEquals(true, service.isDue(t));
    }

    @Test
    void manualProbeMissingTargetThrows() {
        when(mapper.selectById(999L)).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.itsuka.core.exception.ServiceException.class,
                () -> service.manualProbe(999L));
    }
}
