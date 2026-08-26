CREATE TABLE IF NOT EXISTS probe_target (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    protocol VARCHAR(10) DEFAULT 'TCP' NOT NULL,
    enabled TINYINT DEFAULT 1 NOT NULL,
    timeout_ms INT DEFAULT 2000 NOT NULL,
    interval_seconds INT DEFAULT 60 NOT NULL,
    sort_order INT DEFAULT 0 NOT NULL,
    last_probe_at TIMESTAMP,
    last_status VARCHAR(20),
    last_latency_ms INT,
    last_error VARCHAR(500),
    fail_count INT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP, update_time TIMESTAMP,
    create_by VARCHAR(64), update_by VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_probe_enabled ON probe_target (enabled);
