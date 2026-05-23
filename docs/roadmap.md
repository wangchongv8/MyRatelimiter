# Roadmap

## 动态配置（待实现）

当前限流参数（`permits`、`intervalSeconds`）在客户端构造时通过 `RateLimiterBuilder` 硬编码，随 ARGV 每次传给 Redis Lua 脚本。修改限流规则需要重新部署客户端。

### 优化方向

独立配置服务动态下发限流规则：

- 配置方（配置中心 / 管理后台）将规则写入 Redis（如 Hash: `rl:config:{key} → {permits, interval_seconds}`）
- Lua 脚本执行前从 Redis 读取配置，而非从 ARGV 获取
- 客户端只需传业务 key，不再关心限流参数
- 配置变更即时生效，无需重启客户端

### 待细化

- 配置存储结构（per-key 粒度 vs 全局模板 + 覆盖）
- SDK 侧 API 变化（移除 `permits`/`perSecond`，改为绑定配置 key）
- Lua 脚本兼容性（不破坏现有 ARGV 结构或平滑迁移）
- 配置缺失时的兜底策略

---

## 监控指标（待实现）

当前 SDK 不暴露任何运行时指标，用户无法感知每个 key 的限流状态、拒绝率、Redis 延迟等。

### 优化方向

- **限流指标**：每个 key 的放行/拒绝次数、当前已消耗许可数、剩余许可数
- **性能指标**：`eval` 调用延迟（P50/P99）、Redis 连接池状态
- **算法特有指标**：TokenBucket 当前令牌数、LeakyBucket 当前水位
- **指标暴露**：支持接入 Micrometer / Prometheus，或自定义回调
- **轻量模式**：不引入重依赖，可选开启

### 待细化

- 指标收集方式（SDK 内存计数后定期上报 vs 直接读 Redis）
- 与动态配置的配合（配置中心读取指标做出自动调整）
- 是否影响 Lua 脚本设计（如脚本内计数并返回）
- 对性能的影响（指标收集不能成为瓶颈）

---

## Lua 精确返回 retry-after（待实现）

当前 `acquire` 的阻塞等待依赖指数退避（1ms→2ms→4ms→...→200ms）。每次退避后重试都需要一次 Redis `eval` 调用——被拒绝→park→重试→再被拒绝→park→... 直到放行或超时，中途可能浪费多轮 Redis 往返。

### 优化方向

Lua 脚本在拒绝时不止返回 `0`，而是返回 `{0, wait_ms}`（被拒绝 + 还需等多少毫秒）。Java 侧解析后 `LockSupport.parkNanos` 一次等待到位，醒来重试一次即成功。

```lua
-- token_bucket.lua 拒绝分支改为：
if tokens >= requested then
    return {1}            -- 放行
end
local deficit = requested - tokens
local wait_ms = math.ceil(deficit / rate * 1000)
return {0, wait_ms}       -- 拒绝 + 还需等多久
```

- TokenBucket：`deficit / rate * 1000`（缺多少令牌，等多久填充）
- LeakyBucket：`excess / rate * 1000`（超出容量多少，等多久漏掉）
- FixedWindow：`TTL * 1000`（等窗口重置）
- SlidingWindow：`(now - oldest_entry_time + window)` 或退化到 TTL
- SlidingLog：`(now - oldest_ZSET_score + window)`

### 待细化

- `RedisOperations.eval()` 返回类型需从 `Long` 升级为能承载列表返回值（如 `Object` 或新增 `evalList` 方法）
- 现有 SPI 实现（Jedis/Redisson）如何适配 Lua 返回 table 的解析
- 向后兼容：SDK 升级后旧 Lua 脚本、自定义 `RedisOperations` 实现如何兼容
- 回退策略：如果 Lua 脚本未升级（仍返回单个 `0`），Java 退化为当前指数退避
