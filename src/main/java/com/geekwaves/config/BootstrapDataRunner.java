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
        seedIfAbsent("GitHub Trending", "github-trending", "GITHUB_API", "https://api.github.com", 24);
        seedIfAbsent("V2EX", "v2ex-hot", "RSS", "https://www.v2ex.com/index.xml", 60);
        seedIfAbsent("Linux Do", "linux-do", "RSS", "https://linux.do/latest.rss", 60);
    }

    /** 内置源按 code 缺失才插入:老库升级补新源,用户数据零影响 */
    private void seedIfAbsent(String name, String code, String type, String url, int interval) {
        if (sourceMapper.selectCount(new QueryWrapper<InfoSource>().eq("code", code)) > 0) {
            return;
        }
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
