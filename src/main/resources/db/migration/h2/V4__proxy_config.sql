-- 代理配置(单行):用于所有出站 HTTP(来源抓取 / AI / 框架 / 工具)走代理。设置页可修改。
CREATE TABLE IF NOT EXISTS proxy_config (
    id BIGINT PRIMARY KEY,
    enabled TINYINT DEFAULT 1 NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT DEFAULT 7890 NOT NULL,
    username VARCHAR(255),
    password VARCHAR(255),
    create_time TIMESTAMP, update_time TIMESTAMP,
    create_by VARCHAR(64), update_by VARCHAR(64)
);
