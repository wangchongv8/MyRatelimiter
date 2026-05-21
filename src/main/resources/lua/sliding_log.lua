-- KEYS[1]: key (Redis ZSET)
-- ARGV[1]: 窗口大小（毫秒）
-- ARGV[2]: 每个窗口的最大请求数
-- ARGV[3]: 唯一日志条目 ID（来自 Java 的 System.nanoTime()）
-- ARGV[4]: 请求的许可数量（每条单独作为 ZSET 条目添加）
-- 返回: 1 允许, 0 拒绝
--
-- 逐请求 ZSET 方案：每条被允许的请求都作为 ZSET 条目记录，时间戳为
-- score。不同于 SlidingWindow 的子窗口计数器，这里存储每一次请求，
-- 精度极高，代价是高流量下内存无上限。
--
-- 适用于需要精确审计每次请求的场景，而非仅做近似限流。
-- 大多数场景下 SlidingWindow 是更合适的选择。
--
-- 窗口边界判断（score）使用 redis.call('TIME')，避免应用实例间的
-- 时钟偏移。来自 Java 的 entry_id 仍使用 System.nanoTime()，
-- 因为只需要保证唯一性，不需要时间准确性。

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local entry_id = ARGV[3]
local requested = tonumber(ARGV[4])

-- 获取 Redis 服务器时间作为窗口边界判断。
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

-- 删除已滑出窗口的条目。
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 统计窗口内的条目数。
local count = redis.call('ZCARD', key)
if count + requested <= limit then
    -- 每个许可作为独立的 ZSET 条目，保证 member 唯一。
    -- 同一批次中的条目 score（时间戳）相同。
    for i = 1, requested do
        redis.call('ZADD', key, now, entry_id .. ':' .. i)
    end
    -- TTL：窗口 + 1s 冗余。如果该时间段内无请求到达，整个 ZSET
    -- 被自动删除，为不活跃的 key 释放内存。
    redis.call('PEXPIRE', key, window + 1000)
    return 1
end

return 0
