-- KEYS[1]: bucket key (Redis Hash)
-- ARGV[1]: 最大令牌数（桶容量）
-- ARGV[2]: 填充速率（每秒填充令牌数，如 "10.0"）
-- ARGV[3]: 请求的许可数量
-- 返回: 1 允许, 0 拒绝
--
-- 使用 redis.call('TIME') 获取 Redis 服务器时钟，避免不同应用实例间
-- 的时钟偏移问题。

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

-- 获取 Redis 服务器时间，转换为毫秒。
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

-- 读取当前桶状态。首次访问时两个字段均为 nil。
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- 首次请求：初始化桶为满容量。
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- 按距上次填充的经过时间比例补充令牌。
-- 使用浮点除法保证平滑填充；不超过桶容量。
local elapsed_ms = now - last_refill
if elapsed_ms > 0 then
    local refill = (elapsed_ms / 1000.0) * rate
    tokens = math.min(capacity, tokens + refill)
end

if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill_time', now)
    -- TTL：空桶填满所需时间 + 1s 冗余，防止时钟抖动导致提前过期。
    local ttl = math.ceil(capacity / rate) + 1
    if ttl < 1 then ttl = 1 end
    redis.call('EXPIRE', key, ttl)
    return 1
end

-- 拒绝：不更新任何状态。下次请求从同一个 last_refill_time 重新计算
-- 经过时间，可能累积足够的令牌后通过。
return 0
