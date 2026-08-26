CREATE TABLE info_source (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(64) NOT NULL,
    type VARCHAR(20) NOT NULL,
    base_url VARCHAR(512),
    config_json JSON,
    enabled TINYINT DEFAULT 1 NOT NULL,
    sort_order INT DEFAULT 0 NOT NULL,
    refresh_minutes INT DEFAULT 30 NOT NULL,
    last_fetch_at DATETIME(3),
    last_fetch_status VARCHAR(20),
    last_error VARCHAR(1000),
    fail_count INT DEFAULT 0 NOT NULL,
    create_time DATETIME(3), update_time DATETIME(3),
    create_by VARCHAR(64), update_by VARCHAR(64),
    CONSTRAINT uk_source_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE news_item (
    id BIGINT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    category VARCHAR(10) NOT NULL,
    source_item_id VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    url VARCHAR(1024),
    summary VARCHAR(2048),
    content LONGTEXT,
    author VARCHAR(128),
    tags VARCHAR(255),
    extra_json JSON,
    score INT DEFAULT 0 NOT NULL,
    published_at DATETIME(3),
    fetched_at DATETIME(3),
    ai_status VARCHAR(10) DEFAULT 'NONE' NOT NULL,
    ai_summary LONGTEXT,
    ai_summary_at DATETIME(3),
    create_time DATETIME(3), update_time DATETIME(3),
    create_by VARCHAR(64), update_by VARCHAR(64),
    CONSTRAINT uk_source_item UNIQUE (source_id, source_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_news_category_published ON news_item (category, published_at);
CREATE TABLE framework_watch (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    github_repo VARCHAR(128) NOT NULL,
    latest_version VARCHAR(64),
    last_release_at DATETIME(3),
    create_time DATETIME(3), update_time DATETIME(3),
    create_by VARCHAR(64), update_by VARCHAR(64),
    CONSTRAINT uk_fw_repo UNIQUE (github_repo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ai_provider (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    vendor VARCHAR(20) NOT NULL,
    base_url VARCHAR(512),
    api_key_enc VARCHAR(512),
    model VARCHAR(128),
    enabled TINYINT DEFAULT 0 NOT NULL,
    is_default TINYINT DEFAULT 0 NOT NULL,
    create_time DATETIME(3), update_time DATETIME(3),
    create_by VARCHAR(64), update_by VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
