-- 移除 Hacker News:先删其历史资讯,再删来源(保留 adapter 类,不再有 HN_API 来源)
DELETE FROM news_item WHERE source_id IN (SELECT id FROM info_source WHERE code = 'hn');
DELETE FROM info_source WHERE code = 'hn';

-- V2EX 改用 RSS 订阅(原 JSON_API hot.json)
UPDATE info_source
SET type = 'RSS',
    base_url = 'https://www.v2ex.com/index.xml'
WHERE code = 'v2ex-hot';
