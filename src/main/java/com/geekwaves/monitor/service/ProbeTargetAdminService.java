package com.geekwaves.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.config.MonitorProperties;
import com.geekwaves.monitor.domain.ProbeTarget;
import com.geekwaves.monitor.domain.mapper.ProbeTargetMapper;
import com.geekwaves.monitor.dto.ProbeTargetUpsertRequest;
import com.geekwaves.monitor.support.ProbeTargetValidator;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.exception.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 探测目标 CRUD:字段校验集中在 Validator,数量受 maxTargets 上限约束。
 * 更新会重置 last_* 探测结果(host/port 变了旧结果无意义)。
 */
@Service
@RequiredArgsConstructor
public class ProbeTargetAdminService {
    private final ProbeTargetMapper mapper;
    private final ProbeTargetValidator validator;
    private final MonitorProperties props;

    public List<ProbeTarget> list() {
        return mapper.selectList(new QueryWrapper<ProbeTarget>()
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public ProbeTarget create(ProbeTargetUpsertRequest req) {
        validate(req);
        if (mapper.selectCount(null) >= props.getMaxTargets()) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST,
                    "探测目标已达上限(" + props.getMaxTargets() + ")");
        }
        ProbeTarget t = new ProbeTarget();
        apply(t, req);
        mapper.insert(t);
        return t;
    }

    public ProbeTarget update(Long id, ProbeTargetUpsertRequest req) {
        validate(req);
        ProbeTarget t = require(id);
        apply(t, req);
        // 目标本身变了,旧探测结果作废
        t.setLastProbeAt(null);
        t.setLastStatus(null);
        t.setLastLatencyMs(null);
        t.setLastError(null);
        t.setFailCount(0);
        mapper.updateById(t);
        return t;
    }

    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private ProbeTarget require(Long id) {
        ProbeTarget t = mapper.selectById(id);
        if (t == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "探测目标不存在");
        return t;
    }

    private void validate(ProbeTargetUpsertRequest req) {
        String err = validator.validateName(req.name());
        if (err == null) err = validator.validateHost(req.host());
        if (err == null) err = validator.validatePort(req.port());
        if (err == null) err = validator.validateTimeoutMs(req.timeoutMs());
        if (err == null) err = validator.validateIntervalSeconds(req.intervalSeconds());
        if (err != null) throw ServiceException.create(HttpStatus.BAD_REQUEST, err);
    }

    private void apply(ProbeTarget t, ProbeTargetUpsertRequest req) {
        t.setName(req.name().trim());
        t.setHost(req.host().trim());
        t.setPort(req.port());
        t.setProtocol("TCP");
        t.setEnabled(req.enabled() == null || req.enabled());
        t.setTimeoutMs(req.timeoutMs() == null ? 2000 : req.timeoutMs());
        t.setIntervalSeconds(req.intervalSeconds() == null ? 60 : req.intervalSeconds());
        t.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
    }
}
