package com.geekwaves.config;

import com.geekwaves.aggregation.adapter.FetchedItem;
import com.geekwaves.aggregation.adapter.RssAdapter;
import com.geekwaves.aggregation.domain.InfoSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证:通过用户在设置页配置的代理(默认 127.0.0.1:7890)用 RssAdapter
 * 拉取真实 V2EX RSS,证明 RssAdapter 已注入带代理的共享 WebClient.Builder。
 * 仅当显式传入 -Dgeekwaves.e2e=true 才运行(避免 CI/无代理环境失败)。
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:proxye2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@EnabledIfSystemProperty(named = "geekwaves.e2e", matches = "true")
class ProxyE2ETest {

    @Autowired
    private RssAdapter rssAdapter;

    @Test
    void fetchesV2exFeedThroughProxyViaRssAdapter() {
        InfoSource source = new InfoSource();
        source.setName("V2EX");
        source.setCode("v2ex-hot");
        source.setType("RSS");
        source.setBaseUrl("https://www.v2ex.com/index.xml");
        source.setConfigJson("{}");
        try {
            List<FetchedItem> items = rssAdapter.fetch(source, null);
            assertTrue(!items.isEmpty(), "RssAdapter 应能通过代理获取到 V2EX 资讯");
        } catch (Exception e) {
            throw new AssertionError("RssAdapter 走代理拉取 V2EX 失败: " + e.getMessage(), e);
        }
    }
}
