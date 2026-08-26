package com.geekwaves.config.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.config.ProxyProperties;
import com.geekwaves.config.domain.ProxyConfig;
import com.geekwaves.config.domain.mapper.ProxyConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 代理配置的读写与缓存。
 * 单行配置:不存在时用 ProxyProperties(application.yaml)做兜底并落库首行。
 * 保存后刷新内存缓存,使 WebClient 在下一次构建时立即生效(无需重启)。
 */
@Service
@RequiredArgsConstructor
public class ProxySettingsService {
    private static final String PASSWORD_MASK = "********";

    private final ProxyConfigMapper mapper;
    private final ProxyProperties properties;

    private volatile ProxyConfig cache;

    /** 读取当前生效配置(含密码占位符;空则回退代理组件默认值)。不落库。 */
    public ProxyConfig current() {
        ProxyConfig cfg = cache;
        if (cfg == null) {
            cfg = loadOrSeed();
            cache = cfg;
        }
        return copyOf(cfg);
    }

    private ProxyConfig loadOrSeed() {
        List<ProxyConfig> rows = mapper.selectList(new QueryWrapper<>());
        if (rows.isEmpty()) {
            ProxyConfig fresh = new ProxyConfig();
            fresh.setEnabled(properties.isEnabled());
            fresh.setHost(properties.getHost());
            fresh.setPort(properties.getPort());
            fresh.setUsername(properties.getUsername());
            fresh.setPassword(properties.getPassword());
            mapper.insert(fresh);
            return fresh;
        }
        return rows.get(0);
    }

    /** 落库更新,并刷新缓存。 */
    @Transactional
    public ProxyConfig update(Boolean enabled, String host, int port, String username, String password) {
        ProxyConfig cfg = loadOrSeed();
        cfg.setEnabled(enabled == null || enabled);
        cfg.setHost(host);
        cfg.setPort(port);
        cfg.setUsername(StringUtils.hasText(username) ? username : null);
        if (password != null) {
            if (password.equals(PASSWORD_MASK) || password.isBlank()) {
                cfg.setPassword(null);
            } else {
                cfg.setPassword(password);
            }
        }
        mapper.updateById(cfg);
        this.cache = copyOf(cfg);
        return copyOf(cfg);
    }

    /** 对外暴露时,密码以占位符呈现,避免明文回显。 */
    private ProxyConfig copyOf(ProxyConfig src) {
        ProxyConfig d = new ProxyConfig();
        d.setId(src.getId());
        d.setEnabled(src.getEnabled());
        d.setHost(src.getHost());
        d.setPort(src.getPort());
        d.setUsername(src.getUsername());
        d.setPassword(src.getPassword() == null || src.getPassword().isBlank() ? null : PASSWORD_MASK);
        return d;
    }

    /** 供 WebClient 代理构造使用:返回真实密码(非掩码)。 */
    public String rawPassword() {
        ProxyConfig cfg = cache == null ? loadOrSeed() : cache;
        String pwd = cfg.getPassword();
        return (pwd == null || pwd.isBlank() || PASSWORD_MASK.equals(pwd)) ? null : pwd;
    }

    /** 供 WebClient 代理构造使用:当前生效的 host:port。 */
    public ProxyConfig effective() {
        return cache == null ? loadOrSeed() : cache;
    }
}
