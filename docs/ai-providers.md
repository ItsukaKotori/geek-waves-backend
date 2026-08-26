# GeekWaves AI 厂商接入指南(BYOK)

GeekWaves 采用 BYOK(Bring Your Own Key):AI 厂商地址与密钥由用户在本站后台配置,密钥用 AES-256-GCM 加密后落库,接口只出明文进出(永不回显)。

## 支持协议

| vendor | 协议 | 要求 |
|---|---|---|
| `OPENAI_COMPAT` | OpenAI Chat Completions(`POST {base_url}/chat/completions`) | 任意 OpenAI 兼容服务(OpenAI / DeepSeek / Ollama / vLLM / LM Studio…) |
| `ANTHROPIC` | Anthropic Messages(`POST {base_url}/v1/messages`) | 头 `x-api-key` + `anthropic-version: 2023-06-01` |

## 配置示例(管理接口)

`POST /api/admin/providers`,Body 字段:`name`(必填)、`vendor`(必填)、`baseUrl`、`apiKey`(明文,仅写入)、`model`、`enabled`、`isDefault`。

- DeepSeek:

```json
{
  "name": "DeepSeek",
  "vendor": "OPENAI_COMPAT",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "sk-xxxxxxxx",
  "model": "deepseek-chat",
  "enabled": true,
  "isDefault": true
}
```

- OpenAI(直连或中转站):

```json
{
  "name": "OpenAI",
  "vendor": "OPENAI_COMPAT",
  "baseUrl": "https://api.openai.com",
  "apiKey": "sk-xxxxxxxx",
  "model": "gpt-4o-mini",
  "enabled": true,
  "isDefault": true
}
```

- Ollama(本地):

```json
{
  "name": "Ollama",
  "vendor": "OPENAI_COMPAT",
  "baseUrl": "http://localhost:11434/v1",
  "model": "qwen2.5:7b",
  "enabled": true,
  "isDefault": true
}
```

- Anthropic:

```json
{
  "name": "Claude",
  "vendor": "ANTHROPIC",
  "baseUrl": "https://api.anthropic.com",
  "apiKey": "sk-ant-xxxxxxxx",
  "model": "claude-sonnet-4-5",
  "enabled": true,
  "isDefault": true
}
```

其他端点:`GET /api/admin/providers` 列表、`PUT /api/admin/providers/{id}` 更新、`DELETE /api/admin/providers/{id}` 删除、`POST /api/admin/providers/{id}/set-default` 设为默认。

## 使用

- `POST /api/ai/analyze/{newsId}?force=false` → SSE(`text/event-stream`):增量文本块,结束事件 data 为 `__DONE__`。
  - 命中缓存(`ai_status=DONE`)且未 force 时直接回流全文,不消耗 token;`force=true` 重新生成。
  - 结果写回 `news_item.ai_summary`(DONE / FAILED / PENDING)。
- `POST /api/news/{id}/refresh-ai` → 等价 `analyze(id, force=true)`。

## 限制与错误

- 默认 provider 规则:`enabled=true` 且 `is_default=1` 唯一;未配置 → `400 请先配置 AI 厂商(默认启用)`。
- 全局限流:同一 IP 10 分钟内最多 5 次;全局同时只允许 1 个解读流 → `429`。
- 资讯不存在 → `404`。流式中断时输出 `[流式中断] <原因>` 并结束(状态落 DONE 但是错误文本)。
- 用户消息 = title + summary + content 截断 6000 字符;系统提示词固定中文模板(输出 5 段结构、300 字内)。
