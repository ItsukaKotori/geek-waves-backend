package com.geekwaves.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BootstrapDataRunner implements ApplicationRunner {
    private final InfoSourceMapper sourceMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (sourceMapper.selectCount(new QueryWrapper<>()) > 0) {
            return;
        }
        seed("GitHub Trending", "github-trending", "GITHUB_API", "https://api.github.com", 24);
        seed("V2EX", "v2ex-hot", "RSS", "https://www.v2ex.com/index.xml", 60);
    }

    private void seed(String name, String code, String type, String url, int interval) {
        InfoSource s = new InfoSource();
        s.setName(name);
        s.setCode(code);
        s.setType(type);
        s.setBaseUrl(url);
        s.setConfigJson("{}");
        s.setEnabled(true);
        s.setRefreshMinutes(interval);
        s.setSortOrder(0);
        sourceMapper.insert(s);
    }
}
