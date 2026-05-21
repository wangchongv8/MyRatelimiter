-- KEYS[1]: key (Redis Hash)
-- ARGV[1]: 窗口大小（毫秒）
-- ARGV[2]: 每个窗口的最大请求数
-- ARGV[3]: 请求的许可数量
-- 返回: 1 允许, 0 拒绝
--
-- 子窗口计数器方案：将滑动窗口切分为固定大小的桶（最多 ~10 个）。
-- 每个桶累积请求计数。查询时只统计与当前窗口有时间重叠的桶。
-- 过期的桶在求和过程中惰性删除。
--
-- 内存：O(桶个数) — 有界常量，与请求速率无关。
-- CPU：O(桶个数) — 每次请求最多遍历 ~10 个 hash field。
--
-- 精度：同一子窗口内（100–200ms）的请求被视为一个原子单元。
-- 子窗口要么完全在滑动窗口内，要么完全在外。这会引入 ±1 格子
-- 的计数误差，这是子窗口计数器方案相比逐请求 ZSET 方案的
-- 标准 trade-off。
--
-- 使用 redis.call('TIME') 获取 Redis 服务器时钟，避免不同应用实例间
-- 的时钟偏移问题。

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

-- 获取 Redis 服务器时间，转换为毫秒。
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

-- 子窗口粒度：目标 ~10 个桶/窗口，最低 100ms。
-- 更细的粒度意味着更高的精度，但也需要遍历更多的 hash field。
local sub_size = math.max(100, math.floor(window / 10))
local current_bucket = math.floor(now / sub_size)
local oldest_valid = math.floor((now - window) / sub_size)

-- 遍历所有桶，求和有效桶并惰性删除过期的。
local buckets = redis.call('HGETALL', key)
local total = 0

for i = 1, #buckets, 2 do
    local bucket_time = tonumber(buckets[i])
    local count = tonumber(buckets[i + 1])
    if bucket_time < oldest_valid then
        redis.call('HDEL', key, buckets[i])
    else
        total = total + count
    end
end

if total + requested <= limit then
    redis.call('HINCRBY', key, current_bucket, requested)
    -- TTL：窗口 + 1s 冗余。如果该时间段内无请求到达，整个 hash
    -- 被自动删除，为不活跃的 key 释放内存。
    redis.call('PEXPIRE', key, window + 1000)
    return 1
end

return 0
